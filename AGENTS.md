# TVApp Development Rules

This file is the persistent working agreement for coding agents and other automated tools in
this repository. Read it before changing code. Product documentation belongs in `README.md`
and `MARKET_LISTING.md`; this file contains implementation and workflow constraints.

## Working Tree And Git

- The user may edit the project concurrently. Inspect `git status` and the relevant diff before
  every group of edits and again before committing.
- Never discard, overwrite, reformat, stage, or commit unrelated changes. Work with concurrent
  edits when they touch the same file.
- Keep changes narrowly scoped. Do not perform opportunistic dependency upgrades or broad
  refactors while fixing a feature.
- After a requested implementation is verified, commit and push only the files belonging to that
  implementation to `main`, unless the user says not to push. If unrelated uncommitted changes
  make selective staging ambiguous, stop before committing and report it.
- Do not amend, force-push, reset, or rewrite published history.
- `.build` is intentionally stable during development. Do not increment it unless the user
  explicitly changes the versioning policy.
- Never commit keystores, signing property files, credentials, playlist credentials, logs, or
  exported user data. Signing material lives in `../.signing` locally and GitHub Secrets in CI.

## Build Variants

- `local` uses package `com.tvapp.livetv` and includes the GitHub self-updater.
- `paid` uses package `com.tvapp.livetv.play`, has no external updater, and is the future Play
  distribution.
- The purchase-state simulator exists only in debug source sets. It must never be packaged in a
  release build.
- Until real Play Billing is complete, do not grant production entitlement from a local `isPaid`
  flag. Production purchases must be verified using purchase tokens and only `PURCHASED` grants
  access; pending purchases do not.
- GitHub development releases publish `TVApp.apk`, `TVApp-Paid-Test.apk`, and `version.json`.
  Do not silently replace one package identity with the other.

## Required Verification

Use JDK 17. On this workstation the known JDK is:

