#include "logging.h"
#include "native_api.h"
#include <dlfcn.h>
#include <jni.h>
#include <cstring>

static HookFunType hook_func = nullptr;
static UnhookFunType unhook_func = nullptr;

// Original function pointer backup
static bool (*orig_isCallingBySystemui)(void *thiz, int pid) = nullptr;

// Our hook replacement function
// android::MiSurfaceFlingerImpl::isCallingBySystemui returns bool
static bool hooked_isCallingBySystemui(void *thiz, int pid) {
    LOGI("isCallingBySystemui called with pid: %d, returning false", pid);
    // Always return false to bypass the systemui check
    return false;
}

static void on_library_loaded(const char *name, void *handle) {
    if (name == nullptr || handle == nullptr) return;

    // Check if this is libmisurfaceflinger.so
    if (strstr(name, "libmisurfaceflinger.so") == nullptr) return;

    LOGI("libmisurfaceflinger.so loaded, setting up hook");

    // Find the symbol: _ZN7android20MiSurfaceFlingerImpl19isCallingBySystemuiEi
    // This is the mangled name for: android::MiSurfaceFlingerImpl::isCallingBySystemui(int)
    void *symbol = dlsym(handle, "_ZN7android20MiSurfaceFlingerImpl19isCallingBySystemuiEi");

    if (symbol == nullptr) {
        LOGE("Failed to find symbol _ZN7android20MiSurfaceFlingerImpl19isCallingBySystemuiEi");
        return;
    }

    LOGI("Found isCallingBySystemui at %p", symbol);

    if (hook_func == nullptr) {
        LOGE("hook_func is null, cannot hook");
        return;
    }

    int result = hook_func(symbol, (void *) hooked_isCallingBySystemui,
                           (void **) &orig_isCallingBySystemui);

    if (result == 0) {
        LOGI("Successfully hooked isCallingBySystemui");
    } else {
        LOGE("Failed to hook isCallingBySystemui, error: %d", result);
    }
}

// LSPosed Native API entry point
// This function is called by LSPosed framework
extern "C" [[gnu::visibility("default")]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    if (entries == nullptr) {
        LOGE("native_init: entries is null");
        return nullptr;
    }

    LOGI("native_init called, version: %u", entries->version);

    hook_func = entries->hookFunc;
    unhook_func = entries->unhookFunc;

    if (hook_func == nullptr) {
        LOGE("hookFunc is null");
        return nullptr;
    }

    LOGI("Native hook API initialized successfully");

    // Return callback for library loading
    return on_library_loaded;
}

// JNI_OnLoad for library initialization
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("JNI_OnLoad called");
    return JNI_VERSION_1_6;
}
