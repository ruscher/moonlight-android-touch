# 11 — Release

## Version

`12.2-brpc3`, `versionCode 315` (set on `brp/modern-ui`; this branch adds to the
same unreleased version). The system installer accepts it as an update of
`12.2-brpc2` because the application id and the signing key are the same.

## Gate before publishing

Do not publish until these have passed on a real phone (see [10](10-testing.md)):
build, install over brpc2, pair, stream, touch/OSC, keyboard bar, screen switch,
disconnect/reconnect. As of this branch, only the automated part has passed.

## Build and sign

```sh
export ANDROID_HOME=$HOME/Android/Sdk JAVA_HOME=$HOME/Android/jdk-21
git submodule update --init --recursive
./gradlew clean lintNonRootDebug testNonRootDebugUnitTest assembleNonRootRelease
# sign with the Big Remote Play Controller key kept outside the repository (BRP-TOUCH.md)
apksigner sign --ks <external keystore> --out BigRemotePlayController-12.2-brpc3.apk \
  app/build/outputs/apk/nonRoot/release/app-nonRoot-release-unsigned.apk
apksigner verify --print-certs BigRemotePlayController-12.2-brpc3.apk
sha256sum BigRemotePlayController-12.2-brpc3.apk
aapt2 dump badging BigRemotePlayController-12.2-brpc3.apk | head -3   # id, version
```

Check: package `io.github.bigremoteplay.controller`, version `12.2-brpc3`, the same
certificate digest as brpc2, `lib/` contains arm64-v8a, armeabi-v7a, x86, x86_64.

Then update the download link, SHA-256 and certificate digest in the main
repository's README with the real values.

## Release notes (draft — only what exists)

Big Remote Play Controller 12.2-brpc3

- New Big Remote Play look and icon
- Stream Control Center: pull the bar at the top or press Back
- Extra keys bar: Esc, Tab, Ctrl/Alt/Shift/Win, arrows, F1–F24, shortcuts
- Type text copied on the phone on the computer
- Switch between the computer's screens (Big Remote Play hosts)
- Bitrate presets, resolution, frame rate and codec from the stream, applied by reconnecting
- Lower quality and reconnect; connection-lost screen with the last measurements
- Minimal or detailed statistics on screen
- Fit or stretch during the stream
- Security: the TV poster provider only serves box-art files
