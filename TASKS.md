# TVApp Görev Listesi

Durumlar: `[ ]` bekliyor, `[~]` devam ediyor, `[x]` tamamlandı. Öncelikler: P0 kritik,
P1 yüksek, P2 normal, P3 sonraki sürüm.

## Epic PERF - Ölçüm ve performans tabanı

- [x] **PERF-001 (P0): Performans ölçüm altyapısı**
  Startup, kanal yükleme, liste açma, sayfa sorgusu, arama, EPG ve tune sürelerini debug loguna
  yapılandırılmış biçimde yaz. Release'te hassas URL veya kimlik kaydetme.
  **Kabul:** Her işlemde süre, kayıt sayısı ve cihaz çalışma modu görülebilir.

- [x] **PERF-002 (P0): Büyük veri test üreticisi**
  0/500/15.000/50.000 IPTV kaydı ve büyük XMLTV verisi üreten debug-only araç ekle.
  Üretim veritabanını kullanma; test verisi açıkça ayrı tutulmalı ve güvenle temizlenebilmelidir.
  **Kabul:** Aynı veri seti emülatör ve cihaz testlerinde tekrar üretilebilir.

- [x] **PERF-003 (P0): Macrobenchmark ve frame ölçümü**
  Soğuk/sıcak başlangıç, kanal listesi açma ve hızlı gezinme benchmarklarını ekle.
  **Kabul:** CI veya yerel komut hedefleri DEVELOPMENT_PLAN.md eşikleriyle raporlar.

- [x] **PERF-004 (P1): Baseline Profile**
  Ana açılış, IPTV liste açma ve oynatma rotaları için profil üret.
  **Kabul:** Release başlangıç süresi ölçümle iyileşir; profil iki dağıtım varyantında paketlenir.

- [x] **PERF-005 (P1): Düşük RAM politikası**
  `ActivityManager.isLowRamDevice`, decoder sayısı ve bellek baskısına göre logo prefetch,
  Grid ve Multi View limitlerini belirle.
  **Kabul:** Düşük RAM cihazda bellek sürekli büyümez; uygulama kaynak baskısında kontrollü düşer.

## Epic PLATFORM - TIF ve IPTV-only cihaz desteği

- [x] **PLATFORM-001 (P0): DeviceCapabilities modeli**
  Leanback, TIF özelliği, TvInputManager, kullanılabilir vendor tuner, kanal erişimi, PiP,
  decoder ve düşük RAM sonuçlarını tek modelde topla.
  **Kabul:** TVApp'in kendi TIF input'u vendor tuner sayılmaz; başarısız sorgu crash üretmez.

- [x] **PLATFORM-002 (P0): ExperienceModeResolver**
  `HYBRID_TV`, `IPTV_ONLY_TV`, `MOBILE_TEST` modlarını üret ve sonucu oturum boyunca sabitle.
  **Kabul:** Marka/model kontrolü yoktur; aynı karar UI ve repository tarafından kullanılır.

- [x] **PLATFORM-003 (P0): IPTV-only açılış akışı**
  Tuner bulunmayan TV stick/box cihazında TIF izin ve hata dialoglarını atla. Kayıtlı IPTV varsa
  son geçerli IPTV görünümünü aç; kaynak yoksa IPTV kaynak yönetimini öner.
  **Kabul:** TIF'siz cihazda uygulama siyah/hata ekranında kalmadan tamamen kullanılabilir.

- [x] **PLATFORM-004 (P1): Yeteneklere göre UI**
  TIF yoksa Uydu/Radio, tuner setup ve fiziksel tuner editörü seçeneklerini gizle; IPTV, VOD,
  EPG ve ayarları koru. Kullanıcıya Ayarlar > Sistem altında algılanan modu göster.
  **Kabul:** Kaynak döngüsünde boş kaynak oluşmaz; dokümantasyon algılanan modu açıklar.

- [x] **PLATFORM-005 (P1): Manuel çalışma modu override**
  Otomatik, Hibrit ve IPTV-only seçenekleri ekle. Geçersiz Hibrit seçimde güvenli fallback yap.
  **Kabul:** Tercih kalıcıdır ve uygulama yeniden açıldığında korunur.

## Epic DATA - Büyük katalog ve veritabanı

- [x] **DATA-001 (P0): Sorgu envanteri ve query planı**
  IPTV/EPG DAO sorgularını `EXPLAIN QUERY PLAN` ile 15.000 ve 50.000 kayıtta ölç.
  **Kabul:** Tam tarama yapan ekran sorguları ve gerekli composite indeksler raporlanır.

