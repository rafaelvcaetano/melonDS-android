#include "UriFileHandler.h"
#include "Platform.h"

using namespace melonDS::Platform;

UriFileHandler::UriFileHandler(JniEnvHandler* jniEnvHandler, jobject uriFileHandler)
{
    this->jniEnvHandler = jniEnvHandler;
    this->uriFileHandler = uriFileHandler;
}

FILE* UriFileHandler::open(const char* path, FileMode mode)
{
    JNIEnv* env = this->jniEnvHandler->getCurrentThreadEnv();

    jstring pathString = env->NewStringUTF(path);
    jstring modeString = env->NewStringUTF(getAccessMode(mode, false).c_str());
    jclass handlerClass = env->GetObjectClass(this->uriFileHandler);
    jmethodID openMethod = env->GetMethodID(handlerClass, "open", "(Ljava/lang/String;Ljava/lang/String;)I");
    jint fileDescriptor = env->CallIntMethod(this->uriFileHandler, openMethod, pathString, modeString);

    if (fileDescriptor == -1) {
        return nullptr;
    } else {
        std::string nativeMode = getNativeAccessMode(mode, false);
        return fdopen(fileDescriptor, nativeMode.c_str());
    }
}

std::string UriFileHandler::getNativeAccessMode(FileMode mode, bool fileExists)
{
    std::string modeString;

    if (mode & FileMode::Append)
        modeString += 'a';
    else if (!(mode & FileMode::Write))
        // If we're only opening the file for reading...
        modeString += 'r';
    else if (mode & (FileMode::NoCreate))
        // If we're not allowed to create a new file...
        modeString += 'r'; // Open in "r+" mode (IsExtended will add the "+")
    else if ((mode & FileMode::Preserve) && fileExists)
        // If we're not allowed to overwrite a file that already exists...
        modeString += 'r'; // Open in "r+" mode (IsExtended will add the "+")
    else
        modeString += 'w';

    if ((mode & FileMode::ReadWrite) == FileMode::ReadWrite)
        modeString += '+';

    if (!(mode & FileMode::Text))
        modeString += 'b';

    return modeString;
}

std::string UriFileHandler::getAccessMode(FileMode mode, bool fileExists)
{
    std::string modeString;

    if (mode & FileMode::Read)
        modeString += 'r';

    if (mode & FileMode::Write)
    {
        modeString += 'w';

        if (mode & FileMode::Append)
            modeString += 'a';
        else if (!(mode & FileMode::Preserve))
            modeString += 't';
    }
    else if (mode & FileMode::Append)
        modeString += "wa";

    return modeString;
}

UriFileHandler::~UriFileHandler()
{
}