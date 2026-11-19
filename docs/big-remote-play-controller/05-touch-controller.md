# 05 — Controllers on the phone and on the computer

There are two different on-screen controllers:

| | Phone controller | Host touch controller |
| --- | --- | --- |
| Drawn by | this app (upstream's on-screen controls) | the computer (Big WM Virtual Keyboard, "Touch controls" in Big Remote Play) |
| Sent as | gamepad state over the protocol | every finger as native touch ([BRP-TOUCH.md](../../BRP-TOUCH.md)) |
| Works in Game Window | yes | no (the phone sees only the game) |

With both visible the game sees two controllers.

## In the Control Center (Controller page)

- **Show controller on this phone** — live. If the stream started without it,
  it is created on the spot (and gamepad 1 is reported as attached, as upstream
  does when it starts with it). Hiding it first releases every button and stick,
  and the choice survives rotation and picture-in-picture
  (`VirtualController.setHiddenByUser`).
- **Edit layout** — closes the menu and enters upstream's edit mode (move;
  tap the gear for resize; tap again to save). Same editor as before.
- **Opacity** — live, saved to the same preference as Settings.
- **Which controller to use** — explains the two controllers in words. With
  native touch on, it says to use one of the two; with native touch off, that the
  host's Touch controls cannot work.

## Why there is no automatic "only one controller" switch

The app cannot see whether Touch controls are open on the computer: that state
lives in Big Remote Play on the host and is not exposed through Sunshine's app
list or the stream. A switch that claimed to disable the host controller would
be a false control, so the page says what to do instead. A future host
capability could be published as an app-list entry property (see
[09](09-host-integration.md)).

## Release guarantees

`VirtualController.releaseAll()` (new) sends `ACTION_CANCEL` to each element
(the elements' own release path), zeroes the input context and sends it with
upstream's retransmissions. It runs when the Control Center opens, when the
controller is hidden from the menu and on every controlled reconnect. Upstream's
own cases (touch cancel, focus loss, stop) are unchanged.

## Not done

Profiles (Default/Compact/Racing/FPS…), per-orientation layouts, rotation,
duplication, per-button haptic strength, trigger/stick curves. These touch
upstream's `VirtualControllerConfigurationLoader` storage format and need
device testing; they are the next controller iteration. Settings that already
exist upstream (buttons shown, vibration, dead zone, flip face buttons) stay in
Settings and apply to the next stream.
