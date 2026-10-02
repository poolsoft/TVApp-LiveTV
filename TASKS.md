# TVApp Görev Listesi

Durumlar: `[ ]` bekliyor, `[~]` devam ediyor, `[x]` tamamlandı. Öncelikler: P0 kritik,
P1 yüksek, P2 normal, P3 sonraki sürüm.

## IPTV geliştirme uygulama sırası (2026-10-01)

Kullanıcının onayladığı kapsam; ayrı bir medya merkezi ana ekranı eklenmez.
Her aşama kendi testleriyle doğrulanıp gönderilir, aşağıdakiler topluca tamamlandı sayılmaz.

1. `IPTVCORE-004`: Kaynakta canlı/VOD buffer, azami kalite ve otomatik kurtarma;
   her alan ayrı ayrı genel ayarı miras alabilir. Yenileme ve yedekleme tercihleri korur.
2. `IPTVCORE-005`: M3U özel header/DRM alanları, HLS/DASH/TS test matrisi.
3. `IPTVCORE-006`: Sistem medya oturumu, aktif oynatıcı için tek ses odağı sahibi,
   Home dönüşü/PiP/Multi-View regresyonu ve isteğe bağlı kare hızı eşleme.
4. `FEATURE-006` + `FEATURE-005`: Gerçek eklenen/kaldırılan/korunan kayıt özeti;
   alternatif akış sırası ve geçici atlama, kanal geçmişini değiştirmeden.
5. `FEATURE-007`: Ayarlarda yerelleştirilmiş başlık/anahtar sözcük araması, sonuçtan ilgili sekme/satıra odak.
6. `FEATURE-003`: Sayfalı sezon/bölüm, kaldığın bölüm ve sonraki bölüm; büyük keşif ekranı yok.
7. `STORE-003`: Tuner yeteneğine göre kısa, atlanabilir ilk kurulum; satın alma işi bu kapsamda değil.

- [ ] **FEATURE-006 (P1): Kaynak güncelleme özeti**
  Staging karşılaştırmasından yeni, kaldırılan ve korunmuş seçili kayıt sayılarını hesapla.
  İptal/başarısız indirme başarı özeti göstermez; yalnız tamamlanan atomik güncelleme sonucu yayımlanır.
- [ ] **FEATURE-007 (P2): Ayar araması**
  Mevcut sekmeleri koruyarak buffer, EPG, altyazı ve benzeri başlıkları arat;
  seçilen sonucun gerçek ayar satırına odaklan, sahte/boş sonuç oluşturma.

## VOD ekranı yenileme planı (2026-10-02)

Canlı TV ve sade VOD ayrı kullanım modlarıdır; büyük Netflix benzeri keşif ekranı kurulmayacak.

- [x] **VODMODE-001 (P0): Canlı TV / VOD ayrımı**
  Açık mod seçimi, ayrı son kanal/film ve geçmiş, VOD'da canlı kanal gezinmesini engelleme,
  OK/MENU ile filmi duraklatıp kütüphaneye dönüş ve son filme devam uygulandı.
  Kumanda yönlendirmesi ve içerik türü testleri eklendi. Gerçek TV odak testi bekliyor.

- [ ] **VODUI-001 (P0): Arama ve yükleme yaşam döngüsü**
  VodHomeActivity.reloadGrid eski işi iptal edip loadingPage durumunu sıfırlamalı.
  Sayfa sorgusu kaynak/index değerlerini yerel kopyalarda taşımalı; eski sorgu yeni aramanın
  cursor değerlerini değiştirmemeli. Hatalar boş katalog gibi gösterilmemeli; tekrar dene olmalı.
  **Kabul:** Yükleme sürerken art arda arama, boş sonuç ve ağ/veritabanı hatası ekranı kilitlemez.
- [ ] **VODUI-002 (P1): Tek kaynak ve kategori seçimi**
  Üstte kayıtlı IPTV listesi, ardından kategori; son seçilen kaynak/kategori hatırlanır.
  Binlerce film farklı kaynaklardan tek görünümde karıştırılmaz. Mavi arama, sarı filtre dialogu;
  arama alanı başlangıç odağını ve yazılım klavyesini kendiliğinden açmaz.
- [ ] **VODUI-003 (P1): Sade katalog ve detay**
  TV için 4-5, telefonda 2-3 poster sütunu; mevcut Coil cache ve kaynak görselleri kullanılır.
  Görsel yoksa gerçek başlıkla sade kart; sahte poster, puan, yıl veya açıklama üretilmez.
  Başlık iki satır, belirgin odak ve konum göstergesi. OK detay OSD; Oynat/Devam et/Baştan başlat.
