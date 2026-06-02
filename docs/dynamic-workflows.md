# Dynamic Workflow Plan

Claude Code's dynamic workflows are product-specific and are not directly available as the same feature here. This project uses the closest supported equivalent:

- Parallel sub-agent work for bounded research/documentation tasks.
- GitHub Actions for repeatable build/export gates.
- Documentation-defined implementation stages so future agents or contributors can pick up discrete work items.

## Current Workflow Stages

1. Capability scanner
   - Verify Samsung S24/S25 camera IDs.
   - Verify front+rear concurrent sets.
   - Verify available stabilization modes.
   - Verify logical multi-camera physical IDs for wide/main/tele switching.

2. Preview director
   - Add front preview.
   - Add rear preview.
   - Add fast mode switching.
   - Add PiP and side-by-side layout controls.

3. Stabilized capture
   - Apply Camera2 stabilization mode where supported.
   - Detect fallback cases where stabilization is unavailable in concurrent mode.

4. Program compositor
   - Composite selected layout on-device.
   - Output one program surface for encoding.

5. Encoder and transport
   - Encode H.264 with `MediaCodec`.
   - Send SRT to OBS.
   - Surface connection/latency status in the UI.

6. OBS validation
   - Validate `1080p30` first.
   - Validate Wi-Fi movement.
   - Validate heat and battery behavior over sustained use.

