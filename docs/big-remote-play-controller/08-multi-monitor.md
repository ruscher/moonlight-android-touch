# 08 — Switching screens

Big Remote Play creates one Sunshine entry per connected screen —
`01 - Monitor`, `02 - Monitor`, … — and, with its Sunshine build, each entry
captures its own screen (`output-name` per entry; see `docs/moonlight-entries.md`
in the main repository).

## Discovery

When the stream starts, `BrpStreamController.loadHostEntries()` asks the host for
its app list with `NvHTTP.getAppList()` — the same paired HTTPS request the
entries screen makes (client certificate, pinned server certificate), on a
background thread. `brp/HostEntries` (unit-tested) classifies the names:

- numbered entries (`NN - …`) are screens, sorted by number; a game called
  "Monitor Tycoon" is not;
- `Desktop`, `Desktop Low` (the low-bandwidth variant), `Steam Big Picture`.

No shell, no new port, no Big Remote Play-specific protocol. A plain Sunshine
host simply has none of these entries.

## What the Screen page shows

- **Streaming now**: the current entry's name.
- **Screens of the computer**: one button per screen, the current one checked;
  only when the host offers **two or more** screens. With one, it explains how
  Big Remote Play adds per-screen entries. While reading: "Reading the computer's
  entries…"; on failure: says so (the stream is unaffected).
- **Other entries**: Desktop, Desktop Low (with a note that it limits bitrate on
  the computer), Steam Big Picture — those the host has and that are not playing.
- **Game Window**: the host offers no monitor entries there, so the switch never
  appears.

## Controlled reconnect

Sunshine streams one entry per session and cannot change the captured screen
inside it, so switching is a reconnect, done in one step:

1. `suspendInput()` releases every finger, button, key and modifier on the host.
2. The current stream is stopped and the next one starts only after the stop has
   finished on a background thread (`Game.stopConnectionThen`): `NvConnection`
   launches the entry over HTTP *before* its start/stop semaphore, so starting
   at once could reach Sunshine while the old session still counted. Failure
   dialogs of the stream being left are suppressed; if the person leaves the app
   during the wait, nothing is relaunched.
3. `Game` is started again with the same intent but the new entry's id, name and
   HDR flag, plus a spinner text ("Switching to 02 - Monitor…").
4. In the new stream, `NvConnection` sees a different entry running on the host and quits it before
   launching the new one (upstream's `quitAndLaunch`).

Settings are kept because they are the saved settings. Native touch starts with a
`CANCEL_ALL`, as on every connection.

`Game` is `singleTask`: `finish()` then `startActivity()` normally creates a new
instance. If Android instead delivers the intent to the finishing instance
(`onNewIntent`), it is kept and started from `onDestroy` with
`FLAG_ACTIVITY_NEW_TASK`, so the switch happens either way.

Switching away from a **game** closes it on the computer, so that case asks for
a second tap ("Tap again: this closes … on the computer"). Switching between
screen/desktop entries does not ask: nothing is lost.

## Not done

Remembering zoom per screen (no zoom yet), and measuring the switch time on
hardware (expected: the stop plus a normal connection, a few seconds).