- [x] **DATA-002 (P0): Focus-aware keyset pager**
  Büyük IPTV ekranlarında yüksek `OFFSET` yerine `originalIndex/sourceKey` tabanlı ileri/geri
  pencere getir. Odağı ve filtreyi async yükleme sırasında koru.
  **Kabul:** İlk/son ve CH+/CH- gezinimi çalışır; hiçbir UI rotası tüm kataloğu yüklemez.

- [x] **DATA-003 (P0): Hafif liste projection'ları**
  Liste satırları için yalnız kimlik, ad, sıra, logo, tür, kategori ve seçim bilgisini sorgula.
  **Kabul:** Görünür liste oluştururken stream kimlik bilgileri ve kullanılmayan sütunlar taşınmaz.

- [x] **DATA-004 (P0): Room FTS araması**
  Kanal adı, `tvg-name` ve kategori için FTS tablosu ve migration ekle.
  **Kabul:** 15.000 kayıtta arama p95 300 ms altında ve seçim/filtre değişiminde odak stabildir.

- [x] **DATA-005 (P1): Güvenli kaynak yenileme**
  M3U/Xtream/Stalker yenilemeyi staging + diff ile yap; seçim, özel sıra, özel ad ve EPG override
  bilgilerini koru.
  **Kabul:** Başarısız yenileme çalışan eski listeyi silmez; işlem iptal edilebilir.

- [x] **DATA-006 (P1): Ağır toplu metotları sınırla**
  `libraryChannels(null)`, `getAllEnabledLibraryChannels` ve benzeri tam katalog rotalarını UI
  kullanımından çıkar; yedeklemede akış/chunk kullan.
  **Kabul:** Heap içinde 15.000 satırlık katalog listesi tutulmaz.

- [x] **DATA-007 (P1): Logo cache bütçesi**
  Görünür pencere + sınırlı prefetch kullan; disk ve bellek limitlerini cihaz sınıfına göre uygula.
  **Kabul:** Kaydırma logo yüklemesi yüzünden odak/frame atlamaz; cache sınırı aşılmaz.

## Epic EPG - Program verisi ve rehber

- [x] **EPG-001 (P0): Ortak EPG snapshot cache**
  Infobar, kanal listesi ve rehber için tek now/next veri kaynağı kur.
  **Kabul:** Aynı anda aynı kanal için farklı EPG gösterilmez.

- [x] **EPG-002 (P0): Odak öncelikli prefetch**
  Odaktaki kanalı hemen, komşuları debounce ile getir; stale async yanıtları reddet.
  **Kabul:** Hızlı tuş basılı gezinmede ağ/DB kuyruğu büyümez ve eski EPG yeni satıra yazılmaz.

- [x] **EPG-003 (P1): EPG ölçüm ve cache politikası**
  Geçerli programı bitişine kadar, boş sonucu kısa süre cache'le; TIF/XMLTV sorgu süresini ölç.
  **Kabul:** Kanal listesinde tekrarlanan gereksiz sorgular belirgin biçimde azalır.

- [x] **EPG-004 (P2): Zaman çizelgeli rehber**
  Sabit kanal kolonu, yatay program zaman çizelgesi, açıklama alanı ve kaynak göstergesi ekle.
  **Kabul:** D-pad ile kanal/program kolonları arasında kayıpsız gezinilir; Back yayına döner.

- [x] **EPG-005 (P2): Eşleştirme güveni**
  Kesin `tvg-id`, kesin ad, normalize ad ve elle eşleşmeyi kullanıcıya göster.
  **Kabul:** Yanlış eşleşme tek ekrandan düzeltilebilir ve tüm EPG tüketicilerine yansır.

## Epic UIINPUT - OSD, kumanda ve görsel sistem

- [x] **UIINPUT-001 (P0): PlaybackUiState/OsdCoordinator**
  Kanal listesi, infobar, IPTV kontrolü, son kanallar, PIN, hata, grid ve Multi View durumlarını
  tek koordinatöre taşı.
  **Kabul:** Aynı anda çakışan birincil OSD yok; görünürlük değişimi tek noktadan yapılır.

- [x] **UIINPUT-002 (P0): RemoteActionRouter**
  Kısa/uzun tuş, Back, medya, yön, kanal ve renk eylemlerini UI durumuna göre yönlendir.
  **Kabul:** Eylem tablosu birim testli; dialog odaktayken tuş arkadaki ekrana gitmez.

