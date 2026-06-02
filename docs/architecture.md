# Architecture

## Goal

Build a native Android app that turns a Samsung Galaxy S24/S25 into a self-contained mobile director:

1. Capture front and rear cameras at the same time.
2. Stabilize and prepare each camera feed.
3. Composite a picture-in-picture program view.
4. Encode the program feed as H.264.
5. Publish the stream to OBS by SRT.

## High-Level Pipeline

```text
Front Camera   -> Camera input -> Transform/Stabilize -> PiP compositor
Rear Camera    -> Camera input -> Transform/Stabilize -> PiP compositor
                                                               |
                                                               v
Preview Surface <- Program renderer <- H.264 encoder <- SRT sender -> OBS
```

## Main Modules

### Camera Controller

Owns camera discovery, lifecycle binding, concurrent camera validation, resolution/FPS selection, zoom/lens controls, exposure, focus, and torch state.

Recommended split:

- Use CameraX for lifecycle, preview, common controls, and device compatibility.
- Use Camera2 interop where CameraX does not expose enough control for concurrent camera behavior or Samsung-specific tuning.

### Frame Pipeline

Receives frames from both cameras and normalizes them into a common render path.

Responsibilities:

- Match target frame rate, usually `30 fps` first.
- Convert camera buffers into GPU textures when possible.
- Apply rotation, mirroring, crop, and aspect-fit/fill transforms.
- Keep timestamps consistent enough for stable compositing.

### Stabilization

Prefer device/hardware stabilization when supported by the selected camera stream configuration. Fall back to software stabilization only when the latency and crop cost are acceptable.

The app should expose a simple stabilization mode:

- `Off`
- `Standard`
- `High`, only if the selected resolution/FPS can sustain it

### Program Compositor

Renders the final OBS-facing output with rear camera as the default full-frame source and front camera as PiP.

Responsibilities:

- Layout PiP position, size, border, and optional shadow.
- Support quick swaps between front/rear main view.
- Render to both local preview and encoder input.
- Avoid CPU frame copies in the hot path.

### Encoder

Uses Android `MediaCodec` H.264 hardware encoding.

Baseline target:

- `1920x1080`
- `30 fps`
- `6-10 Mbps`
- `2 second` keyframe interval
- H.264 AVC, hardware encoder where available

The encoder should accept a surface input from the compositor so the rendered program feed is encoded directly.

### SRT Sender

Publishes encoded H.264 to OBS as an SRT caller.

Responsibilities:

- Maintain connection state and retry policy.
- Surface bitrate, latency, packet loss, and connection errors.
- Keep OBS-oriented defaults simple.

OBS listens; the phone calls:

```text
srt://<OBS_PC_LAN_IP>:9001?mode=caller
```

## Threading Model

Keep UI, camera control, rendering, encoding, and networking separated:

- UI thread: controls and status only.
- Camera threads: CameraX/Camera2 callbacks and frame acquisition.
- GL/render thread: compositing and preview rendering.
- Encoder thread: `MediaCodec` drain loop.
- Network thread: SRT socket writes and reconnects.

## Failure Modes

The app should detect and report:

- Concurrent camera mode unavailable.
- Requested resolution/FPS unsupported for dual capture.
- Encoder initialization failure.
- SRT connection refused or timed out.
- Wi-Fi throughput too low for selected bitrate.
- Thermal throttling or sustained frame drops.

