# TV OSD Themes

## Scope

- Default preserves the existing channel panel, row selector and info-card backgrounds.
- Glass adds translucent surfaces, light borders and a distinct focused channel row.
- High Contrast uses opaque black panels and a white focused-row outline.
- Settings > Appearance stores one shared choice for the TV channel list and infobar.
- The preference is included in backups; missing or unknown values restore Default.
- No video capture, real-time blur, new dependencies, Room migrations or remote mapping changes.
- Web, mobile and other management screens retain their existing appearance.

## Verification

- Enum default, unknown-value fallback and stable-name restoration have unit coverage.
- Local and paid unit tests: 219 tests per variant, zero failures or errors.
- assembleLocalDebug and assemblePaidDebug passed with JDK 17 (BUILD SUCCESSFUL, 10m 24s).
- Emulator visual verification is blocked by the pending USB debugging permission; existing emulator data was not cleared or replaced.
- Physical Android 11 TV focus, transparency and video-overlay appearance still need user verification.