- [x] **UIINPUT-003 (P0): UiAutomator kumanda testleri**
  Kanal listesi, editor, EPG, IPTV seek, PIN, son kanallar, grid ve Multi View senaryolarını ekle.
  **Kabul:** Kritik akışlar emülatörde tek komutla tekrar edilebilir.

- [x] **UIINPUT-004 (P1): Ortak TV görsel bileşenleri**
  Focus çerçevesi, teknik ikon slotu, renk eylemi, dialog satırı, panel ve safe-area ölçülerini
  ortaklaştır.
  **Kabul:** İsteğe bağlı ikonlar satırı kaydırmaz; 720p/1080p/4K ekran görüntüleri tutarlıdır.

- [x] **UIINPUT-005 (P2): Ayarlar OSD yerleşimi**
  Ayarları yayın kapanmadan video üzerinde OSD olarak göster; ayrı örnek önizleme oluşturma.
  **Kabul:** Video arka planda sürer; ayar paneli kumandayla kullanılabilir ve gereksiz önizleme
  oynatıcısı kaynak tüketmez.

## Epic IPTVCORE - IPTV oynatma sağlamlığı

Bu epic'in amacı TVApp'i TIF bulunmayan TV stick/box cihazlarında da güvenilir bir canlı TV
uygulaması yapmaktır. Medya merkezi görünümü, zengin katalog süsleri ve çok sayıda ikincil özellik
bu aşamanın kapsamında değildir. Her iş IPTV-only modu güçlendirirken Hibrit TV/TIF davranışını
korumalıdır.

- [x] **IPTVCORE-001 (P0): Ortak oynatma sağlık modeli**
  Media3 oynatma durumunu ilk kare süresi, son kare zamanı, buffer, bitrate, çözünürlük, codec,
  dropped frame ve son hata sınıfıyla tek bir salt-okunur modelde topla. TIF tarafı desteklediği
  alanları aynı teşhis sözleşmesine verir fakat IPTV kurtarma kararlarına dahil edilmez.
  **Kabul:** Sistem bilgileri ve debug logu aynı anlık görüntüyü kullanır; kimlik bilgisi veya tam
  özel URL yazılmaz; ölçüm UI thread'i bloke etmez.

- [x] **IPTVCORE-002 (P0): Donma ve görüntü gelmeme watchdog'u**
  İlk video karesi gelmeyen, `READY` olduğu halde görüntü üretmeyen ve oynarken ilerlemesi duran
  yayınları ayrı durumlar olarak algıla. Kanal değişimi, Back ve Activity kapanışı bekleyen bütün
  retry/watchdog işlerini iptal etmelidir.
  **Kabul:** Çalışan yayına eski timer müdahale etmez; sesli radyo yanlışlıkla görüntü hatası sayılmaz;
  donan akış kontrollü kurtarılır veya kullanıcıya kısa, işlem yapılabilir durum gösterilir.

- [x] **IPTVCORE-003 (P0): Deterministik kurtarma durum makinesi**
  Geçici ağ hatası, HTTP hata sınıfı, decoder hatası, canlı akış sonu ve kullanıcı yenilemesini
  farklılaştır. Sınırlı geri çekilme, aynı akışı yeniden hazırlama ve varsa alternatif akışa geçme
  sırasını tek noktadan yönet.
  **Kabul:** Sonsuz yeniden bağlanma döngüsü oluşmaz; kanal/listesi ve izleme geçmişi değişmez;
  her denemenin nedeni ve sonucu hassas veri olmadan teşhis kaydına düşer.

- [ ] **IPTVCORE-004 (P1): Kaynak bazlı oynatma profili**
  Her IPTV kaynağı için canlı buffer/gecikme, VOD buffer, ABR/kalite, otomatik kurtarma ve gelecekteki
  oynatıcı tercihini sakla. Kanal bazlı istisna yalnız gerçekten gerektiğinde kullanılmalıdır.
  **Kabul:** Varsayılanlar düşük RAM TV stick için güvenlidir; mevcut global ayarlar migration sonrası
  korunur; kaynak yenileme tercihleri silmez.

- [ ] **IPTVCORE-005 (P1): Protokol, header ve DRM uyumluluk matrisi**
  HLS, DASH ve doğrudan MPEG-TS için User-Agent/Referrer/header aktarımını tamamla. M3U
  `#EXTVLCOPT`, `#EXTHTTP`, URL sonu header'ları ve `#KODIPROP` Widevine/ClearKey alanlarını güvenli
  biçimde modelle.
  **Kabul:** Desteklenen alanlar kaynak yenilemede korunur; credential loglanmaz; örnek akışlarla
  parser ve MediaItem üretim testleri bulunur.

