# Android 17 compatibility: experimental

Status on 2026-10-30: the tester reports successful FLAG_SECURE screenshots and
screen recording on a physical Android 17 device running **ColorOS 17.0.0.100**.
The module bypassed the screenshot restriction and captured the window content.

No device was connected to the build host; this device result was supplied by
the tester. The specific screenshot entry point and recording mode were not
identified. Independent AOSP device tests, individual recording modes, device
stability, module-disable recovery, and Android 16 device regressions remain
unverified. Android 17 must not be moved
into the broadly supported OS list until the device matrix below passes.

## Source audit

The audit used these Android 17 release revisions:

- frameworks/base: `94b4c163b7dfe5ce3607f7bb8456f9573f7de57d`
- frameworks/native: `ae266dcb706d083868578cfedce381ef44488a07`

All source links below are pinned to these revisions.

| Area | AOSP evidence and consequence |
| --- | --- |
| SystemUI | [`ImageCaptureImpl.captureDisplay`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/packages/SystemUI/src/com/android/systemui/screenshot/ImageCaptureImpl.kt) builds `ScreenCaptureInternal.CaptureArgs` and calls `IWindowManager.captureDisplay`. [`ScreenshotController`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/packages/SystemUI/src/com/android/systemui/screenshot/ScreenshotController.kt) also uses this image capture implementation for scroll capture transitions. |
| Legacy capture entry | [`WindowManagerService.captureDisplay`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/wm/WindowManagerService.java) converts the request to `LayerCaptureArgs`, then calls `ScreenCaptureInternal.captureLayers`, which reaches `nativeCaptureLayers`. |
| New capture entry | [`ScreenCapture.capture`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/window/ScreenCapture.java) sends `ScreenCaptureParams` through `IWindowManager.screenCapture`. WMS translates it to `DisplayCaptureArgs`; [`DisplayManagerService.systemScreenshotInternal`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/display/DisplayManagerService.java) calls `ScreenCaptureInternal.captureDisplay`, reaching `nativeCaptureDisplay`. There is no separate Java native capture path to invent a hook for. |
| Policies | `ScreenCaptureParams.Builder.setSecureContentPolicy` accepts REDACT=0, CAPTURE=1, THROW_EXCEPTION=2. CAPTURE_MODE_NONE=0 and REQUIRE_OPTIMIZED=1. The default builder policy is REDACT. WMS overrides both secure and protected policies to THROW_EXCEPTION for REQUIRE_OPTIMIZED. That mode explicitly disallows automatic fallback. |
| JNI | [`ScreenCaptureInternal`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/window/ScreenCaptureInternal.java) still declares `nativeCaptureDisplay(DisplayCaptureArgs, long)` and `nativeCaptureLayers(LayerCaptureArgs, long, boolean)`. [`android_window_ScreenCapture.cpp`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/jni/android_window_ScreenCapture.cpp) reads the integer `CaptureArgs.mSecureContentPolicy` directly. The existing module's value 1 is correct and still reaches both paths. |
| Capture results | `ScreenshotHardwareBuffer` now belongs to **ScreenCaptureInternal**, including `containsSecureLayers`. The old module still looked for `ScreenCapture$ScreenshotHardwareBuffer`, so its optional result check hook could not install on this AOSP revision. Returning false from this method only affects consumers' rejection checks; it cannot reconstruct blacked-out pixels. |
| Window security | [`WindowState.isSecureLocked`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/wm/WindowState.java) still checks FLAG_SECURE, sensitive-content policy, and device policy. It is also used when creating and updating secure surfaces. WMS `relayoutWindow` and [`RootWindowContainer.refreshSecureSurfaceState`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/wm/RootWindowContainer.java) pass its result to `setSecureLocked`. Android 17 now preserves these update callers as well as the existing surface-creation callers. |
| SurfaceControl | [`SurfaceControl`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/core/java/android/view/SurfaceControl.java) still supplies layer handles and secure transactions. There is no global hook of `Builder.setSecure`, `Transaction.setSecure`, or surface creation. Capture policy, rather than permanent surface flag removal, is used. |
| Virtual displays | [`VirtualDisplayAdapter.createVirtualDisplayLocked`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/display/VirtualDisplayAdapter.java) has eight arguments, with projection at index 1 and flags at index 6. Its display factory calls the **six-argument** [`DisplayControl.createVirtualDisplay`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/display/DisplayControl.java), bypassing the old two-argument hook. Only calls with a non-null MediaProjection receive VIRTUAL_DISPLAY_FLAG_SECURE in the new branch. |
| MediaProjection | [`MediaProjectionManagerService`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/services/core/java/com/android/server/media/projection/MediaProjectionManagerService.java) continues to manage consent, tokens and sessions. These checks are left intact. Projection/mirroring is a virtual display path, not a call to the Java screenshot natives on every frame. |
| SurfaceFlinger | [`SurfaceFlinger`](https://android.googlesource.com/platform/frameworks/native/+/ae266dcb706d083868578cfedce381ef44488a07/services/surfaceflinger/SurfaceFlinger.cpp) checks READ_FRAME_BUFFER and CAPTURE_BLACKOUT_CONTENT and makes the capture target secure only for `SecureLayerMode::Capture`. System and graphics UIDs are explicitly accepted for blackout content. DPU readback can fail for protected content or unsupported configurations when required. Keeping a secure source layer is compatible with a permitted secure capture target; it does not grant ordinary shell callers additional permissions. |

The native policy hook was already present for Android 16 QPR and was **not**
proven to be absent or broken on a running Android 17 device. The changes fix
source-confirmed result-class and display-call mismatches and harden the new
parameter path. They do not prove that any particular device's black screenshots
have been fixed.

The [Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17#deprecate-setcontentcaptureenabled)
deprecate `ContentCaptureManager.setContentCaptureEnabled(false)` for content
capture / system intelligence. This API is not the FLAG_SECURE pixel capture
policy, and is not hooked here.

## Hook changes

Everything below is gated on API >= 37; Android 12-16 retain the prior capture,
window-state, display-control and virtual-display algorithms.

- WMS `screenCapture(ScreenCaptureParams, IScreenCaptureCallback)`: set secure
  content policy to CAPTURE before translation and change REQUIRE_OPTIMIZED to
  NONE. This permits ordinary composition/fallback rather than demanding DPU
  readback. The protected-content policy and other parameters remain unchanged.
- `ScreenCaptureInternal.nativeCaptureDisplay` and `nativeCaptureLayers`: resolve
  each signature separately, set the integer policy to CAPTURE, and normalize
  REQUIRE_OPTIMIZED to NONE as a fallback for direct internal callers. A missing
  method does not prevent the other method or subsequent hooks from installing.
- `ScreenCaptureInternal.ScreenshotHardwareBuffer.containsSecureLayers`: use the
  actual Android 17 result class in system_server and recommended screenshot
  processes. SystemUI does not need blanket native hooks; AOSP delegates capture
  to system_server.
- `WindowState.isSecureLocked`: retain the existing screenshot eligibility
  bypass, while preserving real security results during surface creation,
  initial properties, relayout, and secure-surface refresh. Surface security
  transactions are not globally suppressed.
- `VirtualDisplayAdapter.createVirtualDisplayLocked`: validate the full AOSP
  signature and add SECURE only to MediaProjection displays. Do not force all
  DisplayControl calls to secure on API 37. Wi-Fi Display, Miracast, and virtual
  displays without MediaProjection keep their original flags in this branch.

API 102 executable IDs, `onHotReloading` state, and old-handle cleanup are retained.
All new hooks use `hookE`. Missing classes, methods, and unexpected field types
are diagnosed and skipped; capture field-update failures are logged once and
delegate to the original method. Capture hits are logged once per installed
method to avoid logging every frame.

## Optional OPlus integration

[PR #126](https://github.com/LSPosed/DisableFlagSecure/pull/126), head
`be6d5e0b954241d208ad6957e8e87186d4d57a9a`, was inspected. Its author's reported
ColorOS device results are **not** this change's test results.

The isolated optional methods reuse its two relevant ideas:

- In `com.oplus.screenshot` and `com.oplus.screenrecorder`,
  `OplusScreenCapture$ScreenshotHardwareBuffer.containsSecureLayers()` returns
  false if the class exists and the zero-argument method returns boolean.
- Only in `com.oplus.screenrecorder`, void `OplusInfoMonitor.registerSfEvent`
  overloads with at least three parameters, a reference listener and a String
  third parameter are candidates. Skip the call only when the tag equals
  `com.oplus.screenrecordersecure` **and** the listener class belongs to
  `com.oplus.screenrecorder.*`. Other registrations are forwarded.

The recorder is the sole addition to `scope.list`. Select it only for the OPlus
recorder integration; ordinary target applications should not be selected.
Absent OEM classes/signatures are safe skips. No dependency or Gradle upgrades
from the PR were imported.

## Local verification

Use the existing wrapper and SDK configuration:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
.\gradlew.bat :app:assembleRelease --console=plain
python tests/host_hooks.py
git diff --check
```

On Unix, the build command is `./gradlew :app:assembleDebug :app:lintDebug`.
The host harness requires Python 3 and JDK 21 (`JAVA_HOME` or PATH). It compiles
the actual module source against small fake Android/libxposed classes and
executes callbacks. It exercises native policies, the new WMS parameters,
protected-policy preservation, failure isolation, virtual display scoping,
optional OEM checks, legacy Android 16 routing, surface update guards, and API
102 hot reload IDs/cleanup. It does **not** emulate libxposed, ART, SurfaceFlinger,
OEM frameworks, permissions, or hardware buffers.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Lint report: `app/build/reports/lint-results-debug.html`.

Local results: `:app:assembleDebug :app:lintDebug` succeeded with 0 lint errors
and 5 warnings in unchanged files (annotation version, backup rules, resource
shrinking, and two strings in the existing debug Activity). The host harness
passed 61 assertions. The R8-minified `:app:assembleRelease` also succeeded.
Both APKs passed `apksigner verify`; their packaged Xposed metadata retained
`targetApiVersion=102` and `autoHotReload=true`. `git diff --check` passed.
These are host results only.

## Device test procedure

Use a rooted Android 17 API 37 device with official LSPosed supporting libxposed
API 102. Keep a recovery method available and test on a nonessential device.
Use an ordinary test Activity which toggles
`window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)` and
`window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)` while showing a
colored checkerboard, a changing timestamp, and readable text. This content
allows actual image inspection; successful return codes or nonzero PNG size do
not prove that secure pixels were captured. Do not select this test app in the
module scope.

The existing debug APK already includes a secure-at-launch smoke-test Activity:

```powershell
adb shell am start -n io.github.lsposed.disableflagsecure/.TestActivity
```

Its yellow TextView region and cyan secure SurfaceView region should both be
visible in the enabled-module screenshot. This existing Activity always uses
FLAG_SECURE and a secure SurfaceView, so a separate ordinary/toggle Activity is
still needed for the complete baseline and relayout matrix.

1. Record build details and the baseline: disable the module, reboot, capture
   the ordinary test window, then turn FLAG_SECURE on and confirm capture is
   blocked or redacted. Check both secure-at-launch and toggled-after-launch
   variants.
2. Install the debug APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`,
   enable it for the recommended scope, and reboot. Installing alone does not
   enable the module. Confirm Android 17 hook installation messages.
3. Capture the same ordinary and secure windows with the system screenshot UI
   and with volume-down + power. Inspect the checkerboard, text, and timestamp
   in the saved images. Empty, black, or stale content is a failure.
4. Toggle FLAG_SECURE repeatedly, rotate, enter split-screen, background and
   reopen the test app. Repeat capture to exercise relayout and surface refresh.
5. Start a user-consented MediaProjection session with both full-display and
   single-app sharing. Inspect the encoded recording, stop/restart projection,
   and confirm the permission UI still works. Check an ordinary non-projection
   virtual display, and Wi-Fi Display / Miracast if available, for regressions.
6. Disable the module, reboot, and verify that FLAG_SECURE protection returns.
   Reboot matters: hot reload is not proof that previously created display or
   surface state has been reset.
7. Repeat baseline/enabled/disabled checks on Android 16, including QPR if
   available. This is a device regression test; host routing checks cannot
   substitute for it.
8. Check system_server and SystemUI logs throughout, not only at the end. Record
   any crashes, process restarts, black outputs, policy denials, and hook skips.

Capture metadata and logs from PowerShell:

```powershell
New-Item -ItemType Directory -Force device-results
adb shell getprop ro.build.fingerprint > device-results/fingerprint.txt
adb shell getprop ro.build.version.sdk > device-results/sdk.txt
adb shell dumpsys window > device-results/window.txt
adb shell dumpsys display > device-results/display.txt
adb shell dumpsys media_projection > device-results/media-projection.txt
adb shell pidof system_server com.android.systemui > device-results/pids-before.txt
adb logcat -b all -d -v threadtime > device-results/logcat-before.txt
adb logcat -b all -v threadtime > device-results/logcat-live.txt
# Stop the live collector with Ctrl+C after reproducing captures and recording.
adb logcat -b crash -d -v threadtime > device-results/crash.txt
adb shell pidof system_server com.android.systemui > device-results/pids-after.txt
```

Also export LSPosed's module/framework logs: its logger may not appear in
logcat on every framework build. Look for:

```text
Android 17 WMS.screenCapture(ScreenCaptureParams, callback) installed
Android 17 installed ...nativeCaptureDisplay...
Android 17 installed ...nativeCaptureLayers...
Android 17 WindowState.isSecureLocked installed; surface updates preserved
Android 17 VirtualDisplayAdapter.createVirtualDisplayLocked installed
Android 17 ...ScreenshotHardwareBuffer.containsSecureLayers installed
Android 17 nativeCaptureLayers invoked; secure policy=1
Android 17 WMS.screenCapture invoked; secure policy=1
Android 17 nativeCaptureDisplay invoked; secure policy=1
Android 17 MediaProjection virtual display invoked; secure=true
```

Installation is not an execution hit. AOSP SystemUI should hit native layers;
the new ScreenCapture API should hit WMS.screenCapture and native display.
Use a platform-signed test client with READ_FRAME_BUFFER to explicitly exercise
`ScreenCapture.capture` with default params and REQUIRE_OPTIMIZED params if
available; normal app access must not be assumed. A missing WMS hit from the
legacy SystemUI path is expected.

For shell screencap, use device-side output followed by pull to avoid binary
redirection corruption on older PowerShell:

```powershell
adb shell screencap -p /sdcard/dfs-shell.png
adb pull /sdcard/dfs-shell.png device-results/dfs-shell.png
```

Report this result separately. [`screencap.cpp`](https://android.googlesource.com/platform/frameworks/base/+/94b4c163b7dfe5ce3607f7bb8456f9573f7de57d/cmds/screencap/screencap.cpp)
uses the native ScreenshotClient directly, outside the module's Java hooks.
Its default redaction and native permission checks may still produce black
secure regions even while the system screenshot works. Running it as root
does not route it through the Java hooks or change its default capture policy.

## Acceptance matrix and limits

| Test | Verification status | Required device result |
| --- | --- | --- |
| Debug compilation / lint | Passed; 0 errors, 5 warnings in unchanged files | Successful build / no new lint errors |
| Host callback harness | Passed; 61 assertions | All assertions pass; no pixel claims |
| FLAG_SECURE screenshot | Passed on ColorOS 17.0.0.100, reported by the tester | Window content captured despite FLAG_SECURE |
| Screen recording | Passed on ColorOS 17.0.0.100, reported by the tester | Recorded content is visible |
| Ordinary + FLAG_SECURE system screenshot entry-point matrix | Specific entry points and ordinary-window control not reported | Both contain correct pixels when enabled |
| Volume-down + power | Pending device | Saved screenshot contains secure test content |
| New ScreenCaptureParams API, including optimized requests | Pending privileged test client | Capture succeeds with ordinary secure content |
| MediaProjection full-display + single-app | Recording passed; individual modes and consent/stop behavior not reported | Readable recording; normal consent/stop behavior |
| Native adb screencap | Pending device; known path limitation | Record actual result separately |
| Module disabled and rebooted | Pending device | Original protection restored |
| Android 16 and QPR regression | Host routing only; device pending | Existing screenshots/recording still work |
| system_server / SystemUI stability | Pending device | No new crashes or process restarts |
| Optional ColorOS screenshot/recorder hooks | ColorOS screenshot and recording passed; individual hook hits not reported | Both capture ordinary FLAG_SECURE test content |

Widevine L1, hardware DRM, protected HardwareBuffers, device policy enforcement,
OEM capture stacks outside the observed signatures, and native callers with
insufficient permissions are not certified by this work. Protected-content
policy is never set to CAPTURE. No protected buffer pixel extraction is added.
