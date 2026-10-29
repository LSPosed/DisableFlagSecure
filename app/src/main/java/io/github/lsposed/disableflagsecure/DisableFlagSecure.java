package io.github.lsposed.disableflagsecure;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import io.github.libxposed.api.XposedModule;

@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public class DisableFlagSecure extends XposedModule {
    private static final String TAG = "DisableFlagSecure";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String OPLUS_APPPLATFORM = "com.oplus.appplatform";
    private static final String OPLUS_SCREENSHOT = "com.oplus.screenshot";
    private static final String FLYME_SYSTEMUIEX = "com.flyme.systemuiex";
    private static final String MIUI_SCREENSHOT = "com.miui.screenshot";
    private static final String OPLUS_SCREENRECORDER = "com.oplus.screenrecorder";
    private static final int ANDROID_17 = 37;
    private static final int SECURE_CONTENT_POLICY_CAPTURE = 1;
    private static final int CAPTURE_MODE_NONE = 0;
    private static final int CAPTURE_MODE_REQUIRE_OPTIMIZED = 1;

    private static XposedModule module;
    private Pair<String, ClassLoader> param;
    private final Set<String> hookedIds = new HashSet<>();

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        module = this;
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        var classLoader = param.getClassLoader();
        this.param = Pair.create("system", classLoader);
        try {
            deoptimizeSystemServer(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "deoptimize system server failed", t);
        }

        hookSystemServer(classLoader);
    }

    private void hookSystemServer(ClassLoader classLoader) {
        if (Build.VERSION.SDK_INT >= ANDROID_17) {
            try {
                hookAndroid17ScreenCaptureParams(classLoader);
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Android 17 WMS.screenCapture skipped", t);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Screen record detection (V~Baklava)
            try {
                hookWindowManagerService(classLoader);
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hook WindowManagerService failed", t);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Screenshot detection (U~Baklava)
            try {
                hookActivityTaskManagerService(classLoader);
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hook ActivityTaskManagerService failed", t);
            }

            // Xiaomi HyperOS (U~Baklava)
            // OS2.0.300.1.WOCCNXM
            try {
                hookHyperOS(classLoader);
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hook HyperOS failed", t);
            }
        }

        // ScreenCapture in WindowManagerService (S~Baklava)
        try {
            hookScreenCapture(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook ScreenCapture failed", t);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Blackout permission check (S~T)
            try {
                hookActivityManagerService(classLoader);
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "hook ActivityManagerService failed", t);
            }
        }

        // WifiDisplay (S~Baklava) / OverlayDisplay (S~Baklava) / VirtualDisplay (U~Baklava)
        try {
            hookDisplayControl(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook DisplayControl failed", t);
        }

        // VirtualDisplay with MediaProjection (S~Baklava)
        try {
            hookVirtualDisplayAdapter(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook VirtualDisplayAdapter failed", t);
        }

        // OneUI
        try {
            hookScreenshotHardwareBuffer(classLoader);
        } catch (Throwable t) {
            if (Build.VERSION.SDK_INT >= ANDROID_17) {
                log(Log.WARN, TAG, "Android 17 ScreenshotHardwareBuffer skipped", t);
            } else if (!(t instanceof ClassNotFoundException)) {
                log(Log.ERROR, TAG, "hook ScreenshotHardwareBuffer failed", t);
            }
        }
        try {
            hookOneUI(classLoader);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                log(Log.ERROR, TAG, "hook OneUI failed", t);
            }
        }

        // secureLocked flag
        try {
            // Screenshot
            hookWindowState(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook WindowState failed", t);
        }

        // oplus dumpsys
        // dumpsys window screenshot systemQuickTileScreenshotOut display_id=0
        try {
            hookOplus(classLoader);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                log(Log.ERROR, TAG, "hook Oplus failed", t);
            }
        }
    }

    @SuppressLint("PrivateApi")
    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage()) return;

        var classLoader = param.getClassLoader();
        var packageName = param.getPackageName();
        this.param = Pair.create(packageName, classLoader);
        hookPackage(packageName, classLoader);
    }

    private void hookPackage(String packageName, ClassLoader classLoader) {
        switch (packageName) {
            case OPLUS_SCREENSHOT:
                if (Build.VERSION.SDK_INT >= ANDROID_17) {
                    hookOplusHardwareBuffer(classLoader);
                }
                // Oplus Screenshot 15.0.0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    try {
                        hookOplusScreenCapture(classLoader);
                    } catch (Throwable t) {
                        if (!(t instanceof ClassNotFoundException)) {
                            log(Log.ERROR, TAG, "hook OplusScreenCapture failed", t);
                        }
                    }
                }
            case FLYME_SYSTEMUIEX:
            case OPLUS_APPPLATFORM:
                // Flyme SystemUI Ext 10.3.0
                // OPlus AppPlatform 13.1.0 / 14.0.0
                try {
                    hookScreenshotHardwareBuffer(classLoader);
                } catch (Throwable t) {
                    if (!(t instanceof ClassNotFoundException)) {
                        log(Log.ERROR, TAG, "hook ScreenshotHardwareBuffer failed", t);
                    }
                }
            case SYSTEMUI:
            case MIUI_SCREENSHOT:
                if (Build.VERSION.SDK_INT >= ANDROID_17
                        && (SYSTEMUI.equals(packageName) || MIUI_SCREENSHOT.equals(packageName))) {
                    try {
                        hookScreenshotHardwareBuffer(classLoader);
                    } catch (Throwable t) {
                        log(Log.WARN, TAG, "Android 17 ScreenshotHardwareBuffer skipped", t);
                    }
                }
                if (OPLUS_APPPLATFORM.equals(packageName) || OPLUS_SCREENSHOT.equals(packageName) ||
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // ScreenCapture in App (S~T) (OPlus S~V)
                    // TODO: test Oplus Baklava
                    try {
                        hookScreenCapture(classLoader);
                    } catch (Throwable t) {
                        log(Log.ERROR, TAG, "hook ScreenCapture failed", t);
                    }
                }
                break;
            case OPLUS_SCREENRECORDER:
                if (Build.VERSION.SDK_INT >= ANDROID_17) {
                    hookOplusHardwareBuffer(classLoader);
                    hookOplusInfoMonitor(classLoader);
                }
                break;
            default:
                try {
                    hookOnResume();
                } catch (Throwable ignored) {
                }
        }
    }

    @Override
    public boolean onHotReloading(@NonNull HotReloadingParam param) {
        param.setSavedInstanceState(this.param);
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull HotReloadedParam param) {
        var isSystemServer = param.isSystemServer();
        if (param.getSavedInstanceState() instanceof Pair<?, ?> pair
                && pair.first instanceof String packageName
                && pair.second instanceof ClassLoader classLoader) {
            this.param = Pair.create(packageName, classLoader);
            try {
                if (isSystemServer) {
                    hookSystemServer(classLoader);
                } else {
                    hookPackage(packageName, classLoader);
                }
            } catch (Throwable tr) {
                log(Log.ERROR, TAG, "Hot reload failed", tr);
            }
        }
        param.getOldHookHandles().forEach(h -> {
            if (!hookedIds.contains(h.getId())) {
                h.unhook();
            }
        });
    }

    private void deoptimizeSystemServer(ClassLoader classLoader) throws ClassNotFoundException {
        deoptimizeMethods(
                classLoader.loadClass("com.android.server.wm.WindowStateAnimator"),
                "createSurfaceLocked");

        deoptimizeMethods(
                classLoader.loadClass("com.android.server.wm.WindowManagerService"),
                "relayoutWindow");

        for (int i = 0; i < 20; i++) {
            try {
                var clazz = classLoader.loadClass("com.android.server.wm.RootWindowContainer$$ExternalSyntheticLambda" + i);
                if (BiConsumer.class.isAssignableFrom(clazz)) {
                    deoptimizeMethods(clazz, "accept");
                }
            } catch (ClassNotFoundException ignored) {
            }
            try {
                var clazz = classLoader.loadClass("com.android.server.wm.DisplayContent$" + i);
                if (BiPredicate.class.isAssignableFrom(clazz)) {
                    deoptimizeMethods(clazz, "test");
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
    }

    private void deoptimizeMethods(Class<?> clazz, String... names) {
        var list = Arrays.asList(names);
        Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> list.contains(method.getName()))
                .forEach(this::deoptimize);
    }

    private void hookWindowState(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var windowStateClazz = classLoader.loadClass("com.android.server.wm.WindowState");
        var systemServerCl = windowStateClazz.getClassLoader();
        var isSecureLockedMethod = windowStateClazz.getDeclaredMethod("isSecureLocked");
        hookE(isSecureLockedMethod).intercept(chain -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
                var match = walker.walk(frames -> frames
                        .anyMatch(frame -> frame.getDeclaringClass() != null &&
                                frame.getDeclaringClass().getClassLoader() == systemServerCl &&
                                (frame.getMethodName().equals("setInitialSurfaceControlProperties") ||
                                        frame.getMethodName().equals("createSurfaceLocked") ||
                                        (Build.VERSION.SDK_INT >= ANDROID_17 &&
                                                (frame.getMethodName().equals("relayoutWindow") ||
                                                        frame.getMethodName().equals("refreshSecureSurfaceState"))))));
                if (match) return chain.proceed();
            } else {
                var stackTrace = new Throwable().getStackTrace();
                for (var frame : stackTrace) {
                    var name = frame.getMethodName();
                    try {
                        if ((name.equals("setInitialSurfaceControlProperties") ||
                                name.equals("createSurfaceLocked")) &&
                                classLoader.loadClass(frame.getClassName()).getClassLoader() == systemServerCl) {
                            return chain.proceed();
                        }
                    } catch (ClassNotFoundException ignored) {
                    }
                }
            }
            return false;
        });
        if (Build.VERSION.SDK_INT >= ANDROID_17) {
            log(Log.INFO, TAG, "Android 17 WindowState.isSecureLocked installed; surface updates preserved");
        }
    }

    private void hookScreenCapture(ClassLoader classLoader) throws ClassNotFoundException, NoSuchFieldException {
        if (Build.VERSION.SDK_INT >= ANDROID_17) {
            hookAndroid17ScreenCapture(classLoader);
            return;
        }
        Class<?> screenCaptureClazz;
        Class<?> captureArgsClazz;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            screenCaptureClazz = classLoader.loadClass("android.window.ScreenCaptureInternal");
            captureArgsClazz = classLoader.loadClass("android.window.ScreenCaptureInternal$CaptureArgs");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            screenCaptureClazz = classLoader.loadClass("android.window.ScreenCapture");
            captureArgsClazz = classLoader.loadClass("android.window.ScreenCapture$CaptureArgs");
        } else {
            screenCaptureClazz = SurfaceControl.class;
            captureArgsClazz = classLoader.loadClass("android.view.SurfaceControl$CaptureArgs");
        }
        var captureSecureLayersField = captureArgsClazz.getDeclaredField(Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1 ? "mSecureContentPolicy" : "mCaptureSecureLayers");
        captureSecureLayersField.setAccessible(true);
        Hooker hooker = chain -> {
            var captureArgs = chain.getArg(0);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                        Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                    captureSecureLayersField.set(captureArgs, 1);
                } else {
                    captureSecureLayersField.set(captureArgs, true);
                }
            } catch (IllegalAccessException t) {
                module.log(Log.ERROR, TAG, "ScreenCaptureHooker failed", t);
            }
            return chain.proceed();
        };
        hookMethods(screenCaptureClazz, hooker, "nativeCaptureDisplay");
        hookMethods(screenCaptureClazz, hooker, "nativeCaptureLayers");
    }

    private static Field intField(Class<?> clazz, String name) throws NoSuchFieldException {
        var field = clazz.getDeclaredField(name);
        if (field.getType() != int.class || Modifier.isStatic(field.getModifiers())) {
            throw new NoSuchFieldException(clazz.getName() + "." + name + " is not an instance int");
        }
        field.setAccessible(true);
        return field;
    }

    private void hookAndroid17ScreenCapture(ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchFieldException {
        var clazz = classLoader.loadClass("android.window.ScreenCaptureInternal");
        var captureArgsClazz = classLoader.loadClass("android.window.ScreenCaptureInternal$CaptureArgs");
        var policy = intField(captureArgsClazz, "mSecureContentPolicy");
        Field captureMode = null;
        try {
            captureMode = intField(captureArgsClazz, "mCaptureMode");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Android 17 native capture mode fallback unavailable", t);
        }
        var mode = captureMode;
        for (var name : new String[]{"nativeCaptureDisplay", "nativeCaptureLayers"}) {
            try {
                var argsClazz = classLoader.loadClass("android.window.ScreenCaptureInternal$"
                        + (name.equals("nativeCaptureDisplay") ? "DisplayCaptureArgs" : "LayerCaptureArgs"));
                var method = name.equals("nativeCaptureDisplay")
                        ? clazz.getDeclaredMethod(name, argsClazz, long.class)
                        : clazz.getDeclaredMethod(name, argsClazz, long.class, boolean.class);
                if (!captureArgsClazz.isAssignableFrom(argsClazz)
                        || !Modifier.isStatic(method.getModifiers()) || method.getReturnType() != int.class) {
                    throw new NoSuchMethodException(method.toString());
                }
                var invoked = new AtomicBoolean();
                var failed = new AtomicBoolean();
                hookE(method).intercept(chain -> {
                    try {
                        var args = chain.getArg(0);
                        policy.setInt(args, SECURE_CONTENT_POLICY_CAPTURE);
                        // REQUIRE_OPTIMIZED cannot fall back when DPU readback is unavailable.
                        if (mode != null && mode.getInt(args) == CAPTURE_MODE_REQUIRE_OPTIMIZED) {
                            mode.setInt(args, CAPTURE_MODE_NONE);
                        }
                        if (invoked.compareAndSet(false, true)) {
                            module.log(Log.INFO, TAG, "Android 17 " + name + " invoked; secure policy=1");
                        }
                    } catch (Throwable t) {
                        if (failed.compareAndSet(false, true)) {
                            module.log(Log.ERROR, TAG, "Android 17 " + name + " parameter update failed", t);
                        }
                    }
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "Android 17 installed " + method.toGenericString());
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Android 17 " + name + " skipped", t);
            }
        }
    }

    private void hookAndroid17ScreenCaptureParams(ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchFieldException, NoSuchMethodException {
        var paramsClazz = classLoader.loadClass("android.window.ScreenCapture$ScreenCaptureParams");
        var policy = intField(paramsClazz, "mSecureContentPolicy");
        var mode = intField(paramsClazz, "mCaptureMode");
        var clazz = classLoader.loadClass("com.android.server.wm.WindowManagerService");
        var callbackClazz = classLoader.loadClass("android.window.IScreenCaptureCallback");
        var method = clazz.getDeclaredMethod("screenCapture", paramsClazz, callbackClazz);
        var invoked = new AtomicBoolean();
        var failed = new AtomicBoolean();
        hookE(method).intercept(chain -> {
            try {
                var args = chain.getArg(0);
                policy.setInt(args, SECURE_CONTENT_POLICY_CAPTURE);
                // WMS otherwise overwrites the secure policy with THROW_EXCEPTION in this mode.
                if (mode.getInt(args) == CAPTURE_MODE_REQUIRE_OPTIMIZED) {
                    mode.setInt(args, CAPTURE_MODE_NONE);
                }
                if (invoked.compareAndSet(false, true)) {
                    module.log(Log.INFO, TAG, "Android 17 WMS.screenCapture invoked; secure policy=1");
                }
            } catch (Throwable t) {
                if (failed.compareAndSet(false, true)) {
                    module.log(Log.ERROR, TAG, "Android 17 ScreenCaptureParams update failed", t);
                }
            }
            return chain.proceed();
        });
        log(Log.INFO, TAG, "Android 17 WMS.screenCapture(ScreenCaptureParams, callback) installed");
    }

    private void hookDisplayControl(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        if (Build.VERSION.SDK_INT >= ANDROID_17) {
            // The adapter uses a six-argument overload now. Only projection displays need changing.
            log(Log.INFO, TAG, "Android 17 DisplayControl unchanged; secure projection handled by VirtualDisplayAdapter");
            return;
        }
        var displayControlClazz = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ?
                classLoader.loadClass("com.android.server.display.DisplayControl") :
                SurfaceControl.class;
        var systemServerCl = displayControlClazz.getClassLoader();
        var method = displayControlClazz.getDeclaredMethod(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ?
                        "createVirtualDisplay" :
                        "createDisplay", String.class, boolean.class);
        hookE(method).intercept(chain -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var stackTrace = new Throwable().getStackTrace();
                for (var frame : stackTrace) {
                    var name = frame.getMethodName();
                    try {
                        if (name.equals("createVirtualDisplayLocked") &&
                                classLoader.loadClass(frame.getClassName()).getClassLoader() == systemServerCl) {
                            return chain.proceed();
                        }
                    } catch (ClassNotFoundException ignored) {
                    }
                }
            }
            var args = chain.getArgs().toArray();
            args[1] = true;
            return chain.proceed(args);
        });
    }

    private void hookVirtualDisplayAdapter(ClassLoader classLoader) throws ClassNotFoundException {
        var displayControlClazz = classLoader.loadClass("com.android.server.display.VirtualDisplayAdapter");
        if (Build.VERSION.SDK_INT >= ANDROID_17) {
            hookAndroid17VirtualDisplayAdapter(displayControlClazz, classLoader);
            return;
        }
        hookMethods(displayControlClazz, chain -> {
            var caller = (int) chain.getArg(2);
            if (caller >= 10000 && chain.getArg(1) == null) {
                // not os and not media projection
                return chain.proceed();
            }
            for (int i = 3; i < chain.getArgs().size(); i++) {
                var arg = chain.getArg(i);
                if (arg instanceof Integer flags) {
                    flags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;
                    var args = chain.getArgs().toArray();
                    args[i] = flags;
                    return chain.proceed(args);
                }
            }
            module.log(Log.WARN, TAG, "flag not found in CreateVirtualDisplayLockedHooker");
            return chain.proceed();
        }, "createVirtualDisplayLocked");
    }

    private void hookAndroid17VirtualDisplayAdapter(Class<?> clazz, ClassLoader classLoader) {
        try {
            var method = clazz.getDeclaredMethod("createVirtualDisplayLocked",
                    classLoader.loadClass("android.hardware.display.IVirtualDisplayCallback"),
                    classLoader.loadClass("android.media.projection.IMediaProjection"),
                    int.class, String.class, String.class,
                    classLoader.loadClass("android.view.Surface"), int.class,
                    classLoader.loadClass("android.hardware.display.VirtualDisplayConfig"));
            var invoked = new AtomicBoolean();
            hookE(method).intercept(chain -> {
                if (chain.getArg(1) == null) return chain.proceed();
                var args = chain.getArgs().toArray();
                args[6] = (int) args[6] | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;
                if (invoked.compareAndSet(false, true)) {
                    module.log(Log.INFO, TAG, "Android 17 MediaProjection virtual display invoked; secure=true");
                }
                return chain.proceed(args);
            });
            log(Log.INFO, TAG, "Android 17 VirtualDisplayAdapter.createVirtualDisplayLocked installed");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Android 17 VirtualDisplayAdapter skipped", t);
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void hookActivityTaskManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var activityTaskManagerServiceClazz = classLoader.loadClass("com.android.server.wm.ActivityTaskManagerService");
        var iBinderClazz = classLoader.loadClass("android.os.IBinder");
        var iScreenCaptureObserverClazz = classLoader.loadClass("android.app.IScreenCaptureObserver");
        var method = activityTaskManagerServiceClazz.getDeclaredMethod("registerScreenCaptureObserver", iBinderClazz, iScreenCaptureObserverClazz);
        hookE(method).intercept(chain -> null);
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private void hookWindowManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var windowManagerServiceClazz = classLoader.loadClass("com.android.server.wm.WindowManagerService");
        var iScreenRecordingCallbackClazz = classLoader.loadClass("android.window.IScreenRecordingCallback");
        var method = windowManagerServiceClazz.getDeclaredMethod("registerScreenRecordingCallback", iScreenRecordingCallbackClazz);
        hookE(method).intercept(chain -> false);
    }

    private void hookActivityManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var activityTaskManagerServiceClazz = classLoader.loadClass("com.android.server.am.ActivityManagerService");
        var method = activityTaskManagerServiceClazz.getDeclaredMethod("checkPermission", String.class, int.class, int.class);
        hookE(method).intercept(chain -> {
            var permission = chain.getArg(0);
            if ("android.permission.CAPTURE_BLACKOUT_CONTENT".equals(permission)) {
                var args = chain.getArgs().toArray();
                args[0] = "android.permission.READ_FRAME_BUFFER";
                return chain.proceed(args);
            }
            return chain.proceed();
        });
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void hookHyperOS(ClassLoader classLoader) throws ClassNotFoundException {
        var windowManagerServiceImplClazz = classLoader.loadClass("com.android.server.wm.WindowManagerServiceImpl");
        hookMethods(windowManagerServiceImplClazz, chain -> false, "notAllowCaptureDisplay");
    }

    private void hookScreenshotHardwareBuffer(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var screenshotHardwareBufferClazz = classLoader.loadClass(
                Build.VERSION.SDK_INT >= ANDROID_17 ?
                        "android.window.ScreenCaptureInternal$ScreenshotHardwareBuffer" :
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ?
                        "android.window.ScreenCapture$ScreenshotHardwareBuffer" :
                        "android.view.SurfaceControl$ScreenshotHardwareBuffer");
        var method = screenshotHardwareBufferClazz.getDeclaredMethod("containsSecureLayers");
        hookE(method).intercept(chain -> false);
        if (Build.VERSION.SDK_INT >= ANDROID_17) {
            log(Log.INFO, TAG, "Android 17 ScreenCaptureInternal.ScreenshotHardwareBuffer.containsSecureLayers installed");
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private void hookOplusScreenCapture(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var oplusScreenCaptureClazz = classLoader.loadClass("com.oplus.screenshot.OplusScreenCapture$CaptureArgs$Builder");
        var method = oplusScreenCaptureClazz.getDeclaredMethod("setUid", long.class);
        hookE(method).intercept(chain -> {
            var args = chain.getArgs().toArray();
            args[0] = -1;
            return chain.proceed(args);
        });
    }

    private void hookOplus(ClassLoader classLoader) throws ClassNotFoundException {
        // caller: com.android.server.wm.OplusLongshotWindowDump#dumpWindows
        var longshotMainClazz = classLoader.loadClass("com.android.server.wm.OplusLongshotMainWindow");
        hookMethods(longshotMainClazz, chain -> false, "hasSecure");
    }

    private void hookOplusHardwareBuffer(ClassLoader classLoader) {
        try {
            var clazz = classLoader.loadClass("com.oplus.screenshot.OplusScreenCapture$ScreenshotHardwareBuffer");
            var method = clazz.getDeclaredMethod("containsSecureLayers");
            if (method.getReturnType() != boolean.class) throw new NoSuchMethodException(method.toString());
            hookE(method).intercept(chain -> false);
            log(Log.INFO, TAG, "Android 17 optional Oplus ScreenshotHardwareBuffer installed");
        } catch (ClassNotFoundException | NoSuchMethodException t) {
            log(Log.INFO, TAG, "Android 17 optional Oplus ScreenshotHardwareBuffer absent; skipped");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Android 17 optional Oplus ScreenshotHardwareBuffer skipped", t);
        }
    }

    private void hookOplusInfoMonitor(ClassLoader classLoader) {
        try {
            var clazz = classLoader.loadClass("com.oplus.display.OplusInfoMonitor");
            var installed = false;
            for (var method : clazz.getDeclaredMethods()) {
                var types = method.getParameterTypes();
                if (!method.getName().equals("registerSfEvent") || types.length < 3
                        || types[0].isPrimitive() || types[2] != String.class
                        || method.getReturnType() != void.class) continue;
                hookE(method).intercept(chain -> {
                    var listener = chain.getArg(0);
                    if ("com.oplus.screenrecordersecure".equals(chain.getArg(2))
                            && listener != null
                            && listener.getClass().getName().startsWith("com.oplus.screenrecorder.")) {
                        return null;
                    }
                    return chain.proceed();
                });
                installed = true;
                log(Log.INFO, TAG, "Android 17 optional Oplus installed " + method.toGenericString());
            }
            if (!installed) log(Log.INFO, TAG, "Android 17 optional Oplus registerSfEvent signature absent; skipped");
        } catch (ClassNotFoundException t) {
            log(Log.INFO, TAG, "Android 17 optional OplusInfoMonitor absent; skipped");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Android 17 optional OplusInfoMonitor skipped", t);
        }
    }

    private void hookOneUI(ClassLoader classLoader) throws ClassNotFoundException {
        var wmScreenshotControllerClazz = classLoader.loadClass("com.android.server.wm.WmScreenshotController");
        hookMethods(wmScreenshotControllerClazz, chain -> true, "canBeScreenshotTarget");
    }

    private void hookMethods(Class<?> clazz, Hooker hooker, String... names) {
        var list = Arrays.asList(names);
        Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> list.contains(method.getName()))
                .forEach(method -> hookE(method).intercept(hooker));
    }

    private HookBuilder hookE(Executable executable) {
        var builder = hook(executable);

        if (getApiVersion() >= 102) {
            var id = executable.toGenericString();
            builder.setId(id);
            hookedIds.add(id);
        }

        return builder;
    }

    private void hookOnResume() throws NoSuchMethodException {
        var method = Activity.class.getDeclaredMethod("onResume");
        hookE(method).intercept(chain -> {
            var activity = (Activity) chain.getThisObject();
            new AlertDialog.Builder(activity)
                    .setTitle("Enable Screenshot")
                    .setMessage("Incorrect module usage, remove this app from scope.")
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialog, which) -> System.exit(0))
                    .show();
            return chain.proceed();
        });
    }
}
