# Capture and Compositing

## Target Devices

Primary targets are Samsung Galaxy S24 and S25-class devices. These phones are expected to have strong camera hardware, hardware H.264 encoding, and enough GPU capacity for real-time PiP compositing, but concurrent camera support still must be detected at runtime.

## Concurrent Front and Rear Capture

Use Android concurrent camera APIs defensively:

1. Enumerate camera IDs and capabilities.
2. Select one rear camera and one front camera.
3. Check whether the front+rear combination is supported for concurrent operation.
4. Bind only stream combinations the device reports as supported.
5. Fall back to single-camera mode if concurrent capture is unavailable.

CameraX should be the default API because it handles lifecycle and common device quirks. Camera2 interop should be used for lower-level configuration, stabilization flags, frame duration, and device-specific controls when CameraX alone is not enough.

## Resolution Strategy

Start with a stable profile before attempting maximum quality:

| Mode | Main Use | Output |
| --- | --- | --- |
| Stable | Default streaming | `1080p30` |
| Quality | Strong Wi-Fi and cool device | `1080p60` or `4K30` |
| Recovery | Weak Wi-Fi or thermal pressure | `720p30` |

Both camera inputs do not need to match the output resolution. The compositor can render one full-frame feed and scale the PiP feed.

## PiP Layout

Default layout:

- Rear camera: full-frame background.
- Front camera: lower-right PiP.
- PiP size: about `24-30%` of output width.
- PiP aspect: preserve camera aspect ratio.
- PiP safety margin: leave room for OBS overlays and captions.

Recommended controls:

- Swap main/PiP camera.
- Move PiP to four corners.
- Resize PiP.
- Hide/show PiP.
- Mirror front camera preview and optionally program output.

## Stabilization

Prefer camera-native stabilization because it is lower latency and power efficient.

Implementation order:

1. Query supported stabilization modes per selected camera.
2. Enable stabilization only when compatible with the selected resolution/FPS.
3. Keep a visible state when stabilization is unavailable.
4. Avoid heavy software stabilization unless the user explicitly chooses it.

Stabilization can alter crop and field of view, so the compositor should not assume fixed framing after the mode changes.

## Rendering Path

Use a GPU-first render path:

```text
Camera texture -> transform shader -> compositor framebuffer -> preview surface
                                                   |
                                                   v
                                           encoder input surface
```

Avoid converting every frame through CPU bitmaps. CPU copies will increase latency, heat, and frame drops.

## Sync Expectations

Front and rear frames may not arrive at identical times. For a director-style stream, visual smoothness is more important than strict multi-camera sync.

Acceptable behavior:

- Composite the freshest available frame from each camera.
- Drop late frames instead of blocking the renderer.
- Keep output cadence steady at the encoder frame rate.

