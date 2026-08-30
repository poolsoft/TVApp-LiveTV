package com.tvapp.livetv.data

import android.content.Context
import android.media.tv.TvContract
import com.tvapp.livetv.model.LiveChannel

data class ProgramSummary(
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
)

data class NowNextPrograms(
    val current: ProgramSummary?,
    val next: ProgramSummary?,
)

class ProgramRepository(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver
    private val xmlTvRepository = XmlTvRepository(context)

    fun nowAndNext(channel: LiveChannel, now: Long = System.currentTimeMillis()): NowNextPrograms {
        val tif = if (channel.source == LiveChannel.Source.TIF) nowAndNext(channel.id, now)
        else NowNextPrograms(null, null)
        return if (tif.current != null || tif.next != null) tif else xmlTvRepository.nowAndNext(channel, now)
    }

    fun programsForChannel(channel: LiveChannel, startTimeMillis: Long, endTimeMillis: Long): List<ProgramSummary> {
        val tif = if (channel.source == LiveChannel.Source.TIF) {
            programsForChannel(channel.id, startTimeMillis, endTimeMillis)
        } else emptyList()
        return tif.ifEmpty { xmlTvRepository.programs(channel, startTimeMillis, endTimeMillis) }
    }

    fun nowAndNext(channelId: Long, now: Long = System.currentTimeMillis()): NowNextPrograms {
        val projection = arrayOf(
            TvContract.Programs.COLUMN_TITLE,
            TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
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
            buildList {
                while (cursor.moveToNext() && size < MAX_PROGRAMS) {
                    add(
                        ProgramSummary(
                            title = cursor.getString(titleIndex).orEmpty(),
                            startTimeMillis = cursor.getLong(startIndex),
                            endTimeMillis = cursor.getLong(endIndex),
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
                        ),
                    )
                }
            }
        }.orEmpty()
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
        )
        return contentResolver.query(
            TvContract.Programs.CONTENT_URI,
            projection,
            "${TvContract.Programs.COLUMN_CHANNEL_ID} = ? AND " +
                "${TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS} > ? AND " +
                "${TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS} < ?",
            arrayOf(
                channelId.toString(),
                startTimeMillis.toString(),
                endTimeMillis.toString(),
            ),
            "${TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS} ASC",
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(TvContract.Programs.COLUMN_TITLE)
            val startIndex = cursor.getColumnIndexOrThrow(
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
            )
            val endIndex = cursor.getColumnIndexOrThrow(
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
            )
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ProgramSummary(
                            title = cursor.getString(titleIndex).orEmpty(),
                            startTimeMillis = cursor.getLong(startIndex),
                            endTimeMillis = cursor.getLong(endIndex),
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
}
