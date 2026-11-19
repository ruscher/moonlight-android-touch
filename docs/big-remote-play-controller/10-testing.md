# 10 — Testing

## Automated (run on every change)

```sh
export ANDROID_HOME=$HOME/Android/Sdk JAVA_HOME=$HOME/Android/jdk-21
./gradlew lintNonRootDebug assembleNonRootDebug testNonRootDebugUnitTest
```

Unit tests (JUnit 4, no Android runtime):

| Test | Covers |
| --- | --- |
| `ModifierKeysTest` | latch/hold, no duplicate downs, release-all, chords keep latched modifiers |
| `HostEntriesTest` | 3-screen host, one screen, plain Sunshine, Game Window, games named like monitors |
| `QualityAndGestureTest` | presets, preset recognition, lower-quality step, Mbps text, quality in words, compact line, pull gesture (tap, long pull, flick, slow pull, sideways) |
| `TextChunkerTest` | 32-byte packets without split characters, emoji/control characters left out, 2000-char limit |
| `BrpAppKindTest` | entry recognition (from brp/modern-ui) |

Lint: no errors (one real `NewApi` error found and fixed during development:
IME insets on API < 30).

## Device tests (REAL HARDWARE REQUIRED — not yet run)

No device or emulator was available when this branch was written. Nothing below
has been seen on a screen yet.

### Control Center
- [ ] Pull the handle down: opens; tap on the handle: does not; Back opens/closes.
- [ ] Holding an OSC button / a native-touch finger / a key-bar key, open the menu: released on the computer.
- [ ] Stats tiles update every second and match the detailed overlay.
- [ ] Landscape phone, portrait phone, tablet (columns), TV with remote (Back opens, D-pad moves, OK clicks).
- [ ] TalkBack: handle, tabs, toggles read with state; font 130%.

### Keyboard (from the brief)
- [ ] open from the menu; IME types; bar sits above the IME (Android 11+), at the top before.
- [ ] Esc, Tab, Ctrl, Alt, Shift, Win, Delete, arrows (held arrow repeats), F1–F12, F13–F24.
- [ ] Ctrl+C, Ctrl+V, Alt+Tab, Alt+F4, Ctrl+Alt+Del; latched Ctrl + IME "c" = Ctrl+C.
- [ ] modifiers released on: bar close, menu open, rotation, Home, Wi-Fi off, disconnect.
- [ ] Bluetooth and USB keyboards still work; type the phone clipboard (accents, newline).

### Controller
- [ ] show/hide live; edit layout from the menu; opacity live.
- [ ] MT-01…MT-25 (big-remote-play `docs/touch/08-validacao-android.md`) unchanged.
- [ ] physical controller plays while the menu is open; held button released.

### Screens
- [ ] 1 screen: switch hidden; 2 and 3 screens: names, current checked.
- [ ] switch 1→2, 2→3: one reconnect, input correct, nothing stuck.
- [ ] from a game: second tap required; Game Window: no switch.
- [ ] Desktop Low and Steam Big Picture buttons.

### Video
- [ ] presets and slider; apply changes only what changed; H.264/HEVC/AV1 where supported; HDR unchanged.
- [ ] 30/60/90/120 offered per screen; lower quality step; fit/stretch with touch aligned.

### Network
- [ ] LAN/Wi-Fi; Tailscale/ZeroTier/Headscale (SIMULATED acceptable: same code path);
      cut Wi-Fi mid-stream: dialog with last measurements, Reconnect works.

### Leaks and performance
- [ ] connect → menu → reconnect → switch → PiP → background, 10×: `dumpsys meminfo` stable, no leaked activity.
- [ ] `dumpsys gfxinfo` with the menu closed: no extra frames; with it open: no jank in the open animation.
