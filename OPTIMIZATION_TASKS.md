# TVApp - Optimization Task Tracker (New)

> **Source**: [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)  
> **Created**: 2026-09-20  
> **Status**: 🟡 Planning - Awaiting Approval  
> **Note**: Separate from existing TASKS.md (which tracks product features)

---

## 📋 Phase 1: Quick Wins (Week 1-2) - ~12h

| ID | Task | Area | Effort | Status | Branch | PR | Notes |
|----|------|------|--------|--------|--------|-----|-------|
| OPT-1.1 | HTTP timeout tuning (connect 10s, read 20s) | Network | 1h | ⬜ Todo | | | IptvRepository.kt connect/read timeouts |
| OPT-1.2 | Standardize HTTP headers (User-Agent, Accept) | Network | 1h | ⬜ Todo | | | Centralized DefaultHttpDataSource.Factory |
| OPT-1.3 | Failed logo request TTL (5 min, max 100 entries) | Image | 1h | ⬜ Todo | | | ChannelLogoLoader.kt failedRequests Set |
| OPT-1.4 | Bandwidth fraction 0.75 → 0.82 | Playback | 30m | ⬜ Todo | | | IptvPlaybackController.kt ADAPTIVE_BANDWIDTH_FRACTION |
| OPT-1.5 | Renderer mode ON → PREFER (Media3 1.6+) | Playback | 15m | ⬜ Todo | | | DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER |
| OPT-1.6 | Remove dead IJK code | Cleanup | 2h | ⬜ Todo | | | enableIjkFallback, IJK enum, callbacks, classifyIjkPlaybackFailure |
| OPT-1.7 | Selective EPG cache invalidation | Database | 1h | ⬜ Todo | | | EpgSnapshotCache.invalidate only changed channel |
| OPT-1.8 | Channel list program progress via Flow (1s tick) | UI | 1h | ⬜ Todo | | | ChannelAdapter.kt bindProgram → coroutine flow |
| OPT-1.9 | Channel list prefetch 3-5 items viewport-based | Image | 2h | ⬜ Todo | | | ChannelAdapter.kt prefetchAround → scroll direction aware |
| OPT-1.10 | Unit test coverage push to 60% (DAO, Merger, Navigator) | Test | 2h | ⬜ Todo | | | ChannelRepository, ChannelMerger, ChannelNavigator tests |

---

## 📋 Phase 3: Major Features (Week 5-8) - ~30h

| ID | Task | Area | Effort | Status | Branch | PR | Notes |
|----|------|------|--------|--------|--------|-----|-------|
| OPT-3.1 | EPG virtualized layout + sticky time header | UI | 6h | ⬜ Todo | | | GuideScheduleAdapter → only visible slots, StickyHeaderDecoration |
| OPT-3.2 | Incremental channel sync (modifiedSince per source) | Database | 4h | ⬜ Todo | | | ChannelRepository.channels() → cache + timestamp per source |
| OPT-3.3 | Proactive Stalker/Xtream token refresh (T-5min) | Network | 3h | ⬜ Todo | | | StalkerClient.kt, XtreamClient.kt token expiry tracking |
| OPT-3.4 | HLS/DASH segment prefetch (PreloadMediaSource) | Playback | 3h | ⬜ Todo | | | Media3 PreloadMediaSource for next segment |
| OPT-3.5 | Material3 ColorScheme + High Contrast variant | UI | 4h | ⬜ Todo | | | Material3 theming, highContrast color scheme |
| OPT-3.6 | Settings SearchFragment + keyword index | UI | 3h | ⬜ Todo | | | Leanback SearchFragment, preference keyword mapping |
| OPT-3.7 | Local playback metrics Room table | Observability | 3h | ⬜ Todo | | | playback_events(channel, engine, startup_ms, error, fallback?) |
| OPT-3.8 | Integration tests (Room DAO, Repository flows) | Test | 4h | ⬜ Todo | | | In-memory Room, Repository integration tests |

---

## 📋 Phase 4: Architecture (Ongoing) - ~22h

