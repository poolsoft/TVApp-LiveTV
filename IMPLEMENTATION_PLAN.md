# TVApp Implementation Plan - Performance, HTTP, Media3 & UI Optimizations

> **Created**: 2026-09-20  
> **Status**: Planning Phase - Awaiting Approval  
> **Branch**: `main` (10 commits behind origin/main)

---

## 📋 Executive Summary

Based on codebase analysis (commit `b5e5402`), the project has **already implemented** many optimizations I initially recommended:

| Area | Status | Notes |
|------|--------|-------|
| **IJK Fallback** | ❌ **REMOVED** | ijkplayer/gsyijkjava dependencies removed. Media3 internal decoder fallback used instead |
| **Media3 Decoder Fallback** | ✅ **DONE** | `DefaultRenderersFactory` with `enableDecoderFallback=true` + `EXTENSION_RENDERER_MODE_ON` |
| **Dynamic Buffer (LIVE/VOD)** | ✅ **DONE** | `targetBufferSeconds` preference → `liveTargetOffsetMillis()` + `LoadControl` |
| **Adaptive Track Selection** | ✅ **DONE** | `AdaptiveTrackSelection.Factory` with custom thresholds |
| **Health Monitoring/Watchdog** | ✅ **DONE** | `IptvPlaybackHealthSnapshot`, watchdog with `FIRST_FRAME_TIMEOUT`, `BUFFERING_TIMEOUT`, `PLAYBACK_STALLED` |
| **Alternative Stream Fallback** | ✅ **DONE** | `MainActivity.handleIptvPlaybackError` → `iptvRepository.alternativeStreams()` |
| **Room WAL Mode** | ✅ **DONE** | Migration v22+ enables WAL, non-blocking channel load |
| **Google TV Watch Next** | ✅ **DONE** | VOD resume sync, PreviewChannel/Program auto-publish |
| **Multi-View** | ✅ **DONE** | 2/3/4 cell grids, focus/audio management, TIF+IPTV mix |
| **Incremental EPG/Channel Sync** | ✅ **DONE** | Cursor-based pagination, FTS triggers |

---

## 🎯 Actual Gaps & Improvement Opportunities

### 1. Media3 / ExoPlayer Optimizations (High Impact)

| # | Task | Current | Target | Effort |
|---|------|---------|--------|--------|
| 1.1 | **Dynamic LoadControl per content type** | Single `LoadControl` for all profiles | LIVE: 6-10s buffer, VOD: 30-60s buffer, Grid: 3-5s | ~2h |
| 1.2 | **Bandwidth fraction tuning** | `ADAPTIVE_BANDWIDTH_FRACTION = 0.75f` | 0.80-0.85 for more stable quality | ~30m |
| 1.3 | **Renderer extension mode** | `EXTENSION_RENDERER_MODE_ON` | `EXTENSION_RENDERER_MODE_PREFER` (Media3 1.6+) | ~15m |
| 1.4 | **Custom AnalyticsListener** | Manual `VideoFrameMetadataListener` (250ms) | `AnalyticsListener` for frame drops, bitrate changes | ~2h |
| 1.5 | **HLS/DASH segment prefetch** | None | `PreloadMediaSource` for next segment | ~3h |

### 2. HTTP / Network Stack (Medium Impact)

| # | Task | Current | Target | Effort |
|---|------|---------|--------|--------|
| 2.1 | **HTTP timeout tuning** | Connect: 15s, Read: 30s (in repository) | Connect: 8-10s, Read: 15-20s | ~1h |
| 2.2 | **OkHttp connection pooling** | Default `DefaultDataSource` | Custom `OkHttpClient` with pool (5 conns, 5 min) | ~2h |
| 2.3 | **Redirect handling** | `MAX_REDIRECTS = 5` in repo | Separate counter for HLS master playlist redirects | ~1h |
| 2.4 | **Proactive token refresh** | Reactive on 401 | Stalker/Xtream: refresh at T-5min | ~3h |
| 2.5 | **Request headers standardization** | Ad-hoc User-Agent/Referer | Centralized `DefaultHttpDataSource.Factory` | ~1h |

### 3. Database / Repository (Medium Impact)