- [ ] **VODUI-004 (P1): Bounded sayfalama ve odak**
  Bütün gezilmiş kataloğu biriktirip tekrar diff etmek yerine sınırlı pencere ve DAO keyset.
  Devam Et en fazla mevcut bounded geçmiş kadar; kaynak filtresine uyar. Back önce detayı kapatır,
  sonra filtreyi/ekranı terk eder. Oynatmadan dönüşte aynı karta ve kaydırma konumuna dönülür.
- [ ] **VODUI-005 (P2): Bölüm görünümü ve doğrulama**
  Xtream gerçek sezon/bölüm bilgisi sağlıyorsa sade sezon/bölüm seçimi; düz M3U başlıklarından
  tahmin edilen metadata gerçek bilgi gibi sunulmaz. Sonraki bölüm isteğe bağlıdır.
  **Kabul:** 15.000 kayıt, eksik görsel/metadata, kumanda ve mobil dokunma test matrisi.

## MultiView liste seçimi (2026-10-02)

- [~] **MULTIVIEW-SELECT (P1): Mevcut kanal listesinden seçim**
  Dialog yalnız eklenen kanalları ve Başlat/Kanal ekle eylemlerini gösterir. Kanal ekle mevcut
  normal/IPTV kanal listesini kullanır; arama, kaynak ve harf filtresi korunur. Seçim modunda OK
  ekler/çıkarır, yeşil/Back özet dialoguna döner; auto-tune ve geçmiş kaydı yapılmaz.
  En fazla bir TIF ve cihaz kapasitesi, VOD reddi korunur. Gerçek TV kumanda doğrulaması bekliyor.
- [~] **MULTIVIEW-TIF-SIZE (P0): İç yüzey ve buffer ölçüsü**
  TvView çerçevesiyle birlikte SurfaceView ölçümü ve yerleşimden buffer boyutu güncellenir.
  Grid/tam ekran/oran regresyonu emülatörde; gerçek MediaTek görüntü ölçeği TV üzerinde doğrulanmalı.

## Harfle gezinme

- [x] **UIALPHA-002 (P2): Harfi başlangıç filtresi olarak kullanma**
  Mevcut atlama yerine Tümü + geçerli harfler seçimi; seçilen harfle başlayan kayıtları mevcut
  kullanıcı/katalog sırasını bozmadan göster. Normal liste ve IPTV DAO sayfalaması aynı harf
  normalizasyonunu kullanmalı; katalog UI thread üzerinde taranmaz/sıralanmaz. Tümü filtreyi temizler.
  Normal liste, IPTV kütüphanesi ve Devam Et görünümünde uygulandı. IPTV harfleri DAO özetiyle
  hesaplanır; ileri/geri/son sayfa ve sayı ile seçim aynı filtreyi kullanır. Gerçek TV odak testi bekliyor.

- [~] **UIALPHA-001 (P2): Filtreye bağlı harf çubuğu**
  Ana kanal listesi ve IPTV kütüphanesinde ayarla aç/kapat, uzun sağ ok, sol/Back dönüş,
  Tümü + mevcut harfler ile başlangıç filtresi uygulandı. IPTV tam kataloğu UI belleğine yüklenmez.
  Kanal editörü, IPTV seçim editörü ve XMLTV eşleştirme listesine aynı davranışın aktarımı bekliyor;
  bu ekranlardaki sağ/sol eylemleri korunarak yalnız boş yön tuşları kullanılacak.
  **Kabul:** Harf gezinmesi auto-tune, üyelik, sıra veya izleme geçmişini değiştirmez;
  Android 11 Google TV kumandasında odak ve sayfa geçişi doğrulanır.

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

- [x] **IPTVCORE-004 (P1): Kaynak bazlı oynatma profili**
  Her IPTV kaynağı için canlı buffer/gecikme, VOD buffer, ABR/kalite, otomatik kurtarma ve gelecekteki
  oynatıcı tercihini sakla. Kanal bazlı istisna yalnız gerçekten gerektiğinde kullanılmalıdır.
  **Kabul:** Varsayılanlar düşük RAM TV stick için güvenlidir; mevcut global ayarlar migration sonrası
  korunur; kaynak yenileme tercihleri silmez.
  **2026-10-01:** Canlı/VOD buffer, azami çözünürlük ve otomatik kurtarma kaynak menüsüne eklendi;
  alanlar ayrı ayrı genel ayarı kullanabilir. Genel kalite/kurtarma ayarları, profil kullanan seekbar
  buffer değişimi, kaynak yenileme/adlandırma koruması ve yedek desteği tamamlandı.
  Room 24/25 -> 26 geçişleri ve eski yedek uyumluluğu emülatörde doğrulandı; iki debug varyantın
  birim testleri ve derlemeleri geçti. Android 11 fiziksel TV'de kumanda odağı ve yayın testi gerekli.
  Gelecekte başka motor eklenirse kaynak motor tercihi ayrı genişletme olarak ele alınacak;
  bu sürüm yalnız mevcut Media3 motorunu kullanır.

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
  **2026-10-01 ilerleme:** Atomik yayımlama ve bozuk/boş kaynakta eski EPG'yi koruma tamamlandı;
  kesilmiş dosya ve boş kaynak testleri izole Room veritabanında emülatörde geçti.
  Kanal kataloğunu koruyarak program zaman penceresini sınırlama henüz tamamlanmadı.
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