- [ ] **IPTVCORE-006 (P1): MediaSession, audio focus ve kare hızı eşleme**
  Medya tuşlarını sistem MediaSession ile yayınla, bildirimlerde duraklatmak yerine uygun audio-focus
  davranışını uygula ve isteğe bağlı içerik kare hızı eşlemeyi cihaz desteğine göre aç.
  **Kabul:** Home/geri dönüş, PiP, Grid ve Multi View sonrasında ses odağı kaybolmaz; özellik
  desteklenmeyen cihazda güvenli şekilde etkisiz kalır.

- [ ] **IPTVCORE-007 (P2): İkinci oynatıcı motoru fizibilitesi**
  libmpv veya başka bir FFmpeg tabanlı motoru yalnız Media3'ün açamadığı IPTV/VOD akışları için
  prototiple. GPL yükümlülüğü, APK/ABI boyutu, açılış süresi, bellek ve decoder çatışmasını ölçmeden
  üretime ekleme.
  **Kabul:** En az 20 sorunlu ve çalışan akıştan anonim bir uyumluluk matrisi, boyut/perf ölçümü ve
  devam/ret kararı belgelenir; bu görev doğrudan motor ekleme taahhüdü değildir.

## Epic EPGNEXT - IPTV EPG dayanıklılığı

- [ ] **EPGNEXT-001 (P1): Sınırlı ve atomik XMLTV yenileme**
  Gzip destekli XMLTV'yi akış halinde ayrıştır; kanal başlıklarını eşleştirme için korurken programları
  yalnız eşleşmiş/aday kanallar ve gerekli zaman penceresi için yaz. Yeni veri doğrulanmadan çalışan
  EPG tablolarını değiştirme.
  **Kabul:** Yarım indirme veya parse hatası eski rehberi silmez; catch-up süresi kadar geçmiş ve en
  az 48 saat gelecek korunur; bellek dosya büyüklüğüyle doğrusal büyümez.

- [ ] **EPGNEXT-002 (P1): Kaynak ve kanal bazlı EPG düzeltmeleri**
  Global kaynak saat farkı, kanal bazlı saat farkı ve XMLTV kaynak logosunu kullanma tercihi ekle.
  **Kabul:** Düzeltme infobar, kanal listesi ve rehbere aynı cache snapshot'ından yansır; elle eşleşme
  bozulmaz ve kaynağın yenilenmesi tercihi silmez.

- [ ] **EPGNEXT-003 (P2): EPG senkron teşhisi**
  Kaynak indirme, parse, aday/eşleşen kanal, eklenen/elenen program ve pencere bilgilerini özetle.
  **Kabul:** Kullanıcı tek ekranda neden bir kanalda EPG olmadığını anlayabilir; özel URL ve kaynak
  kimlik bilgileri gösterilmez.

## Epic FEATURE - Sonraki kullanıcı özellikleri

- [x] **FEATURE-001 (P1): Ana kanal listesinde hızlı arama**
  TV `SEARCH` tuşu ve liste başlığı üzerinden tüm geçerli kaynaklarda arama aç.
  **Kabul:** Sayı ile kanal seçimi korunur; IPTV-only modda arama tüm seçili kataloğu kapsar.

- [x] **FEATURE-002 (P2): Catch-up/arşiv**
  M3U catch-up öznitelikleri ve Xtream arşiv API'sini modelle, EPG programından oynat.
  **Kabul:** Arşivlenebilir program işaretlidir; canlıya dönüş tek eylemdir.

- [ ] **FEATURE-003 (P2): Sade dizi/sezon/bölüm**
  Xtream seri kataloğu, kumandayla hızlı sezon-bölüm seçimi, devam et ve sonraki bölümü ekle.
  TMDB, oyuncu kadrosu, fragman veya ayrıntılı medya merkezi ana ekranı bu görevin parçası değildir.
  **Kabul:** 15.000+ içerikte liste sayfalıdır; bölümler canlı kanal geçmişine karışmaz; kullanıcı
  en fazla birkaç kumanda hareketiyle kaldığı bölüme dönebilir.

- [x] **FEATURE-004 (P2): Yayın sağlık bilgisi arayüzü**
  `IPTVCORE-001` modelindeki codec, çözünürlük, bitrate, buffer, dropped frame ve hata nedenini
  mevcut Sistem Bilgileri/teşhis görünümünde göster.
  **Kabul:** Normal izleme arayüzünü kalabalıklaştırmaz; kullanıcı açmadıkça ek sorgu maliyeti yaratmaz.

