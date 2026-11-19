# 03 — Stream Control Center

During a stream the screen shows only the picture and a small translucent pill
at the top centre. Pulling it down — or pressing Back — opens the Control
Center, a sheet with the session's state and what can be changed in it.

## Opening

| Way | Detail |
| --- | --- |
| Pull the handle down | `PullDownGesture`: 56dp down always opens; a flick of 24dp opens if faster than 0.6dp/ms; a tap or a sideways slide never opens. Haptic tick on open. |
| Back (navigation bar, gesture, TV remote) | `Game.onBackPressed` toggles the Control Center while connected. Back no longer drops the stream: **Disconnect** is in the menu. The manifest keeps `enableOnBackInvokedCallback="false"`, so `onBackPressed` is the path on every Android version. |
| TalkBack | The handle is a labelled button ("Open stream menu"); double-tap opens it. |

The handle is 72×30dp, 4dp below the top edge: the system's own swipe from the
very top (status bar, notification shade) is not blocked — Android does not
allow excluding the top edge from system gestures, and we do not try. Hidden on
devices without a touchscreen (TV) and in picture-in-picture.

## What happens on open

1. `StreamSession.suspendInput()` (`BrpStreamController`):
   - `CANCEL_ALL` for every native touch pointer the host still holds;
   - `cancelTouch()` on the touch contexts (mouse buttons held by touch);
   - every key and latched modifier of the key bar released;
   - the on-screen controller gets `ACTION_CANCEL` on every element, its state is
     zeroed and sent, and it is hidden;
   - mouse capture is released so a physical mouse can use the menu.
2. The scrim (full screen, `clickable`) is brought to the front: from now on no
   touch reaches the stream. Tapping it closes the menu.
3. A physical controller keeps playing (its sticks still reach the game). Its
   button *presses* go to the menu: B/Start/Escape close, A clicks the focused
   item, D-pad keys move focus. Key *releases* always reach the game, so a button
   held when the menu opened is released on the host.

Closing restores the on-screen controller's visibility and mouse capture.

## Pages

| Page | Contents |
| --- | --- |
| Quick | Connection quality in words (Excellent/Good/Unstable/Poor, with a coloured dot that only repeats it), network latency ± variance, resolution, rendered/received FPS, codec, bitrate setting, decode time, network frame loss, host encode time. Actions: keyboard, on-screen controller, statistics mode, switch screen (only when available), stretch (only when the aspect ratios differ), lower quality and reconnect, Disconnect. |
| Keyboard | See [04](04-virtual-keyboard.md). |
| Controller | See [05](05-touch-controller.md). |
| Screen | See [08](08-multi-monitor.md). |
| Video | See [07](07-video-controls.md). |
| Session | Resume, Reconnect, Disconnect, Close on the computer (two-tap confirmation), Ctrl+Alt+Del, picture-in-picture (when supported), statistics overlay mode. |

Destructive actions use a two-tap confirmation in the button itself ("Tap again
to close …", 4 s) instead of a dialog, because a dialog takes window focus from
the stream (which `Game` treats as focus loss).

## Statistics overlay

Off, Minimal (one line: `60 FPS · 12 ms · HEVC · 0.5%`, built from the
`StreamStats` snapshot) or Detailed (upstream's text). Saved in
`brp_perf_overlay_mode`; Detailed also sets upstream's
`checkbox_enable_perf_overlay`. Changing it is live: the decoder reads
`prefConfig.enablePerfOverlay` every second.

## Layout and accessibility

- Top sheet at most 760dp wide, at most 94% of the height, inside the display
  cutout's safe insets; scrolls. Pages use a wrapping `FlowLayout`, so tiles and
  buttons form columns on tablets and landscape and a list on narrow phones.
- Every control is at least 48dp high; text in sp follows the font size.
- Always dark (it sits on a video). Text contrast on the card colour is ≥ 7:1.
- Selected state is never only colour: a check mark, bold text, and TalkBack
  reads toggles as checkable with their state. Section titles are accessibility
  headings. Focused items get a 3dp cyan ring for D-pad and keyboard.

## Connection lost

Upstream showed a dialog with the error and closed the stream. Now the same
message is followed by what was last measured (latency, % frames lost, FPS, if
measured in the last 15 s) and three buttons: **Reconnect**, **Lower quality and
reconnect** (when the bitrate is above 1.5 Mbps) and **Close**.

Not done: automatic reconnect with backoff (1/2/4/8 s). The building blocks are
there (`relaunch`), but deciding which termination codes are safe to retry
without a device to test on was left for the next iteration.
