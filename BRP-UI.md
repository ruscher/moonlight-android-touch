# Big Remote Play Controller: look and features

Branch `brp/modern-ui` (from `brp/native-touch`). It changes how the app looks
and how a Big Remote Play user is guided; **streaming, decoding, input and
touch code are not touched** (`Game.java`, `binding/`, `nvstream/`, `ui/StreamView`
and the stream theme are as in `brp/native-touch`). See [BRP-TOUCH.md](BRP-TOUCH.md)
for the fork itself and its touch work.

## Design decisions

The app follows the desktop app's **AshyWall** language
(`big-remote-play/docs/visual-design.md`): system font, one accent colour, cards
only for what you act on, state in words, a visible keyboard/D-pad focus ring,
no gradients or animation of its own.

- **Follows the system.** Light by default, dark when the system is dark
  (`values-night`), always dark on Android TV (`values-television`). The dark
  palette is the desktop **Gamer** palette, so phone and computer match.
- **One surface.** Every container the app draws is a card: surface colour,
  hairline outline, 16dp corners, a ripple on press (`drawable/brp_card.xml`).
  Buttons 12dp, dialogs 22dp corners.
- **State in words.** A computer says *Online · Paired*, *Online · Not paired*,
  *Online · Playing*, *Offline* or *Checking…*; colour (a dot and the text
  colour) only repeats it. Entries say *Playing*, *Hidden* or *Low bandwidth*.
- **Touch and remote.** Header buttons and actions are at least 48dp. Grids
  draw a 3dp focus ring (cyan in dark, violet in light) on the selected card
  (`brp_grid_selector.xml`); buttons draw the same ring when focused.
- **The stream stays upstream's.** `StreamTheme` now derives from
  `LegacyBaseTheme`, a verbatim copy of the old base theme, so the stream
  activity, its overlays and its dialogs look and behave exactly as before.

### Palette (`res/values*/brp_colors.xml`)

| Role | Light | Dark (Gamer) |
| --- | --- | --- |
| Window | `#F4F3F8` | `#151A35` |
| Card | `#FFFFFF` | `#232846` |
| Dialog | `#FFFFFF` | `#2A2F50` |
| Text | `#1E1B2E` (16.8:1 on card) | `#ECEEFF` (12.5:1) |
| Secondary text | `#5D5A6B` (6.7:1) | `#B8BDDA` (7.7:1) |
| Accent text, icons | `#5B3AE6` | `#A48BFF` (5.3:1) |
| Controls (switches) | `#6F4AFF` | `#835DFF` |
| Primary button | `#6F4AFF`, white text (5.1:1) | same |
| Focus ring | `#5B3AE6` | `#22D3EE` |
| Online / Paired | `#1A7F4B` (5.0:1) | `#8FF0A4` (10.4:1) |
| Not paired | `#8A5A00` (5.9:1) | `#F8E45C` (11.1:1) |
| Playing | `#0E7490` (5.4:1) | `#22D3EE` (7.9:1) |
| Offline / Checking | secondary text | secondary text |

Ratios are WCAG sRGB contrast against the card colour; all meet AA for text.

### Theme structure (`res/values*/styles.xml`)

`AppTheme` → `AppBaseTheme` → `Brp.Base` (chained per API level:
`Brp.Base.V21` colours and dialog theme, `V23` light status bar, `V27` light
navigation bar, `V28` dialog corner radius; `values-v29` keeps the transparent
bars upstream draws under and `forceDarkAllowed=false`). `BrpThemeParent` is
`Theme.Material.Light` or, in `values-night-v21`/`values-television-v21`,
`Theme.Material`. Before API 23 the status bar stays dark navy because its
icons are always white there.

## What changed, per screen

- **Icon.** Launcher, themed (monochrome, Android 13+) icon, computer
  shortcut icon, Android TV banner and channel logo, OUYA art: all from the Big
  Remote Play logo (`brp-assets/big-remote-play.svg`, copied from
  `big-remote-play/usr/share/big-remote-play/icons/`). The adaptive foreground
  keeps the glyph inside the 66dp safe zone (measured, not guessed). Moonlight's
  logo (`ic_lime_layer`, `app_icon.png`, grey `#565C64` background) is gone.
  Regenerate with `python3 brp-assets/make-icons.py` (needs `rsvg-convert`,
  ImageMagick for the safe-zone measurement, Noto Sans for the banner).
- **Computers (PcView).** A header with the logo, *Big Remote Play /
  Controller* and three 48dp actions (Add computer, How to connect, Settings,
  each with a spoken name and tooltip). Computers are horizontal cards in
  one column on phones in portrait and two or more on wider screens:
  icon chip, name, state in words. Offline computers get a neutral chip and a
  warning badge; unpaired ones a lock badge; *Checking…* a ring around the icon.
  One layout for portrait and landscape (`layout-land` removed).
