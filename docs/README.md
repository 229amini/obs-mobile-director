# OBS Mobile Director Documentation

OBS Mobile Director is a native Android camera director app concept for Samsung Galaxy S24/S25-class devices. It captures front and rear cameras concurrently, composites a picture-in-picture program feed on-device, encodes to H.264, and sends the result to OBS over SRT.

## Documents

- [Architecture](architecture.md) - major app modules and media pipeline.
- [Capture and Compositing](capture-compositing.md) - CameraX/Camera2 concurrent camera strategy, PiP layout, and stabilization.
- [SRT to OBS Workflow](srt-obs-workflow.md) - app-to-OBS connection flow, stream settings, and operator checklist.
- [Android Project Notes](android-project-notes.md) - target devices, package naming, permissions, and constraints.

## Package Name

The package form `com.229mG...` is invalid for Android/Kotlin because a package segment cannot start with a digit. Normalize the application package to:

```text
com.mostafa229.obsmobiledirector
```
