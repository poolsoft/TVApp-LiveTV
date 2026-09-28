package com.tvapp.livetv.data

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** XMLTV `programme start/stop` çözümlemesi.
 *
 *  Önceki SimpleDateFormat uygulaması her program satırında üç formatter
 *  kurup parse ediyordu; 100k+ programlı kataloglarda bu tüm içe aktarmayı
 *  baskılıyordu. java.time ayrıştırıcıları kalıp başına bir kez derlenir,
 *  thread-safe'tir ve offset eksikse sistem yereline değil UTC'ye düşer —
 *  XMLTV belirtimine uygun davranış. Geçersiz değerler 0 döner (önceki gibi). */
object XmlTvTime {
    private val OFFSET_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
    private val OFFSET_COMPACT_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ")
    private val NAIVE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC)

    fun parse(value: String?): Long {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return 0L
        val parsed = runCatching {
            OffsetDateTime.parse(text, OFFSET_FORMAT).toInstant().toEpochMilli()
        }.getOrElse {
            runCatching {
                OffsetDateTime.parse(text, OFFSET_COMPACT_FORMAT).toInstant().toEpochMilli()
            }.getOrElse {
                runCatching {
                    LocalDateTime.parse(text, NAIVE_FORMAT)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
                }.getOrDefault(0L)
            }
        }
        return parsed
    }
}