- **Empty state.** Instead of a bare spinner: *Looking for computers…*, what to
  do on the game computer, a hint for Tailscale/Headscale/ZeroTier, and two
  buttons (*How to connect*, *Add computer*).
- **Entries (AppView).** Header with the computer's name and *Choose what to
  play*. Rounded poster cards; box art is clipped to the card. Without box art
  the card shows a recognisable icon and the name (below).
- **Add computer.** Title, an explanation of what to type (LAN or private-network
  address), a full-width field and one primary button.
- **Settings.** The same preference screen in the new theme, plus an
  **About** section. Window titles are translated (*Settings*, *Add computer*).
- **Dialogs** (pairing PIN, context menus, confirmations, progress): the dialog
  theme gives them the elevated surface, 22dp corners and accent buttons.

## New features (and why)

1. **How to connect** (header button and empty state; offline dialog). A Big
   Remote Play user needs to know that the game computer must *Share*, that
   both sides need the same network or private network, and that the PIN is
   typed in Big Remote Play on the computer. The steps use the desktop app's
   own words (*Share*, *Start sharing*). The upstream web guide stays one button
   away (hidden on Fire TV, as upstream did).
2. **Pairing hint for Big Remote Play.** The PIN dialog now says that Big
   Remote Play on the computer asks for the PIN (Share page), and that
   Sunshine's web page also accepts it. Upstream only mentioned Sunshine's web UI.
3. **Recognisable entries.** The host offers *Desktop*, *Desktop Low*,
   *Steam Big Picture*, *01 - Monitor*, *02 - Monitor*… without box art, so
   Sunshine sends its 130x180 placeholder, which upstream showed as a grey box.
   `grid/BrpAppKind` recognises the name and the card shows a desktop icon, a
   monitor with its number, a TV with a gamepad (Steam) or a gamepad (any
   other game), and a *Low bandwidth* label for the *Low* variants. Real box art
   still covers the card. Unit-tested (`BrpAppKindTest`).
4. **About Big Remote Play Controller** (Settings → About): version, application
   id, that it is an unofficial build based on Moonlight 12.2, and the license.
   Required context for a GPL fork distributed as an APK.

No new permissions, no network calls (all new screens are local text), no
analytics.

## Build

As in [BRP-TOUCH.md](BRP-TOUCH.md):

```sh
export ANDROID_HOME=$HOME/Android/Sdk JAVA_HOME=$HOME/Android/jdk-21
./gradlew assembleNonRootDebug lintNonRootDebug testNonRootDebugUnitTest
./gradlew assembleNonRootRelease   # then apksigner, see BRP-TOUCH.md
```

`testNonRootDebugUnitTest` needs `junit:junit:4.13.2` (test-only dependency,
not in the APK).

## Manual test checklist (phone, tablet, TV)

Automated checks cover the build, lint, the entry-name rules and the APK's
icon resources. Nothing here was seen on a device or emulator yet.

- [ ] Launcher: new icon on round, squircle and square masks; Android 13+
      themed icons show the monochrome logo; the app name is unchanged.
- [ ] First run with no computer: empty state, both buttons work, text is
      readable in light and dark, scrolls in landscape on a small phone.
- [ ] Computer appears by itself (same Wi-Fi) and by address (Tailscale).
      Card states: *Checking…* → *Online · Not paired* (lock) → pair →
      *Online · Paired*; computer off → *Offline* (warning badge).
- [ ] Pairing dialog shows the PIN and the Big Remote Play hint; typing it in
      Big Remote Play on the computer pairs.
- [ ] Entries: *Desktop* (desktop icon), *Desktop Low* (*Low bandwidth*),
      *Steam Big Picture* (TV + gamepad), *01 - Monitor* (monitor with 1), a game
      with box art (art fills the rounded card). Long-press menu works; hidden
      entries say *Hidden* in the *View all apps* list.
- [ ] Start *Desktop*, stream, multi-touch on-screen controller, leave: the
      card says *Playing*; the computer card *Online · Playing*; resume/quit work.
- [ ] Stream itself unchanged: black background, overlays, decoder fit message,
      PiP, keyboard, gamepad.
- [ ] Settings: switches and categories in the accent colour; About shows
      version `12.2-brpc3` and the license text.
- [ ] Light ↔ dark switch while the app is open; system font size 130%+;
      RTL language (e.g. Hebrew) mirrors the header and cards.
- [ ] Portrait and landscape phone; tablet (2-4 columns); Android TV: always
      dark, D-pad moves header ↔ grid with a visible cyan ring, banner shown.
- [ ] pt-BR, pt, es, de, fr, it show the new texts translated; other languages
      fall back to English for the new texts.

## Known limits

- Some upstream texts still say "Moonlight" (network test, decoder crash,
  language setting summary, ...) in all languages; they were left as upstream
  wrote them.
- `versionCode` stays 315 (only the `-brpcN` suffix changes, as before); the
  system installer accepts this as an update of `12.2-brpc2`.
- New strings are translated into pt-BR, pt, es, de, fr and it only.
