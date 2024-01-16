package io.github.lsposed.disableflagsecure;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DisableFlagSecure implements IXposedHookLoadPackage {
    private final static Method deoptimizeMethod;

    static {
        Method m = null;
        try {
            //noinspection JavaReflectionMemberAccess
            m = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        deoptimizeMethod = m;
    }

    static void deoptimizeMethod(Class<?> c, String n) throws InvocationTargetException, IllegalAccessException {
        for (Method m : c.getDeclaredMethods()) {
            if (deoptimizeMethod != null && m.getName().equals(n)) {
                deoptimizeMethod.invoke(null, m);
                Log.d("DisableFlagSecure", "Deoptimized " + m);
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (loadPackageParam.packageName.equals("android")) {
            try {
                Class<?> windowsState = XposedHelpers.findClass("com.android.server.wm.WindowState", loadPackageParam.classLoader);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    XposedHelpers.findAndHookMethod(
                            windowsState,
                            "isSecureLocked",
                            XC_MethodReplacement.returnConstant(false));
                } else {
                    XposedHelpers.findAndHookMethod(
                            "com.android.server.wm.WindowManagerService",
                            loadPackageParam.classLoader,
                            "isSecureLocked",
                            windowsState,
                            XC_MethodReplacement.returnConstant(false));
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    XposedHelpers.findAndHookMethod(
                            "com.android.server.wm.ActivityTaskManagerService",
                            loadPackageParam.classLoader,
                            "registerScreenCaptureObserver",
                            "android.os.IBinder",
                            "android.app.IScreenCaptureObserver",
                            XC_MethodReplacement.DO_NOTHING);
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
            try {
                deoptimizeMethod(XposedHelpers.findClass("com.android.server.wm.WindowStateAnimator", loadPackageParam.classLoader), "createSurfaceLocked");
                var c = XposedHelpers.findClass("com.android.server.display.DisplayManagerService", loadPackageParam.classLoader);
                deoptimizeMethod(c, "setUserPreferredModeForDisplayLocked");
                deoptimizeMethod(c, "setUserPreferredDisplayModeInternal");
                c = XposedHelpers.findClass("com.android.server.wm.InsetsPolicy$InsetsPolicyAnimationControlListener", loadPackageParam.classLoader);
                for (var m : c.getDeclaredConstructors()) {
                    deoptimizeMethod.invoke(null, m);
                }
                c = XposedHelpers.findClass("com.android.server.wm.InsetsPolicy", loadPackageParam.classLoader);
                deoptimizeMethod(c, "startAnimation");
                deoptimizeMethod(c, "controlAnimationUnchecked");
                for (int i = 0; i < 20; i++) {
                    c = XposedHelpers.findClassIfExists("com.android.server.wm.DisplayContent$$ExternalSyntheticLambda" + i, loadPackageParam.classLoader);
                    if (c != null && BiPredicate.class.isAssignableFrom(c)) {
                        deoptimizeMethod(c, "test");
                    }
                }
                c = XposedHelpers.findClass("com.android.server.wm.WindowManagerService", loadPackageParam.classLoader);
                deoptimizeMethod(c, "relayoutWindow");
                for (int i = 0; i < 20; i++) {
                    c = XposedHelpers.findClassIfExists("com.android.server.wm.RootWindowContainer$$ExternalSyntheticLambda" + i, loadPackageParam.classLoader);
                    if (c != null && BiConsumer.class.isAssignableFrom(c)) {
                        deoptimizeMethod(c, "accept");
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
            try {
                Class<?> windowsManagerServiceImpl = XposedHelpers.findClassIfExists("com.android.server.wm.WindowManagerServiceImpl", loadPackageParam.classLoader);
                if (windowsManagerServiceImpl != null) {
                    XposedBridge.hookAllMethods(
                            windowsManagerServiceImpl,
                            "notAllowCaptureDisplay",
                            XC_MethodReplacement.returnConstant(false));
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        } else if (loadPackageParam.packageName.equals("com.flyme.systemuiex")) {
            try {
                XposedHelpers.findAndHookMethod("android.view.SurfaceControl$ScreenshotHardwareBuffer", loadPackageParam.classLoader, "containsSecureLayers", XC_MethodReplacement.returnConstant(false));
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        } else if (loadPackageParam.packageName.equals("com.oplus.screenshot")) {
            try {
                Class<?> screenshotContext = XposedHelpers.findClassIfExists("com.oplus.screenshot.screenshot.core.ScreenshotContext", loadPackageParam.classLoader);
                XposedBridge.hookAllMethods(screenshotContext, "setScreenshotReject", XC_MethodReplacement.DO_NOTHING);
                XposedBridge.hookAllMethods(screenshotContext, "setLongshotReject", XC_MethodReplacement.DO_NOTHING);
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        } else {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;
                    Toast.makeText(activity, "DFS: Incorrect module usage, remove this app from scope.", Toast.LENGTH_LONG).show();
                    activity.finish();
                }
            });
        }
    }

}
