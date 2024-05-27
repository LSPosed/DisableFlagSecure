package io.github.lsposed.disableflagsecure;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public class DisableFlagSecure extends XposedModule {

    public DisableFlagSecure(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
    }

    @Override
    public void onSystemServerLoaded(@NonNull SystemServerLoadedParam param) {
        var classLoader = param.getClassLoader();

        try {
            deoptimizeSystemServer(classLoader);
        } catch (Throwable t) {
            log("deoptimize system server failed", t);
        }
        try {
            hookWindowState(classLoader);
        } catch (Throwable t) {
            log("hook WindowState failed", t);
        }

        try {
            hookHyperOS(classLoader);
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            log("hook HyperOS failed", t);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                hookActivityTaskManagerService(classLoader);
            } catch (Throwable t) {
                log("hook ActivityTaskManagerService failed", t);
            }
        }
    }

    @SuppressLint("PrivateApi")
    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        if (!param.isFirstPackage()) return;

        var classLoader = param.getClassLoader();
        switch (param.getPackageName()) {
            case "com.flyme.systemuiex":
                try {
                    hookFlyme(classLoader);
                } catch (Throwable t) {
                    log("hook Flyme failed", t);
                }
                break;
            case "com.oplus.screenshot":
                try {
                    hookOplus(classLoader);
                } catch (Throwable t) {
                    log("hook OPlus failed", t);
                }
                try {
                    hookOplusNew(classLoader);
                } catch (Throwable t) {
                    if (!(t instanceof ClassNotFoundException)) {
                        log("hook OPlus failed", t);
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
        Method isSecureLockedMethod;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isSecureLockedMethod = windowStateClazz.getDeclaredMethod("isSecureLocked");
        } else {
            var windowManagerServiceClazz = classLoader.loadClass("com.android.server.wm.WindowManagerService");
            isSecureLockedMethod = windowManagerServiceClazz.getDeclaredMethod("isSecureLocked", windowStateClazz);
        }
        hook(isSecureLockedMethod, ReturnFalseHooker.class);
    }

    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void hookActivityTaskManagerService(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var activityTaskManagerServiceClazz = classLoader.loadClass("com.android.server.wm.ActivityTaskManagerService");
        var iBinderClazz = classLoader.loadClass("android.os.IBinder");
        var iScreenCaptureObserverClazz = classLoader.loadClass("android.app.IScreenCaptureObserver");
        var method = activityTaskManagerServiceClazz.getDeclaredMethod("registerScreenCaptureObserver", iBinderClazz, iScreenCaptureObserverClazz);
        hook(method, ReturnNullHooker.class);
    }

    private void hookHyperOS(ClassLoader classLoader) throws ClassNotFoundException {
        var windowManagerServiceImplClazz = classLoader.loadClass("com.android.server.wm.WindowManagerServiceImpl");
        hookMethods(windowManagerServiceImplClazz, ReturnFalseHooker.class, "notAllowCaptureDisplay");
    }

    private void hookFlyme(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var screenshotHardwareBufferClazz = classLoader.loadClass("android.view.SurfaceControl$ScreenshotHardwareBuffer");
        var method = screenshotHardwareBufferClazz.getDeclaredMethod("containsSecureLayers");
        hook(method, ReturnFalseHooker.class);
    }

    private void hookOplus(ClassLoader classLoader) throws ClassNotFoundException {
        var screenshotContextClazz = classLoader.loadClass("com.oplus.screenshot.screenshot.core.ScreenshotContext");
        hookMethods(screenshotContextClazz, ReturnNullHooker.class, "setScreenshotReject", "setLongshotReject");
    }

    private void hookOplusNew(ClassLoader classLoader) throws ClassNotFoundException {
        var screenshotContextClazz = classLoader.loadClass("com.oplus.screenshot.screenshot.core.ScreenshotContentContext");
        hookMethods(screenshotContextClazz, ReturnNullHooker.class, "setScreenshotReject", "setLongshotReject");
    }

    private void hookMethods(Class<?> clazz, Class<? extends Hooker> hooker, String... names) {
        var list = Arrays.asList(names);
        Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> list.contains(method.getName()))
                .forEach(method -> hook(method, hooker));
    }

    private void hookOnResume() throws NoSuchMethodException {
        var method = Activity.class.getDeclaredMethod("onResume");
        hook(method, ToastHooker.class);
    }

    @XposedHooker
    private static class ReturnFalseHooker implements Hooker {
        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            callback.returnAndSkip(false);
        }
    }

    @XposedHooker
    private static class ReturnNullHooker implements Hooker {
        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            callback.returnAndSkip(null);
        }
    }

    @XposedHooker
    private static class ToastHooker implements Hooker {
        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            var activity = (Activity) callback.getThisObject();
            assert activity != null;
            Toast.makeText(activity, "DFS: Incorrect module usage, remove this app from scope.", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }
}
