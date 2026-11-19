# 06 — Gestures

## Implemented

- **Pull down from the top handle** opens the Control Center
  (`brp/PullDownGesture`, unit-tested: tap never opens, long pull opens once,
  quick flick opens, slow short pull and sideways slides do not).
- Upstream gestures are unchanged: three-finger tap toggles the keyboard; in
  trackpad mode, two-finger scroll and taps; native touch sends every finger.

## Fit and stretch

**Screen → Picture** switches between fit (black bars) and stretch, live. Only the
`SurfaceView`'s measured size changes (`StreamView.setDesiredAspectRatio`); the
surface is resized, not destroyed, so the decoder keeps running. Touch
coordinates are computed from the view's size, so native touch stays aligned
with the picture in both modes. Shown only when the stream's and the screen's
aspect ratios differ. Session-only (the Settings switch still decides the start).

## Not implemented: pinch zoom and pan

Zoom was designed but not built in this iteration, because doing it right needs
real-device iteration:

- **Input ownership.** In native touch every finger belongs to the game. A pinch
  must not steal fingers from a host-drawn controller, so zoom needs an explicit
  *View mode* (fingers move the picture) versus *Game mode* (fingers go to the
  computer), switched from the Control Center or a dedicated button.
- **Coordinates.** Upstream maps touches with the view's position and size;
  zoom would scale the view (or a `TextureView` matrix). Native touch, absolute
  mouse and the OSC must then map through the same transform; the black-bar fix in
  `getStreamViewRelativeNormalizedXY` must use the transformed viewport.
- **Renderer.** Scaling a `SurfaceView` with `setScaleX/Y` is cheap on API 24+
  but its behaviour with the decoder's direct surface output differs between
  devices; this must be measured (frame time, latency) on hardware.

Planned order: View/Game mode switch → pinch/pan with transform-aware touch
mapping → double-tap fit/100% → presets (100/125/150/200%).
