/*
    Copyright 2016-2019 StapleButter

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

#include <cassert>
#include <chrono>
#include <dlfcn.h>
#include <filesystem>
#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <semaphore.h>
#include <pthread.h>
#include <unistd.h>
#include "pcap/pcap.h"
#include "Platform.h"
#include "MelonDS.h"
#include "MelonDSAudio.h"
#include "ROMManager.h"
#include "PlatformAndroid.h"
#include "MelonInstance.h"
#include "MelonLog.h"
#include "net/MPInterface.h"

using namespace melonDS;

namespace melonDS
{
namespace Platform
{
    typedef struct
    {
        int val;
        pthread_mutex_t mutex;
        pthread_cond_t cond;
    } AndroidSemaphore;

    typedef struct
    {
        pthread_t ID;
        std::function<void()> Func;

    } AndroidThread;

    typedef pthread_mutex_t AndroidMutex;

    void* ThreadEntry(void* data)
    {
        AndroidThread* thread = (AndroidThread*)data;
        thread->Func();
        return nullptr;
    }

    void SignalStop(StopReason reason, void* userdata)
    {
        MelonDSAndroid::eventMessenger->onEmulatorStop(reason);
    }

    constexpr char AccessMode(FileMode mode, bool fileExists)
    {
        if (mode & FileMode::Append)
            return  'a';

        if (!(mode & FileMode::Write))
            // If we're only opening the file for reading...
            return 'r';

        if (mode & (FileMode::NoCreate))
            // If we're not allowed to create a new file...
            return 'r'; // Open in "r+" mode (IsExtended will add the "+")

        if ((mode & FileMode::Preserve) && fileExists)
            // If we're not allowed to overwrite a file that already exists...
            return 'r'; // Open in "r+" mode (IsExtended will add the "+")

        return 'w';
    }

    constexpr bool IsExtended(FileMode mode)
    {
        // fopen's "+" flag always opens the file for read/write
        return (mode & FileMode::ReadWrite) == FileMode::ReadWrite;
    }

    static std::string GetModeString(FileMode mode, bool fileExists)
    {
        std::string modeString;

        modeString += AccessMode(mode, fileExists);

        if (IsExtended(mode))
            modeString += '+';

        if (!(mode & FileMode::Text))
            modeString += 'b';

        return modeString;
    }

    FileHandle* OpenFile(const std::string& path, FileMode mode)
    {
        if ((mode & (FileMode::ReadWrite | FileMode::Append)) == FileMode::None)
        { // If we aren't reading or writing, then we can't open the file
            Log(LogLevel::Error, "Attempted to open \"%s\" in neither read nor write mode (FileMode 0x%x)\n", path.c_str(), mode);
            return nullptr;
        }

        if (path.empty())
        {
            return nullptr;
        }

        // If it's a standard absolute file path, open it a simple file. If not, delegate to the file handler
        if (path[0] == '/')
        {
            bool fileExists = access(path.c_str(), F_OK) == 0;
            std::string modeString = GetModeString(mode, fileExists);
            bool mustExist = (mode & FileMode::NoCreate) != 0;
            if (mustExist)
            {
                if (fileExists)
                    return reinterpret_cast<FileHandle*>(fopen(path.c_str(), modeString.c_str()));
                else
                    return nullptr;
            }
            else
                return reinterpret_cast<FileHandle*>(fopen(path.c_str(), modeString.c_str()));
        }
        else
        {
            return reinterpret_cast<FileHandle*>(MelonDSAndroid::fileHandler->open(path.c_str(), mode));
        }
    }

    FileHandle* OpenLocalFile(const std::string& path, FileMode mode)
    {
        if (path.empty())
            return nullptr;

        // Always open file as absolute
        return OpenFile(path, mode);
    }

    FileHandle* OpenInternalFile(const std::string path, FileMode mode)
    {
        std::filesystem::path fullFilePath = MelonDSAndroid::internalFilesDir;
        fullFilePath /= path;
        return OpenFile(fullFilePath, mode);
    }

    bool CloseFile(FileHandle* file)
    {
        return fclose(reinterpret_cast<FILE *>(file)) == 0;
    }

    bool IsEndOfFile(FileHandle* file)
    {
        return feof(reinterpret_cast<FILE *>(file)) != 0;
    }

    bool FileReadLine(char* str, int count, FileHandle* file)
    {
        return fgets(str, count, reinterpret_cast<FILE *>(file)) != nullptr;
    }

    bool FileExists(const std::string& name)
    {
        FileHandle* f = OpenFile(name, FileMode::Read);
        if (!f) return false;
        CloseFile(f);
        return true;
    }

    bool LocalFileExists(const std::string& name)
    {
        FileHandle* f = OpenLocalFile(name, FileMode::Read);
        if (!f) return false;
        CloseFile(f);
        return true;
    }

    bool CheckFileWritable(const std::string& filepath)
    {
        FileHandle* file = Platform::OpenFile(filepath.c_str(), FileMode::Read);

        if (file)
        {
            // if the file exists, check if it can be opened for writing.
            Platform::CloseFile(file);
            file = Platform::OpenFile(filepath.c_str(), FileMode::Append);
            if (file)
            {
                Platform::CloseFile(file);
                return true;
            }
            else return false;
        }
        else
        {
            // if the file does not exist, create a temporary file to check, to avoid creating an empty file.
            /*if (QTemporaryFile(filepath.c_str()).open())
            {
                return true;
            }
            else return false;*/
            // TODO: Check if folder is writable?
            return true;
        }
    }

    bool CheckLocalFileWritable(const std::string& name)
    {
        FileHandle* file = Platform::OpenLocalFile(name.c_str(), FileMode::Append);
        if (file)
        {
            Platform::CloseFile(file);
            return true;
        }
        else return false;
    }

    u64 FilePosition(FileHandle* file)
    {
        return ftell(reinterpret_cast<FILE *>(file));
    }

    bool FileSeek(FileHandle* file, s64 offset, FileSeekOrigin origin)
    {
        int stdorigin;
        switch (origin)
        {
            case FileSeekOrigin::Start: stdorigin = SEEK_SET; break;
            case FileSeekOrigin::Current: stdorigin = SEEK_CUR; break;
            case FileSeekOrigin::End: stdorigin = SEEK_END; break;
        }

        return fseek(reinterpret_cast<FILE *>(file), offset, stdorigin) == 0;
    }

    void FileRewind(FileHandle* file)
    {
        rewind(reinterpret_cast<FILE *>(file));
    }

    u64 FileRead(void* data, u64 size, u64 count, FileHandle* file)
    {
        return fread(data, size, count, reinterpret_cast<FILE *>(file));
    }

    bool FileFlush(FileHandle* file)
    {
        return fflush(reinterpret_cast<FILE *>(file)) == 0;
    }

    u64 FileWrite(const void* data, u64 size, u64 count, FileHandle* file)
    {
        return fwrite(data, size, count, reinterpret_cast<FILE *>(file));
    }

    u64 FileWriteFormatted(FileHandle* file, const char* fmt, ...)
    {
        if (fmt == nullptr)
            return 0;

        va_list args;
        va_start(args, fmt);
        u64 ret = vfprintf(reinterpret_cast<FILE *>(file), fmt, args);
        va_end(args);
        return ret;
    }

    u64 FileLength(FileHandle* file)
    {
        FILE* stdfile = reinterpret_cast<FILE *>(file);
        long pos = ftell(stdfile);
        fseek(stdfile, 0, SEEK_END);
        long len = ftell(stdfile);
        fseek(stdfile, pos, SEEK_SET);
        return len;
    }

    void Log(LogLevel level, const char* fmt, ...)
    {
        if (fmt == nullptr)
            return;

        va_list args;
        va_start(args, fmt);

        switch (level)
        {
            case LogLevel::Debug:
                __android_log_vprint(ANDROID_LOG_DEBUG, "melonDS", fmt, args);
                break;
            case LogLevel::Info:
                __android_log_vprint(ANDROID_LOG_INFO, "melonDS", fmt, args);
                break;
            case LogLevel::Warn:
                __android_log_vprint(ANDROID_LOG_WARN, "melonDS", fmt, args);
                break;
            case LogLevel::Error:
                __android_log_vprint(ANDROID_LOG_ERROR, "melonDS", fmt, args);
                break;
        }

        va_end(args);
    }

    Thread* Thread_Create(std::function<void()> func)
    {
        AndroidThread* data = new AndroidThread;
        data->Func = func;
        pthread_create(&data->ID, nullptr, ThreadEntry, data);
        return (Thread*) data;
    }

    void Thread_Free(Thread* thread)
    {
        delete (AndroidThread*)thread;
    }

    void Thread_Wait(Thread* thread)
    {
        pthread_t pthread = ((AndroidThread*) thread)->ID;
        pthread_join(pthread, NULL);
    }

    Semaphore* Semaphore_Create()
    {
        AndroidSemaphore* semaphore = (AndroidSemaphore*) malloc(sizeof(AndroidSemaphore));
        pthread_mutex_init(&semaphore->mutex, NULL);
        pthread_cond_init(&semaphore->cond, NULL);
        semaphore->val = 0;
        return (Semaphore*) semaphore;
    }

    void Semaphore_Free(Semaphore* sema)
    {
        AndroidSemaphore* semaphore = (AndroidSemaphore*) sema;
        pthread_mutex_destroy(&semaphore->mutex);
        pthread_cond_destroy(&semaphore->cond);
        free(semaphore);
    }

    void Semaphore_Reset(Semaphore* sema)
    {
        AndroidSemaphore* semaphore = (AndroidSemaphore*) sema;
        pthread_mutex_lock(&semaphore->mutex);
        semaphore->val = 0;
        pthread_cond_broadcast(&semaphore->cond);
        pthread_mutex_unlock(&semaphore->mutex);
    }

    void Semaphore_Wait(Semaphore* sema)
    {
        AndroidSemaphore* semaphore = (AndroidSemaphore*) sema;
        pthread_mutex_lock(&semaphore->mutex);
        while (semaphore->val == 0)
            pthread_cond_wait(&semaphore->cond, &semaphore->mutex);

        semaphore->val--;
        pthread_mutex_unlock(&semaphore->mutex);
    }

    bool Semaphore_TryWait(Semaphore* sema, int timeout_ms)
    {
        AndroidSemaphore* semaphore = (AndroidSemaphore*) sema;
        pthread_mutex_lock(&semaphore->mutex);
        bool result;

        if (semaphore->val > 0)
        {
            semaphore->val--;
            pthread_mutex_unlock(&semaphore->mutex);
            return true;
        }

        if (!timeout_ms)
        {
            // No resources available. Can't acquire
            result = false;
        }
        else
        {
            timespec timeout {
                .tv_sec = 0,
                .tv_nsec = timeout_ms * 1000000
            };

            int waitResult = 0;
            while (semaphore->val == 0 && waitResult == 0)
                waitResult = pthread_cond_timedwait(&semaphore->cond, &semaphore->mutex, &timeout);

            if (waitResult == 0)
            {
                semaphore->val--;
                result = true;
            }
            else
            {
                result = false;
            }
        }

        return result;
    }

    void Semaphore_Post(Semaphore* sema, int count)
    {
        AndroidSemaphore* semaphore = (AndroidSemaphore*) sema;
        pthread_mutex_lock(&semaphore->mutex);
        semaphore->val += count;
        pthread_cond_broadcast(&semaphore->cond);
        pthread_mutex_unlock(&semaphore->mutex);
    }

    Mutex* Mutex_Create() {
        AndroidMutex* mutex = (AndroidMutex*) malloc(sizeof(AndroidMutex));
        pthread_mutex_init(mutex, nullptr);
        return (Mutex*) mutex;
    }

    void Mutex_Free(Mutex* mutex) {
        pthread_mutex_destroy((AndroidMutex*) mutex);
        free((AndroidMutex*) mutex);
    }

    void Mutex_Lock(Mutex* mutex) {
        pthread_mutex_lock((AndroidMutex*) mutex);
    }

    void Mutex_Unlock(Mutex* mutex) {
        pthread_mutex_unlock((AndroidMutex*) mutex);
    }

    bool Mutex_TryLock(Mutex* mutex) {
        return pthread_mutex_trylock((AndroidMutex*) mutex) == 0;
    }

    void WriteNDSSave(const u8* savedata, u32 savelen, u32 writeoffset, u32 writelen, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        if (emulatorInstance)
            emulatorInstance->requestNdsSaveWrite(savedata, savelen, writeoffset, writelen);
    }

    void WriteGBASave(const u8* savedata, u32 savelen, u32 writeoffset, u32 writelen, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        if (emulatorInstance)
            emulatorInstance->requestGbaSaveWrite(savedata, savelen, writeoffset, writelen);
    }

    void WriteFirmware(const Firmware& firmware, u32 writeoffset, u32 writelen, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        if (!emulatorInstance)
            return;

        if (firmware.GetHeader().Identifier != GENERATED_FIRMWARE_IDENTIFIER)
        { // If this is not the default built-in firmware...
            // ...then write the whole thing back.
            emulatorInstance->requestFirmwareSaveWrite(firmware.Buffer(), firmware.Length(), writeoffset, writelen);
        }
        else
        {
            u32 eapstart = firmware.GetExtendedAccessPointOffset();
            u32 eapend = eapstart + sizeof(firmware.GetExtendedAccessPoints());

            u32 apstart = firmware.GetWifiAccessPointOffset();
            u32 apend = apstart + sizeof(firmware.GetAccessPoints());

            // assert that the extended access points come just before the regular ones
            assert(eapend == apstart);

            if (eapstart <= writeoffset && writeoffset < apend)
            { // If we're writing to the access points...
                const u8* buffer = firmware.GetExtendedAccessPointPosition();
                u32 length = sizeof(firmware.GetExtendedAccessPoints()) + sizeof(firmware.GetAccessPoints());
                emulatorInstance->requestFirmwareSaveWrite(buffer, length, writeoffset - eapstart, writelen);
            }
        }
    }

    void WriteDateTime(int year, int month, int day, int hour, int minute, int second, void* userdata)
    {
        // TODO
    }

    void MP_Begin(void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        MPInterface::Get().Begin(emulatorInstance->getInstanceId());
    }

    void MP_End(void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        MPInterface::Get().End(emulatorInstance->getInstanceId());
    }

    int MP_SendPacket(u8* data, int len, u64 timestamp, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        return MPInterface::Get().SendPacket(emulatorInstance->getInstanceId(), data, len, timestamp);
    }

    int MP_RecvPacket(u8* data, u64* timestamp, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        return MPInterface::Get().RecvPacket(emulatorInstance->getInstanceId(), data, timestamp);
    }

    int MP_SendCmd(u8* data, int len, u64 timestamp, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        return MPInterface::Get().SendCmd(emulatorInstance->getInstanceId(), data, len, timestamp);
    }

    int MP_SendReply(u8* data, int len, u64 timestamp, u16 aid, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        return MPInterface::Get().SendReply(emulatorInstance->getInstanceId(), data, len, timestamp, aid);
    }

    int MP_SendAck(u8* data, int len, u64 timestamp, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        return MPInterface::Get().SendAck(emulatorInstance->getInstanceId(), data, len, timestamp);
    }

    int MP_RecvHostPacket(u8* data, u64* timestamp, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        return MPInterface::Get().RecvHostPacket(emulatorInstance->getInstanceId(), data, timestamp);
    }

    u16 MP_RecvReplies(u8* data, u64 timestamp, u16 aidmask, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        return MPInterface::Get().RecvReplies(emulatorInstance->getInstanceId(), data, timestamp, aidmask);
    }

    int Net_SendPacket(u8* data, int len, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        return emulatorInstance->sendNetPacket(data, len);
    }

    int Net_RecvPacket(u8* data, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        return emulatorInstance->receiveNetPacket(data);
    }

    void Sleep(u64 usecs)
    {
        usleep(usecs);
    }

    void Mic_Start(void* userdata)
    {
        MelonDSAndroid::enableMic();
    }

    void Mic_Stop(void* userdata)
    {
        MelonDSAndroid::disableMic();
    }

    int Mic_ReadInput(s16* data, int maxlength, void* userdata)
    {
        return MelonDSAndroid::readMic(data, maxlength);
    }

    u64 GetMSCount()
    {
        return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
    }

    void Camera_Start(int num, void* userdata)
    {
        MelonDSAndroid::cameraHandler->startCamera(num);
    }

    void Camera_Stop(int num, void* userdata)
    {
        MelonDSAndroid::cameraHandler->stopCamera(num);
    }

    void Camera_CaptureFrame(int num, u32* frame, int width, int height, bool yuv, void* userdata)
    {
        MelonDSAndroid::cameraHandler->captureFrame(num, frame, width, height, yuv);
    }

    bool Addon_KeyDown(KeyType type, void* userdata)
    {
        return false;
    }

    void Addon_RumbleStart(u32 len, void* userdata)
    {
        MelonDSAndroid::eventMessenger->onRumbleStart(len);
    }

    void Addon_RumbleStop(void* userdata)
    {
        MelonDSAndroid::eventMessenger->onRumbleStop();
    }

    float Addon_MotionQuery(MotionQueryType type, void* userdata)
    {
        auto emulatorInstance = (MelonDSAndroid::MelonInstance*) userdata;
        if (emulatorInstance)
            return emulatorInstance->getMotionData(type);

        // Fallback: return gravity on Z axis (rest position), 0 for everything else
        if (type == MotionAccelerationZ)
            return 9.80665f;
        return 0.0f;
    }

    DynamicLibrary* DynamicLibrary_Load(const char* lib)
    {
        void* library = dlopen(lib, RTLD_NOW | RTLD_LOCAL);
        return reinterpret_cast<DynamicLibrary*>(library);
    }

    void DynamicLibrary_Unload(DynamicLibrary* lib)
    {
        dlclose(reinterpret_cast<void*>(lib));
    }

    void* DynamicLibrary_LoadFunction(DynamicLibrary* lib, const char* name)
    {
        void* library = reinterpret_cast<void*>(lib);
        return dlsym(library, name);
    }
}

}