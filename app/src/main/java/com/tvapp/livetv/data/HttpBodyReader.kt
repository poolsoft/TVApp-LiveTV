package com.tvapp.livetv.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Yanıt gövdesini en fazla [maximumBytes] bayt biriktirerek metne çevirir.
 *
 * Tam gövdeyi `readText()` ile okuyan yollar, bozuk veya beklenenden büyük yanıt veren bir
 * uç nokta bütün gövdeyi String'e çevirirken süreci bellek tükenmesiyle çökertebiliyor
 * (TASKS.md CRASH-003). Burada sınır aşıldığı anda biriktirme bırakılır ve adlandırılmış bir
 * hata fırlatılır; koparılmış gövde sessizce geri döndürülmez.
 *
 * Büyük kataloglar bu yardımcının yanından geçmez: M3U satır satır (`M3uParser.sequence`),
 * XMLTV `XmlPullParser` ile, Xtream ve Stalker liste uçları `JsonReader` ile akışarak
 * ayrıştırılır ve asla kesilmez. Bu sınır yalnızca gövdenin tamamı bellekte gereken
 * kontrol yanıtları içindir.
 */
internal fun InputStream.readLimitedText(maximumBytes: Int, label: String): String {
    val body = ByteArrayOutputStream(DEFAULT_BUFFER_SIZE)
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var received = 0
    while (true) {
        val count = read(chunk)
        if (count < 0) break
        received += count
        check(received <= maximumBytes) {
            "$label yanıtı ${maximumBytes / 1024} KB sınırını aştı (okunan: $received bayt)"
        }
        body.write(chunk, 0, count)
    }
    return body.toString(Charsets.UTF_8.name())
}

/** Oturma (handshake), hesap bilgisi ve portal komut yanıtları tek bir JSON nesnesidir. */
internal const val CONTROL_RESPONSE_BYTES = 4 * 1024 * 1024

/**
 * Ucuncu tarafa ait belge govdesi (kullanici manifestolari, cozumlenen web sayfalari) buyuk
 * olabilir ama sinirsiz olamaz. Sinir `IptvWebServer.MAX_BODY_BYTES` ile aynidir.
 */
internal const val DOCUMENT_RESPONSE_BYTES = 16 * 1024 * 1024
