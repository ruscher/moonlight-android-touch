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

## Layouts (`brp/OscLayouts`)

Chosen in **Controller → Layout**, live, with a preview drawn from the same data
that builds the controller (`brp/ui/OscLayoutPreview`). Saved as
`brp_osc_layout`; applies to the next streams too.

| Layout | Idea |
| --- | --- |
| Pad (default for new installs) | A modern controller: left stick top-left, D-pad below, face buttons top-right, right stick below them, LT/RT in the bottom corners, LB/RB/Back/Start/Guide in the centre. |
| Shooter | Big movement stick low on the left, aim (LT) above it; aim stick, fire (RT) and a big jump (A) under the right thumb; L3/R3 as buttons (sprint, melee). |
| Racing | Big steering stick; brake (LT) and a bigger accelerator (RT) as pedals; LB/RB to shift. No right stick. |
| Fighting | Big D-pad; arcade panel X Y RB over A B RT, LB and LT on top. No sticks. |
| Classic | Upstream's code and its saved edits. Selected automatically for someone who had already moved buttons in it. |

- Positions are in upstream's 128×72 grid, anchored left/centre/right, so a 20:9
  phone gets its extra width in the middle. Unit tests check that no two
  elements overlap at 16:9 and 20:9, that everything fits, ids are unique and
  every layout has A/B/X/Y, D-pad, triggers, bumpers, Back/Start/Guide.
- Each layout keeps its own moves and resizes (its own preferences file);
  **Reset this layout** (two taps) forgets them.
- The controller is laid out in the stream's real frame. Upstream used
  `DisplayMetrics`, which excludes the hidden navigation bar: on the test phone
  (2400×1080) the right-hand buttons ended 220 px before the edge. It is now
  rebuilt once the frame's real size is known or changes.

Verified on a Poco X4 Pro (Android 13) streaming from a Big Remote Play host:
element bounds read with `uiautomator` match the specs; switching Pad → Shooter
from the open Control Center rebuilt the controller live without a crash.

## Not done

Per-orientation layouts, rotation and duplication of elements, per-button
haptic strength, trigger/stick curves, restyling the elements (they keep
upstream's drawing). Settings that already exist upstream (buttons shown,
vibration, dead zone, flip face buttons) stay in Settings and apply to the next
stream.