## Epic REMOTEEDIT - TV Sunucu / Telefon Client Liste Yönetimi (İPTAL EDİLDİ)

> **DURUM: İptal edildi.** Epic uygulandı (sprint R1–R5 + web panosu + NSD), ancak
> sistemde yavaşlık ve kararsız davranışa yol açtığı için kullanıcı denemesini iptal etti;
> tüm kod geri alındı ve DB v25 → v24 geri dönüş migration'ı eklendi (veri kaybı yok).
> Aşağıdaki plan yalnızca geçmiş/ referans amaçlı korunmaktadır; yeniden ele alınacaksa
> performans ve stabilite çıkarımlarıyla baştan tasarlanmalıdır.

> Vizyon: TV sürümü **sunucu (server) modunda** çalışır; telefon sürümü **client modunda**
> TVApp'in kanal/liste verisini sunucudan çeker, telefon arayüzünde düzenler ve değişiklikleri
> sunucuya geri senkron eder. Sunucu veri sahibidir (source of truth), client yalnızca
> açıkça gönderilen değişiklikleri uygular. Bu epic tamamen bir plan taslağıdır; her sprint
> önce onaylanır, sonra uygulanır.

### Mimari hedefler ve ilkeler
- **Room yetkili kalır:** Tüm listeye/katalog verisi TV tarafındaki Room veritabanında yaşar.
  Server modu Room'un üstünde bir salt-okunur anlık görüntü + değişiklik kuyruğu servis eder;
  client asla doğrudan TV'nin veritabanına yazmaz.
- **Yetki modeli:** Varsayılan olarak kanal düzenleme istekleri TVApp'in mevcut
  `ChannelRepository`/`IptvRepository` API'lerinden geçer; başka yazma yolu açılmaz.
- **Güvenlik:** İstemci-TV eşleşmesi yerel ağda tek seferlik eşleştirme kodu (pairing code)
  ile yapılır. Jetonlar (token) cihazda saklanır, **asla loglanmaz**; trafik yalnız
  yerel ağ (LAN) amaçlıdır ve varsayılan olarak Wi-Fi ağ arabirimine bağlıdır.
- **Ölçek:** 15.000+ katalog senaryosu için listeleme/aktarım her zaman sayfalıdır
  (mevcut keyset `originalIndex >= :fromIndex` sorguları yeniden kullanılır); tam katalog
  belleğe tek seferde yüklenmez.
- **Çakışma stratejisi:** Alan bazlı "son kazanan" (field-level last-write-wins) +
  sürüm sayacı. Her kullanıcı düzenlemesi `user_channels` satırında monoton bir
  `revision` alanı taşır; client eski `revision` üzerinden değişiklik gönderirse sunucu
  çakışma yanıtı döner ve client mevcut satırı yeniden çeker. Kanal silme/ekleme
  idempotent (yeniden gönderilebilir) tanımlanır.
- **Tek/çoklu client:** v1'de tek eşleşmiş telefon; ileride eşleştirilmiş cihaz listesi
  genişletilebilir. Sunucu aynı anda tek yazma oturumu kabul eder.

### Sprint planı

- [ ] **REMOTEEDIT-001 (P1): Sunucu modu temeli — yerel HTTP servis**
  TV uygulaması içinde yalnız LAN'da çalışan gömülü HTTP sunucusu (örn. Ktor/NanoHTTPD;
  mevcut bağımlılık setine en yakın olanı seçilir) ve Ayarlar > Sistem altında
  "Telefonla yönetim" anahtarı. Endpoint taslağı:
  `GET /api/v1/ping`, `GET /api/v1/channels?pageAfter=<index>&limit=<n>`,
  `GET /api/v1/groups`, `GET /api/v1/sources`.
  **Kabul:** TV uygulaması ön planda/arka planda iken sunucu davranışı belgelenir;
  ekran kapalıyken davranış (Doze/battery) cihazda test edilir; şifresiz trafik yalnız
  LAN'da çalışır; PIN/eşleştirme olmadan hiçbir veri dönmez.

