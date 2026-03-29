package io.github.lsposed.disableflagsecure;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Arrays;
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

    private static XposedModule module;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        module = this;
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        var classLoader = param.getClassLoader();

        try {
            deoptimizeSystemServer(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "deoptimize system server failed", t);
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
            if (!(t instanceof ClassNotFoundException)) {
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
        var pn = param.getPackageName();
        switch (pn) {
            case OPLUS_SCREENSHOT:
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
                if (OPLUS_APPPLATFORM.equals(pn) || OPLUS_SCREENSHOT.equals(pn) ||
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
            default:
                try {
                    hookOnResume();
                } catch (Throwable ignored) {
                }
        }
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
        hook(isSecureLockedMethod).intercept(chain -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
                var match = walker.walk(frames -> frames
                        .anyMatch(frame -> frame.getDeclaringClass() != null &&
                                frame.getDeclaringClass().getClassLoader() == systemServerCl &&
                                (frame.getMethodName().equals("setInitialSurfaceControlProperties") ||
                                        frame.getMethodName().equals("createSurfaceLocked"))));
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
    }

    private void hookScreenCapture(ClassLoader classLoader) throws ClassNotFoundException, NoSuchFieldException {
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

    private void hookDisplayControl(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var displayControlClazz = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ?
                classLoader.loadClass("com.android.server.display.DisplayControl") :
                SurfaceControl.class;
        var systemServerCl = displayControlClazz.getClassLoader();
        var method = displayControlClazz.getDeclaredMethod(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ?
                        "createVirtualDisplay" :
                        "createDisplay", String.class, boolean.class);
        hook(method).intercept(chain -> {
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void hookActivityTaskManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var activityTaskManagerServiceClazz = classLoader.loadClass("com.android.server.wm.ActivityTaskManagerService");
        var iBinderClazz = classLoader.loadClass("android.os.IBinder");
        var iScreenCaptureObserverClazz = classLoader.loadClass("android.app.IScreenCaptureObserver");
        var method = activityTaskManagerServiceClazz.getDeclaredMethod("registerScreenCaptureObserver", iBinderClazz, iScreenCaptureObserverClazz);
        hook(method).intercept(chain -> null);
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private void hookWindowManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var windowManagerServiceClazz = classLoader.loadClass("com.android.server.wm.WindowManagerService");
        var iScreenRecordingCallbackClazz = classLoader.loadClass("android.window.IScreenRecordingCallback");
        var method = windowManagerServiceClazz.getDeclaredMethod("registerScreenRecordingCallback", iScreenRecordingCallbackClazz);
        hook(method).intercept(chain -> false);
    }

    private void hookActivityManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var activityTaskManagerServiceClazz = classLoader.loadClass("com.android.server.am.ActivityManagerService");
        var method = activityTaskManagerServiceClazz.getDeclaredMethod("checkPermission", String.class, int.class, int.class);
        hook(method).intercept(chain -> {
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
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ?
                        "android.window.ScreenCapture$ScreenshotHardwareBuffer" :
                        "android.view.SurfaceControl$ScreenshotHardwareBuffer");
        var method = screenshotHardwareBufferClazz.getDeclaredMethod("containsSecureLayers");
        hook(method).intercept(chain -> false);
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private void hookOplusScreenCapture(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var oplusScreenCaptureClazz = classLoader.loadClass("com.oplus.screenshot.OplusScreenCapture$CaptureArgs$Builder");
        var method = oplusScreenCaptureClazz.getDeclaredMethod("setUid", long.class);
        hook(method).intercept(chain -> {
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

    private void hookOneUI(ClassLoader classLoader) throws ClassNotFoundException {
        var wmScreenshotControllerClazz = classLoader.loadClass("com.android.server.wm.WmScreenshotController");
        hookMethods(wmScreenshotControllerClazz, chain -> true, "canBeScreenshotTarget");
    }

    private void hookMethods(Class<?> clazz, Hooker hooker, String... names) {
        var list = Arrays.asList(names);
        Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> list.contains(method.getName()))
                .forEach(method -> hook(method).intercept(hooker));
    }

    private void hookOnResume() throws NoSuchMethodException {
        var method = Activity.class.getDeclaredMethod("onResume");
        hook(method).intercept(chain -> {
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
