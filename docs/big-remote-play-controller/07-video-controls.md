# 07 — Video controls

## What can change during a session

Checked in `moonlight-common-c` and `NvConnection`: resolution, frame rate,
bitrate, codec and HDR are negotiated in RTSP when the stream starts
(`LiStartConnection` takes them once). There is no message to change them in a
running session, and Sunshine has none either. So:

| Setting | Live? | How it is applied |
| --- | --- | --- |
| Statistics overlay | live | `prefConfig.enablePerfOverlay` read every second |
| Fit / stretch | live | view size only |
| On-screen controller visibility, opacity | live | views |
| Bitrate, resolution, frame rate, codec | **reconnect** | saved, then a controlled reconnect |
| Entry (screen, Desktop Low…) | **reconnect** | see [08](08-multi-monitor.md) |

The Video page says "Requires reconnecting" in the warning colour and its main
button is **Apply and reconnect** — enabled only when something differs from the
saved settings. Only the keys that changed are written (so a setting never
touched keeps following the app's default, e.g. 720p).

## Bitrate

- Slider from 0.5 to 150 Mbps in 0.5 Mbps steps (the Settings limits).
- Presets scale with Moonlight's own default for the chosen resolution and frame
  rate (`getDefaultBitrate`): Data saver ×0.4, Balanced ×1, High ×1.5, Ultra ×2.5,
  rounded to 0.5 Mbps and clamped (`brp/QualityPresets`, unit-tested). The preset
  matching the current value (within 10%) is marked.

### Lower quality and reconnect

One conservative step (`QualityPresets.lower`): 40% less bitrate, never below
1.5 Mbps; once at or below 5 Mbps, also cap at 720p and 60 FPS, because a starved
high resolution looks worse and costs the decoder more. Offered in Quick and in
the connection-lost dialog.

### Automatic bitrate: not implemented

A real automatic mode would have to reconnect to change anything, and a stream
that drops for a second every time the network wobbles is worse than a stable
lower bitrate. The current design gives the person one-tap actions with real
numbers instead. An automatic mode, if added, should only *suggest* (banner)
after a sustained window (e.g. 10 s of >5% loss), with hysteresis and a cooldown;
`StreamStats.quality()` already provides the classification.

## Codec

Automatic, H.264, and only the codecs this device decodes: HEVC if
`isHevcSupported()`, AV1 if `isAv1Supported()` (the decoder's own checks). Saved
to upstream's `video_format`.

## Renderer: no choice offered

Audited: there is one rendering path — MediaCodec decoding straight to the
`SurfaceView`'s surface. `GlPreferences.glRenderer` is the GPU's renderer
*string*, read once to apply per-GPU decoder quirks; it is not a choice. Software
decoders are excluded on purpose (`MediaCodecHelper` skips them). Offering a
"renderer" list would be a control that changes nothing, so there is none. The
Video page shows the active decoder's name.

## Frame rate

30 and 60 always; 90/120/144 only when the screen has a mode at that rate
(`Display.getSupportedModes`).

## Not done

A "This device" capabilities page (max decoder resolution/FPS per codec, HDR
types) and latency presets (Lowest latency / Balanced / Smoothest over the
existing frame-pacing setting).