| # | Task | Current | Target | Effort |
|---|------|---------|--------|--------|
| 3.1 | **FTS trigger → batched updates** | SQLite triggers on every write | Room `@Transaction` + coroutine batch (100ms window) | ~3h |
| 3.2 | **Selective EPG invalidation** | `EpgSnapshotCache.invalidate(sourceKey)` on any override | Invalidate only changed channel's cache | ~1h |
| 3.3 | **Incremental channel sync** | Full TIF+IPTV merge on every `channels()` call | Cache + `modifiedSince` timestamp per source | ~4h |
| 3.4 | **Library page query optimization** | Multiple DAO calls per page | Single query with `UNION ALL` for category+contentType | ~2h |

### 4. Image / Logo Loading (Low-Medium Impact)

| # | Task | Current | Target | Effort |
|---|------|---------|--------|--------|
| 4.1 | **Failed request TTL** | Permanent `failedRequests` Set | TTL 5 min + max 100 entries | ~1h |
| 4.2 | **Viewport-based prefetch** | 1 item ahead | 3-5 items ahead/behind based on scroll direction | ~2h |
| 4.3 | **Disk cache LRU stats** | Manual `cacheSizeBytes()` | Coil `DiskCache` stats + auto-eviction tuning | ~1h |

### 5. UI / UX Improvements (High Visibility)

| # | Task | Current | Target | Effort |
|---|------|---------|--------|--------|
| 5.1 | **OsdCoordinator State Machine** | Single OSD rule | Explicit states: `HIDDEN \| INFOBAR \| CHANNEL_LIST \| PLAYBACK_CONTROLS \| EPG \| SETTINGS \| MULTI_VIEW_PICKER` with transitions | ~4h |
| 5.2 | **EPG Virtualized Layout** | `GuideScheduleAdapter` full bind | Only visible time slots + sticky time header | ~6h |
| 5.3 | **Multi-View Focus/Audio Swap** | D-Pad L/R switches focus | Instant focus request + mute swap (100ms) | ~2h |
| 5.4 | **Settings Search** | Leanback PreferenceFragment | `SearchFragment` + keyword index | ~3h |
| 5.5 | **Material3 Theme + High Contrast** | Custom colors | `Material3 ColorScheme` + `highContrast` variant | ~4h |
| 5.6 | **Channel List Program Progress** | Recalc on every bind | Coroutine `flow` with 1s tick | ~1h |

### 6. Testing & Observability (Foundation)

| # | Task | Current | Target | Effort |
|---|------|---------|--------|--------|
| 6.1 | **Unit test coverage** | ~40% (Repository, Health, Parser) | **80%+** (DAO, Merger, Navigator, Preferences) | ~8h |
| 6.2 | **Integration tests** | None | Room DAO (in-memory), Repository flows | ~6h |
| 6.3 | **UI Automator tests** | 1 smoke test | Remote navigation: channel list, EPG, settings, PiP | ~8h |
| 6.4 | **Benchmark module** | Exists | Channel switch < 800ms, Cold start < 2s, EPG scroll 60fps | ~4h |
| 6.5 | **Local metrics** | Debug log only | `Room` table: `playback_events(channel, engine, startup_ms, error?)` | ~3h |

### 7. Code Health / Technical Debt

| # | Task | Description | Effort |
|---|------|-------------|--------|
| 7.1 | **Remove dead IJK code** | `enableIjkFallback` param, `IptvPlaybackEngine.IJK`, `onExternalFallbackRecommended` callback, `shouldUseIjkFallback` | ~2h |
| 7.2 | **Split `IptvPlaybackController`** | 6200 lines → `IptvMedia3Controller`, `IptvHealthMonitor`, `IptvTrackManager` | ~8h |
| 7.3 | **Split `MainActivity`** | 6200 lines → `PlaybackController`, `OsdManager`, `ChannelNavigator`, `MultiViewManager` | ~12h |
| 7.4 | **Consolidate `IptvPlaybackHealth`** | Duplicate in `.freebuff/` worktree | ~1h |

---

## 🚀 Recommended Priority Order

### Phase 1: Quick Wins (Week 1-2) - **~12h total**
1. **HTTP timeout tuning** (2.1, 2.5) - 2h
2. **Failed logo request TTL** (4.1) - 1h  
3. **Bandwidth fraction + renderer mode** (1.2, 1.3) - 1h
4. **Remove dead IJK code** (7.1) - 2h
5. **Selective EPG invalidation** (3.2) - 1h
6. **Program progress flow** (5.6) - 1h
7. **Channel list prefetch viewport** (4.2) - 2h
8. **Unit test coverage push** (6.1) - 2h

