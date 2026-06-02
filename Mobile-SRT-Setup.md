# Mobile SRT to OBS Setup

PC LAN IP: `192.168.1.9`

OBS scene collection created:

- `Mobile SRT`
- `Phones - Split Monitor`
- `Phone 1 - Full`
- `Phone 2 - Full`

## Phone App

Use Larix Broadcaster on each phone.

Larix is a good fit for easy mobile lens control:

- Front/back camera hot switching is supported.
- Wide/telephoto support is available on supported multi-lens phones.
- Multi-camera capture is available on supported devices.
- On iOS, picture-in-picture and side-by-side multi-camera capture is supported on iPhone XR, iPhone XS, iPhone XS Max, iPad Pro 3rd generation, and newer devices.
- On Android, multi-camera capture depends heavily on Android version and the phone manufacturer's camera API support.

Important limitation: multi-camera capture from one phone usually arrives in OBS as one composited stream. For example, front+back picture-in-picture is baked into the same video feed. If you need front and back as two independent OBS layers that can be moved, cropped, hidden, or switched separately inside OBS, use two phones or a dedicated native/custom app that can publish two separate streams.

## Phone 1 Larix Connection

- Protocol: `SRT`
- URL: `srt://192.168.1.9:9001`
- Mode: `Caller`
- Video codec: `H.264`
- Resolution: `1920x1080`
- FPS: `30`
- Bitrate: `6000-10000 Kbps`
- Keyframe interval: `2 seconds`
- Audio: off, unless you specifically want phone audio

## Phone 2 Larix Connection

- Protocol: `SRT`
- URL: `srt://192.168.1.9:9002`
- Mode: `Caller`
- Video codec: `H.264`
- Resolution: `1920x1080`
- FPS: `30`
- Bitrate: `6000-10000 Kbps`
- Keyframe interval: `2 seconds`
- Audio: off, unless you specifically want phone audio

## OBS Inputs

The created OBS media sources listen on:

- `Phone 1 SRT`: `srt://0.0.0.0:9001?mode=listener&latency=200000&timeout=5000000`
- `Phone 2 SRT`: `srt://0.0.0.0:9002?mode=listener&latency=200000&timeout=5000000`

Start OBS first, then start streaming from the phones.

## Windows Firewall

If the phones do not appear in OBS, allow inbound UDP ports:

- `9001`
- `9002`

Fast manual path:

1. Open Windows Defender Firewall.
2. Go to Advanced settings.
3. Create a new Inbound Rule.
4. Choose Port.
5. Choose UDP.
6. Enter `9001-9002`.
7. Allow the connection.
8. Apply to Private networks.
9. Name it `OBS Mobile SRT`.

## Wi-Fi Notes

- Keep the PC on Ethernet.
- Put both phones on the same 5 GHz or 6 GHz Wi-Fi network.
- Avoid guest Wi-Fi, because it often blocks phone-to-PC traffic.
- Start at `1080p30`; move to `4K` only after the link is stable.
- If movement causes drops, reduce bitrate before reducing resolution.

## Why Not OBS Teleport for Phones

OBS Teleport is useful for sending an OBS scene from one computer to another computer on the LAN. It is not a mobile camera ingest protocol.

Use OBS Teleport when the sender is another PC/laptop running OBS.

Use Larix SRT when the sender is a phone camera.

Possible hybrid setup:

- Phone 1 and Phone 2 use Larix SRT into the mobile/camera OBS machine.
- That OBS machine composites/switches the phones.
- OBS Teleport sends that finished scene to the main streaming OBS machine.