- [ ] **FEATURE-005 (P2): Alternatif akış önceliği**
  Kullanıcının alternatif stream sırasını düzenlemesine ve başarısız streami geçici atlamasına
  izin ver.
  **Kabul:** Failover kanal/listesini veya izleme geçmişini değiştirmez.

## IPTV v1 kapsam sınırı

IPTV-only cihaz desteğinin ilk kararlı sürümünde şu özellikler bilinçli olarak kapsam dışıdır:

- Netflix/Prime benzeri ayrı bir keşif ana ekranı
- TMDB oyuncu kadrosu, fragman, trend listeleri ve zengin metadata zorunluluğu
- Çoklu kullanıcı profili ve cihazlar arası sosyal/hesap özellikleri
- Film/bölüm indirme ve çevrimdışı katalog
- Çok sayıda tema, arka plan fotoğrafı veya hava durumu gibi oynatmayla ilgisiz süsler

Bu maddeler ileride kullanıcı talebi ve ölçülmüş fayda varsa ayrı epic olarak değerlendirilebilir.

## Epic STORE - Yayın ve güvenlik

- [ ] **STORE-001 (P2): Gerçek Play Billing**
  Purchase token'ı sunucuda/Firebase Functions ile doğrula; yalnız `PURCHASED` yetki versin.
  **Kabul:** Pending ödeme erişim açmaz; debug simulator release paketinde bulunmaz.

- [ ] **STORE-002 (P2): Release küçültme ve doğrulama**
  R8 ve resource shrinking'i local/paid release üzerinde kademeli etkinleştir.
  **Kabul:** TIF service, Room, Media3 ve deep link kuralları release smoke testini geçer.

- [ ] **TXT-001 (P3): Teletext stratejisi — TIF araştırması tamamlandı**
  AOSP TIF kuralı gereği teletext çözümü vendor TV Input'un sorumluluğudur; `TvTrackInfo`'da
  teletext ayrı bir tip yoktur (yalnızca VIDEO/AUDIO/SUBTITLE) ve üçüncü parti uygulamaların
  vendor input'a sayfa isteği/event gönderme API'si bulunmaz. Bu yüzden TvView üzerinden gerçek
  sayfa görünümü mümkün değildir. Kabul edilen geçici davranış: TXT tuşu (KEYCODE_TV_TELETEXT,
  233) tüm kaynaklarda VOD Ana Sayfa'yı açar. İleride: vendor bir SUBTITLE track'i teletext
  olarak bildiriyorsa TIF'te o track'i seçen + "teletext yok" OSD'si düşen bir tespit katmanı
  (`TifPlaybackController.teletextTrack()`), gerçek sayfalar içinse IPTV/HTTP teletext kaynağı
  değerlendirilir.

- [ ] **STORE-003 (P2): İlk kurulum ve gizlilik**
  Çalışma modu, IPTV kaynağı, EPG ve kumanda kontrolünü içeren kısa kurulum akışı oluştur.
  **Kabul:** Kullanıcı TIF olmayan cihazda neden yalnız IPTV gördüğünü anlayabilir.

## Önerilen sıradaki sprintler

Tamamlanmış performans, platform, veri, EPG ve kumanda temeli korunarak geliştirme şu sırayla
ilerlemelidir:

1. **Sprint A - Gözlem:** `IPTVCORE-001` ve `FEATURE-004`
2. **Sprint B - Kurtarma:** `IPTVCORE-002`, `IPTVCORE-003` ve `FEATURE-005`
3. **Sprint C - Kaynak uyumluluğu:** `IPTVCORE-004` ve `IPTVCORE-005`
4. **Sprint D - EPG sağlamlığı:** `EPGNEXT-001`, `EPGNEXT-002`, `EPGNEXT-003`
5. **Sprint E - Sade içerik:** `FEATURE-003`
6. **Sprint F - Sistem entegrasyonu:** `IPTVCORE-006`
7. **Karar çalışması:** `IPTVCORE-007`

Her sprintte Hibrit TV için DVB/ATV kanal açma, kanal listesi, infobar, EPG, ses ve Back davranışı
regresyon testinden geçirilmelidir. İkinci motor ve medya merkezi özellikleri bu çekirdek işler
gerçek TV/TV stick üzerinde doğrulanmadan başlatılmamalıdır.

## Epic REMOTEEDIT - TV Sunucu / Telefon Client Liste Yönetimi