### Phase 2: Core Performance (Week 3-4) - **~16h total**
9. **Dynamic LoadControl per profile** (1.1) - 2h
10. **OkHttp connection pooling** (2.2) - 2h
11. **FTS batched updates** (3.1) - 3h
12. **AnalyticsListener integration** (1.4) - 2h
13. **OsdCoordinator state machine** (5.1) - 4h
14. **Multi-View focus/audio swap** (5.3) - 2h
15. **Benchmark metrics** (6.4) - 1h

### Phase 3: Major Features (Week 5-8) - **~30h total**
16. **EPG virtualized layout** (5.2) - 6h
17. **Incremental channel sync** (3.3) - 4h
18. **Proactive token refresh** (2.4) - 3h
19. **HLS/DASH segment prefetch** (1.5) - 3h
20. **Material3 theme + High Contrast** (5.5) - 4h
21. **Settings search** (5.4) - 3h
22. **Local playback metrics table** (6.5) - 3h
23. **Integration tests** (6.2) - 4h

### Phase 4: Architecture (Ongoing) - **~22h total**
24. **Split IptvPlaybackController** (7.2) - 8h
25. **Split MainActivity** (7.3) - 12h
26. **UI Automator test suite** (6.3) - 8h (can parallelize)

---

## ✅ Acceptance Criteria (Definition of Done)

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| **Channel switch (IPTV)** | ~1.2-2s | **< 800ms** | Benchmark: `ChannelSwitchBenchmark` |
| **Cold start (MainActivity)** | ~3-4s | **< 2s** | Benchmark: `StartupBenchmark` |
| **EPG scroll FPS** | ~45fps | **60fps** | `FrameTimingMetrics` |
| **Crash-free sessions** | ~99% | **> 99.5%** | Play Console / local metrics |
| **Unit test coverage** | ~40% | **> 80%** | `jacocoTestReport` |
| **APK size (local)** | ~45MB | **< 40MB** | `bundletool` / `aapt` |
| **Memory (IPTV playback)** | ~180MB | **< 150MB** | `procstats` / `dumpsys meminfo` |

---

## 🔍 Validation Checklist Before Implementation

Before starting any task, verify:

- [ ] **Git status clean** (no uncommitted changes except `.freebuff/`)
- [ ] **Pull latest origin/main** (10 commits behind)
- [ ] **Run full test suite**: `.\gradlew.bat testLocalDebugUnitTest testPaidDebugUnitTest`
- [ ] **Run build**: `.\gradlew.bat assembleLocalDebug assemblePaidDebug`
- [ ] **Device test** on Google TV (Android 11) for playback changes
- [ ] **Update CHANGELOG.md** with each completed task
- [ ] **Update README.md** if user-facing behavior changes

---

## 📝 Notes for Reviewer

> **Important**: The original analysis assumed IJK fallback was active. It has been **removed** in favor of Media3's built-in decoder fallback. Tasks related to IJK (fallback logic, ping-pong protection, IJK options) are **obsolete** and replaced with Media3 optimization tasks.

> **Dead Code to Clean**: `enableIjkFallback` constructor param, `IptvPlaybackEngine.IJK`, `onExternalFallbackRecommended` callback, `shouldUseIjkFallback`, `classifyIjkPlaybackFailure`, `IJK_SOFTWARE_VIDEO_REASON`, `DIRECT_IJK_REASON`.

> **Branch Strategy**: Each Phase 1-2 task → separate feature branch → PR → merge to main. Phase 3+ tasks can be larger epics.

---

## 📎 Related Files

| Area | Key Files |
|------|-----------|
| Playback Core | `IptvPlaybackController.kt`, `IptvPlaybackHealth.kt`, `IptvDataSourceFactory.kt` |
| Repository | `IptvRepository.kt`, `ChannelRepository.kt`, `ChannelMerger.kt`, `M3uParser.kt` |
| Database | `TVAppDatabase.kt` (migrations), `*Dao.kt`, `*Entity.kt` |
| UI | `ChannelAdapter.kt`, `GuideScheduleAdapter.kt`, `OsdCoordinator.kt`, `MultiView*.kt` |
| Settings | `IptvPlaybackPreferencesStore.kt`, `DisplayPreferencesStore.kt`, `DisplaySettingsActivity.kt` |
| Main | `MainActivity.kt` (6200 lines - needs split) |
| Image | `ChannelLogoLoader.kt` |
| Build | `app/build.gradle.kts`, `gradle.properties` |