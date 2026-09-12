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
