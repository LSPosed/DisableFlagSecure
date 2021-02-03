package io.github.lsposed.disableflagsecure;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DisableFlagSecure implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (loadPackageParam.packageName.equals("android")) {
            try {
                XposedHelpers.findAndHookMethod(
                        "com.android.server.wm.WindowState",
                        loadPackageParam.classLoader,
                        "isSecureLocked",
                        XC_MethodReplacement.returnConstant(false));
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }


}