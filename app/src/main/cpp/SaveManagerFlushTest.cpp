// Host-side test for SaveManager's flush to disk. It builds the real SaveManager.cpp and the
// real FileMode translation (FileModeString.cpp) against a minimal Platform implementation
// defined at the bottom of this file, so no NDK, no JNI, no emulator and no device are needed.
// See tools/host-tests.sh.
//
// The regression it demonstrates (upstream #1594 "Picross save wiped", #1531): the flush used
// to open the destination with FileMode::Write, which truncates the file at open time. The save
// data only lives in RAM until the write completes, so a process kill in that window -- Android
// kills backgrounded emulators routinely -- left the player with a save file that had been
// emptied before a single byte of the new contents landed. The retry logic added later cannot
// help: the buffer died with the process.
//
// test_legacy_truncating_open_loses_data() measures the old behaviour on every run (0 bytes
// left when the process dies right after the open, half the file when it dies half way through
// the write), and test_preserving_flush_survives_a_kill() shows the file staying at full length
// through the same kill with the mode the flush asks for now.

#include "SaveManager.h"
#include "EmulatorMessageQueueJNI.h"
#include "FileModeString.h"
#include "WfcSaveRange.h"

#include <csignal>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <sys/stat.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
#include <vector>

using namespace melonDS;
using namespace melonDS::Platform;

static const u32 SaveLength = 4096;
static const u8 OldByte = 0xAA;
static const u8 NewByte = 0x55;

static int failures = 0;

static void expect(bool condition, const char* description)
{
    if (!condition)
    {
        std::fprintf(stderr, "FAIL: %s\n", description);
        failures++;
    }
}

static void expectSize(const std::string& path, long expected, const char* description)
{
    struct stat info;
    if (stat(path.c_str(), &info) != 0)
    {
        std::fprintf(stderr, "FAIL: %s (could not stat %s)\n", description, path.c_str());
        failures++;
        return;
    }

    if (info.st_size != expected)
    {
        std::fprintf(stderr, "FAIL: %s (expected %ld bytes, found %lld)\n", description, expected, (long long) info.st_size);
        failures++;
    }
}

// ---------------------------------------------------------------------------
// Stub state, driven by the tests
// ---------------------------------------------------------------------------

// When non-zero, the next FileWrite writes this many bytes, flushes them, and kills the
// process: the stand-in for Android killing the app half way through a save.
static u32 killAfterBytes = 0;
static int openAttempts = 0;
static int saveFlushFailedEvents = 0;

static std::string testDir;

static std::string testPath(const char* name)
{
    return testDir + "/" + name;
}

static void writeFileOfBytes(const std::string& path, u8 value, u32 length)
{
    FILE* f = fopen(path.c_str(), "wb");
    std::vector<u8> data(length, value);
    fwrite(data.data(), length, 1, f);
    fclose(f);
}

static std::vector<u8> readWholeFile(const std::string& path)
{
    std::vector<u8> contents;
    FILE* f = fopen(path.c_str(), "rb");
    if (!f)
        return contents;

    u8 chunk[512];
    size_t read;
    while ((read = fread(chunk, 1, sizeof(chunk), f)) > 0)
        contents.insert(contents.end(), chunk, chunk + read);

    fclose(f);
    return contents;
}

static bool allBytesAre(const std::vector<u8>& data, size_t from, size_t to, u8 value)
{
    for (size_t i = from; i < to && i < data.size(); i++)
    {
        if (data[i] != value)
            return false;
    }

    return true;
}

