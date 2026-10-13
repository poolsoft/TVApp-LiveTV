# IPTV web yönetimi

## 2026-10-12: Oturum süresi düzeltmesi

- Sabit süre yerine 15 dakika boşta kalma: TV/web veri işlemi boyunca sunucu açık kalır. Başarılı eşleştirme, kabul edilen web komutu ve web işlemi sonucu süreyi yeniler. Durum sorguları süreyi uzatmaz; elle durdurma ve yönetim ekranından ayrılma kapanışı korunur.
- Monotonik saatle sınır, uzun işlem, süre yenileme ve pasif sorgu testleri eklendi. Gerçek TV/LAN doğrulaması gerekir.
- 2026-10-14 doğrulaması: JDK 17 ile iki varyantın testleri ve debug APK derlemeleri başarılı (8m 15s). Her varyantta 226 test, toplam 452 test; sıfır hata/başarısızlık. Gerçek TV/LAN süre testi bekliyor.

### Sonraki liste iyileştirmeleri (öneri, uygulanmadı)

- Web kaynak listesinde ad/adres araması ve URL/Dosya/Xtream/Stalker tür filtresi.
- İşlem gören kaynağın kendi satırında aşama, gerçek okunan kayıt sayısı ve geçen süre; bilinmeyen toplam için sahte yüzde yok.
- Güncelleme sonucunda yeni/kaldırılan/korunan seçili kayıt özeti, gerçek staging eşleşmelerinden hesaplanmalı.
- Çoklu kaynak güncellemesi için sırayla çalışan kuyruk; aynı anda bir veri işlemi kuralı korunmalı.
- Webden kaynak içeriğine sayfalı erişim ve ana kanal listesine ekleme/çıkarma; büyük katalog tarayıcıya tek seferde taşınmamalı.

## İstek ve kapsam

- Web kaynaklarında yeniden adlandırma: mevcut adla açılan koyu düzenleme penceresi, Kaydet/İptal, boş ve 200 karakterden uzun ad kontrolü, ortak işlem kilidi ve mevcut repository güncellemesi. Eşleştirme artık 4 hane; token/rate-limit/oturum süresi değişmedi. Tema sorusu web için değil TV kanal listesi ve infobar içindir: mevcut/Glass/yüksek kontrast seçenekleri yalnız değerlendirme aşamasında; tema veya oynatma kodu değiştirilmedi.
- TV’den IPTV kaynak yönetiminde sunucuyu başlat/durdur; aynı ağdaki tarayıcıdan ekleme, provider silme, URL kaynağı güncelleme.
- URL, UTF-8 M3U dosyası, Xtream ve Stalker ekleme desteklenir. Kanal seçimi, XMLTV ve internet üzerinden yönetim bu kapsamda değildir.
- Tarayıcı yalnız komut gönderir. TV’de mevcut `IptvRepository` ve Room/staging işlemleri yürür; katalog veya kullanıcı tercihleri için ikinci bir veritabanı yoktur.

## Dosyalar ve davranış

- `web/IptvWebServer.kt`: bağımlılıksız, sınırlı HTTP sunucusu; seçilen LAN IPv4 adresinde geçici port, 4 haneli kod ve bellekte oturum tokenı; Host/origin kontrolü, 5 hatalı kod sonrası 60 saniye bekleme, 10 saniye istemci timeout’u, 16 MiB JSON sınırı ve sınırlı eşzamanlı istemci.
- `web/IptvWebManager.kt`: repository komutları, durum/ilerleme ve 15 dakikalık oturum. Dosya yüklemeleri UUID adlı M3U olarak uygulamanın özel alanında saklanır. URL güncellemesi mevcut seçim eşleştirmesini korur.
- `IptvSourcesActivity.kt` ve iki kaynak layout’u: başlat/durdur, adres/kod, kumandayla kapatma. Adres/kod penceresinin Kapat/Back ile kapanması sunucuyu durdurmaz; Web’den yönet tekrar açar. Sunucu boşta iken TV sayfası kullanılabilir. Ortak atomik işlem kilidi yalnız veri işlemi sırasında TV/web komutlarını karşılıklı engeller; TV değişiklikleri ve ilerleme tarayıcıya iletilir. IPTV yönetiminden ayrılmak (kanal seçimi veya dosya seçiciyi açmak dahil), Home veya 15 dakika boşta kalma erişimi kapatır; veri işlemi sürerken süre dolmaz. Başlamış işlem mevcut activity scope’u yaşarken tamamlanabilir; activity yok edilirse iptal edilir.
- `res/raw/iptv_web.html`: bağımsız, dış CDN kullanmayan mobil/masaüstü web arayüzü; kaynak adı/tür/adres/sayı/tarih, onaylı silme, URL güncelleme ve gerçek ilerleme. Kullanıcı metinleri TR/EN kaynaklarından gelir. Hassas bilgiler DOM’a `textContent` ile yazılır; şifreler liste yanıtında verilmez.
- Kullanım kılavuzu, README, mağaza metni ve değişiklik günlüğü güncellendi.

