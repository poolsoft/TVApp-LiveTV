# TVApp Geliştirme Planı

Bu plan TVApp'i yalnız TIF destekli Google TV cihazlarına değil; Android TV televizyonlara,
Google TV cihazlarına ve donanım tuner sunmayan TV stick/box cihazlarına uygun hale getirmek
için hazırlanmıştır. İlk öncelik performans ve kararlılıktır. Yeni özellikler, ölçülebilir
performans tabanı ve güvenilir kumanda akışı kurulduktan sonra eklenir.

Ürün IPTV-only modda da öncelikle bir **televizyon uygulamasıdır**. Hedef Netflix/Prime benzeri
ayrı ve ayrıntılı bir medya merkezi kurmak değil; canlı IPTV, VOD ve temel dizi akışlarının hızlı,
kumandayla kolay ve hata karşısında dayanıklı çalışmasıdır. Zengin metadata, trend/keşif ekranları,
çoklu profil, indirme ve kapsamlı tema seçenekleri ilk kararlı IPTV kapsamına dahil değildir.

## 1. Ürün çalışma modları

Uygulama cihaz adından veya yalnız `FEATURE_LIVE_TV` değerinden karar vermemelidir. Açılışta
gerçek yetenekler ölçülerek aşağıdaki deneyimlerden biri seçilmelidir:

| Mod | Koşul | Varsayılan deneyim |
| --- | --- | --- |
| Hibrit TV | Leanback var, kullanılabilir harici/vendor tuner input'u ve okunabilir TIF kanalı var | Tümü/Uydu/Radio/IPTV kaynakları, TIF + IPTV oynatma |
| IPTV-only TV | Leanback var, fakat kullanılabilir vendor tuner veya TIF kanal erişimi yok | Tam IPTV kütüphanesi, VOD ve IPTV ayarları; TIF seçenekleri gizli |
| Mobil test | `mobile` varyantı veya dokunmatik telefon/tablet | IPTV/VOD test deneyimi ve dokunmatik kontroller |

TIF'in bulunmaması hata sayılmamalıdır. IPTV-only TV modunda izin veya tuner bulunamadı
dialogu gösterilmeden son IPTV kanalı açılmalı; hiç kaynak yoksa doğrudan IPTV kaynak ekleme
akışına gidilmelidir. Kullanıcı Ayarlar'dan otomatik seçimi geçersiz kılabilmelidir.

## 2. Performans hedefleri

Hedefler düşük donanımlı, 2 GB RAM'li TV stick sınıfı cihazlar esas alınarak ölçülmelidir:

| Ölçüm | Hedef |
| --- | --- |
| Soğuk açılıştan kullanılabilir ilk ekrana | en fazla 2 saniye |
| Sıcak açılıştan kullanılabilir ilk ekrana | en fazla 1 saniye |
| Kanal listesini açma, cache hazırken | p95 en fazla 150 ms |
| Kumanda tuşundan görünür odak hareketine | p95 en fazla 50 ms |
| IPTV sayfasının ilk 100-200 satırını getirme | p95 en fazla 250 ms |
| 15.000 kayıtta arama sonucu | p95 en fazla 300 ms |
| Cache içindeki EPG'nin odak satırına gelmesi | p95 en fazla 200 ms |
| Kanal değişiminde UI thread bloklama | 16 ms üzeri veritabanı/ağ işi yok |
| Tek IPTV oynatımında uygulama belleği | cihaz üzerinde ölçülmüş, sürekli yükselmeyen kararlı kullanım |

Bu değerler tahmin olarak bırakılmayacak; başlangıç, liste açma, sorgu, adapter güncelleme,
EPG ve tune süreleri debug ölçüm kaydına yazılacaktır.

Emülatör sonuçları yalnız aynı AVD üzerinde değişiklik öncesi/sonrası regresyon karşılaştırması
için kullanılır. Emülatör süreleri fiziksel TV veya TV stick performansı olarak kabul edilmez.
Gerçek düşük donanımlı cihaz edinildiğinde hedefler aynı test verileriyle yeniden kalibre edilir;
o zamana kadar sorgu sayısı, UI thread bloklaması, frame gecikmesi ve göreli süre değişimi esas
alınır.

