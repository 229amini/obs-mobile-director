# Android Project Notes

## Package Name

The package prefix `com.229mG...` is invalid. Android package segments follow Java/Kotlin identifier rules, and a segment cannot begin with a number.

Use this normalized package:

```text
com.mostafa229.obsmobiledirector
```

Keep the package lowercase for consistency with Android conventions.

## Native Android Scope

This project is intended as a native Android app, not a web wrapper. Core media features should use Android platform APIs:

- CameraX for camera lifecycle and high-level capture.
- Camera2 interop for concurrent camera tuning and advanced controls.
- OpenGL ES or Vulkan-backed rendering for PiP compositing.
- `MediaCodec` for hardware H.264 encoding.
- SRT library bindings for UDP-based transport to OBS.

## Permissions

Expected permissions:

```text
android.permission.CAMERA
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.RECORD_AUDIO
```

`RECORD_AUDIO` should only be requested if audio capture is enabled. If the default stream is video-only, delay audio permission until the user turns audio on.

## Runtime Capability Checks

Do not assume every Samsung S24/S25 variant exposes the same concurrent camera stream combinations.

At startup or before enabling director mode, check:

- Front+rear concurrent camera support.
- Supported resolutions and frame rates for each camera pair.
- Stabilization compatibility.
- Hardware H.264 encoder availability.
- Thermal and performance headroom during sustained capture.

## Product Constraints

The first stable release should prioritize:

- Reliable `1080p30` output.
- Low-latency preview.
- Clear SRT connection status.
- Fast camera swap and PiP controls.
- Graceful fallback when concurrent capture is unavailable.

Defer advanced features until the core pipeline is stable:

- Dual independent SRT streams.
- 4K program output.
- Complex scene layouts.
- Heavy software stabilization.
- Audio mixing.
