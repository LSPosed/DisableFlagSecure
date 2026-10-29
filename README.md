# Enable Screenshot (formerly known as Disable FLAG_SECURE)
Enabling screenshots in apps that normally wouldn\'t allow it, and disabling screenshot(Android 14+) & screen record(Android 15+) detection.

**Unofficial LSPosed versions are not supported.**

## Supported OSes
- Android 12-16 (Custom ROMs are **not** supported)
- Xiaomi Hyper OS
- OPlus OS (Color OS/Realme UI/Oxygen OS)
- Samsung One UI

## Android 17 status

Android 17 (API 37) compatibility is **experimental**. FLAG_SECURE screenshots
and screen recording have been successfully tested on a physical device running
**ColorOS 17.0.0.100**.
The module handles AOSP's `ScreenCaptureInternal` and `ScreenCaptureParams`
capture paths, with separate optional ColorOS 17 screenshot/recorder hooks.
Other Android 17 builds and individual recording modes still require device validation.

Use the recommended scope; `com.oplus.screenrecorder` is only needed for the
optional OPlus recorder integration. Ordinary target apps do not belong in scope.
Hardware DRM and protected HardwareBuffers are outside this module's scope.
Native `adb shell screencap` does not pass through the Java capture hooks and
may still redact secure layers.

See [the Android 17 implementation notes and device test procedure](docs/android17.md)
for source references, diagnostics, validation results, and limitations.

## Usage
1. Enable the module
2. Select **ONLY** recommended apps
3. Reboot
