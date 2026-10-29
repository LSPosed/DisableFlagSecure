"""Compile the real module against host fixtures and exercise its hook callbacks.

Run with Python 3 and JDK 21 on PATH (or JAVA_HOME). No Android device is used.
These fixtures verify routing and failure isolation, not ART or captured pixels.
"""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap


SOURCES = {
    "android/annotation/SuppressLint.java": """
        package android.annotation;
        public @interface SuppressLint { String[] value(); }
    """,
    "androidx/annotation/NonNull.java": """
        package androidx.annotation;
        public @interface NonNull {}
    """,
    "androidx/annotation/RequiresApi.java": """
        package androidx.annotation;
        public @interface RequiresApi { int value(); }
    """,
    "android/app/Activity.java": """
        package android.app;
        public class Activity { protected void onResume() {} }
    """,
    "android/app/AlertDialog.java": """
        package android.app;
        public class AlertDialog {
            public interface Listener { void click(Object dialog, int which); }
            public static class Builder {
                public Builder(Activity activity) {}
                public Builder setTitle(String s) { return this; }
                public Builder setMessage(String s) { return this; }
                public Builder setCancelable(boolean b) { return this; }
                public Builder setPositiveButton(String s, Listener l) { return this; }
                public void show() {}
            }
        }
    """,
    "android/hardware/display/DisplayManager.java": """
        package android.hardware.display;
        public class DisplayManager { public static final int VIRTUAL_DISPLAY_FLAG_SECURE = 4; }
    """,
    "android/os/Build.java": """
        package android.os;
        public class Build {
            public static class VERSION { public static int SDK_INT = 37, SDK_INT_FULL = 3700000; }
            public static class VERSION_CODES {
                public static final int UPSIDE_DOWN_CAKE = 34, VANILLA_ICE_CREAM = 35, BAKLAVA = 36;
            }
            public static class VERSION_CODES_FULL { public static final int BAKLAVA_1 = 3600001; }
        }
    """,
    "android/util/Log.java": """
        package android.util;
        public class Log { public static final int INFO = 4, WARN = 5, ERROR = 6; }
    """,
    "android/util/Pair.java": """
        package android.util;
        public class Pair<A, B> {
            public final A first; public final B second;
            public Pair(A a, B b) { first = a; second = b; }
            public static <A, B> Pair<A, B> create(A a, B b) { return new Pair<>(a, b); }
        }
    """,
    "android/view/SurfaceControl.java": """
        package android.view;
        public class SurfaceControl {}
    """,
    "io/github/libxposed/api/XposedModule.java": """
        package io.github.libxposed.api;
        import java.lang.reflect.*;
        import java.util.*;
        public class XposedModule {
            public final Map<Executable, Hooker> hooks = new LinkedHashMap<>();
            public final List<String> logs = new ArrayList<>();
            public final Set<String> ids = new HashSet<>();
            public interface Hooker { Object intercept(Chain chain) throws Throwable; }
            public static class Chain {
                final Object[] args;
                public Object[] forwarded;
                public int proceeds;
                public Chain(Object... args) { this.args = args; }
                @SuppressWarnings("unchecked") public <T> T getArg(int i) { return (T) args[i]; }
                public List<Object> getArgs() { return Arrays.asList(args); }
                public Object getThisObject() { return null; }
                public Object proceed() { proceeds++; forwarded = args; return Boolean.TRUE; }
                public Object proceed(Object... args) {
                    proceeds++; forwarded = args; return Boolean.TRUE;
                }
            }
            public class HookBuilder {
                final Executable executable;
                HookBuilder(Executable executable) { this.executable = executable; }
                public void setId(String id) { ids.add(id); }
                public void intercept(Hooker hooker) { hooks.put(executable, hooker); }
            }
            public HookBuilder hook(Executable e) { return new HookBuilder(e); }
            public void deoptimize(Executable e) {}
            public int getApiVersion() { return 102; }
            public void log(int level, String tag, String message) { logs.add(message); }
            public void log(int level, String tag, String message, Throwable t) { logs.add(message); }
            public interface ModuleLoadedParam {}
            public interface SystemServerStartingParam { ClassLoader getClassLoader(); }
            public interface PackageReadyParam {
                boolean isFirstPackage(); ClassLoader getClassLoader(); String getPackageName();
            }
            public interface HotReloadingParam { void setSavedInstanceState(Object state); }
            public interface HookHandle { String getId(); void unhook(); }
            public interface HotReloadedParam {
                boolean isSystemServer(); Object getSavedInstanceState();
                List<HookHandle> getOldHookHandles();
            }
            public void onModuleLoaded(ModuleLoadedParam p) {}
            public void onSystemServerStarting(SystemServerStartingParam p) {}
            public void onPackageReady(PackageReadyParam p) {}
            public boolean onHotReloading(HotReloadingParam p) { return false; }
            public void onHotReloaded(HotReloadedParam p) {}
        }
    """,
    "android/window/ScreenCaptureInternal.java": """
        package android.window;
        public class ScreenCaptureInternal {
            public static class CaptureArgs {
                public final int mSecureContentPolicy, mCaptureMode, mProtectedContentPolicy;
                public CaptureArgs(int policy, int mode) {
                    mSecureContentPolicy = policy; mCaptureMode = mode; mProtectedContentPolicy = 2;
                }
            }
            public static class DisplayCaptureArgs extends CaptureArgs {
                public DisplayCaptureArgs(int p, int m) { super(p, m); }
            }
            public static class LayerCaptureArgs extends CaptureArgs {
                public LayerCaptureArgs(int p, int m) { super(p, m); }
            }
            public static int nativeCaptureDisplay(DisplayCaptureArgs args, long listener) { return 0; }
            public static int nativeCaptureLayers(LayerCaptureArgs args, long listener, boolean sync) { return 0; }
            public static class ScreenshotHardwareBuffer {
                public boolean containsSecureLayers() { return true; }
            }
        }
    """,
    "android/window/ScreenCapture.java": """
        package android.window;
        public class ScreenCapture {
            public static class ScreenCaptureParams {
                private final int mSecureContentPolicy, mCaptureMode, mProtectedContentPolicy;
                public ScreenCaptureParams(int p, int m) {
                    mSecureContentPolicy = p; mCaptureMode = m; mProtectedContentPolicy = 2;
                }
                public int getSecureContentPolicy() { return mSecureContentPolicy; }
                public int getCaptureMode() { return mCaptureMode; }
                public int getProtectedContentPolicy() { return mProtectedContentPolicy; }
            }
            public static class CaptureArgs { public boolean mCaptureSecureLayers; }
            public static int nativeCaptureDisplay(CaptureArgs args, long listener) { return 0; }
            public static int nativeCaptureLayers(CaptureArgs args, long listener, boolean sync) { return 0; }
            public static class ScreenshotHardwareBuffer {
                public boolean containsSecureLayers() { return true; }
            }
        }
    """,
    "com/android/server/wm/WindowManagerService.java": """
        package com.android.server.wm;
        public class WindowManagerService {
            public void screenCapture(android.window.ScreenCapture.ScreenCaptureParams p,
                    android.window.IScreenCaptureCallback callback) {}
        }
    """,
    "com/android/server/wm/WindowState.java": """
        package com.android.server.wm;
        public class WindowState { public boolean isSecureLocked() { return true; } }
    """,
    "com/android/server/display/DisplayControl.java": """
        package com.android.server.display;
        public class DisplayControl { public static Object createVirtualDisplay(String n, boolean s) { return null; } }
    """,
    "com/android/server/display/VirtualDisplayAdapter.java": """
        package com.android.server.display;
        public class VirtualDisplayAdapter {
            public Object createVirtualDisplayLocked(
                android.hardware.display.IVirtualDisplayCallback c,
                android.media.projection.IMediaProjection p, int uid, String pkg, String id,
                android.view.Surface surface, int flags,
                android.hardware.display.VirtualDisplayConfig config) { return null; }
        }
    """,
    "com/oplus/screenshot/OplusScreenCapture.java": """
        package com.oplus.screenshot;
        public class OplusScreenCapture {
            public static class ScreenshotHardwareBuffer {
                public boolean containsSecureLayers() { return true; }
            }
        }
    """,
    "com/oplus/display/OplusInfoMonitor.java": """
        package com.oplus.display;
        public class OplusInfoMonitor {
            public void registerSfEvent(Object listener, int event, String tag) {}
            public int registerSfEvent(Object listener, String tag) { return 7; }
        }
    """,
    "com/oplus/screenrecorder/Listener.java": """
        package com.oplus.screenrecorder;
        public class Listener {}
    """,
    "HookHarness.java": """
        import io.github.lsposed.disableflagsecure.DisableFlagSecure;
        import io.github.libxposed.api.XposedModule;
        import io.github.libxposed.api.XposedModule.Chain;
        import android.os.Build;
        import android.window.ScreenCapture;
        import android.window.ScreenCaptureInternal;
        import java.lang.reflect.*;
        import java.util.*;

        public class HookHarness {
            static final ClassLoader LOADER = HookHarness.class.getClassLoader();
            static int assertions;
            static void check(boolean value, String message) {
                assertions++;
                if (!value) throw new AssertionError(message);
            }
            static DisableFlagSecure module() {
                var m = new DisableFlagSecure(); m.onModuleLoaded(null); return m;
            }
            static void install(DisableFlagSecure m, String name, ClassLoader loader) throws Throwable {
                var method = DisableFlagSecure.class.getDeclaredMethod(name, ClassLoader.class);
                method.setAccessible(true);
                try { method.invoke(m, loader); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            }
            static XposedModule.Hooker callback(DisableFlagSecure m, String name) {
                return m.hooks.entrySet().stream()
                    .filter(e -> e.getKey().getName().equals(name)).findFirst().orElseThrow().getValue();
            }
            static void captures() throws Throwable {
                var m = module(); install(m, "hookScreenCapture", LOADER);
                check(m.hooks.size() == 2, "both native methods installed");
                for (var entry : m.hooks.entrySet()) {
                    for (int policy : new int[]{0, 1, 2}) {
                        ScreenCaptureInternal.CaptureArgs args = entry.getKey().getName().equals("nativeCaptureDisplay")
                            ? new ScreenCaptureInternal.DisplayCaptureArgs(policy, 1)
                            : new ScreenCaptureInternal.LayerCaptureArgs(policy, 1);
                        var chain = new Chain(args, 42L, false);
                        entry.getValue().intercept(chain);
                        check(args.mSecureContentPolicy == 1, "native policy changed to capture");
                        check(args.mCaptureMode == 0, "native optimized mode allows fallback");
                        check(args.mProtectedContentPolicy == 2, "protected policy unchanged");
                        check(chain.proceeds == 1, "native original called once");
                    }
                    var invalid = new Chain(new Object(), 42L, false);
                    entry.getValue().intercept(invalid);
                    check(invalid.proceeds == 1, "reflection failure falls through");
                }
                check(m.logs.stream().filter(s -> s.contains("invoked; secure policy")).count() == 2,
                    "native hit diagnostics are once per method");
                check(m.ids.size() == 2, "API 102 hook IDs assigned");
                install(m, "hookAndroid17ScreenCaptureParams", LOADER);
                var params = new ScreenCapture.ScreenCaptureParams(2, 1);
                var chain = new Chain(params, null);
                callback(m, "screenCapture").intercept(chain);
                check(params.getSecureContentPolicy() == 1 && params.getCaptureMode() == 0,
                    "new params normalized before WMS");
                check(params.getProtectedContentPolicy() == 2 && chain.proceeds == 1,
                    "new params preserve protected policy and original call");
            }
            static void displays() throws Throwable {
                var m = module(); install(m, "hookDisplayControl", LOADER);
                check(m.hooks.isEmpty(), "no global display hook on 17");
                install(m, "hookVirtualDisplayAdapter", LOADER);
                var hook = callback(m, "createVirtualDisplayLocked");
                var ordinary = new Chain(null, null, 1000, "system", "id", null, 8, null);
                hook.intercept(ordinary);
                check((int)ordinary.forwarded[6] == 8, "ordinary system display untouched");
                var app = new Chain(null, null, 10001, "app", "id", null, 8, null);
                hook.intercept(app);
                check((int)app.forwarded[6] == 8, "ordinary app display untouched");
                var projection = new Chain(null, new Object(), 10001, "app", "id", null, 8, null);
                hook.intercept(projection);
                check((int)projection.forwarded[6] == 12, "projection gains secure flag");
                check((int)projection.forwarded[2] == 10001 && projection.proceeds == 1,
                    "owner UID and original invocation preserved");
            }
            static void optional() throws Throwable {
                var m = module(); install(m, "hookScreenshotHardwareBuffer", LOADER);
                check(m.hooks.keySet().iterator().next().getDeclaringClass()
                    == ScreenCaptureInternal.ScreenshotHardwareBuffer.class, "17 buffer owner");
                check(Boolean.FALSE.equals(callback(m, "containsSecureLayers").intercept(new Chain())),
                    "secure buffer result accepted");
                m = module(); install(m, "hookOplusInfoMonitor", LOADER);
                check(m.hooks.size() == 1, "only supported void OEM signature hooked");
                var hook = callback(m, "registerSfEvent");
                var blocked = new Chain(new com.oplus.screenrecorder.Listener(), 1,
                    "com.oplus.screenrecordersecure");
                check(hook.intercept(blocked) == null && blocked.proceeds == 0, "recorder event skipped");
                for (var chain : new Chain[]{
                    new Chain(new Object(), 1, "com.oplus.screenrecordersecure"),
                    new Chain(new com.oplus.screenrecorder.Listener(), 1, "other"),
                    new Chain(null, 1, "com.oplus.screenrecordersecure")}) {
                    hook.intercept(chain);
                    check(chain.proceeds == 1, "unrelated OEM registration preserved");
                }
                var missing = new ClassLoader(LOADER) {
                    public Class<?> loadClass(String name) throws ClassNotFoundException {
                        if (name.startsWith("com.oplus.")) throw new ClassNotFoundException(name);
                        return super.loadClass(name);
                    }
                };
                m = module();
                install(m, "hookOplusHardwareBuffer", missing);
                install(m, "hookOplusInfoMonitor", missing);
                check(m.hooks.isEmpty(), "missing OEM classes safely skipped");
                var changed = new ClassLoader(LOADER) {
                    public Class<?> loadClass(String name) throws ClassNotFoundException {
                        if (name.equals("android.window.ScreenCaptureInternal$LayerCaptureArgs"))
                            return Object.class;
                        return super.loadClass(name);
                    }
                };
                m = module(); install(m, "hookScreenCapture", changed);
                check(m.hooks.size() == 1 && m.logs.stream().anyMatch(s -> s.contains("nativeCaptureLayers skipped")),
                    "changed layers signature does not prevent display hook");
                install(m, "hookScreenshotHardwareBuffer", LOADER);
                check(m.hooks.size() == 2, "installation continues after missing method");
            }
            static void legacy() throws Throwable {
                Build.VERSION.SDK_INT = 36; Build.VERSION.SDK_INT_FULL = 3600000;
                var m = module(); install(m, "hookScreenCapture", LOADER);
                check(m.hooks.size() == 2, "Android 16 native hooks retained");
                var args = new ScreenCapture.CaptureArgs();
                callback(m, "nativeCaptureLayers").intercept(new Chain(args, 42L, false));
                check(args.mCaptureSecureLayers, "Android 16 boolean policy retained");
                install(m, "hookScreenshotHardwareBuffer", LOADER);
                check(m.hooks.keySet().stream().anyMatch(e -> e.getDeclaringClass()
                    == ScreenCapture.ScreenshotHardwareBuffer.class), "Android 16 buffer owner unchanged");
                Build.VERSION.SDK_INT_FULL = 3600001;
                m = module(); install(m, "hookScreenCapture", LOADER);
                var qpr = new ScreenCaptureInternal.DisplayCaptureArgs(0, 1);
                callback(m, "nativeCaptureDisplay").intercept(new Chain(qpr, 42L));
                check(qpr.mSecureContentPolicy == 1 && qpr.mCaptureMode == 1,
                    "Android 16 QPR internal policy path unchanged");
                install(m, "hookDisplayControl", LOADER);
                check(m.hooks.size() == 3, "legacy DisplayControl hook retained");
                Build.VERSION.SDK_INT = 37; Build.VERSION.SDK_INT_FULL = 3700000;
            }
            static Object createSurfaceLocked(XposedModule.Hooker hook) throws Throwable {
                return hook.intercept(new Chain());
            }
            static Object setInitialSurfaceControlProperties(XposedModule.Hooker hook) throws Throwable {
                return hook.intercept(new Chain());
            }
            static Object relayoutWindow(XposedModule.Hooker hook) throws Throwable {
                return hook.intercept(new Chain());
            }
            static Object refreshSecureSurfaceState(XposedModule.Hooker hook) throws Throwable {
                return hook.intercept(new Chain());
            }
            static void windows() throws Throwable {
                var m = module(); install(m, "hookWindowState", LOADER);
                var hook = callback(m, "isSecureLocked");
                check(Boolean.FALSE.equals(hook.intercept(new Chain())), "window capture gate bypassed");
                check(Boolean.TRUE.equals(createSurfaceLocked(hook)), "surface creation secure state preserved");
                check(Boolean.TRUE.equals(setInitialSurfaceControlProperties(hook)), "initial secure state preserved");
                check(Boolean.TRUE.equals(relayoutWindow(hook)), "17 relayout secure state preserved");
                check(Boolean.TRUE.equals(refreshSecureSurfaceState(hook)), "17 refresh secure state preserved");
                Build.VERSION.SDK_INT = 36;
                check(Boolean.FALSE.equals(relayoutWindow(hook)), "16 relayout behavior unchanged");
                Build.VERSION.SDK_INT = 37;
            }
            static class Handle implements XposedModule.HookHandle {
                final String id; boolean removed;
                Handle(String id) { this.id = id; }
                public String getId() { return id; }
                public void unhook() { removed = true; }
            }
            static void hotReload() {
                var old = module();
                old.onPackageReady(new XposedModule.PackageReadyParam() {
                    public boolean isFirstPackage() { return true; }
                    public ClassLoader getClassLoader() { return LOADER; }
                    public String getPackageName() { return "com.android.systemui"; }
                });
                check(old.hooks.size() == 1, "SystemUI only needs buffer result hook");
                Object[] state = {null};
                check(old.onHotReloading(value -> state[0] = value), "hot reload accepted");
                var kept = new Handle(old.ids.iterator().next());
                var stale = new Handle("removed hook");
                var next = module();
                next.onHotReloaded(new XposedModule.HotReloadedParam() {
                    public boolean isSystemServer() { return false; }
                    public Object getSavedInstanceState() { return state[0]; }
                    public List<XposedModule.HookHandle> getOldHookHandles() { return List.of(kept, stale); }
                });
                check(next.ids.contains(kept.id) && !kept.removed, "matching API 102 hook retained");
                check(stale.removed, "stale hot reload hook removed");
            }
            public static void main(String[] args) throws Throwable {
                captures(); displays(); optional(); legacy(); windows(); hotReload();
                System.out.println("PASS: " + assertions + " host assertions (no ART/device/pixel validation)");
            }
        }
    """,
}

