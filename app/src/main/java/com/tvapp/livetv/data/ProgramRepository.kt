package com.tvapp.livetv.data

import android.content.Context
import android.media.tv.TvContract
import com.tvapp.livetv.model.LiveChannel

data class ProgramSummary(
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val description: String = "",
)

data class NowNextPrograms(
    val current: ProgramSummary?,
    val next: ProgramSummary?,
)

data class EpgDiagnosticStep(
    val query: String,
    val success: Boolean,
    val durationMillis: Long,
    val detail: String,
)

internal fun replaceCurrentPrograms(
    existing: Map<String, ProgramSummary>,
    refreshedChannels: Collection<LiveChannel>,
    fresh: Map<String, ProgramSummary>,
): Map<String, ProgramSummary> {
    val refreshedKeys = refreshedChannels.asSequence().map(LiveChannel::sourceKey).toSet()
    return existing.filterKeys { it !in refreshedKeys } + fresh.filterValues { it.title.isNotBlank() }
}

class ProgramRepository(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver
    private val xmlTvRepository = XmlTvRepository(context)

    fun nowAndNext(channel: LiveChannel, now: Long = System.currentTimeMillis()): NowNextPrograms {
        val tif = if (channel.source == LiveChannel.Source.TIF) nowAndNext(channel.id, now)
        else NowNextPrograms(null, null)
        val fallback = xmlTvRepository.nowAndNext(channel, now)
        return NowNextPrograms(
            current = tif.current ?: fallback.current,
            next = tif.next ?: fallback.next,
        )
    }

    fun programsForChannel(channel: LiveChannel, startTimeMillis: Long, endTimeMillis: Long): List<ProgramSummary> {
        val tif = if (channel.source == LiveChannel.Source.TIF) {
            programsForChannel(channel.id, startTimeMillis, endTimeMillis)
        } else emptyList()
        val fallback = xmlTvRepository.programs(channel, startTimeMillis, endTimeMillis)
        return mergeProgramSources(tif, fallback)
    }

    fun nowAndNext(channelId: Long, now: Long = System.currentTimeMillis()): NowNextPrograms {
        val projection = arrayOf(
            TvContract.Programs.COLUMN_TITLE,
            TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
            TvContract.Programs.COLUMN_LONG_DESCRIPTION,
        )
        val programs = contentResolver.query(
            TvContract.buildProgramsUriForChannel(
                channelId,
                now - CURRENT_WINDOW_BEFORE_MS,
                now + CURRENT_WINDOW_AFTER_MS,
            ),
            projection,
            null,
            null,
            "${TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS} ASC",
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(TvContract.Programs.COLUMN_TITLE)
            val startIndex = cursor.getColumnIndexOrThrow(
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
            )
            val endIndex = cursor.getColumnIndexOrThrow(
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
            )
            val shortDescriptionIndex = cursor.getColumnIndex(
                TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
            )
            val longDescriptionIndex = cursor.getColumnIndex(
                TvContract.Programs.COLUMN_LONG_DESCRIPTION,
            )
            buildList {
                while (cursor.moveToNext() && size < MAX_PROGRAMS) {
                    add(
                        ProgramSummary(
                            title = cursor.getString(titleIndex).orEmpty(),
                            startTimeMillis = cursor.getLong(startIndex),
                            endTimeMillis = cursor.getLong(endIndex),
                            description = cursor.programDescription(
                                longDescriptionIndex,
                                shortDescriptionIndex,
                            ),
                        ),
                    )
                }
            }
        }.orEmpty()
        val current = programs.firstOrNull { now in it.startTimeMillis until it.endTimeMillis }
        val next = programs.firstOrNull { it.startTimeMillis >= (current?.endTimeMillis ?: now) }
            ?: programs.firstOrNull { it.startTimeMillis > now && it != current }
        return NowNextPrograms(current, next)
    }

    fun currentPrograms(
        channelIds: Set<Long>,
        now: Long = System.currentTimeMillis(),
    ): Map<Long, ProgramSummary> {
        if (channelIds.isEmpty()) return emptyMap()
        val projection = arrayOf(
            TvContract.Programs.COLUMN_CHANNEL_ID,
            TvContract.Programs.COLUMN_TITLE,
            TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
            TvContract.Programs.COLUMN_LONG_DESCRIPTION,
        )
        return contentResolver.query(
            TvContract.Programs.CONTENT_URI,
            projection,
            "${TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS} <= ? AND " +
                "${TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS} > ?",
            arrayOf(now.toString(), now.toString()),
            null,
        )?.use { cursor ->
            val channelIndex = cursor.getColumnIndexOrThrow(TvContract.Programs.COLUMN_CHANNEL_ID)
            val titleIndex = cursor.getColumnIndexOrThrow(TvContract.Programs.COLUMN_TITLE)
            val startIndex = cursor.getColumnIndexOrThrow(
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
            )
            val endIndex = cursor.getColumnIndexOrThrow(
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
            )
            val shortDescriptionIndex = cursor.getColumnIndex(
                TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
            )
            val longDescriptionIndex = cursor.getColumnIndex(
                TvContract.Programs.COLUMN_LONG_DESCRIPTION,
            )
            buildMap {
                while (cursor.moveToNext()) {
                    val channelId = cursor.getLong(channelIndex)
                    if (channelId !in channelIds) continue
                    put(
                        channelId,
                        ProgramSummary(
                            title = cursor.getString(titleIndex).orEmpty(),
                            startTimeMillis = cursor.getLong(startIndex),
                            endTimeMillis = cursor.getLong(endIndex),
                            description = cursor.programDescription(
                                longDescriptionIndex,
                                shortDescriptionIndex,
                            ),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    fun currentProgramsForChannels(
        channels: List<LiveChannel>,
        now: Long = System.currentTimeMillis(),
    ): Map<String, ProgramSummary> {
        if (channels.isEmpty()) return emptyMap()
        val tifIds = channels.asSequence()
            .filter { it.source == LiveChannel.Source.TIF }
            .map { it.id }
            .toSet()
        val tifPrograms = currentPrograms(tifIds, now)
        val resultMap = channels.asSequence()
            .filter { it.source == LiveChannel.Source.TIF }
            .mapNotNull { channel ->
                tifPrograms[channel.id]?.let { channel.sourceKey to it }
            }
            .toMap()
            .toMutableMap()
        val missingChannels = channels.filterNot { it.sourceKey in resultMap }
        resultMap.putAll(xmlTvRepository.currentPrograms(missingChannels, now))
        return resultMap
    }

    /**
     * Small, focus-driven list windows use the per-channel URI because some vendor TV providers
     * return no rows for the global current-program query unless a large channel set is supplied.
     */
    fun currentProgramsForListWindow(
        channels: List<LiveChannel>,
        now: Long = System.currentTimeMillis(),
    ): Map<String, ProgramSummary> = buildMap {
        channels.forEach { channel ->
            runCatching { nowAndNext(channel, now).current }
                .getOrNull()
                ?.takeIf { it.title.isNotBlank() }
                ?.let { put(channel.sourceKey, it) }
        }
    }

    fun diagnose(channel: LiveChannel, now: Long = System.currentTimeMillis()): List<EpgDiagnosticStep> {
        fun run(query: String, block: () -> String): EpgDiagnosticStep {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            return runCatching { block() }.fold(
                onSuccess = { detail ->
                    EpgDiagnosticStep(
                        query,
                        true,
                        android.os.SystemClock.elapsedRealtime() - startedAt,
                        detail,
                    )
                },
                onFailure = { error ->
                    EpgDiagnosticStep(
                        query,
                        false,
                        android.os.SystemClock.elapsedRealtime() - startedAt,
                        "${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                    )
                },
            )
        }

        fun NowNextPrograms.detail(): String =
            "now=${current?.title ?: "<empty>"}; next=${next?.title ?: "<empty>"}"

        return buildList {
            if (channel.source == LiveChannel.Source.TIF) {
                add(run("TIF_CHANNEL_URI") { nowAndNext(channel.id, now).detail() })
                add(run("TIF_GLOBAL_CURRENT") {
                    val program = currentPrograms(setOf(channel.id), now)[channel.id]
                    "now=${program?.title ?: "<empty>"}"
                })
            } else {
                add(EpgDiagnosticStep("TIF_CHANNEL_URI", true, 0L, "skipped: IPTV"))
                add(EpgDiagnosticStep("TIF_GLOBAL_CURRENT", true, 0L, "skipped: IPTV"))
            }
            add(run("XMLTV_NOW_NEXT") { xmlTvRepository.nowAndNext(channel, now).detail() })
            add(run("MERGED_NOW_NEXT") { nowAndNext(channel, now).detail() })
            add(run("LIST_WINDOW_PATH") {
                val program = currentProgramsForListWindow(listOf(channel), now)[channel.sourceKey]
                "now=${program?.title ?: "<empty>"}"
            })
        }
    }

    fun programsForChannel(
        channelId: Long,
        startTimeMillis: Long,
        endTimeMillis: Long,
    ): List<ProgramSummary> {
        val projection = arrayOf(
            TvContract.Programs.COLUMN_TITLE,
            TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
            TvContract.Programs.COLUMN_LONG_DESCRIPTION,
        )
        return contentResolver.query(
            TvContract.buildProgramsUriForChannel(
                channelId,
                startTimeMillis,
                endTimeMillis,
            ),
            projection,
            null,
            null,
            "${TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS} ASC",
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(TvContract.Programs.COLUMN_TITLE)
            val startIndex = cursor.getColumnIndexOrThrow(
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
            )
            val endIndex = cursor.getColumnIndexOrThrow(
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
            )
            val shortDescriptionIndex = cursor.getColumnIndex(
                TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
            )
            val longDescriptionIndex = cursor.getColumnIndex(
                TvContract.Programs.COLUMN_LONG_DESCRIPTION,
            )
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ProgramSummary(
                            title = cursor.getString(titleIndex).orEmpty(),
                            startTimeMillis = cursor.getLong(startIndex),
                            endTimeMillis = cursor.getLong(endIndex),
                            description = cursor.programDescription(
                                longDescriptionIndex,
                                shortDescriptionIndex,
                            ),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private companion object {
        const val MAX_PROGRAMS = 32
        const val CURRENT_WINDOW_BEFORE_MS = 6 * 60 * 60 * 1_000L
        const val CURRENT_WINDOW_AFTER_MS = 72 * 60 * 60 * 1_000L
    }

    private fun android.database.Cursor.programDescription(
        longDescriptionIndex: Int,
        shortDescriptionIndex: Int,
    ): String = sequenceOf(longDescriptionIndex, shortDescriptionIndex)
        .filter { it >= 0 && !isNull(it) }
        .map { getString(it).orEmpty().trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}
