/*
    Copyright 2016-2022 melonDS team

    This file is part of melonDS.

    melonDS is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free
    Software Foundation, either version 3 of the License, or (at your option)
    any later version.

    melonDS is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
    FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with melonDS. If not, see http://www.gnu.org/licenses/.
*/

#include <stdio.h>
#include <string.h>

#include "SaveManager.h"
#include "EmulatorMessageQueueJNI.h"
#include "Platform.h"

// A failing flush is retried on every worker tick, so the log is rate limited
// after the first report of an episode.
static const u32 FlushFailureReportInterval = 50;

// Truncates an open file to a given length. Platform has no entry point for this, and on
// Android a FileHandle is a FILE* (see PlatformAndroid.cpp); the host test's platform stub
// keeps the same rule so that this cast stays honest there too.
static bool TruncateFile(FileHandle* file, u32 length)
{
    FILE* stdFile = reinterpret_cast<FILE*>(file);
    if (fflush(stdFile) != 0)
        return false;

    return ftruncate(fileno(stdFile), static_cast<off_t>(length)) == 0;
}

// Retry backoff, in worker ticks: 1, 2, 4, 8, then held at the cap. It deliberately
// lives in the attempt schedule and not in the sleep, because run() only checks
// Running after Sleep: a longer sleep would lengthen every shutdown too.
static const u32 MaxRetryDelayTicks = 16;

SaveManager::SaveManager(std::string path)
{
    SecondaryBuffer = nullptr;
    SecondaryBufferLength = 0;
    SecondaryBufferLock = Platform::Mutex_Create();

    Running = false;

    Path = path;

    Buffer = nullptr;
    Length = 0;
    FlushRequested = false;

    FlushVersion = 0;
    PreviousFlushVersion = 0;
    TimeAtLastFlushRequest = 0;

    ConsecutiveFlushFailures = 0;

    if (!path.empty())
    {
        Running = true;
        Thread = Platform::Thread_Create(std::bind(&SaveManager::run, this));
    }
}

SaveManager::~SaveManager()
{
    if (Running)
    {
        Running = false;
        Platform::Thread_Wait(Thread);
        // This runs whenever the manager is released, which is at every ROM or firmware
        // change on a live instance, not only when the application exits.
        const u32 failuresBeforeRelease = ConsecutiveFlushFailures.load();
        if (!FlushSecondaryBuffer())
            Log(LogLevel::Error, "SaveManager: last flush of %s before release failed; generation %u stays unwritten\n",
                Path.c_str(), FlushVersion);
        else if (failuresBeforeRelease != 0)
            // Without this the log would end on an alarm that was in fact resolved.
            Log(LogLevel::Info, "SaveManager: flush of %s recovered on the last flush before release\n",
                Path.c_str());
    }

    SecondaryBuffer = nullptr;

    Platform::Mutex_Free(SecondaryBufferLock);
    // An empty path means no worker was ever created (see the constructor), and Thread_Free
    // deletes what it is given.
    if (Thread)
        Platform::Thread_Free(Thread);

    Buffer = nullptr;
}

std::string SaveManager::GetPath()
{
    return Path;
}

void SaveManager::SetPath(std::string path, bool reload)
{
    Path = path;
    // The failure episode belonged to the previous path; it does not carry over.
    ConsecutiveFlushFailures = 0;

    if (reload)
    {
        FileHandle* f = Platform::OpenFile(Path, FileMode::Read);
        if (f)
        {
            if (u32 length = Platform::FileLength(f); length != Length)
            { // If the new file is a different size, we need to re-allocate the buffer.
                Length = length;
                Buffer = std::make_unique<u8[]>(Length);
            }

            FileRead(Buffer.get(), 1, Length, f);
            CloseFile(f);
        }
    }
    else
        FlushRequested = true;
}

void SaveManager::RequestFlush(const u8* savedata, u32 savelen, u32 writeoffset, u32 writelen)
{
    if (!savedata || savelen == 0 || writeoffset >= savelen || writelen > savelen)
    {
        Log(LogLevel::Error,
            "SaveManager: rejected invalid flush range offset=%u length=%u save=%u\n",
            writeoffset, writelen, savelen);
        return;
    }

    if (Length != savelen)
    {
        Length = savelen;
        Buffer = std::make_unique<u8[]>(Length);

        memcpy(Buffer.get(), savedata, Length);
    }
    else
    {
        const u32 bytesToEnd = savelen - writeoffset;
        if (writelen > bytesToEnd)
        {
            memcpy(&Buffer[writeoffset], &savedata[writeoffset], bytesToEnd);
            const u32 wrappedLength = writelen - bytesToEnd;
            memcpy(&Buffer[0], &savedata[0], wrappedLength);
        }
        else
        {
            memcpy(&Buffer[writeoffset], &savedata[writeoffset], writelen);
        }
    }

    FlushRequested = true;
}

void SaveManager::CheckFlush()
{
    if (!FlushRequested) return;

    Platform::Mutex_Lock(SecondaryBufferLock);

    Log(LogLevel::Info, "SaveManager: Flush requested\n");

    if (SecondaryBufferLength != Length)
    {
        SecondaryBufferLength = Length;
        SecondaryBuffer = std::make_unique<u8[]>(SecondaryBufferLength);
    }

    memcpy(SecondaryBuffer.get(), Buffer.get(), Length);

    FlushRequested = false;
    FlushVersion++;
    TimeAtLastFlushRequest = time(nullptr);

    Platform::Mutex_Unlock(SecondaryBufferLock);
}

