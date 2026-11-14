# Big Remote Play Controller

A build of [Moonlight for Android](https://github.com/moonlight-stream/moonlight-android)
12.2 that can send **native multi-touch** to Sunshine, so an on-screen
controller drawn on the game computer (Big WM Virtual Keyboard, through
Big Remote Play) receives every finger as its own touch.

It is **not** the official Moonlight: different application id
(`io.github.bigremoteplay.controller`), name *Big Remote Play Controller*
and version `12.2-brpc1`. It installs beside the official app; its pairing
and settings are separate. It is not published anywhere and not signed with
Moonlight's key.

## What changed (only `brp/native-touch`)

- Setting **Send touch to the computer (multi-touch)** (`checkbox_native_touch`,
  **on** by default in this app). Off: behaviour identical to upstream 12.2.
- On, with a host that supports touch (Sunshine `native_pen_touch`): each
  finger goes to the host as `LiSendTouchEvent` with its pointer id and the
  position normalised to the video area (black bars excluded).
- Why upstream keeps this disabled, and the fix:
  - Android cancels single pointers (`FLAG_CANCELED`, API 33+) or the whole
    gesture (`ACTION_CANCEL`): sent as `CANCEL` / `CANCEL_ALL`, never as a
    release that would count as a tap; only pointers whose DOWN was sent are
    reported.
  - The three-finger keyboard tap cancels the other fingers: it is off while
    the setting is on (the setting says so).
  - Edge swipes (back gesture) stealing fingers: system gesture exclusion on
    both side edges (Android 10+, up to the 200 dp the system allows).
  - Moves are sent only when a pointer moved; focus loss, leaving the stream
    and the start of a new stream send `CANCEL_ALL`, so nothing stays pressed
    on the host (Sunshine 2026.914 keeps touches of a dropped session).
- A host without touch support falls back to the normal mouse emulation.

## Build (debug APK, signed with your own local debug key)

Requirements: JDK 17 or newer, Android SDK (platform 37, build-tools) and
NDK `29.0.14206865`, as upstream's `app/build.gradle` says.

```sh
git submodule update --init --depth 1
export ANDROID_HOME=$HOME/Android/Sdk JAVA_HOME=/path/to/jdk-21
./gradlew assembleNonRootDebug
# app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk
adb install -r app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk
```

The debug key is created by the Android tools on your computer
(`~/.android/debug.keystore`); it is fine for testing on your own phone.

## License

GPL-3.0, like upstream (`LICENSE.txt`). Anyone you give the APK to is
entitled to this source (the branch and this file). This build is
unofficial and not supported by the Moonlight developers.
