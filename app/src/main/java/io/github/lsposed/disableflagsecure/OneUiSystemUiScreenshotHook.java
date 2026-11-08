package io.github.lsposed.disableflagsecure;

import java.lang.reflect.Constructor;

final class OneUiSystemUiScreenshotHook {
    static final int TYPE_ARG_INDEX = 0;
    static final int SECURE_LAYER_ARG_INDEX = 10;
    static final int WORK_PROFILE_SCREENSHOT_TYPE = 3;

    private OneUiSystemUiScreenshotHook() {
    }

    static boolean isScreenshotDataConstructor(Constructor<?> constructor) {
        if (constructor == null) {
            return false;
        }
        var params = constructor.getParameterTypes();
        return params.length == 11
                && params[0] == int.class
                && params[1] == int.class
                && "android.os.UserHandle".equals(params[2].getName())
                && "android.content.ComponentName".equals(params[3].getName())
                && params[4] == int.class
                && "android.graphics.Rect".equals(params[5].getName())
                && "android.graphics.Insets".equals(params[6].getName())
                && "android.graphics.Bitmap".equals(params[7].getName())
                && params[8] == int.class
                && params[9] == boolean.class
                && params[10] == boolean.class;
    }

    static boolean clearWorkProfileSecureLayer(Object[] args) {
        if (args == null || args.length <= SECURE_LAYER_ARG_INDEX) {
            return false;
        }
        var typeArg = args[TYPE_ARG_INDEX];
        if (!(typeArg instanceof Integer type) || type != WORK_PROFILE_SCREENSHOT_TYPE) {
            return false;
        }
        var current = args[SECURE_LAYER_ARG_INDEX];
        if (!(current instanceof Boolean secureLayer) || !secureLayer) {
            return false;
        }
        args[SECURE_LAYER_ARG_INDEX] = false;
        return true;
    }
}