- [ ] **REMOTEEDIT-002 (P1): Eşleştirme ve yetkilendirme**
  TV'de 6 haneli tek seferlik eşleştirme kodu gösterilir; telefon kodu girer, sunucu
  cihaz kaydeder ve zaman sınırlı jeton üretir. Tüm istekler jetonla doğrulanır.
  **Kabul:** Yanlış/eksik jeton 401 döner; jeton ve eşleştirme kodu hiçbir günlüğe
  yazılmaz; eşleştirme TV'den geri alınabilir (cihaz listesi + iptal).

- [ ] **REMOTEEDIT-003 (P1): Telefon client okuma yolu**
  Mobil flavor'da "TV'ye bağlan" akışı: sunucu keşfi (el ile IP:port; mDNS/DNS-SD
  sonradan), eşleştirme, sayfalı kanal/liste görüntüleme (mevcut keyset desenine uygun).
  **Kabul:** 15.000+ kayıtlı katalog telefonda akıcı sayfalanır; cihaz çevrimdışıysa
  net hata gösterilir; loglarda kanal kaynak kimlik bilgileri görünmez.

- [ ] **REMOTEEDIT-004 (P1): Düzenleme + senkron yazma yolu**
  Telefondan sık kullanı, gizle/skip, sıralama (sortOrder), ad ve grup düzenlemeleri.
  İstek biçimi: alan bazlı yama (`PATCH /api/v1/channels/{sourceKey}`) + `revision`;
  sunucu mevcut `ChannelRepository` DAO güncellemelerini kullanır. Toplu işlem için
  sınırlı batch endpoint'i (maks. N satır/istek).
  **Kabul:** Telefondaki düzenleme TV'de anlık/`revision` uyuşmazlığında temiz çakışma
  mesajıyla yansır; ana liste sıralaması yalnız Room'a yazılır (TIF veritabanına
  asla); çevrimdışı yapılan düzenlemeler kuyrukta tutulup bağlantı gelince uygulanır.

- [ ] **REMOTEEDIT-005 (P2): Değişiklik bildirimi ve canlı yenileme**
  Sunucuda işlem sonrası `revision` artışı; client kısa periyotlu uzun sorgulama
  (long-poll) ile değişikliği alır (v1'de push yok). TV tarafı listesi Room akışıyla
  zaten canlı güncellenir.
  **Kabul:** TV'de ve telefonda aynı satır `revision`'da buluşur; ağ kesilmesi
  durumunda her iki taraf da tutarlı son duruma döner.

- [ ] **REMOTEEDIT-006 (P2): IPTV kaynak/liter yönetimi (kısıtlı)**
  Telefondan yeni IPTV listesi ekleme/güncelleme isteği sunucuda kuyruğa alınır ve
  TV uygulaması mevcut import akışıyla (IptvRepository) uygular; kimlik bilgileri
  telefonda girilir, sunucuya aktarımda şifreli alan kullanılır ve loglanmaz.
  **Kabul:** Büyük katalog importu TV'de mevcut sayfalı akışla çalışır; telefondan
  kaynak silme iki aşamalı onay ister; kimlik bilgisi hiçbir yerde düz metin loglanmaz.

- [ ] **REMOTEEDIT-007 (P3): Testler, güvenlik sertleştirme ve belgeler**
  Sahte sunucu/client ile entegrasyon testleri, çakışma senaryoları, Kılavuz ve
  README güncellemeleri, paid flavor dahil davranışın belgelenmesi.
  **Kabul:** Hem `local` hem `paid` paketinde derlenir; ödeme duvarı politikasına
  göre özellik bayrağıyla açılır/kapanır; Kılavuz + README + CHANGELOG güncel.

### Sprint sırası
1. Sprint R1: `REMOTEEDIT-001` + `REMOTEEDIT-002` (sunucu + eşleştirme)
2. Sprint R2: `REMOTEEDIT-003` (telefonda okuma)
3. Sprint R3: `REMOTEEDIT-004` + `REMOTEEDIT-005` (yazma + canlı)
4. Sprint R4: `REMOTEEDIT-006` (kaynak yönetimi)
5. Sprint R5: `REMOTEEDIT-007` (sertleştirme + dokümantasyon)

### Riskler ve açık kararlar
- Arka planda sunucunun dayanıklılığı: foreground service + pil istisnası istenip
  istenmeyeceği kullanıcı kararıyla netleşmeli (uygulama açıkken sınırlı kalmak da
  yeterli olabilir).
- Sunucu bileşeni için bağımlılık seçimi (Ktor vs NanoHTTPD): APK boyutu etkisi
  ölçülmeli; mevcut OkHttp ailesiyle uyumlu minimal çözüm tercih edilir.
- Uzaktan (LAN dışı) erişim v1'de kapsamda **değildir**; gerekirse ileride ayrı
  güvenlik değerlendirmesiyle ele alınır.
