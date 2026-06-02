# SRT to OBS Workflow

## Stream Shape

The phone sends one composited program feed to OBS. Front and rear cameras are baked into the same H.264 stream after on-device PiP compositing.

If OBS needs front and rear as independently movable scene layers, the app must send two separate streams or use two phones. The standard workflow sends one finished program feed.

## App Output Defaults

Recommended first-run settings:

| Setting | Value |
| --- | --- |
| Protocol | SRT |
| Mode | Caller |
| Codec | H.264 |
| Resolution | `1920x1080` |
| FPS | `30` |
| Bitrate | `6000-10000 Kbps` |
| Keyframe interval | `2 seconds` |
| Audio | Off by default |

## OBS Listener

Create a Media Source in OBS and configure it to listen for SRT:

```text
srt://0.0.0.0:9001?mode=listener&latency=200000&timeout=5000000
```

Then configure the Android app to call the OBS machine:

```text
srt://<OBS_PC_LAN_IP>:9001?mode=caller
```

Example:

```text
srt://192.168.1.9:9001?mode=caller
```

## Operator Checklist

1. Put the OBS PC on Ethernet.
2. Put the phone on the same 5 GHz or 6 GHz Wi-Fi network.
3. Start OBS and confirm the SRT Media Source is listening.
4. Open OBS Mobile Director.
5. Confirm concurrent camera mode is active.
6. Select `1080p30` and a moderate bitrate.
7. Start SRT output from the phone.
8. Increase bitrate or resolution only after the stream is stable.

## Network Notes

SRT uses UDP. The OBS PC firewall must allow inbound UDP on the selected port, commonly `9001`.

Avoid:

- Guest Wi-Fi networks.
- VPN routes between phone and OBS PC.
- High bitrate on crowded 2.4 GHz Wi-Fi.
- Starting at 4K before validating stability.

## Recovery Behavior

The app should keep stream recovery predictable:

- Show clear connection state: disconnected, connecting, live, retrying, failed.
- Retry after temporary network loss.
- Keep camera preview active if SRT disconnects.
- Allow bitrate reduction without restarting the whole app when possible.