Yerel macrobenchmark komutu:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest
```

İlk API 31 Google TV emülatör tabanı 9 Eylül 2026 tarihinde kaydedildi. Soğuk başlangıç medyanı
3.890 ms; sıcak başlangıç + kanal paneli hızlı gezinme medyanı 1.839 ms oldu. İlk sıcak örnekteki
emülatör/kurulum sapması dahil aralık 1.728-9.295 ms'dir. Bu değerler cihaz hedefi değil, sonraki
değişikliklerin aynı AVD üzerindeki regresyon tabanıdır. Ayrıntılı kare izleri benchmark çıktısında
Perfetto dosyaları olarak üretilir.

## 3. Mimari yön

### Yetenek tabanlı çalışma

`DeviceCapabilities` tek kaynaktan şu bilgileri üretmelidir:

- Leanback/TV cihazı olup olmadığı
- `FEATURE_LIVE_TV` durumu
- `TvInputManager` erişimi
- TVApp'in kendi input'u dışındaki kullanılabilir tuner input'ları
- `TvContract` kanal sorgusunun başarı durumu
- PiP, decoder ve düşük RAM cihaz özellikleri

`ExperienceModeResolver`, bu bilgileri `HYBRID_TV`, `IPTV_ONLY_TV` veya `MOBILE_TEST` moduna
dönüştürmelidir. Repository, menüler ve kaynak filtreleri aynı kararı kullanmalıdır.

### OSD ve kumanda durumu

`MainActivity` içindeki kanal listesi, infobar, IPTV kontrolü, son kanallar, PIN, grid ve
Multi View görünürlükleri tek bir `PlaybackUiState`/`OsdCoordinator` tarafından yönetilmelidir.
Her anda yalnız birincil bir OSD aktif olmalıdır. `RemoteActionRouter`, ham key kodunu mevcut
duruma göre eyleme çevirmelidir. Bu yapı Back, uzun OK, renk tuşları ve seek sırasında kanal
değişmesi gibi çakışmaları azaltacaktır.

### Büyük IPTV katalogları

- UI hiçbir zaman tüm kataloğu belleğe almamalıdır.
- `OFFSET` büyüdükçe yavaşladığı için liste gezinimi mümkün olduğunda son görülen
  `originalIndex/sourceKey` üzerinden keyset pagination kullanmalıdır.
- Satırlar için `SELECT *` yerine yalnız görünür alanları taşıyan projection kullanılmalıdır.
- Arama Room FTS tablosundan yapılmalıdır; `%LIKE%` yalnız küçük listelerde fallback olmalıdır.
- Adapter sabit kimlik, payload ve küçük artımlı güncelleme kullanmalıdır.
- Kaynak yenileme mevcut tabloyu önce silmemeli; staging/diff yaklaşımıyla seçimleri korumalıdır.
- Logo indirme görünür pencere ve küçük prefetch alanıyla sınırlandırılmalıdır.

### EPG

Kanal listesi, infobar ve program rehberi ayrı ayrı sorgulamak yerine ortak bir EPG snapshot
cache kullanmalıdır. Odaktaki kanal öncelikli, komşu kanallar gecikmeli prefetch edilmelidir.
Eski async sonuçlar yeni odağı ezmemeli; boş sonuçlar kısa, geçerli programlar bitiş zamanına
kadar cache'lenmelidir. XMLTV eşleştirme sonucu ve kaynağı teşhis ekranından görülebilmelidir.

### IPTV oynatma sağlamlığı

Media3 için ilk kare, son kare, buffer, bitrate, codec, decoder ve hata sınıfını taşıyan ortak bir
sağlık modeli kurulmalıdır. Görüntü hiç gelmemesi, oynatımın donması, ağ hatası ve decoder hatası
aynı "yeniden bağlanıyor" durumu olarak ele alınmamalıdır. Watchdog ve retry işleri kanal değişimi,
Back veya Activity kapanışında kesin olarak iptal edilmelidir.

Kurtarma sırası tek bir durum makinesinde sınırlı ve gözlemlenebilir olmalıdır: kısa geri çekilme,
aynı akışı yeniden hazırlama, varsa alternatif akışa geçme ve son olarak kullanıcıya müdahale
seçeneği. TIF oynatma aynı teşhis sözleşmesine desteklediği verileri sağlayabilir, ancak IPTV
kurtarma kararları vendor tuner oturumuna uygulanmamalıdır.

Buffer, canlı gecikme, ABR/kalite, otomatik kurtarma ve ilerideki oynatıcı tercihi kaynak bazında
saklanmalıdır. HLS, DASH ve MPEG-TS yanında M3U header/catch-up/DRM öznitelikleri kaybolmadan
modellenmelidir. İkinci bir oynatıcı motoru ancak sorunlu akış uyumluluğu, APK/ABI boyutu, bellek,
açılış ve lisans etkisi ölçüldikten sonra değerlendirilir.

### IPTV içerik kapsamı

Canlı yayın mevcut kanal listesi ve OSD davranışını korur. VOD için oynat/duraklat, seek, kaldığın
yerden devam, ses/altyazı/kalite ve harici oynatıcı yeterli çekirdektir. Dizi desteği; sezon, bölüm,
devam et ve sonraki bölümden oluşan sade, sayfalı bir akış olarak eklenir. TMDB veya ayrıntılı keşif
ekranı dizi desteğinin ön koşulu değildir.

## 4. Uygulama aşamaları

### Aşama 0 - Ölçüm ve regresyon tabanı

Önce mevcut davranış ölçülür. 0, 500, 15.000 ve 50.000 IPTV kaydı; küçük ve büyük XMLTV
dosyaları için tekrar kullanılabilir test verisi hazırlanır. Startup, liste, arama, EPG ve
kanal geçişi süreleri kaydedilir. Kritik kumanda akışları UiAutomator testine alınır.

### Aşama 1 - TV stick ve IPTV-only çalışma

Yetenek algılama eklenir. TIF olmayan Leanback cihazlarda uygulama hatasız IPTV-only açılır,
TIF menüleri gizlenir ve kaynak döngüsü yalnız geçerli kaynakları içerir. Hibrit cihazların
mevcut DVB davranışı korunur.

### Aşama 2 - Veri ve liste performansı

IPTV sorguları projection, keyset sayfalama ve FTS ile düzenlenir. Kaynak içe aktarma ve
yenileme staging/diff modeline geçirilir. Ana kanal listesi yalnız kullanıcı tarafından seçilen
IPTV kanallarını yüklemeye devam eder. EPG snapshot cache ortaklaştırılır.

### Aşama 3 - OSD ve kumanda kararlılığı

OSD koordinatörü ve kumanda yönlendiricisi çıkarılır. Mevcut görünüm değiştirilmeden davranış
testleri geçirilir. Sonrasında ortak odak, ikon, renk eylemi ve dialog bileşenleri kurulur.

### Aşama 4 - Kullanıcı deneyimi

Ana kanal listesine hızlı arama, gelişmiş EPG zaman çizelgesi, program açıklaması ve eşleştirme
durumu eklenir. Ayarlar yayın kapanmadan OSD olarak çalışır; ayrı örnek önizleme kullanılmaz.
720p/1080p/4K ve ekran overscan alanları screenshot testleriyle doğrulanır.

### Aşama 5 - IPTV oynatma sağlamlığı

Önce yayın sağlık modeli ve isteğe bağlı teşhis arayüzü kurulur. Ardından görüntü gelmeme/donma
watchdog'u, sınırlı kurtarma durum makinesi ve alternatif akış önceliği tamamlanır. Kaynak bazlı
buffer/ABR tercihleri, HTTP header ve DRM uyumluluğu bu temelin üzerine eklenir. Düşük RAM
cihazlarda Grid/Multi View kanal sayısı decoder ve bellek yeteneklerine göre sınırlandırılır.

### Aşama 6 - IPTV EPG ve sade içerik

XMLTV yenileme akış halinde, sınırlı zaman penceresiyle ve eski çalışan veriyi koruyan atomik
değişimle yapılır. Kaynak/kanal saat farkı ve EPG logosu tercihleri eklenir. Sonrasında Xtream
dizi-sezon-bölüm, devam et ve sonraki bölüm akışı sayfalı ve kumanda odaklı biçimde tamamlanır.

### Aşama 7 - Sistem entegrasyonu ve oynatıcı kararı

MediaSession, audio focus ve isteğe bağlı kare hızı eşleme gerçek TV ve TV stick üzerinde
doğrulanır. Media3'ün açamadığı örnek akışlar için ikinci motor prototipi hazırlanır; ölçümler
olumlu değilse ürün koduna alınmaz.

### Aşama 8 - Mağaza hazırlığı

Gerçek Play Billing, sunucu doğrulaması, R8/resource shrinking, gizlilik metni, ilk kurulum
akışı ve kullanıcı onaylı hata raporu tamamlanır. Paid ve local varyantların sınırları yeniden
doğrulanır.

## 5. Kodlamaya başlamadan önce karar kapıları

- Başlangıç ölçümlerinin gerçek cihaz ve TV stick/emülatörde alınması
- IPTV-only modda ilk açılış davranışının onaylanması
- Büyük katalog sayfalamasında özel focus-aware pager mı, Paging 3 mü kullanılacağının prototiple
  karşılaştırılması
- OSD refactor'ının görünümü değiştirmeden yapılacağının screenshot tabanıyla sabitlenmesi
- Catch-up için desteklenecek M3U ve Xtream biçimlerinin örnek kaynaklarla belirlenmesi
- Donma/görüntü gelmeme eşiklerinin emülatör yerine gerçek IPTV örnekleriyle kalibre edilmesi
- İkinci oynatıcı motorunun GPL, APK boyutu ve native ABI etkisinin üretim kararından önce yazılması