// Stages one generation of save data in a manager without waiting for the worker.
static void stage(SaveManager& manager, u8 value)
{
    std::vector<u8> data(SaveLength, value);
    manager.RequestFlush(data.data(), SaveLength, 0, SaveLength);
    manager.CheckFlush();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

static void test_flush_writes_the_staged_buffer()
{
    const std::string path = testPath("plain.sav");
    writeFileOfBytes(path, OldByte, SaveLength);

    {
        SaveManager manager(path);
        stage(manager, NewByte);
        expect(manager.FlushSecondaryBuffer(), "a plain flush reports success");
    }

    const std::vector<u8> contents = readWholeFile(path);
    expectSize(path, SaveLength, "a plain flush keeps the save at its own length");
    expect(allBytesAre(contents, 0, SaveLength, NewByte), "a plain flush writes the whole staged buffer");
}

static void test_flush_shrinks_an_oversized_destination()
{
    // Nothing truncates at open any more, so a destination left over from another emulator (or
    // from a larger save type) has to be trimmed explicitly, or the next reload would read the
    // stale tail back as part of the save.
    const std::string path = testPath("oversized.sav");
    writeFileOfBytes(path, OldByte, SaveLength * 2);

    {
        SaveManager manager(path);
        stage(manager, NewByte);
        expect(manager.FlushSecondaryBuffer(), "the flush over an oversized file reports success");
    }

    expectSize(path, SaveLength, "the flush trims a destination that was longer than the save");
    expect(allBytesAre(readWholeFile(path), 0, SaveLength, NewByte), "the trimmed file holds the staged buffer");
}

// Forks, opens the file with the given mode in the child, writes bytesBeforeKill bytes of the
// new data, flushes them to the OS and kills the child. Returns true if the child really was
// killed rather than exiting on its own.
static bool killChildWhileWriting(const std::string& path, FileMode mode, u32 bytesBeforeKill)
{
    fflush(nullptr);

    pid_t child = fork();
    if (child == 0)
    {
        FileHandle* f = Platform::OpenFile(path, mode);
        if (!f)
            _exit(3);

        if (bytesBeforeKill > 0)
        {
            std::vector<u8> data(bytesBeforeKill, NewByte);
            FileWrite(data.data(), bytesBeforeKill, 1, f);
            FileFlush(f);
        }

        raise(SIGKILL);
        _exit(4);
    }

    int status = 0;
    waitpid(child, &status, 0);
    return WIFSIGNALED(status) && WTERMSIG(status) == SIGKILL;
}

static void test_legacy_truncating_open_loses_data()
{
    const FileMode legacyMode = FileMode::Write;
    const std::string path = testPath("legacy.sav");

    // Killed right after the open, before any data was written.
    writeFileOfBytes(path, OldByte, SaveLength);
    expect(killChildWhileWriting(path, legacyMode, 0), "the child was killed (legacy mode, no write)");
    expectSize(path, 0, "FileMode::Write empties the save at open time: 0 bytes left");

    // Killed half way through the write.
    writeFileOfBytes(path, OldByte, SaveLength);
    expect(killChildWhileWriting(path, legacyMode, SaveLength / 2), "the child was killed (legacy mode, half written)");
    expectSize(path, SaveLength / 2, "FileMode::Write leaves only the part that made it out");
}

static void test_preserving_open_keeps_the_file_whole()
{
    const FileMode preservingMode = static_cast<FileMode>(FileMode::Read | FileMode::Write | FileMode::Preserve);
    const std::string path = testPath("preserving.sav");

    writeFileOfBytes(path, OldByte, SaveLength);
    expect(killChildWhileWriting(path, preservingMode, 0), "the child was killed (preserving mode, no write)");
    expectSize(path, SaveLength, "the preserving open leaves the previous save untouched");
    expect(allBytesAre(readWholeFile(path), 0, SaveLength, OldByte), "the previous save data is still there");

    writeFileOfBytes(path, OldByte, SaveLength);
    expect(killChildWhileWriting(path, preservingMode, SaveLength / 2), "the child was killed (preserving mode, half written)");
    expectSize(path, SaveLength, "the preserving open keeps the save at full length through a kill");

    const std::vector<u8> contents = readWholeFile(path);
    expect(allBytesAre(contents, 0, SaveLength / 2, NewByte), "the half that was written is the new data");
    expect(allBytesAre(contents, SaveLength / 2, SaveLength, OldByte), "the half that was not written is still the old data");
}

static void test_preserving_flush_survives_a_kill()
{
    // The same kill, this time through the real SaveManager::FlushSecondaryBuffer().
    const std::string path = testPath("flush-kill.sav");
    writeFileOfBytes(path, OldByte, SaveLength);

    fflush(nullptr);

    pid_t child = fork();
    if (child == 0)
    {
        SaveManager manager(path);
        stage(manager, NewByte);
        killAfterBytes = SaveLength / 2;
        manager.FlushSecondaryBuffer();
        _exit(4);
    }

    int status = 0;
    waitpid(child, &status, 0);
    expect(WIFSIGNALED(status) && WTERMSIG(status) == SIGKILL, "the flushing child was killed mid-write");
    expectSize(path, SaveLength, "a kill during the flush leaves a full-length save, not an empty one");

    const std::vector<u8> contents = readWholeFile(path);
    expect(allBytesAre(contents, 0, SaveLength / 2, NewByte), "the flush had written the first half");
    expect(allBytesAre(contents, SaveLength / 2, SaveLength, OldByte), "the rest is still the previous save");
}

static void test_manager_without_a_path_is_destroyed_safely()
{
    // No path means no worker thread, and the destructor used to free a thread handle that was
    // never assigned (MelonInstance passes an empty firmware path when none is configured).
    SaveManager manager("");
    expect(manager.GetPath().empty(), "a manager with no path keeps an empty path");
}

static void test_failing_flush_reports_once_per_episode()
{
    // A path under a directory that does not exist: every open fails.
    const std::string path = testDir + "/missing-directory/unwritable.sav";

    openAttempts = 0;
    saveFlushFailedEvents = 0;

    {
        SaveManager manager(path);
        stage(manager, NewByte);

        // The worker debounces for two seconds after the last flush request, then retries with
        // a backoff. Wait long enough for the first attempt plus at least one retry.
        for (int i = 0; i < 60 && (saveFlushFailedEvents == 0 || openAttempts < 4); i++)
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    expect(openAttempts >= 4, "the worker keeps retrying a failing flush");
    expect(saveFlushFailedEvents == 1, "the user is told once per failure episode, not once per retry");
}

// A live WFC edit changes both AP copies. Only the first RequestFlush copies the whole
// image; subsequent requests must cover the extended blocks as well as the basic ones.
static void test_consecutive_wfc_edits_keep_extended_slots()
{
    for (bool generated : {false, true})
    {
        const u32 extendedOffset = 0x1F400;
        const u32 basicOffset = 0x1FA00;
        std::vector<u8> firmware(0x20000, 0x44);
        const u32 savedOffset = generated ? extendedOffset : 0;
        const u32 savedLength = generated ? 0x900 : firmware.size();
        const std::string path = testPath(generated ? "wfc-generated.bin" : "wfc-firmware.bin");
        SaveManager manager(path);
        manager.RequestFlush(firmware.data() + savedOffset, savedLength, 0, savedLength);
        manager.CheckFlush();
        expect(manager.FlushSecondaryBuffer(), "initial WFC image is written");

        for (u32 slot = 0; slot < 3; slot++)
        {
            firmware[extendedOffset + slot * 0x200] = 0xA0 + slot;
            firmware[extendedOffset + slot * 0x200 + 0x1FE] = 0xB0 + slot;
            firmware[basicOffset + slot * 0x100] = 0xC0 + slot;
            const auto range = MelonDSAndroid::wfcSaveRange(basicOffset, 0x300, extendedOffset, true);
            manager.RequestFlush(firmware.data() + savedOffset, savedLength,
                                 range.offset - savedOffset, range.length);
            manager.CheckFlush();
            expect(manager.FlushSecondaryBuffer(), "consecutive WFC edit is written");
            const std::vector<u8> expected(firmware.begin() + savedOffset,
                                          firmware.begin() + savedOffset + savedLength);
            expect(readWholeFile(path) == expected,
                   "every WFC edit persists all extended slots and their checksums");
        }
    }
}

static void test_invalid_flush_range_is_rejected()
{
    const std::string path = testPath("invalid-range.sav");
    std::vector<u8> original(32, 0x41);
    std::vector<u8> attacker(32, 0x99);
    SaveManager manager(path);
    manager.RequestFlush(original.data(), original.size(), 0, original.size());
    manager.RequestFlush(attacker.data(), attacker.size(), 33, 4);
    manager.CheckFlush();
    expect(manager.FlushSecondaryBuffer(), "valid data still flushes after an invalid range");
    expect(readWholeFile(path) == original, "out-of-range firmware flush is rejected without copying");
}

int main()
{
    char directoryTemplate[] = "/tmp/melonds-savemanager-testXXXXXX";
    const char* directory = mkdtemp(directoryTemplate);
    if (!directory)
    {
        std::fprintf(stderr, "FAIL: could not create a temporary directory\n");
        return EXIT_FAILURE;
    }
    testDir = directory;

    test_flush_writes_the_staged_buffer();
    test_flush_shrinks_an_oversized_destination();
    test_legacy_truncating_open_loses_data();
    test_preserving_open_keeps_the_file_whole();
    test_preserving_flush_survives_a_kill();
    test_manager_without_a_path_is_destroyed_safely();
    test_failing_flush_reports_once_per_episode();
    test_consecutive_wfc_edits_keep_extended_slots();
    test_invalid_flush_range_is_rejected();

    if (failures != 0)
    {
        std::fprintf(stderr, "%d check(s) failed (files left in %s)\n", failures, testDir.c_str());
        return EXIT_FAILURE;
    }

    std::printf("SaveManager flush: all checks passed\n");
    return EXIT_SUCCESS;
}

// ---------------------------------------------------------------------------
// Minimal Platform implementation
//
// Only the entry points SaveManager actually uses. Files are plain FILE* handles reached
// through the same FileMode translation the app uses, which is the point of the exercise:
// the test would not see the truncating open otherwise.
// ---------------------------------------------------------------------------

namespace melonDS::Platform
{
    struct Mutex
    {
        std::mutex mutex;
    };

    struct Thread
    {
        std::thread thread;
    };

    FileHandle* OpenFile(const std::string& path, FileMode mode)
    {
        openAttempts++;

        if (path.empty())
            return nullptr;

        const bool fileExists = access(path.c_str(), F_OK) == 0;
        const std::string modeString = MelonDSAndroid::GetStdioModeString(mode, fileExists);
        if ((mode & FileMode::NoCreate) && !fileExists)
            return nullptr;

        return reinterpret_cast<FileHandle*>(fopen(path.c_str(), modeString.c_str()));
    }

    bool CloseFile(FileHandle* file)
    {
        return fclose(reinterpret_cast<FILE*>(file)) == 0;
    }

    u64 FileRead(void* data, u64 size, u64 count, FileHandle* file)
    {
        return fread(data, size, count, reinterpret_cast<FILE*>(file));
    }

    u64 FileWrite(const void* data, u64 size, u64 count, FileHandle* file)
    {
        FILE* stdFile = reinterpret_cast<FILE*>(file);

        if (killAfterBytes > 0)
        {
            // Write the first part of the buffer, push it out to the OS so that it is really in
            // the file, and die: the emulator's in-RAM copy goes with the process.
            const u32 partial = killAfterBytes < size * count ? killAfterBytes : (u32) (size * count);
            fwrite(data, partial, 1, stdFile);
            fflush(stdFile);
            raise(SIGKILL);
        }

        return fwrite(data, size, count, stdFile);
    }

    bool FileFlush(FileHandle* file)
    {
        return fflush(reinterpret_cast<FILE*>(file)) == 0;
    }

    u64 FileLength(FileHandle* file)
    {
        FILE* stdFile = reinterpret_cast<FILE*>(file);
        const long position = ftell(stdFile);
        fseek(stdFile, 0, SEEK_END);
        const long length = ftell(stdFile);
        fseek(stdFile, position, SEEK_SET);
        return length;
    }

    Mutex* Mutex_Create()
    {
        return new Mutex;
    }

    void Mutex_Free(Mutex* mutex)
    {
        delete mutex;
    }

    void Mutex_Lock(Mutex* mutex)
    {
        mutex->mutex.lock();
    }

    void Mutex_Unlock(Mutex* mutex)
    {
        mutex->mutex.unlock();
    }

    Thread* Thread_Create(std::function<void()> func)
    {
        Thread* thread = new Thread;
        thread->thread = std::thread(std::move(func));
        return thread;
    }

    void Thread_Wait(Thread* thread)
    {
        if (thread->thread.joinable())
            thread->thread.join();
    }

    void Thread_Free(Thread* thread)
    {
        delete thread;
    }

    void Sleep(u64 usecs)
    {
        std::this_thread::sleep_for(std::chrono::microseconds(usecs));
    }

    void Log(LogLevel level, const char* fmt, ...)
    {
        (void) level;

        va_list args;
        va_start(args, fmt);
        vfprintf(stdout, fmt, args);
        va_end(args);
    }
}

namespace MelonDSAndroid
{
    void fireEmulatorEvent(int type, int dataLength, void* data)
    {
        (void) dataLength;
        (void) data;

        if (type == EVENT_SAVE_FLUSH_FAILED)
            saveFlushFailedEvents++;
    }
}