void SaveManager::run()
{
    // Backoff state stays local to this loop: nothing else schedules retries.
    u32 retryDelayTicks = 0;
    u32 ticksUntilRetry = 0;

    for (;;)
    {
        Platform::Sleep(100000);

        if (!Running) return;

        // Skip ticks instead of sleeping longer, so shutdown still costs one tick.
        // Counted down before the debounce, so a backoff already under way overlaps the
        // debounce of a new generation instead of being added to it.
        if (ticksUntilRetry > 0)
        {
            ticksUntilRetry--;
            continue;
        }

        // We debounce for two seconds after last flush request to ensure that writing has finished.
        if (TimeAtLastFlushRequest == 0 || difftime(time(nullptr), TimeAtLastFlushRequest) < 2)
        {
            continue;
        }

        const u32 failuresBeforeAttempt = ConsecutiveFlushFailures.load();
        if (FlushSecondaryBuffer())
        {
            retryDelayTicks = 0;
            // Only report a recovery if this worker had already reported a failure.
            if (failuresBeforeAttempt != 0)
                Log(LogLevel::Info, "SaveManager: flush to %s recovered\n", Path.c_str());
        }
        else
        {
            // The generation stays pending and eligible, so report the first failure of
            // an episode and then rate limit the identical repeats.
            retryDelayTicks = retryDelayTicks ? retryDelayTicks * 2 : 1;
            if (retryDelayTicks > MaxRetryDelayTicks) retryDelayTicks = MaxRetryDelayTicks;
            ticksUntilRetry = retryDelayTicks;

            const u32 failures = ConsecutiveFlushFailures.fetch_add(1) + 1;
            if (failures == 1)
                // Once per episode, not once per retry: the user gets a single toast telling
                // them the save data is not reaching storage, not one every few seconds.
                MelonDSAndroid::fireEmulatorEvent(MelonDSAndroid::EVENT_SAVE_FLUSH_FAILED);
            if (failures == 1 || (failures % FlushFailureReportInterval) == 0)
                Log(LogLevel::Error, "SaveManager: flush to %s failed (%u consecutive attempts)\n",
                    Path.c_str(), failures);
        }
    }
}

bool SaveManager::FlushSecondaryBuffer(u8* dst, u32 dstLength)
{
    // Nothing was ever staged. For a file flush there is no pending generation to report,
    // but a caller asking for memory got nothing copied and must not be told otherwise.
    if (!SecondaryBuffer) return dst == nullptr;

    // When flushing to a file, there's no point in re-writing the exact same data.
    if (!dst && !NeedsFlush()) return true;
    // When flushing to memory, we don't know if dst already has any data so we only check that we CAN flush.
    if (dst && dstLength < SecondaryBufferLength) return false;

    Platform::Mutex_Lock(SecondaryBufferLock);
    if (dst)
    {
        memcpy(dst, SecondaryBuffer.get(), SecondaryBufferLength);
    }
    else
    {
        // Preserve is what keeps the destination from being emptied at open time: the direct
        // path then gets "r+b" and the SAF path gets "rw", and neither truncates. A process
        // kill half way through the write therefore leaves a full-length save holding a mix of
        // old and new data -- which the games' own checksums and double banks can fall back
        // on -- instead of the empty file a truncating open leaves behind (upstream #1594,
        // #1531). The in-RAM buffer dies with the process, so there is nothing to retry with.
        const FileMode preservingMode = static_cast<FileMode>(FileMode::Read | FileMode::Write | FileMode::Preserve);
        FileHandle* f = Platform::OpenFile(Path, preservingMode);
        if (!f)
            // Not every SAF provider is required to honour "rw". Losing the save outright is
            // worse than losing the guarantee, so fall back to the old truncating open.
            f = Platform::OpenFile(Path, FileMode::Write);

        bool flushed = false;
        if (f)
        {
            const bool written = FileWrite(SecondaryBuffer.get(), SecondaryBufferLength, 1, f) == 1;
            // Nothing truncates any more, so a destination left over from another emulator (or
            // from a larger save type) would keep its tail and be read back at the wrong length.
            const bool trimmed = !written || FileLength(f) <= SecondaryBufferLength
                    || TruncateFile(f, SecondaryBufferLength);
            // Always close the file, including after a short write.
            const bool closed = CloseFile(f);
            flushed = written && trimmed && closed;
            if (flushed)
                Log(LogLevel::Info, "SaveManager: Wrote %u bytes to %s\n", SecondaryBufferLength, Path.c_str());
        }
        if (!flushed)
        {
            // Keep this generation and its debounce timestamp eligible for retry.
            Platform::Mutex_Unlock(SecondaryBufferLock);
            return false;
        }
    }
    PreviousFlushVersion = FlushVersion;
    TimeAtLastFlushRequest = 0;
    // Cleared where the generation is acknowledged, so a flush to memory clears it as well.
    ConsecutiveFlushFailures = 0;
    Platform::Mutex_Unlock(SecondaryBufferLock);
    return true;
}

bool SaveManager::NeedsFlush()
{
    return FlushVersion != PreviousFlushVersion;
}