> Vizyon: TV sürümü **sunucu (server) modunda** çalışır; telefon sürümü **client modunda**
> TVApp'in kanal/liste verisini sunucudan çeker, telefon arayüzünde düzenler ve değişiklikleri
> sunucuya geri senkron eder. Sunucu veri sahibidir (source of truth), client yalnızca
> açıkça gönderilen değişiklikleri uygular. Bu epic tamamen bir plan taslağıdır; her sprint
> önce onaylanır, sonra uygulanır.

### Onaylanmış tasarım kararları (2026-09-27)
- **Web panosu ilk sürümde var:** TVApp'in gömülü sunucusu hem telefona hem bilgisayar
  tarayıcısına aynı REST API'yi servis eder. Web arayüzü APK içine gömülü statik sayfadır
  (ek sunucu/bağımlılık gerektirmez).
- **Çakışma stratejisi:** `revision` sayacı + son yazan kazanır. Client eski `revision`
  ile yama gönderirse sunucu 409 + mevcut satırı döner; client yeniden çeker.
- **Düzenleme kapsamı:** Tüm kullanıcı düzenleme alanları ilk sürümde açılır:
  sık kullanı, gizle/skip, özel ad, özel numara, grup, sıralama.

### Mimari genel bakış

```
[Telefon client (mobil flavor)]     [Web panosu (tarayıcı, LAN)]
            \                           /
             \-- HTTP, yalnız LAN ----/
                       |
         TVApp (TV sürümü) = sunucu modu
         - Gömülü HTTP sunucusu (yalnız Wi-Fi arabirimi)
         - Eşleştirme + jeton doğrulama
         - REST API v1 (/api/v1/...)
         - Yazmalar ChannelRepository üzerinden Room'a
                       |
                Room = yetkili veri
```

- TVApp tek doğruluk kaynağıdır; sunucu modu Room'un üstünde ince bir API katmanıdır.
  Client doğrudan veritabanına asla yazmaz; her yazma isteği mevcut repository
  metotlarına (`setFavorite`, `setHidden`, `setCustomName`, `setCustomNumber`,
  `setGroup`, `setSortOrder`) bağlanır — başka yazma yolu açılmaz.
- Telefon native client ve web panosu aynı API'yi kullanır; davranış birebir aynıdır,
  bakım yükü tektir. Web panosu `assets/webpanel/` altından servis edilir.

### Veri modeli değişiklikleri (DB v25)
- `user_channels` tablosuna `revision INTEGER NOT NULL DEFAULT 0` kolonu; Room migration
  24→25 (yıkıcı değil, kullanıcı verisi korunur). Her kullanıcı düzenleme yazımında
  satırın `revision` değeri monoton artar.
- Yeni tablo `paired_devices(id, deviceName, pairedAt, lastSeenAt, tokenHash)`:
  jeton düz metin saklanmaz, yalnız SHA-256 özeti tutulur; hiçbir günlüğe yazılmaz.
