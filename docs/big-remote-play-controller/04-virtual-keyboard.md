# 04 — Keyboard

Android keyboards have no Esc, Ctrl, Alt, Win, function keys or arrows that a
computer can use. Upstream offered only the three-finger tap (which still works).

## Phone keyboard

**Keyboard → Open the phone keyboard** (or **Keyboard** in Quick) shows the extra
keys bar and asks for the IME with `InputMethodManager.showSoftInput` on the
stream view, falling back to upstream's `toggleSoftInput` when the keyboard does
not become active. Typed text takes upstream's path (`handleKeyDown`: key events
with a mapping, UTF-8 text otherwise).

## Extra keys bar (`brp/ui/RemoteKeyboardBar`)

Row: ⌨ (phone keyboard), Esc, Tab, Ctrl, Alt, Shift, Win, ← ↑ ↓ →, Home, End,
PgUp, PgDn, Ins, Del, ⌫, ⏎, Space, PrtSc, Fn, ✕. **Fn** shows a second row with
F1–F24 (the protocol carries F13–F24, VK 0x7C–0x87). Both rows scroll sideways.

- Keys go down when the finger lands and up when it lifts, so a held arrow or
  Backspace repeats on the computer like a real key.
- Codes are Windows virtual keys with `0x80` in the high byte, exactly what
  upstream's `KeyboardTranslator` produces. PrtSc sends `VK_SNAPSHOT` (0x2C).
- Position: bottom, raised above the IME by its window insets (Android 11+).
  Before Android 11 a full-screen window gets no IME insets, so the bar sits at
  the top there.
- Touches on the bar never reach the game (the bar is clickable and on top).

## Modifiers (`brp/ModifierKeys`, unit-tested)

Two behaviours, chosen in **Keyboard → Ctrl, Alt, Shift and Win** (saved as
`brp_sticky_modifiers`):

- **Tap to lock** (default): tap presses the modifier on the computer, tap again
  releases it. Shown filled, in capitals (CTRL), and read as "checked".
- **Hold while touching**: down while the finger is on the key.

A latched modifier is a real key-down on the host, and its flag is also added
to every key the stream sends (`Game.getModifierState`), so Ctrl latched on the
bar plus "c" from the phone keyboard is Ctrl+C.

Released (key up sent) when: the bar closes, the Control Center opens, the
window loses focus, the configuration changes (rotation, PiP), `onStop`, the
connection ends, a controlled reconnect starts. `releaseAll()` only sends key-ups
for what is actually down.

## Shortcuts

Ctrl+C, Ctrl+V, Ctrl+X, Ctrl+Z, Ctrl+A, Alt+Tab, Alt+F4, Win, Win+D, Win+E,
Ctrl+Alt+Del — sent with `ModifierKeys.chord`: modifiers not already down are
pressed, the key tapped, and only those modifiers released (latched ones stay).
Ctrl+V pastes the *computer's* clipboard.

## Phone clipboard

**Type the text copied on the phone** reads the phone's clipboard (only on that
tap; Android allows it because the app has focus) and types it on the computer
through the protocol's UTF-8 text channel (`LiSendUtf8TextEvent`), in packets of
at most 32 bytes, never splitting a character, paced 5 ms apart from a
background thread (the input queue holds 150 packets). Limits, enforced by
`brp/TextChunker` (unit-tested):

- at most 2000 characters;
- characters outside the Basic Multilingual Plane (most emoji) are left out: the
  JNI bridge uses `GetStringUTFChars` (modified UTF-8), which would send them as
  invalid surrogate bytes;
- control characters other than tab and newline are left out.

How the computer types Unicode depends on Sunshine's platform code (on Linux it
relies on the desktop accepting Unicode input); this is the protocol's own
mechanism, not a new channel.

## Not done

Keyboard opacity, position and per-host keyboard profiles; a dedicated
"capture physical keyboard" switch (upstream already captures it while the
stream has focus).