## Güvenlik ve sınırlar

- HTTP şifrelenmez. Güvenilir yerel ağda kullanın, port yönlendirmeyin. Eşleştirme bu ağdaki pasif dinlemeye karşı TLS yerine geçmez.
- Kaynak verileri ve komutlar oturum tokenı ister; CORS açılmaz. Eşleştirme kodu ve token her başlatmada yenilenir, dosyaya/loga kaydedilmez.
- Dosya boyutu tarayıcıda 12 MiB ile sınırlıdır; gzip yükleme bu ilk sürümde yoktur. Sunucu otomatik başlamaz, arka plan servisi yoktur.
- Room migration, sürüm kodu, oynatma ve mevcut kaynak güncelleme algoritması değiştirilmedi.

## Doğrulama

- Web adı/kısa kod değişikliği: JDK 17 ile iki varyantın testleri ve debug derlemeleri başarılı (6 dakika 39 saniye); her varyant 216 test, toplam 432, sıfır hata. Playwright/Chrome masaüstü 1366x900 ve mobil 390x844 fixture testleri: 4 hane/baştaki sıfır, adın hazır gelmesi, boş ad kontrolü, işlem kilidi, hata sonrası yeniden deneme, kırpılmış adla komut, sayılar ve taşma kontrolü geçti; ekran görüntüleri incelendi. Gerçek TV/LAN doğrulaması bekliyor.
- Kullanılabilirlik düzeltmesi sonrası JDK 17 ile `testLocalDebugUnitTest testPaidDebugUnitTest assembleLocalDebug assemblePaidDebug`: `BUILD SUCCESSFUL` (9 dakika 6 saniye); her varyantta 214 test, toplam 428 test, sıfır hata/başarısızlık. TV/emülatör kurulumu veya veri silme yapılmadı; gerçek TV kumanda/LAN doğrulaması bekliyor.
- Kullanılabilirlik düzeltmesi: ortak kilidin boşta yeniden kullanılabilmesi ve eşzamanlı TV/web komutlarından yalnız birinin alınması için birim testleri eklendi. Playwright/Chrome API fixture testi: boşta form kullanılabilir, TV işlemi sırasında alanlar kilitlenir, bitince açılır ve gönderilmemiş kaynak adı korunur. Bu test fiziksel TV uçtan uca doğrulaması değildir.
- HTTP birim testleri: eşleştirme, yetkisiz erişim, POST zorunluluğu, origin reddi, hatalı JSON, kod deneme sınırı, hata yanıtında veri sızmaması ve yeniden başlatmada token geçersizliği.
- JDK 17 ile `testLocalDebugUnitTest testPaidDebugUnitTest assembleLocalDebug assemblePaidDebug`: son doğrulama `BUILD SUCCESSFUL`; her varyantta 212 test, toplam 424 test, sıfır hata/başarısızlık. Manifest, workflow, sürüm ve bağımlılıklar değişmediğinden release doğrulaması kapsamı açılmadı.
- Playwright/Chrome: masaüstü 1366×900 ve mobil 390×844 ekran görüntüleri incelendi; taşma/script hatası yok. Eşleştirme, alan geçişleri, Xtream ekleme, URL güncelleme, M3U dosya seçimi, parola temizleme ve HTML enjeksiyonunun düz metin kalması kontrollü API test verisiyle doğrulandı; gerçek provider uçtan uca testi olarak sunulmaz.
- Durum sorgusu Room’u periyodik taramaz; yalnız kaynak özetleri başlangıçta ve işlem tamamlandığında yenilenir. TV’den veya web’den silinen uygulamaya ait yükleme dosyası temizlenir; iptal commit sonrasına denk gelirse referanslı dosya korunur.
- ADB’ye bağlı cihaz yoktu. Fiziksel Android 11 Google TV’de kumanda odağı, LAN erişimi ve gerçek provider ekleme/güncelleme ayrıca denenmelidir. Emülatöre/TV’ye uygulama kurma, veri silme veya reset yapılmadı.
