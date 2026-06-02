# OBS Mobile Director

Android native camera director for sending a stabilized, composited mobile camera feed to OBS over a low-latency network path.

Target devices for the first build:

- Samsung Galaxy S24
- Samsung Galaxy S25

Initial MVP:

- Back camera full screen
- Front camera full screen
- Back camera with front PiP
- Front camera with back PiP
- Side-by-side front/back mode
- Runtime camera capability scan
- Hardware stabilization detection
- SRT target configuration for OBS

The Android package is:

```text
com.mostafa229.obsmobiledirector
```

`com.229mG...` is not valid for Android/Kotlin because package segments cannot start with a number.

## Build

The project is configured for GitHub Actions export. Local build requires Android Studio or a local Android SDK/Gradle installation.

GitHub Actions produces:

- Debug APK on every push/PR
- Unsigned release APK on manual run or `v*` tag

## OBS Direction

The first stable transport target is one composited stream:

```text
Phone app -> SRT -> OBS Media Source
```

Two independent streams from one phone are intentionally deferred because they increase encoder load, heat, battery drain, and sync complexity.