```powershell
$env:JAVA_HOME = 'C:\Users\pools\.jdks\jbr-17.0.9'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

For ordinary changes run:

```powershell
.\gradlew.bat testLocalDebugUnitTest testPaidDebugUnitTest assembleLocalDebug assemblePaidDebug
```

For workflow, signing, manifest, or release-specific changes also run:

```powershell
.\gradlew.bat assembleLocalRelease bundlePaidRelease
```

Run `git diff --check` before committing. A successful compilation is not a substitute for
remote-control focus testing on the Android 11 Google TV device; explicitly mention when a change
still needs device verification.

## Architecture Invariants

- Minimum supported platform is Android 11 / API 30. Compile and target SDK changes require an
  explicit compatibility review.
- Vendor DVB/ATV playback uses Android TIF and `TvView`. IPTV playback uses Media3. Keep source
  routing behind the shared `LiveChannel` model.
- TVApp's own IPTV `TvInputService` must never be ingested again as a vendor tuner source. Main
  TIF channel discovery remains limited to actual hardware tuner inputs; the physical input
  selector may expose other non-TVApp tuner and passthrough inputs.
- Room is authoritative for custom order, number, name, favorite, hidden/skip, grouping, locks,
  IPTV source metadata, and IPTV membership. Never write user ordering back into the vendor TIF
  database.
- Imported IPTV catalog entries do not automatically enter the normal channel list. Only entries
  explicitly selected by the user are merged into it.
- Full IPTV catalogs can contain 15,000 or more entries. Use DAO filtering, indexed queries,
  bounded pages, and incremental adapter updates. Do not load, sort, search, or diff the complete
  catalog in memory on the UI thread.
- Preserve selections and filters across refreshes and process recreation. Opening the app starts
  the normal channel list in `All`, while saved IPTV-library filters remain available for the next
  library visit.
- Network and database work must run off the main thread. Keep focus stable while asynchronous
  pages, EPG data, logos, and playback state arrive.
- Cache remote channel logos through the existing Coil-based loader and bounded disk cache. Do
  not create one unmanaged file per channel or eagerly download every logo.
- Add Room migrations for schema changes. Never use destructive migration for user data.

## Remote And Focus Contract

- The experience is remote-first. Every screen, dialog, list, and bottom action must be fully
  usable without touch or an on-screen keyboard.
- `OK` opens/selects; long `OK` opens contextual actions. Numeric PIN entry uses number keys and
  never invokes the software keyboard.
- `Back` dismisses the topmost overlay, dialog, playback control, grid/fullscreen state, or editor
  state. On the main playback screen it must not terminate TVApp.
- `GUIDE` opens EPG directly. `INFO` opens the infobar; pressing `INFO` while the infobar is visible
  opens EPG.
- `INPUT/SOURCE` owns physical input selection, including DTV/ATV and HDMI/AV when exposed by the
  device. Do not assign physical input selection to a color key.
- On the unobstructed playback screen: Green opens IPTV Grid, Blue opens Settings, and Red/Yellow
  are currently unassigned. The infobar hint must describe this context only.
- In the channel list: Red opens the relevant editor; Yellow short-press cycles list sources and
  Yellow long-press opens direct source selection. IPTV-library Blue opens its filter. Keep the
  list's own color hints separate from the playback-screen hints.
- CH+/CH- and page navigation must move both selection and scroll position. Lists wrap from first
  to last and last to first where that behavior is already established.
- Long-press actions must cancel delayed auto-tune so merely opening a context menu never changes
  the channel.
- Settings and Quick Settings keys (`KEYCODE_SETTINGS`, `KEYCODE_QUICK_SETTINGS`, `KEYCODE_TV_CONTENTS_MENU`):
  Short press opens TVApp display settings; long press opens Google/Android TV system settings.
  `KEYCODE_MENU` toggles the channel panel.
- In EPG: Navigation moves selection smoothly across items with standard scrolling, wrapping from first
  to last. D-Pad Left/Right moves focus between channel list and program list.

## TV UI Rules

- Treat the video as the persistent background. Settings, EPG, channel lists, infobar, playback
  controls, PIN entry, and errors are OSD overlays unless a dedicated management screen is needed.
- Only one primary playback OSD may be active at a time. IPTV seek controls hide the channel list
  and infobar; opening the list or infobar hides seek controls.
- Use responsive screen fractions for primary overlay geometry. Avoid hard-coded pixel assumptions
  tied to one TV resolution, and keep equal safe margins at the top and bottom.
- Keep the compact channel list and infobar top edges aligned with a small intentional gap between
  them. Respect the configured left/right panel side and top/bottom infobar side.
- The infobar technical row order is source, quality, audio, subtitles, TXT, and lock. Do not show
  separators for missing items.
- The infobar color action hints must be placed inside the infobar's technical_row, aligned to the far right
  using a flexible spacer, while technical badges occupy the left slots. Do not place color action hints in an
  external container or extra row below the infobar.
- Multi-View supports both TIF + IPTV and IPTV + IPTV simultaneous playback. Dual TIF (satellite + satellite)
  is restricted on single-tuner hardware to prevent driver conflicts.
- In Multi-View: D-Pad Left/Right switches focus and un-mutes the active side while muting the other. CH+/CH-
  and D-Pad Up/Down zap channels on the currently focused side only. Long OK opens the live IPTV channel picker
  for the active side. Back exits Multi-View and resumes the focused channel in fullscreen.
- IPTV catalog row icon order is membership-in-main-list first, then content type (Live/VOD), then
  encryption/lock state. Use fixed-size icon slots so optional icons do not cause visual jitter.
- Avoid blinking caused by full adapter refreshes. Prefer stable IDs, DiffUtil payloads, and updates
  only for the fields that changed.
- Do not add generic large rectangular buttons when a remote color action, icon, or focused list
  row is the established interaction. Focused state must remain clearly visible on the dark OSD.
- Add every user-visible string to both `values/strings.xml` and `values-en/strings.xml`.

## Diagnostics And Documentation

- Operational failures should be written to the existing debug log facility under `.log`; do not
  leave persistent crash/debug dialogs over playback.
- Never log playlist credentials, access tokens, MAC portal secrets, PIN values, or full private
  URLs containing credentials.
- Keep `README.md`, `CHANGELOG.md`, the in-app User Guide, and `MARKET_LISTING.md` synchronized when remote mappings,
  supported source types, paid/free behavior, or major features change. Record all notable changes in `CHANGELOG.md`.
