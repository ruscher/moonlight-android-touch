# 01 — Architecture

Big Remote Play Controller is Moonlight for Android 12.2 with a Big Remote Play
layer on top. Moonlight remains the streaming engine (protocol, decoder, audio,
gamepads); Big Remote Play owns the product experience.

## Branches

| Branch | Contents |
| --- | --- |
| `master` | Upstream Moonlight 12.2 |
| `brp/native-touch` | Native multi-touch for host-drawn controllers, decoder-size fit, our application id ([BRP-TOUCH.md](../../BRP-TOUCH.md)) |
| `brp/modern-ui` | Icon, AshyWall look, computers/entries screens, About, `12.2-brpc3` ([BRP-UI.md](../../BRP-UI.md)) |
| `brp/control-center` | This work: Stream Control Center, extra keys bar, screen switching, quality controls, statistics, reconnect |

## Layers in the stream activity

```text
Game (Activity, upstream)            protocol, decoder, input paths, lifecycle
 │   a handful of hooks ──────────►  BrpStreamController (implements StreamSession)
 │                                     ├─ StreamControlCenter   (brp/ui, views only)
 │                                     ├─ RemoteKeyboardBar     (brp/ui, views only)
 │                                     └─ background: host app list, quit, clipboard
 └─ MediaCodecDecoderRenderer ──────► StreamStats snapshot once per second
```

- `brp/` — pure Java, no Android imports, unit-tested: `ModifierKeys`,
  `HostEntries`, `QualityPresets`, `StreamStats`, `PullDownGesture`, `TextChunker`.
- `brp/ui/` — views drawn in code (`BrpUi`, `FlowLayout`), talking to the stream
  only through the `StreamSession` interface. No protocol code.
- `BrpStreamController` — the only class that knows both sides. `Game` calls it
  from `onCreate`, `connectionStarted`, `connectionTerminated`, `onStop`,
  `onWindowFocusChanged`, `onConfigurationChanged`, `onBackPressed`,
  `handleKeyDown` and `getModifierState`.

`Game.java` grew by ~260 lines (hooks, `onBackPressed`, the connection-lost
dialog, relaunch); everything else is in new files. A few `Game` fields became
package-private so the controller can read them; behaviour is unchanged.

## What the overlays never do

- Touch the decoder or its `Surface`: they are views in the same window, over the
  same `SurfaceView`. Opening the Control Center does not restart MediaCodec.
- Recreate the activity: `Game` already handles every configuration change itself
  (`android:configChanges`), and nothing here calls `recreate()`.
- Add work while closed: the closed Control Center is a `GONE` view and a 72×30dp
  handle; the stats snapshot is a few arithmetic operations and one JNI call per
  second (the RTT query the overlay already made).
- Open a network port: host information comes from the paired HTTPS app list.

## Toolchain (unchanged)

JDK 17+ (built with JDK 21), compileSdk 37, targetSdk 36, minSdk 21,
NDK 29.0.14206865, AGP as in `build.gradle`. No new dependencies, no Kotlin.
Java stays the language: the code base is Java and nothing here needed coroutines.
