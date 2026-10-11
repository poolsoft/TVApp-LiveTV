# IPTV web yönetimi

## İstek ve kapsam

- TV’den IPTV kaynak yönetiminde sunucuyu başlat/durdur; aynı ağdaki tarayıcıdan ekleme, provider silme, URL kaynağı güncelleme.
- URL, UTF-8 M3U dosyası, Xtream ve Stalker ekleme desteklenir. Kanal seçimi, XMLTV ve internet üzerinden yönetim bu kapsamda değildir.
- Tarayıcı yalnız komut gönderir. TV’de mevcut `IptvRepository` ve Room/staging işlemleri yürür; katalog veya kullanıcı tercihleri için ikinci bir veritabanı yoktur.

## Dosyalar ve davranış

- `web/IptvWebServer.kt`: bağımlılıksız, sınırlı HTTP sunucusu; seçilen LAN IPv4 adresinde geçici port, 8 haneli kod ve bellekte oturum tokenı; Host/origin kontrolü, 5 hatalı kod sonrası 60 saniye bekleme, 10 saniye istemci timeout’u, 16 MiB JSON sınırı ve sınırlı eşzamanlı istemci.
- `web/IptvWebManager.kt`: repository komutları, durum/ilerleme ve 15 dakikalık oturum. Dosya yüklemeleri UUID adlı M3U olarak uygulamanın özel alanında saklanır. URL güncellemesi mevcut seçim eşleştirmesini korur.
- `IptvSourcesActivity.kt` ve iki kaynak layout’u: başlat/durdur, adres/kod, kumandayla kapatma; sunucu açıkken çakışan yerel işlemler kapalıdır. Home/Back ile ekranı terk etme erişimi kapatır. Başlamış işlem mevcut activity scope’u yaşarken tamamlanabilir; activity yok edilirse iptal edilir.
- `res/raw/iptv_web.html`: bağımsız, dış CDN kullanmayan mobil/masaüstü web arayüzü; kaynak adı/tür/adres/sayı/tarih, onaylı silme, URL güncelleme ve gerçek ilerleme. Kullanıcı metinleri TR/EN kaynaklarından gelir. Hassas bilgiler DOM’a `textContent` ile yazılır; şifreler liste yanıtında verilmez.
- Kullanım kılavuzu, README, mağaza metni ve değişiklik günlüğü güncellendi.

## Güvenlik ve sınırlar

- HTTP şifrelenmez. Güvenilir yerel ağda kullanın, port yönlendirmeyin. Eşleştirme bu ağdaki pasif dinlemeye karşı TLS yerine geçmez.
- Kaynak verileri ve komutlar oturum tokenı ister; CORS açılmaz. Eşleştirme kodu ve token her başlatmada yenilenir, dosyaya/loga kaydedilmez.
- Dosya boyutu tarayıcıda 12 MiB ile sınırlıdır; gzip yükleme bu ilk sürümde yoktur. Sunucu otomatik başlamaz, arka plan servisi yoktur.
- Room migration, sürüm kodu, oynatma ve mevcut kaynak güncelleme algoritması değiştirilmedi.

## Doğrulama

- HTTP birim testleri: eşleştirme, yetkisiz erişim, POST zorunluluğu, origin reddi, hatalı JSON, kod deneme sınırı, hata yanıtında veri sızmaması ve yeniden başlatmada token geçersizliği.
- JDK 17 ile `testLocalDebugUnitTest testPaidDebugUnitTest assembleLocalDebug assemblePaidDebug`: son doğrulama `BUILD SUCCESSFUL`; her varyantta 212 test, toplam 424 test, sıfır hata/başarısızlık. Manifest, workflow, sürüm ve bağımlılıklar değişmediğinden release doğrulaması kapsamı açılmadı.
- Playwright/Chrome: masaüstü 1366×900 ve mobil 390×844 ekran görüntüleri incelendi; taşma/script hatası yok. Eşleştirme, alan geçişleri, Xtream ekleme, URL güncelleme, M3U dosya seçimi, parola temizleme ve HTML enjeksiyonunun düz metin kalması kontrollü API test verisiyle doğrulandı; gerçek provider uçtan uca testi olarak sunulmaz.
- Durum sorgusu Room’u periyodik taramaz; yalnız kaynak özetleri başlangıçta ve işlem tamamlandığında yenilenir. TV’den veya web’den silinen uygulamaya ait yükleme dosyası temizlenir; iptal commit sonrasına denk gelirse referanslı dosya korunur.
- ADB’ye bağlı cihaz yoktu. Fiziksel Android 11 Google TV’de kumanda odağı, LAN erişimi ve gerçek provider ekleme/güncelleme ayrıca denenmelidir. Emülatöre/TV’ye uygulama kurma, veri silme veya reset yapılmadı.
