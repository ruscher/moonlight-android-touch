# 02 — Branding

Icon, launcher, TV banner, theme and the computers/entries screens were done on
`brp/modern-ui`; see [BRP-UI.md](../../BRP-UI.md) for the decisions and the
palette. The source of the icon is the Big Remote Play SVG
(`usr/share/big-remote-play/icons/big-remote-play.svg` in the main repository,
copied to `brp-assets/`), rendered by `brp-assets/make-icons.py` into every
mipmap density, the adaptive foreground/background and the monochrome (themed)
icon.

## Text that said "Moonlight" (this branch)

Every occurrence in `values/strings.xml` and `values-pt-rBR/strings.xml` was
classified:

| Class | Strings | Action |
| --- | --- | --- |
| The app itself | network test texts, decoder crash message, dead zone note, USB driver summary, language summary, local-network permission rationale | Now "Big Remote Play Controller" |
| Upstream project or service | "Moonlight Internet Hosting Tool", Moonlight's connection-test servers, "View Moonlight's privacy policy", the Moonlight guide button | Kept: they name Moonlight's own tools and pages |
| Credit and license | About text ("based on Moonlight for Android 12.2"), GPL notice | Kept |
| Code | package `com.limelight`, `LimeLog`, `moonlight-common-c`, Wi-Fi lock tags | Kept (technical, invisible) |

Other languages still say "Moonlight" in those upstream texts (as noted in
BRP-UI.md); the new Control Center strings exist in English and pt-BR.

## Not changed on purpose

- Application id `io.github.bigremoteplay.controller` (`.debug` for debug).
- `versionName 12.2-brpc3`, `versionCode 315` (an update over brpc2).
- Credits: About still says this is an unofficial build based on Moonlight 12.2,
  under GPL-3.0; Moonlight and Sunshine are independent projects.
