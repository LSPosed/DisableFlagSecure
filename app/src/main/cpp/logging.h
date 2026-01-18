#ifndef LOGGING_H
#define LOGGING_H

#include <android/log.h>

#ifndef LOG_TAG
#define LOG_TAG    "LSPosedContext"
#endif

#ifndef STRINGIFY
#define STRINGIFY(x) #x
#endif

#ifndef STRINGIFY_MACRO
#define STRINGIFY_MACRO(x) STRINGIFY(x)
#endif

#ifdef LOG_DISABLED
#define LOGD(...)
#define LOGV(...)
#define LOGI(...)
#define LOGW(...)
#define LOGE(...)
#else
#ifndef NDEBUG
#define LOGD(fmt, ...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, STRINGIFY_MACRO(APPLICATION_ID) ": %s: " fmt, __func__, ##__VA_ARGS__)
#else
#define LOGD(...)
#endif
#define LOGV(fmt, ...)  __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, STRINGIFY_MACRO(APPLICATION_ID) ": %s: " fmt, __func__, ##__VA_ARGS__)
#define LOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, STRINGIFY_MACRO(APPLICATION_ID) ": %s: " fmt, __func__, ##__VA_ARGS__)
#define LOGW(fmt, ...)  __android_log_print(ANDROID_LOG_WARN, LOG_TAG, STRINGIFY_MACRO(APPLICATION_ID) ": %s: " fmt, __func__, ##__VA_ARGS__)
#define LOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, STRINGIFY_MACRO(APPLICATION_ID) ": %s: " fmt, __func__, ##__VA_ARGS__)
#define PLOGE(fmt, args...) LOGE(fmt " failed with %d: %s", ##args, errno, strerror(errno))
#endif

#endif // LOGGING_H
