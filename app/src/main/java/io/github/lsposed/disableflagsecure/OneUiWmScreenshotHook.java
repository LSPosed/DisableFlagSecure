package io.github.lsposed.disableflagsecure;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.SurfaceControl;

import java.lang.reflect.Method;

final class OneUiWmScreenshotHook {
    static final int SECURE_CONTENT_POLICY_ARG_INDEX = 5;

    private static final String POLICY_METHOD_NAME = "isScreenshotAllowedByPolicy";
    private static final String SCREENSHOT_METHOD_NAME = "screenshot";
    private static final String REASON_FOR_FAILURE_FIELD = "mReasonForFailure";
    private static final int REASON_FLAG_SECURE = 16;
    private static final int REASON_MDM = 32;

    private OneUiWmScreenshotHook() {
    }

    static boolean isScreenshotAllowedByPolicyMethod(Method method) {
        if (method == null || !POLICY_METHOD_NAME.equals(method.getName())) {
            return false;
        }
        if (method.getReturnType() != boolean.class) {
            return false;
        }
        var params = method.getParameterTypes();
        return params.length == 1 && "DisplayContent".equals(params[0].getSimpleName());
    }

    static boolean isPrimaryProfileScreenshotMethod(Method method) {
        if (method == null || !SCREENSHOT_METHOD_NAME.equals(method.getName())) {
            return false;
        }
        if (method.getReturnType() != Bitmap.class) {
            return false;
        }
        var params = method.getParameterTypes();
        return params.length == 7
                && params[0] == IBinder.class
                && params[1] == Rect.class
                && params[2] == int.class
                && params[3] == int.class
                && params[4] == SurfaceControl.class
                && params[5] == boolean.class
                && params[6] == SurfaceControl[].class;
    }

    static boolean narrowScreenshotPolicyResult(Object controller, boolean originalAllowed) {
        if (originalAllowed) {
            return true;
        }
        var reason = readReasonForFailure(controller);
        if (reason == null) {
            return false;
        }
        return (reason & REASON_FLAG_SECURE) != 0 && (reason & REASON_MDM) == 0;
    }

    private static Integer readReasonForFailure(Object controller) {
        if (controller == null) {
            return null;
        }
        try {
            var field = controller.getClass().getDeclaredField(REASON_FOR_FAILURE_FIELD);
            if (field.getType() != int.class) {
                return null;
            }
            field.setAccessible(true);
            return field.getInt(controller);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static boolean forceIgnoreSecureContentPolicy(Object[] args) {
        if (args == null || args.length <= SECURE_CONTENT_POLICY_ARG_INDEX) {
            return false;
        }
        var current = args[SECURE_CONTENT_POLICY_ARG_INDEX];
        if (!(current instanceof Boolean ignorePolicy) || ignorePolicy) {
            return false;
        }
        args[SECURE_CONTENT_POLICY_ARG_INDEX] = true;
        return true;
    }
}