for name in [
    "android.window.IScreenCaptureCallback",
    "android.hardware.display.IVirtualDisplayCallback",
    "android.hardware.display.VirtualDisplayConfig",
    "android.media.projection.IMediaProjection",
    "android.view.Surface",
]:
    package, simple_name = name.rsplit(".", 1)
    SOURCES[name.replace(".", "/") + ".java"] = (
        f"package {package}; public class {simple_name} {{}}"
    )


def main():
    root = Path(__file__).resolve().parents[1]
    java_home = os.environ.get("JAVA_HOME")
    javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
    java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
    if not javac or not java:
        raise SystemExit("JDK 21 is required; set JAVA_HOME or add its bin directory to PATH")
    with tempfile.TemporaryDirectory(prefix="disableflagsecure-host-") as work:
        work = Path(work)
        files = []
        for name, source in SOURCES.items():
            path = work / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(textwrap.dedent(source), encoding="utf-8")
            files.append(str(path))
        files.append(str(root / "app/src/main/java/io/github/lsposed/disableflagsecure/DisableFlagSecure.java"))
        subprocess.run([javac, "--release", "21", "-d", str(work / "classes"), *files], check=True)
        subprocess.run([java, "-cp", str(work / "classes"), "HookHarness"], check=True)


if __name__ == "__main__":
    main()