| ID | Task | Area | Effort | Status | Branch | PR | Notes |
|----|------|------|--------|--------|--------|-----|-------|
| OPT-4.1 | Split IptvPlaybackController (3 classes) | Architecture | 8h | ⬜ Todo | | | IptvMedia3Controller, IptvHealthMonitor, IptvTrackManager |
| OPT-4.2 | Split MainActivity (4+ classes) | Architecture | 12h | ⬜ Todo | | | PlaybackController, OsdManager, ChannelNavigator, MultiViewManager |
| OPT-4.3 | UI Automator test suite (remote nav, PiP, EPG) | Test | 8h | ⬜ Todo | | | Channel list, EPG, Settings, PiP/Multi-View flows |
| OPT-4.4 | Consolidate IptvPlaybackHealth duplicate | Cleanup | 1h | ⬜ Todo | | | Remove .freebuff/ duplicate |

---

## 🎯 Task Detail Template

```markdown
## Task: OPT-{phase}.{number} - {title}

**Area**: {Playback|Network|Database|UI|Test|Architecture|Cleanup}
**Effort**: {Xh}
**Dependencies**: {OPT-{x.y} or none}
**Files to Modify**: 
- `path/to/file.kt`
- `path/to/other.kt`

### Acceptance Criteria
- [ ] Criterion 1 (measurable)
- [ ] Criterion 2 (measurable)

### Implementation Plan
1. Step 1
2. Step 2
3. Step 3

### Verification
- [ ] Unit tests pass (`./gradlew.bat testLocalDebugUnitTest testPaidDebugUnitTest`)
- [ ] Build succeeds (`./gradlew.bat assembleLocalDebug assemblePaidDebug`)
- [ ] Device tested on Google TV API 30+
- [ ] CHANGELOG.md updated
- [ ] No regression in existing functionality
```

---

## 📊 Progress Tracking

| Phase | Total Tasks | Completed | In Progress | Blocked | % Done |
|-------|-------------|-----------|-------------|---------|--------|
| Phase 1 | 10 | 0 | 0 | 0 | 0% |
| Phase 2 | 7 | 0 | 0 | 0 | 0% |
| Phase 3 | 8 | 0 | 0 | 0 | 0% |
| Phase 4 | 4 | 0 | 0 | 0 | 0% |
| **Total** | **29** | **0** | **0** | **0** | **0%** |

---

## 🔄 Workflow Rules

1. **One task per branch**: `opt/OPT-{x.y}-short-description`
2. **PR required**: No direct commits to main
3. **Tests mandatory**: Unit + build verification before PR
4. **Device test**: Required for playback/UI changes
5. **CHANGELOG entry**: Required in PR description
6. **No drive-by refactors**: Stay scoped to task

---

## 🚫 Obsolete / Superseded (from initial analysis)

| Initial Idea | Reality | Replacement |
|--------------|---------|-------------|
| IJK fallback improvements | ijkplayer removed | Media3 decoder fallback optimization |
| IJK ping-pong protection | No external fallback engine | N/A |
| IJK options tuning | gsyvideoplayer removed | N/A |
| IJK software decoder fallback | Media3 EXTENSION_RENDERER_MODE_PREFER | Renderer mode tuning |

---

## 📝 Quick Reference - Key Files

| Task Area | Primary Files |
|-----------|---------------|
| **Playback Core** | `IptvPlaybackController.kt`, `IptvPlaybackHealth.kt`, `IptvDataSourceFactory.kt` |
| **Network** | `IptvRepository.kt` (timeouts), `StalkerClient.kt`, `XtreamClient.kt`, `M3uParser.kt` |
| **Database** | `TVAppDatabase.kt`, `ChannelRepository.kt`, `IptvRepository.kt`, `*Dao.kt` |
| **Image** | `ChannelLogoLoader.kt`, `ChannelAdapter.kt` (prefetch) |
| **UI - Channel List** | `ChannelAdapter.kt`, `ChannelEditorAdapter.kt` |
| **UI - EPG** | `GuideScheduleAdapter.kt`, `ProgramGuideActivity.kt` |
| **UI - OSD** | `OsdCoordinator.kt`, `MainActivity.kt` (OSD setup) |
| **UI - Multi-View** | `MultiViewChannelAdapter.kt`, `MultiViewFocusResolver.kt` |
| **Settings** | `DisplaySettingsActivity.kt`, `IptvPlaybackPreferencesStore.kt` |
| **Main** | `MainActivity.kt` (6200 lines) |
| **Build** | `app/build.gradle.kts` |