- Sunucunun global sürüm sayacı (preferences'ta monoton sayaç) her yazmada artar;
  canlı yenileme (long-poll) bu sayacı izler.

### Sunucu API tasarımı (v1)
Tümü `Authorization: Bearer <jeton>` ister (`ping` hariç). Gövdeler JSON.

| Metot ve yol | Amaç |
|---|---|
| `GET /api/v1/ping` | Sunucu adı, TVApp sürümü, eşleştirme gerekli mi |
| `POST /api/v1/pair` `{code, deviceName}` | 6 haneli kodu doğrular, jeton döner |
| `GET /api/v1/channels?after=<sortOrder>&limit=<n>&q=<ara>` | Keyset sayfalı liste + satır `revision`; `q` LIKE araması |
| `GET /api/v1/channels/{sourceKey}` | Tek kanal + tüm düzenlenebilir alanlar |
| `PATCH /api/v1/channels/{sourceKey}` `{revision, favorite?, hidden?, customName?, customNumber?, sortOrder?, groupId?}` | Alan bazlı yama; `revision` uyuşmazsa 409 |
| `POST /api/v1/channels/batch` `{ops: [...]}` | Toplu sıralama/toggle; en fazla 100 işlem/istek |
| `GET /api/v1/groups` / `POST /api/v1/groups` | Grup listesi / grup oluşturma |
| `GET /api/v1/sources` | IPTV/XMLTV kaynak özetleri (v1 salt-okunur) |
| `GET /api/v1/events?since=<globalRevision>` | Uzun sorgulama; global sürüm değişince döner |
| `GET /` | Web panosu (APK assets'inden statik sayfa) |

- Hata sözleşmesi: `401` jeton yok/geçersiz, `404` kanal yok, `409` revision çakışması
  (gövde mevcut satırı döner), `413` batch limiti aşıldı, `400` doğrulama hatası.
- 15.000+ katalog: listeleme daima keyset sayfalı; mevcut `originalIndex >= :fromIndex`
  sorgu deseni yeniden kullanılır. Tam katalog hiçbir yerde belleğe tek seferde yüklenmez.

### Eşleştirme akışı (adım adım)
1. **TV:** Ayarlar > Sistem > "Telefonla yönetim" → Aç. Sunucu yalnız Wi-Fi arabirimine
   bağlanır; ekranda `IP:port` ve 6 haneli tek seferlik kod gösterilir (kod 5 dk geçerli,
   3 yanlış denemede 5 dk kilit).
2. **Telefon / tarayıcı:** `IP:port` açılır; `ping` ile sürüm uyumu kontrol edilir.
3. Kod girilir → `POST /api/v1/pair` → cihaz `paired_devices`'a yazılır, 256-bit rastgele
   jeton üretilir ve **yalnız bir kez** gösterilir. Telefon jetonu
   EncryptedSharedPreferences'ta saklar; web panosunda saklama kullanıcı tercihine
   bırakılır (oturumluk / kalıcı).
4. Sonraki tüm istekler jetonla doğrulanır (sabit zamanlı hash karşılaştırması).
   TV'den "cihazı kaldır" jetonu geçersiz kılar; cihaz listesi TV'de yönetilir.

### Telefondan düzenleme UX (mobil flavor)
- "TV'ye bağlan" giriş ekranı: adres (son adres otomatik doldurulur), eşleştirme kodu
  girişi, bağlantı durumu rozeti.
- Bağlıyken: arama kutusu + sayfalı kanal listesi; satıra dokunun → düzenleme sayfası
  (favori ★, gizle, özel ad, özel numara, grup, sıralama).
- Sıralama sürükle-bırak; toplu seçim ile favori/gizle grup işlemleri batch endpoint'e
  gider.
- **Çevrimdışı kuyruk:** Bağlantı yokken yapılan düzenlemeler telefonda yerel kuyrukta
  (JSON dosyası) tutulur; bağlantı gelince sırayla `PATCH` atılır. `409` alan işlemler
  kullanıcıya "TV'de değişmiş" mesajıyla gösterilir, otomatik ezilmez.

### Web panosu (APK içinden servis)
- `assets/webpanel/{index.html, app.js, style.css}` — çerçevesiz vanilla JS + `fetch`,
  tahmini 50-80 KB; ek bağımlılık yok.
- Giriş: eşleştirme kodu → jeton; kanal tablosu (sanallaştırılmış render, API sayfalı),
  satır içi düzenleme, çoklu seçim + toplu favori/gizle, sürükle-bırak sıralama
  (batch işlemlerine dönüşür).
- Yalnız LAN'da çalışır; kimlik bilgisi veya kanal kaynağı sırrı panoda asla görünmez.

### Sprint planı

- [ ] **REMOTEEDIT-001 (P1): Sunucu modu temeli — yerel HTTP servis**
  Gömülü sunucu kütüphanesi: **NanoHTTPD önerilir** (minimal, tek küçük bağımlılık);
  Ktor CIO yalnız NanoHTTPD yetersiz kalırsa değerlendirilir. Wi-Fi arabirimine bağlama,
  varsayılan port 8890, Ayarlar > Sistem altında "Telefonla yönetim" anahtarı ve durum
  satırı (açık/kapalı, adres). Endpoint iskeleti: `ping`, `channels` (sayfalı okuma),
  `groups`, `sources`. Hata sözleşmesi ve loglama kuralları bu sprintte netleşir.
  **Kabul:** Uygulama açıkken sunucu LAN'dan erişilebilir; jeton olmadan hiçbir veri
  dönmez; ekran kapalıyken/Doze'da davranış cihazda test edilip belgelenir; APK boyutu
  etkisi ölçülüp not edilir.

- [ ] **REMOTEEDIT-002 (P1): Eşleştirme ve yetkilendirme**
  `paired_devices` tablosu, 6 haneli kod üretimi/doğrulaması (5 dk, 3 deneme, kilit),
  jeton üretimi + SHA-256 hash saklama, TV'de cihaz listesi ve kaldırma ekranı,
  hız sınırı (brute-force koruması).
  **Kabul:** Yanlış/eksik jeton 401; jeton ve kod hiçbir günlüğe yazılmaz; eşleştirme
  TV'den geri alınabilir.

- [ ] **REMOTEEDIT-003 (P1): Telefon client okuma yolu**
  "TV'ye bağlan" akışı, jeton saklama, keyset sayfalı kanal listesi, `q` araması.
  **Kabul:** 15.000+ katalog telefonda akıcı sayfalanır; cihaz çevrimdışıysa net hata;
  loglarda kimlik bilgisi görünmez.

- [ ] **REMOTEEDIT-004 (P1): Düzenleme + senkron yazma yolu**
  DB v25 (`revision` kolonu, migration 24→25), `PATCH` + `batch` endpoint'leri,
  409 çakışma yanıtı, telefonda çevrimdışı kuyruk ve senkron butonu; TV tarafında
  Room akışı üzerinden canlı yansıma.
  **Kabul:** Tüm düzenleme alanları telefondan çalışır; sıralama yalnız Room'a yazılır
  (TIF veritabanına asla); çakışmada veri kaybı olmadan temiz mesaj.

- [ ] **REMOTEEDIT-005 (P2): Değişiklik bildirimi ve canlı yenileme**
  Global sürüm sayacı, `GET /api/v1/events` long-poll, client'ın listede değişen
  satırları tazelemesi.
  **Kabul:** TV ve telefon aynı `revision`'da buluşur; ağ kesintisinde her iki taraf
  tutarlı son duruma döner.

- [ ] **REMOTEEDIT-008 (P1, Sprint R3): Web panosu**
  `assets/webpanel` statik sayfası, eşleştirme ekranı, sayfalı kanal tablosu, satır içi
  düzenleme, toplu işlemler, sürükle-bırak sıralama. REMOTEEDIT-001/002/004'e bağlıdır.
  **Kabul:** Tarayıcıdan (bilgisayar veya telefon) tüm düzenleme alanları çalışır;
  pano yalnız LAN'dan erişilebilir; ek bağımlılık eklenmez.

- [ ] **REMOTEEDIT-006 (P2): IPTV kaynak/liste yönetimi (kısıtlı)**
  Telefondan/web'den yeni IPTV listesi ekleme/güncelleme isteği sunucuda kuyruğa alınır;
  TV mevcut import akışıyla (IptvRepository) uygular. Kimlik bilgileri şifreli alanda
  taşınır, loglanmaz.
  **Kabul:** Büyük katalog importu sayfalı akışla çalışır; kaynak silme iki aşamalı onay;
  kimlik bilgisi hiçbir yerde düz metin loglanmaz.

- [ ] **REMOTEEDIT-007 (P3): Testler, güvenlik sertleştirme ve belgeler**
  Entegrasyon testleri (sahte client), çakışma senaryoları, Kılavuz + README +
  CHANGELOG, paid flavor davranışı.
  **Kabul:** Hem `local` hem `paid` derlenir; özellik bayrağı
  (`BuildConfig.REMOTE_EDIT_ENABLED`) ile açılır/kapanır; dokümantasyon güncel.

### Sprint sırası
1. Sprint R1: `REMOTEEDIT-001` + `REMOTEEDIT-002` (sunucu + eşleştirme)
2. Sprint R2: `REMOTEEDIT-003` + `REMOTEEDIT-004` (telefon okuma + yazma, DB v25)
3. Sprint R3: `REMOTEEDIT-008` (web panosu) + `REMOTEEDIT-005` (canlı yenileme)
4. Sprint R4: `REMOTEEDIT-006` (kaynak yönetimi)
5. Sprint R5: `REMOTEEDIT-007` (sertleştirme + dokümantasyon)

### Güvenlik ilkeleri
- Jeton ve eşleştirme kodu **asla loglanmaz**; günlüklerde yalnız olay + cihaz adı.
- Trafik yalnız LAN; sunucu yalnız Wi-Fi arabirimine bağlanır, LAN dışı istek reddedilir.
- Sunucu yalnızca kullanıcı açtığında çalışır; kalıcı çalışma açıkça seçilirse
  foreground service + pil davranışı belgelenir.
- `local` ve `paid` flavor'larında aynı davranış; ileride Play politikası gerektirirse
  özellik bayrağıyla kapatılabilir.

### Riskler ve açık kararlar
- **Çözülenler (2026-09-27):** web panosu ilk sürümde var; `revision` + son yazan
  kazanır; tüm düzenleme alanları ilk sürümde.
- **Kalan:** Arka plan dayanıklılığı (uygulama açıkken sınırlı mı, foreground service
  mi — kullanıcı kararı); NanoHTTPD'nin APK boyutu etkisinin ölçülmesi; LAN dışı erişim
  v1'de kapsam dışı; web panosunda jeton kalıcılığı kullanıcı tercihine bırakıldı.
