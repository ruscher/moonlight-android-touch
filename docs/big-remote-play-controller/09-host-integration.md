# 09 — Host integration and security

## Principle

The app learns about a Big Remote Play host only through what Sunshine already
serves to a paired client: the app list over HTTPS with the client certificate
and the pinned server certificate. No new port, no shell, no unauthenticated
endpoint. On a normal Sunshine host every Big Remote Play action stays hidden
and the app behaves as Moonlight.

| Capability | Detected from |
| --- | --- |
| Screens to switch | two or more `NN - Monitor` entries |
| Low-bandwidth reconnect | a `Desktop Low` entry |
| Steam Big Picture | a `Steam Big Picture` entry |
| Native touch | upstream: `LI_ERR_UNSUPPORTED` falls back to mouse |
| Game Window | absence of monitor entries |

If a future feature needs state the app list cannot carry (e.g. whether Touch
controls are open on the computer), the preferred path is another entry
property that Sunshine already passes through, not a new service. A new channel
would need authentication bound to the pairing, encryption, session binding and
replay protection.

## Security review (this branch)

| Item | Finding | Action |
| --- | --- | --- |
| `PosterContentProvider` (exported, read-only, for Android TV channels) | The computer UUID segment went into a file path unchecked: `content://poster.<id>/boxart/..%2F..%2Ffiles/1` resolved outside the box-art cache (only files named `<number>.png` could be read). | Fixed: UUID must match `[A-Za-z0-9-]{1,64}` and the app id be numeric. Still exported: the TV launcher reads the posters. |
| Exported activities | `PcView` (launcher) and `ShortcutTrampoline` (launcher shortcuts and TV channel intents) need it. `Game` is not exported. | Kept. |
| New network use | Host app list and quit use `NvHTTP` with pinned certificates. | — |
| Clipboard | Read only when the person taps "Type the text copied on the phone"; never logged; sent only to the paired host over the stream's input channel. | — |
| Logging | No PIN, key or certificate material logged by new code; the host-entries failure logs only the exception class. Existing upstream logging reviewed for PIN/key output: none. | — |
| Backup | Upstream excludes shared preferences from backup and device transfer; the client key pair in the app's files is included, which carries the pairing to a restored phone. | Left as upstream decided; documented. |
| Cleartext | Allowed by upstream's network config for Sunshine's HTTP port (server info, pairing start); everything authenticated goes over HTTPS. | Kept. |
| Release signing | Keystore outside the repository (see BRP-TOUCH.md); nothing secret committed. | — |
