# Channel Editor IPTV Improvements

## Behavior

- IPTV channel rows show their source playlist name below the channel title. Tuner rows remain single-line; missing source names are not fabricated.
- Names are loaded once per editor reload from source metadata, off the UI thread. No catalog scan or per-row database access.
- Yellow enters multi-select; OK selects channels. The sidebar remove icon is available for selected IPTV entries only.
- Removal requires confirmation, clears main-list membership in one transaction, and preserves source catalog entries and user preferences. Mixed selections never remove tuner entries.
- Sidebar focus updates a full-width bottom hint. Existing color hints and the total footer height are preserved.
- Empty-list removal clears the previous preview and selected playback target, returning focus to the sidebar.

## Verification

- Unit tests cover mixed selections, stale keys, unselected entries and tuner-only/empty selections.
- JDK 17: testLocalDebugUnitTest, testPaidDebugUnitTest, assembleLocalDebug and assemblePaidDebug passed (BUILD SUCCESSFUL, 8m 15s). Each variant ran 226 tests with zero failures or errors, 452 total.
- Earlier attempts hit the workstation commit-memory limit; the successful retry used a single worker and 1536 MiB heap. No project JVM/build configuration was changed.
- Android 11 TV remote focus and long-source-name rendering require physical device verification; no device data has been cleared.
