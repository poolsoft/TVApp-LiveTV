# Performance Query Inventory

This report records the first `DATA-001` baseline. Emulator timings are relative regression
signals, not physical TV targets. Re-run the debug performance analyzer on low-end hardware
before setting absolute budgets.

The full IPTV library and IPTV selection manager now retain one bounded lightweight page and move
with the `(originalIndex, sourceKey)` anchor. Search uses a synchronized Unicode FTS4 index over
display name, `tvg-name`, and category. Vendor TIF channels remain a bounded stable snapshot; list
EPG is fetched by channel URI only for the focused channel and its visible neighbours, with XMLTV
used as the shared fallback.

## Covered paths

| Query | Current plan | 15,000-row benchmark | Finding |
| --- | --- | ---: | --- |
| IPTV first page | `(sourceId, originalIndex)` | 4 ms | Indexed |
| IPTV high offset | `(sourceId, originalIndex)` | 10 ms | Replace `OFFSET` with keyset paging |
| IPTV live category | `(sourceId, contentType, groupTitle, originalIndex)` | <1 ms | Indexed |
| Selected IPTV channels | `(sourceId, originalIndex)` | 12 ms | Add `(sourceId, selected, originalIndex)` with the projection pager |
| IPTV name/category search | Source index plus FTS4 match | 25.2 ms p95 (100 runs) | Indexed; no leading-wildcard scan |
| IPTV categories | Covering source index plus temporary B-trees | 9 ms | Normalize/index category values |
| XMLTV current program | Normalized ID/name time indexes | 1 ms | Program table is indexed; the tiny source table scan is acceptable |

## Next data changes

1. Add `(sourceId, selected, originalIndex)` if selected-row paging becomes measurable.
2. Store a normalized category value if category enumeration remains a measurable cost.
3. Re-run FTS and category measurements on low-end physical TV/stick hardware.

The complete `EXPLAIN QUERY PLAN` output, measured row count, duration, full-scan flag, and
recommendation are written as `QUERY_PLAN` records in the debug log.
