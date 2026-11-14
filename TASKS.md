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

### Birlikte uygulanacak kapsam

- Tek kaynak, kategori, Sinema/Dizi, arama, favoriler, devam edilenler ve son izlenenler.
- Kaynak bazında kalıcı filtre ve sıralama; sınırlı katalog sayfaları, önceki/sonraki sayfa,
  oynatmadan dönüşte odak ve konumun korunması. Tam katalog UI belleğinde tutulmaz.
- İsimli son içerik alanı, iki satırlık kart başlıkları, mevcut Coil disk/bellek cache'i,
  gerçek metadata ile kaydırılabilir detay ve Devam et/Baştan başlat/Favori işlemleri.
- Kırmızı Canlı TV, Yeşil son içeriğe devam, Sarı kaynak, Mavi arama/filtreler.
- Xtream dizi kataloğu ve sezon/bölüm bilgisi istek üzerine cache'lenir. M3U'da gerçek
  sezon bilgisi yoksa bölüm hiyerarşisi uydurulmaz. Sonraki bölüm manuel seçilir;
  otomatik oynatma bu kapsamda yoktur.
- Room migration kullanıcı verilerini korur. DAO, migration, parser ve kumanda testleri;
  gerçek TV ve özel sağlayıcı üzerinde ayrıca kabul testi.

**Uygulama durumu:** Bu kapsam birlikte kodlandı. 15.000 kayıtlı DAO, migration,
Xtream yanıt ayrıştırma, klavye geometrisi ve dolu katalog/detail gezinmesi için beş
emülatör testi geçti. Local/Paid birim testleri ve debug derlemeleri başarılı.
Fiziksel TV, düşük güçlü stick ve gerçek sağlayıcı kabulü tamamlanmış sayılmaz.

Canlı TV ve sade VOD ayrı kullanım modlarıdır; büyük Netflix benzeri keşif ekranı kurulmayacak.

- [x] **VODMODE-001 (P0): Canlı TV / VOD ayrımı**
  Açık mod seçimi, ayrı son kanal/film ve geçmiş, VOD'da canlı kanal gezinmesini engelleme,
  OK/MENU ile filmi duraklatıp kütüphaneye dönüş ve son filme devam uygulandı.
  Kumanda yönlendirmesi ve içerik türü testleri eklendi. Gerçek TV odak testi bekliyor.

- [~] **VODUI-001 (P0): Arama ve yükleme yaşam döngüsü**
  VodHomeActivity.loadPage eski işi iptal eder; sorgular kaynak/filtre/sayfa değerlerini
  yerel kopyada taşır. Geç gelen sonuç generation kontrolünü geçmeden görünümü değiştiremez.
  Hatalar boş katalog gibi gösterilmez; yeniden dene eylemi vardır.
  **Kabul:** Yükleme sürerken art arda arama, boş sonuç ve ağ/veritabanı hatası ekranı kilitlemez.
  **Durum (2026-10-02):** İş iptali, yerel sorgu imleçleri, adapter tamamlanma kontrolü,
  yükleme göstergesi ve yeniden deneme uygulandı. Arama klavyesi katalog ölçülerini değiştirmez.
  Verili büyük katalog ve hata enjeksiyonu ile kabul doğrulaması ayrıca yapılmalı.
- [x] **VODUI-002 (P1): Tek kaynak ve kategori seçimi**
  Üstte kayıtlı IPTV listesi, ardından kategori; son seçilen kaynak/kategori hatırlanır.
  Binlerce film farklı kaynaklardan tek görünümde karıştırılmaz. Mavi arama/filtre, sarı liste dialogu;
  arama alanı başlangıç odağını ve yazılım klavyesini kendiliğinden açmaz.
- [x] **VODUI-003 (P1): Sade katalog ve detay**
  TV için 4-5, telefonda 2-3 poster sütunu; mevcut Coil cache ve kaynak görselleri kullanılır.
  Görsel yoksa gerçek başlıkla sade kart; sahte poster, puan, yıl veya açıklama üretilmez.
  Başlık iki satır, belirgin odak ve konum göstergesi. OK detay OSD; Oynat/Devam et/Baştan başlat.
- [~] **VODUI-004 (P1): Bounded sayfalama ve odak**
  Bütün gezilmiş kataloğu biriktirip tekrar diff etmek yerine 60 kayıtlık DAO sayfaları.
  Birleşik film/dizi sorgusunda filtre ve sıralama veritabanında; sayfa atlama LIMIT/OFFSET ile.
  Derin sayfa performansı fiziksel düşük güçlü cihazda ayrıca ölçülmeli.
  Devam Et en fazla mevcut bounded geçmiş kadar; kaynak filtresine uyar. Back önce detayı kapatır,
  sonra filtreyi/ekranı terk eder. Oynatmadan dönüşte aynı karta ve kaydırma konumuna dönülür.
- [~] **VODUI-005 (P2): Bölüm görünümü ve doğrulama**
  Xtream gerçek sezon/bölüm bilgisi sağlıyorsa sade sezon/bölüm seçimi; düz M3U başlıklarından
  tahmin edilen metadata gerçek bilgi gibi sunulmaz. Sonraki bölüm isteğe bağlıdır.
  **Kabul:** 15.000 kayıt, eksik görsel/metadata, kumanda ve mobil dokunma test matrisi.
  **Durum:** Xtream sezon/bölüm sorgusu ve isteğe bağlı metadata önbelleği uygulandı.
  Otomatik sonraki bölüm ertelidir; gerçek sağlayıcı/TV ve mobil dokunma matrisi ayrıca doğrulanmalı.

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

## 2026-11-13 proje denetimi — görev defteri

Kaynak: `PROJECT_AUDIT_2026_11_13.md` (4 paralel salt-okunur kod denetimi + saha çökme
logları). **ID eşlemesi:** rapordaki `PERF-001..019` bu defterdeki mevcut `PERF-001..005`
kimlikleriyle çakıştığı için deftere **`PERF-006..024`** olarak taşındı (defter = rapor + 5).
`CRASH-*`, `VOD-*`, `UI-*`, `MISS-*` aileleri rapordaki numaralarıyla aynıdır.
Kod değişikliklerinde olduğu gibi her görev kendi testleriyle gönderilir; hiçbir maddesi
Android 11 Google TV cihazında doğrulanmadan **tamamlandı sayılmaz**.

### Epic CRASH — Saha çökmeleri (Expressluck Apollo Premium4K, Android 11 / API 30)

- [ ] **CRASH-001 (P0): WebView ve CookieManager çağrılarını tek iş parçacığına topla**
  `SniffingWebViewClient.shouldInterceptRequest` arka plan thread'inde çalışır; içindeki
  `userAgent` lambda'sı `webView.settings`'e dokunduğu için `WebView.checkThread()` çöküyordu.
  `main` bu kısmı `cachedUserAgent` ile kapattı, ancak `browser/StreamSniffer.kt:62` ve `:75`
  ile `vod/WebVideoResolver.kt:309` hâlâ arka plan thread'inden `CookieManager.getInstance()`
  çağırıyor. Kullanıcı ajanını ve cookie'yi çözümleme başında ana thread'de önbelleğe al,
  sniff callback'i yalnız bu önbelleği okusun.
  **Kabul:** Sniffer yolu boyunca hiçbir WebView/CookieManager erişimi arka plan thread'inde
  olmaz; `refactor/main-activity-split` dalı da düzeltmeyi içerir; aday yakalama davranışı
  değişmez ve cihazda VOD açılışı çökmez.

- [ ] **CRASH-002 (P0): MainActivity karşılıklı çağrı StackOverflowError'u**
  Saha logunda `MainActivity.n1 ↔ u1` ikilisi `setVisibility` üzerinde yüz binlerce frame
  üretmiş. R8 eşleme dosyası olmadığı için hangi iki metodun olduğu **kesin bilinmiyor**
  (bkz. `MISS-005` — bu görev onun ön koşuluna bağlı). 217 metod / 614 kenar analizinde
  visibility değiştiren aday döngüler: `hideChannelPanel ↔ scheduleChannelPanelClose`,
  `hideChromeForPictureInPicture ↔ restoreChromeAfterPictureInPicture`,
  `updateInfoColorActions ↔ action`, `buildNormalPanelChannels ↔ applyChannelFilter`,
  `updateDiagnosticsOverlay ↔ startDiagnosticsObservation`.
  Kalıcı çözüm olarak görünürlük geçişlerini `OsdCoordinator` üzerinden yeniden-giriş
  korumalı tek noktaya indir ve döngü üretmeyen bir kumanda/OSD testi ekle.
  **Kabul:** Eşleme çözüldükten sonra iki metod adı belgelenir; yeniden-giriş bayrağı veya
  durum makinesi ile döngü yapısal olarak imkânsız hâle gelir; OSD aç/kapa davranışı
  değişmez; cihazda uzun oturumda çökme yeniden üretilemez.

- [~] **CRASH-003 (P0): Çökme izi ve tam gövde okumalarında bellek sınırı**
  127 MB'lık tek ayırma çökmesi (`StringWriter.write`). `diagnostics/CrashReportStore.kt:98`
  çökme izini tamamen bellekte String'e kuruyor; CRASH-002'nin 100k frame'lik izi burada
  patlamış olabilir. İzi boyut sınırlı (örn. 200 KB, kesildiği satır not düşülerek) ve
  doğrudan dosyaya akıtarak yaz. İkincil adaylar tüm gövdeyi String'e okuyan ağ yolları:
  `data/IptvRepository.kt:479`, `data/XtreamClient.kt:330`, `data/StalkerClient.kt:160`,
  `data/XmlTvRepository.kt:747` (metin + parse ağacı aynı anda bellekte).
  **Durum (2026-11-14, yapılanlar):**
  - `CrashReportStore`: iz artık `LimitedCharWriter` ile **200.000 karakterle sınırlı** kurulur;
    sınır aşılınca kalan kısım biriktirilmez, izde kaç karakter/satır atlandığı ve sınır
    not olarak yazılır. Rapor dosyaya eskisi gibi tam (sınırlı) hâliyle gider.
  - Tam rapor artık **SharedPreferences'e yazılmıyor**: `KEY_PENDING_REPORT` en fazla
    16.000 karakterlik özet + tam raporun dosya yolu tutuyor (`pendingExcerpt`). Büyük iz,
    `commit()` ile senkron yazılan tercih dosyasının neden olduğu ikinci bellek/ANR riskini
    üretmez. `pendingReport()` sözcüğü değişmedi (özet döner), çağıranı şu an yok.
  - Yeni `data/HttpBodyReader.kt`: `readLimitedText(maximumBytes, label)` sınırı aşan gövdeyi
    **kırpıp döndürmez**, adlandırılmış hata fırlatır ve sınırı geçince okumayı bırakır.
    Uygulandığı yerler:
    - `XtreamClient.requestText` — hesap/handshake (4 MB)
    - `StalkerClient.request` — handshake/get_profile/get_genres/create_link (4 MB)
    - `TmdbApiClient.fetchJson` — TMDB API yanıtı (4 MB; zaten `null` dönen yutulmuş hata yolu)
    - `GithubManifestRepository.downloadText` — kullanıcı kaynak manifesti (16 MB)
    - `WebVideoResolver.fetchHtml` — üçüncü taraf VOD sayfası (16 MB; sınır aşılırsa aday
      çözülemez sayılır, `fetchHtml` eskisi gibi `null` döner)
    - `AppUpdateManager.readUrl` — 64 KB'lık mevcut sınır artık **okuma sırasında**
      uygulanıyor; önceden gövde tamamen String'e çevrilip sonra ölçülüyordu
    Sınır sabitleri `IptvWebServer.MAX_BODY_BYTES` (16 MB) ile uyumlu seçildi.
  - Birim testler: `HttpBodyReaderTest` (sınır ihlali, kırpma yok, çok baytlı UTF-8, sınırda
    tam okuma, sınır sonrası okumaya devam etmeme) ve `LimitedCharWriterTest` (kısa çıktı
    bozulmaz, 50k satır sınırda kalır, satır sınırında kesim + atlanan miktar, gerçek
    `StackOverflowError` izi sınırda kalır).
  **Doğrulanan ayrıntı:** denetimdeki "dört ağ yolu" listesinin ikisi zaten akışarak
  okunuyordu — M3U `M3uParser.sequence` ile satır satır, XMLTV `XmlPullParser` ile, Xtream ve
  Stalker **kanal listeleri** `JsonReader` ile. Bu yollarda sınır uygulanmadı (15.000+ kayıt
  kesintiye uğramasın).
  **Kalan iş (bilinçli erteleme):** (1) JSON VOD içe aktarma (`IptvRepository.kt:498` →
  `JsonVodImporter.importJsonVod` → `JSONArray(text)`) tüm metni + ayrıştırma ağacını birlikte
  tutar; kalıcı çözüm içe aktarıcıyı `JsonReader` tabanlı akışıcı yapmak, sınırla kırpmak
  doğru değil. (2) `XmlTvRepository.kt:747` eski JSON önbellek dosyası aynı şekilde
  `JSONArray(readText())` kullanır. (3) 15.000+ kayıtlı M3U/JSON/XMLTV yüklemede tepe bellek
  ölçümü cihazda bekliyor.
  **Kabul:** Sınırsız boyutta yanıt/istisna bellekte String'e biriktirilmez; çökme raporu
  yazılmaya devam eder ve asla ikinci bir çökme üretmez (testlendi); tepe bellek düşüşü
  cihazda ölçüldüğünde madde kapanır.

### Epic MISS — Denetimde açığa çıkan eksikler

- [ ] **MISS-001 (P0): paid flavor'da IPTV erişim kapısı kapalı**
  **Durum (2026-11-14):** kullanıcı kararıyla **en sona alındı** — stabilite ve arayüz
  görevleri bitirilemeden başlatılmayacak. Bulgular geçerliliğini koruyor.
  `app/build.gradle.kts:66, 76, 87` — `IPTV_PRO_REQUIRED = false` **`paid` dahil her flavor'da**.
  `billing/IptvEntitlementManager.kt:90-92` bu durumda `FREE_BUILD / accessGranted = true`
  döndürüyor, yani production paid derlemesinde ücretli IPTV tamamen açık ve deneme/satın alma
  yolu hiç çalışmıyor. `billing/IptvAccessDialogs.kt:35` "Satın al" butonu yalnız
  `billing_not_connected` Toast'u gösteren bir yer tutucu. Gerçek satın alma altyapısı
  `STORE-001` kapsamındadır; bu görev önce **politikayı netleştirir**: paid derlemede
  `IPTV_PRO_REQUIRED = true` olur ve erişim ancak `PURCHASED` ile açılır, ya da ücretsiz
  yayın bilinçli bir karar olarak README/MARKET_LISTING'e yazılır.
  **Kabul:** Hiçbir production varyantında "paralı ama kapısız" durum kalmaz; debug simülatör
  release paketinde bulunmaz; yer tutucu buton ya çalışır ya da arayüzden kaldırılır;
  `IptvEntitlementPolicy` karar tablosu her flavor için birim testli.

- [ ] **MISS-002 (P1): VOD çözümleme katmanına birim test (AUDIT-000 regresyonunun nedeni)**
  `VodHostTest`, `VodLibraryResolverTest`, `VodUiSupportTest` ve `VodTitleCleanerTest` var;
  **olmayanlar:** `VodUrlClassifier` (68 satır, saf JVM — son iki düzeltme bunu değiştirdi ama
  regresyon testi yok), `WebVideoResolver` (713 satır), `VodMetadataExtractor` (216 satır),
  `IptvPlaybackController` (1276 satır), `IptvResumeStore`, `MultiViewController`.
  En azından saf JVM'de test edilebilir olanları kapsa: URL sınıflandırma (webpage yolu doğrudan
  medya sayılmayacak), `isNetworkMediaCandidate`, `isSegmentLike`, `rankHeadlessCandidates`,
  `extractStreamFromPage` `html = null` girdisi (1 Kasım'da kaçan NPE), `VodMetadataExtractor`
  JSON-LD/OpenGraph alanları.
  **Kabul:** `f1fb402` tipinde bir gerileme derleme geçse bile testte yakalanır; mock web sunucusu
  veya sabit HTML fikstürleriyle test çevrimdışı çalışır; üretim davranışı değişmez.

- [ ] **MISS-003 (P2): Room migration sözleşme testlerini genişlet**
  Zincir eksiksiz ve `fallbackToDestructiveMigration` hiç kullanılmıyor (uyumlu), fakat tek
  migration testi var: `Migration25To24ContractTest.kt`. `MIGRATION_31_32`, `30_31`, `24_26` ve
  yeni eklenecekler test-siz. **Dikkat:** `main` dalında v33 (`alternativeStreamsJson`) var,
  `refactor/main-activity-split` v32'de; birleştirme sırasında numara sırası bozulursa kullanıcı
  verisi tehlikeye girer (`REFACTOR-001`).
  **Kabul:** Son üç migration dahil her geçiş için tablo/kolon/kayıt koruması doğrulanır;
  şema JSON'ları ile versiyon numarası tutarlılığı bir testte kontrol edilir.

- [x] **MISS-004 (P1): `.gitignore` ikili ve playlist sızıntısına kapalı olmalı**
  `.log/`, `*.log.txt`, `*.jks`, `*.keystore`, `keystore.properties`, `.env` doğru korunmuş.
  Ancak `.doc/` (içinde iki APK ve üç playlist var), `.clinerules/` ve `.freebuff/` alt
  içeriği ignore edilmemiş ve untracked duruyor — `git add .` ile repoya girerler.
  **Durum (2026-11-14):** `.doc/`, `.clinerules/`, `.freebuff/` ve Gradle'ın ürettiği
  `.kotlin/` eklendi. `git ls-files -i -c --exclude-standard` boş (hiçbir izlenen dosya
  yanlışlıkla kapanmıyor); `git status --short` bu dizinleri artık göstermiyor.
  **Kabul:** `git status --short` temiz çalıştırıldığında korumasız dizin kalmaz; imzalama
  malzemesi, APK ve playlist hiçbir derlemede stage edilmez.

- [~] **MISS-005 (P0): Release R8 mapping arşivi**
  `isMinifyEnabled = true` (`app/build.gradle.kts:122`) fakat `mapping.txt` hiçbir yere
  kopyalanıp saklanmıyor. Bu yüzden saha loglarındaki `n1`, `u1`, `q2.m` gibi adlar
  çözülemiyor; CRASH-002 ve CRASH-003 bugün **teşhis edilemez** durumda. Release işinde
  mapping'i versiyon etiketiyle eşleşen bir CI artifact'i olarak arşivle (repoya commit etmeden).
  **Durum (2026-11-14):** `release.yml` içine "Archive R8 mapping" + "Upload R8 mapping
  artefact" adımları eklendi; `app/build/outputs/mapping/<varyant>/mapping.txt` dosyaları
  `r8-mapping-dev-r<run>-build<build>` artifact'inde 30 gün saklanıyor (varyant başına ~33 MB
  ölçüldüğü için kota riski nedeniyle kısa tutuldu). Depo **herkese açık** olduğundan mapping
  release asset'i olarak **değil**, yalnızca depo erişimi olanların indirebildiği artifact olarak
  tutuluyor. Mapping bulunamazsa `::warning::` anotü düşer, release kilitlenmez.
  Yerel doğrulama: `assembleLocalRelease bundlePaidRelease` →
  `mapping/localRelease/mapping.txt` ve `mapping/paidRelease/mapping.txt` üretildi (CI kalıbı
  birebir çalışıyor); `retrace` ile `M1(Unknown Source:907)` →
  `updateTechnicalBadges(MainActivity.kt:3190)`, `N(:18)` → `endBlackout(:1382)`,
  `O(:145)` → `ensurePermissionAndLoad(:1023)` çözüldü. Retrace'in adı çözmesi için girdide
  APK'daki **kırpılmış** satır numarası olmalı; README'de belgelendi.
  **Kabul:** Her release APK'sı için doğru sürüm numarasıyla bir mapping dosyası erişilebilir
  (ilk release işinin artifact'i görüldüğünde kapanır); bir saha logundaki kırpılmış yığın
  `retrace` ile okunabilir hâle getirilir (yerelde kanıtlandı); dosya git'e girmez
  (`mapping-archive/` ayrıca `.gitignore`'a eklendi).
  **Not:** Bugüne kadarki sürümlerin mapping'i üretilmedi; CRASH-002'nin eski izi geri
  çözülemez. Mevcut `localRelease` mapping'inde `MainActivity` içinde `n1`/`u1` adında yöntem
  olmadığı doğrulandı — eski iz ile bugünkü derleme arasında ad eşleşmesi yok.

- [ ] **MISS-006 (P3): Ölü bayrak, import ve kaynak temizliği**
  `build.gradle.kts:65, 75, 87` `STORE_BILLING_ENABLED` hiçbir Kotlin kodunda okunmuyor;
  `MainActivity.kt:75` kullanılmayan `main.LongPressKeyGate` import'u; `res/layout/dialog_vod_detail.xml`
  ve `dialog_vod_filters.xml` hiçbir yerden inflate edilmiyor (`androidTest/VodHomeLayoutTest.kt:135,139`
  `vod_detail_scroll` kimliğine bağımlı, test önce güncellenmeli); `vod/VodRightPanelAdapters.kt`
  içindeki `VodSourceAdapter` ve `VodHomeActivity.history` ölü. Koda gömülü "Faz 1/2/3/4/5"
  refaz etiketleri kalıcı terminolojiye çevrilmeli.
  **Kabul:** Derlemeye girmeyen bayrak ve kod kalmaz; silinen her dosyanın yerine bir gerekçe
  veya test güncellemesi bırakılır.

### Epic VOD — Çözümleme ve oynatma sağlamlığı

- [ ] **VOD-001 (P0): Aday bulunamadığında net hata ve Yeniden dene**
  `main/VodHost.kt:82` adaysız durumda orijinal sayfa URL'sini sessizce oynatıcıya veriyor;
  kullanıcı `PARSING_CONTAINER_UNSUPPORTED` veya 403 görüyor ve bu yolda `statusRetryAction`
  hiç kurulmadığı için (`MainActivity.kt:1639-1657` yalnız `showPlaybackError` kurar)
  **Yeniden dene seçeneği sunulmuyor**. `WebVideoResolver.kt:160` `isPlayable = false` üretiyor
  ama tek bir tüketicisi bile yok; `VodPageResolver.kt:16`'nın belgelediği tarayıcı/tiyatro
  geri dönüşü de yok.
  **Kabul:** Çözülemeyen içerikte iki dilli net mesaj + OK ile tetiklenen Yeniden dene ve
  VOD kütüphanesine dön eylemi vardır; ham HTML oynatıcıya gitmez veya yalnız açık bir
  "doğrudan dene" adımı olarak gider; `isPlayable` gerçek bir tüketici tarafından okunur.

- [ ] **VOD-002 (P1): Header, referer ve cookie taşımada üç kayıp**
  (1) `MainVodExtras.EXTRA_VOD_REFERRER/ORIGIN/USER_AGENT/COOKIE` tanımlı ve okunuyor
  (`VodHost.kt:379-382, 418-421, 269-274`) ama `VodHomeActivity` bu dördünü **hiç doldurmuyor**
  (`:619-627, 643-651, 687-694`). (2) `VodLibraryResolver.buildChannel` (`:98-114`) başlık
  taşımıyor. (3) `VodPageResolver.applyTo` (`:34-38`) `base.userAgent ?: chosen.userAgent`
  önceliğiyle **sniffer'ın ürettiği UA/referer/cookie'yi siliyor** — tam da token korumalı CDN
  için gereken cookie düşüyor.
  **Kabul:** Sniffer kaynaklı başlıklar DB kanalının varsayılanlarını ancak gerçekten boşsa
  bastırır; kütüphane ve ana ekran yolları da aynı başlık kümesini taşır; kimlik bilgisi
  loglanmaz; cihazda token'lı bir kaynakta oynatma doğrulanır.

- [ ] **VOD-003 (P1): Çözümleme için küresel zaman bütçesi**
  `fetchHtml` istek başına ~14 sn (`WebVideoResolver.kt:673-674`), `extractStreamFromPage`
  adım 5 iframe listesi sınırsız (`:652-659`) ve aynı embed sayfaları iki ayrı dolaşım tarafından
  **iki kez** indiriliyor (`:231` vs `:242-249`). Aday yokken ~140 ardışık istek ve teorik
  ~30+ dk bekleme mümkün; ekranda yalnız "hazırlanıyor" var. Mp4-only sitelerde
  `HEADLESS_TIMEOUT_MS = 10_000` her seferinde tam 10 sn. Kütüphane/anahtar yolunda
  `context = null` geçtiği için sniffer **hiç çalışmıyor** (`VodHost.kt:318, 363`) — aynı
  içerik derin bağlantıda oynayıp kütüphanede oynamayabilir.
  **Kabul:** Toplam çözümleme süresi üst sınırının (örn. 45 sn) sonunda kullanıcıya durum
  gösterilir; aynı URL bir çözümleme içinde iki kez indirilmez; iframe sayısı sınırlıdır;
  kütüphane yolu ile derin bağlantı yolu aynı çözünülebilirlik davranışını gösterir.

- [ ] **VOD-004 (P1): Ağ ve WebView güvenlik sertleştirmesi**
  Trust-all SSL + daima-true `hostnameVerifier` hem statik çözümlemede
  (`WebVideoResolver.kt:682-685, 701-712`) hem **tüm oynatma veri yolunda**
  (`IptvDataSourceFactory.kt:75-96`); `onReceivedSslError → proceed()` (`:450-456`);
  `MIXED_CONTENT_ALWAYS_ALLOW` + `allowFileAccess` (`:293-301`); global cleartext
  (`AndroidManifest.xml:31`); hard-coded TMDB anahtarı kaynak kodunda ve URL sorgusunda
  (`TmdbApiClient.kt:26, 66, 78`); hard-coded yedek host `play2.pilavyerplay.top`
  (`WebVideoResolver.kt:589-596`). En somut açık: sniffing kanalı
  `console.log('net_stream_sniff:'+url)` (`:336-339, 493-501`) üzerinden sayfadaki betiğin
  aday listesine **istediği URL'yi enjekte edebilmesi** — ardından o URL'ye bizim UA + cookie'mizle
  istek atıyoruz.
  **Kabul:** TMDB anahtarı derleme/secret konfigürasyonuna taşınır; SSL toleransı açıkça
  listelenmiş host'larla sınırlanır veya kullanıcı onayına bağlanır; sniffing kanalı origin
  doğrulaması yapar veya kapatılır; risk kabul edilecekse AGENTS/README'de gerekçesi yazılır.

- [ ] **VOD-005 (P1): Bölüm modelinde üç parser kopyası ve kırılgan `:ep:` anahtarı**
  Bölüm listesi bir yanda Room `vod_metadata` (`parentKey`), diğer yanda `SERIES.streamUrl`
  içine gömülü JSON string (`JsonVodImporter.kt:53-73` yalnız üst kayıt yazıyor). Üç ayrık
  çözümleyici var: `VodUiSupport.parseEpisodesJson` (`:45-85`), `VodHost.showEpisodePicker`
  JSON dalı (`:154-183`), `VodHomeActivity.showEpisodesDialog` satır içi parser (`:564-610`);
  başlık biçimleri farklı (`"Bolum"` vs `"Bölüm"`). `VodHost.kt:158-160` aktif bölümü
  `title ==` ile aradığı için vurgulama **hep 0. bölüme** düşüyor. `:ep:<index>_<hash>`
  anahtarında hash doğrulanmıyor (`VodLibraryResolver.kt:36-39`) — kaynak sırası değişirse aynı
  anahtar **farklı bölümü** oynatır. `buildLibraryChannel` kanal listesi dalında
  `setVodHasEpisodes` hiç çağrılmıyor (`VodHost.kt:306-321`), LIST düğmesi bayat kalabiliyor.
  **Kabul:** Tek bir bölüm çözümleyici tüm yollar tarafından kullanılır; aktif bölüm doğru
  vurgulanır; hash doğrulanmayan anahtarda eski kayıt sessizce yanlış bölümü oynatmaz;
  LIST düğmesi gerçek bölüm durumunu yansıtır; her kural birim testli.

- [ ] **VOD-006 (P2): Bölüm seçici iş iptali ve sayaç paylaşımı**
  Durum makinesi genelinde generation/`cancelResolve`/`tuneGeneration` korumaları çalışıyor;
  kalan boşluklar: `episodeJob` **hiç iptal edilmiyor** (`VodHost.kt:37-40` yalnız `resolveJob`)
  ve callback'i mod değişse de `playVodKey` tetikleyebiliyor (`:107-186`); `selectChannel`
  sayacı ikinci kez artırıyor (`MainActivity.kt:1151`); `handlePayload` aynı anahtarın hazır
  olduğu dalda da sayaç artırıyor (`VodHost.kt:207, 219-224`).
  **Kabul:** Mod/kanal değişimi bekleyen tüm VOD işlerini iptal eder; bekleyen her iş tek bir
  iş sahibine kayıtlıdır ve yalnız o sahip tarafından iptal edilir; eski bir bölüm seçimi
  yeni içeriği oynatamaz.

- [ ] **VOD-007 (P1): Devam Et rayı ve izleme ilerlemesi**
  Oynatma tarafı doğru (30 sn altı kaydedilmez, okumada 5 sn geri sarılır, son 60 sn "bitti"),
  fakat ray `VodCatalogQuery.resumeItems` içinde `sourceKey IN (...)` ile
  `iptv_channels ∪ vod_metadata` arıyor (`VodHomeActivity.kt:289-315`, `VodCatalogQuery.kt:41-45`).
  JSON dizilerinin dinamik `...:ep:3_<hash>` anahtarlarının `vod_metadata`'da satırı olmadığı
  için **yarım kalan JSON bölümleri rayda hiç görünmez**. `position/duration` tutuluyor ama
  `VodPosterAdapter.bind` (`:80-106`) hiçbir ilerleme çizmiyor.
  **Kabul:** Yarım kalan JSON dizisi bölümü rayda görünür ve kaldığı yerden açılır; poster veya
  kart üzerinde gerçek ilerleme oranı gösterilir; biten içerik raydan düşer.

- [ ] **VOD-008 (P2): VOD katmanında altyazı, kalite ve otomatik sonraki bölüm**
  `AUDIO_SUB` düğmesi yalnız sesi açıyor (`MainActivity.kt:2374`); `showSubtitleTracks` yalnız
  canlı IPTV kontrol çubuğunda (`:5901-5903`) ve o çubuk VOD'da gizli (`:5150-5152`) —
  **VOD'da altyazı seçilemiyor**, yalnız otomatik tercih uygulanıyor (`:3218-3238`).
  Kalite/video seçimi VOD katmanında yok (`VodPlayerOverlayController.kt:236-246`).
  `onPlaybackEnded` yalnız resume temizliyor (`MainActivity.kt:729-737`); manuel "sonraki bölüm"
  `VodDao.nextEpisode` sorgusu `kind='EPISODE'` aradığı için JSON dizilerinde hiç çalışmıyor
  (`VodDao.kt:16-21`). Timeline üzerinde sürükleyerek ileri/geri alma yok, yalnız ±10/±30 sn.
  **Kabul:** VOD oynatmada altyazı ve ses parçası kumandayla seçilebilir; sonraki bölüm
  odağa göre önerilir veya otomatik geçer (tercihe bağlı, kapalı varsayılan olabilir) ve JSON
  dizilerinde de çalışır; tüm yeni eylemler iki dilde ve `Back` sözleşmesine uygun.

- [ ] **VOD-009 (P3): Çözümleyicide ölü kod**
  `WebVideoResolver.resolveStream` / `resolve` (`:118-165`) üretimde hiç çağrılmıyor
  (AUDIT-000 tam bu ölü yolda yaşıyordu), `VodHost.classifyDeepLink` (`:52-63`) kullanılmıyor.
  **Kabul:** Ya gerçek bir çağıranı ve testi olur ya da silinir; ölü yol üzerinde düzeltme
  yapılmaz.

### Epic UI — Odak, boş durum ve 10-foot okunabilirlik

`UIINPUT-004` (ortak TV görsel bileşenleri) tamamlandı; ancak aşağıdaki satırlar o ortak
bileşenleri hâlâ kullanmıyor.

- [~] **UI-001 (P0): IPTV ve VOD katmanlarında sistem odağı görünmüyor**
  İlk inceleme bulgusu ("odak göstergesi hiç yok, bu ekranlar kumandayla kullanılamaz")
  **doğru değildi**: bu iki katmanda uygulama ayrı bir **seçim imleci** modeli kullanıyor ve
  seçili öğe kod tarafında boyanıyor — `MainActivity.renderIptvControlSelection`
  (`:2863-2903`) `bg_iptv_control_selected`, `VodPlayerOverlayController.renderSelection`
  (`:317-347`) `bg_vod_button_selected` veriyor; IPTV düğmelerinin odaklanabilirliği
  `iptvControlsInteractive` ile kapılı (`MainActivity.kt:2919-2924`). D-pad akışında
  geri bildirim zaten var.
  Gerçek açık: tema `defaultFocusHighlightEnabled = false` olduğu için (`styles.xml:12, 16,
  35, 46`) **imlekten bağımsız sistem odağı** (çerçeve odak araması, fare/dokunma, ilk
  yerleşim odağı) hiçbir iz bırakmıyordu; `bg_iptv_control` tamamen şeffaftı ve
  `vod_timeline_row` seçili değilken `background = null` alıyordu.
  **Durum (2026-11-14):** `bg_iptv_control` ve `bg_vod_button` seçiciye çevrildi,
  `state_focused` dalı eklendi; yeni `bg_vod_timeline.xml` seçicisi `vod_timeline_row`
  üzerindeki `background = null` yolunun yerine geçti. Seçim imleci modeli ve `*_selected`
  drawable'ları değiştirilmedi. Cihaz ve 720p/1080p/4K ekran görüntüsü doğrulaması bekliyor.
  **Kabul:** Sistem odağı her iki katmanda görünür; seçili öğe ile yalnız odaklı öğe ayırt
  edilebilir (seçili: camgöbeği dolgu + accent kenarlık; yalnız odaklı: accent kenarlık);
  odak davranışı ve renk tuş sözleşmeleri değişmez.
  **İzlenen kalan iş:** imleç ile sistem odağı tek doğruluk kaynağına indirilmeli (odak
  değişimi imleci de taşımalı), aksi hâlde iki gösterge ayrışabilir.

- [ ] **UI-002 (P1): VOD ana ekranında yükleme, hata ve yeniden dene durumu yok**
  `VodHomeActivity.kt:412-456` yüklerken ızgara boş kalıyor, ağ hatası yalnız `debugLog`'a
  düşüyor, ekranda hiçbir şey değişmiyor ve yeniden deneme yolu yok (`vod_grid_empty_state`
  altyapısı `activity_vod_home.xml:319`'de hazır). `VODUI-001` bu davranışı istiyor ama
  VOD ana ekranına tam yansımamış.
  **Kabul:** Yükleme göstergesi, iki dilli hata kartı ve OK ile tetiklenen yeniden dene vardır;
  hata boş katalogdan ayırt edilir; `VODUI-001` bu maddeyle birlikte kapatılır.

- [ ] **UI-003 (P1): Boş durum ekranları eksik**
  Ana kanal listesinde filtre/arama 0 sonuç verdiğinde panel bomboş kalıyor
  (`activity_main.xml` kanal listesi); `activity_iptv_sources.xml` ve `activity_xmltv_sources.xml`
  listelerinde de boş durum ve "nasıl eklenir" yönlendirmesi yok.
  **Kabul:** Her liste için iki dilli boş durum metni ve ilgili renk tuşu/ipucu yönlendirmesi;
  filtre temizleme tek eylemle mümkün.

- [ ] **UI-004 (P1): VOD ekranında sabit Türkçe metin ve emoji**
  `activity_vod_home.xml:31, 99, 137, 153, 162, 192, 209, 247, 274, 342-368` (🌐⚡📂🎬⏱️🔴),
  `VodHomeActivity.kt:545, 578-583, 636-639, 763, 770`, `VodRightPanelAdapters.kt:47` ve
  `TmdbApiClient.kt` tür adları. `values` ile `values-en` paritesi şu an tam (905 = 905);
  bu metinler kaynak dışında olduğu için EN dilinde de Türkçe kalıyor — AGENTS.md kuralı ihlali.
  **Kabul:** Kullanıcıya görünen her VOD metni iki dilde tanımlı; yer tutuculu biçim
  (`%1$d. Sezon`) kullanılır; emojiler sabit boyutlu drawable ikonlarıyla değiştirilir.

- [ ] **UI-005 (P1): 10-foot için okunmaz punto (7-9 sp)**
  Renk aksiyon ipuçları `MainActivity.kt:5037, 5079, 5123` (9f) ve ayraç `:5103` (11f);
  `item_channel.xml:71` kalite rozeti 8sp, `:126` kaynak rozeti 9sp; `activity_main.xml:685`
  tarih autoSize min 7sp; `activity_vod_home.xml:139` 9sp. İpuçlarının `technical_row` içinde
  spacer ile sağa hizalı olması kurala uygun — sorun yalnız boyut.
  **Kabul:** OSD'de minimum punto bir alt sınıra (örn. 12sp) bağlanır; TV mesafesinden
  okunabilirlik cihazda doğrulanır; rozetler satırı kaydırmaz.

- [ ] **UI-006 (P2): Oransal olmayan VOD ve Quick Hub ölçüleri**
  `activity_vod_home.xml` sabit 38dp üst bar, 175dp Devam Et rayı, 185dp sol panel;
  `dialog_quick_hub.xml` sabit 760dp genişlik ve ~35 sabit dp. `TvOverlayLayoutHelper` bu
  ekranları kapsamıyor, ~480dp yükseklikli 720p görünümde alt ipucu barı ile içerik çakışabiliyor.
  **Kabul:** Birincil geometri ekran oranına bağlanır; 720p/1080p/4K ekran görüntülerinde
  üst ve alt güvenli boşluklar korunur.

- [ ] **UI-007 (P2): OSD geçişlerinde animasyon yok**
  `MainActivity.kt:5140+` (`renderPlaybackUiState`) tüm katmanları ani GONE/VISIBLE ile
  değiştiriyor; tek istisna `ui/TvUiComponents.kt` `applyFocusRow`. AGENTS.md tek-birincil-OSD
  kuralı korunarak 120-150 ms alpha geçişi eklenebilir.
  **Kabul:** Geçişler kırpma hissi vermez; aynı anda iki birincil OSD görünmez; kumanda
  gecikmesi artmaz.

- [ ] **UI-008 (P2): Toast bağımlılığı ve ham hata metni**
  40+ Toast çağrısı (örn. `browser/TvBrowserActivity.kt:351, 386, 493, 717, 721, 732, 765, 905,
  1079, 1103`; `DisplaySettingsActivity.kt:307, 911, 967, 1006, 1022, 1031`;
  `playback/MultiViewController.kt:169, 185, 189`) TV'de odaklanamayan ve tekrar okunamayan
  geri bildirim üretiyor. `IptvChannelSelectionActivity.kt:322, 357, 456` ham `error.message`
  gösteriyor (teknik, İngilizce ve kimlik bilgisi parçası içerebilir).
  **Kabul:** Kalıcı/önemli mesajlar infobar benzeri OSD katmanına taşınır; ham hata yalnız
  `.log`'a yazılır, kullanıcıya iki dilli genel mesaj + yeniden dene gösterilir.

- [ ] **UI-009 (P2): Tarayıcıda kendi hata katmanı yok**
  `browser/TvBrowserActivity.kt` içinde `onReceivedError` yok; ağ hatasında Chromium'un varsayılan
  hata sayfası görünüyor ve kumandadan "yeniden yükle" köprüsü yok.
  **Kabul:** Kendi OSD hata katmanı gösterilir; OK ile yeniden yükleme, Back ile çıkış çalışır.

- [ ] **UI-010 (P2): VOD ipucu barı gerçek tuş sözleşmesiyle çelişiyor**
  `activity_vod_home.xml:332-383` Kırmızı'yı tekrarlıyor, Mavi (arama) ve Sarı (kaynak)
  ipuçlarını göstermiyor; `VodHomeActivity.kt:789-811` atamaları farklı ve kaynak yokken sarı
  tuş sessiz no-op (`:252`).
  **Kabul:** İpucu barı `dispatchKeyEvent` sözleşmesiyle birebir eşleşir; kullanılamaz eylem
  devre dışı görünür ve kısa iki dilli açıklama verir.

- [ ] **UI-011 (P2): Bölüm seçici düz AlertDialog**
  `VodHomeActivity.kt:636-639` `setItems` ile düz liste: sezon gruplaması, bölüm numarası ve
  izleme durumu göstergesi yok; uzun serilerde kumandayla gezinmek zor (`VODUI-005` ile bağlantılı,
  `VOD-005` parser işine bağımlı).
  **Kabul:** Sezon başlıklı, bölüm numaralı ve izlenen rozetli odaklanabilir liste; odak
  ilk başta aktif bölümde açılır.

- [ ] **UI-012 (P3): Layout'a gömülü teknik rozet metinleri**
  `activity_main.xml:903, 922, 966` içindeki "RADIO", "DOLBY", "TXT" string kaynağında değil;
  çeviri denetimi bu metinleri kaçırıyor.
  **Kabul:** Sabitler dahil her görünen metin iki dilde tanımlıdır.

### Epic PERF (denetim) — Çalışma zamanı performans bulguları

Aşağıdaki kimlikler `PROJECT_AUDIT_2026_11_13.md` §2 tablosunun `PERF-001..019` satırlarına
sırayla karşılık gelir (defter = rapor + 5). `PERF-001..005` ölçüm/altyapı işleri
tamamlandığı için bu bulgular onlara **yeni** işler olarak eklenmiştir.

- [ ] **PERF-006 (P1): VOD kataloğu her sorguda tam oluşturulup sıralanıyor**
  `data/VodCatalogQuery.kt:18-101` ve `data/VodRepository.kt:17-21`: CATALOG tüm
  `iptv_channels` VOD satırları ile `vod_metadata`'yı UNION ALL ile birleştiriyor ve her
  sayfa/sayaç/kategori sorgusu bu kümenin tamamını oluşturup dış seviyede ORDER BY ile
  sıralıyor; `name LIKE '%q%'` indeks kullanamıyor. 15.000+ kayıtlı katalogda her kaydırma
  tam tarama.
  **Kabul:** `EXPLAIN QUERY PLAN` ile sayfa başına maliyet ölçülür; sayfalama ve sayaçlar
  tüm katalogu oluşturmaz; 15.000 kayıtta kaydırma/jank cihazda önce/sonra ölçülür.

- [ ] **PERF-007 (P1): Kanal arama filtresi ana iş parçacığında tüm listeyi dönüştürüyor**
  `main/ChannelListRules.kt:23-33, 60-70` her tuş vuruşunda her kanal için
  `Normalizer(NFD)` + Regex + `copy()` çalıştırıyor. Yüzlerce kanalda arama kutusunda kare
  düşmesi ve geçici nesne yığını.
  **Kabul:** Normalizasyon ya önbelleğe alınır ya IO'ya alınır; ana thread'de tam liste
  dönüştürülmez; `UIALPHA-002` normalizasyon kuralları birebir korunur.

- [ ] **PERF-008 (P1): DiffUtil senkron hesaplanıyor**
  `ui/ChannelAdapter.kt:49-65` `DiffUtil.calculateDiff` çağrı iş parçacığında (proje genelinde
  `AsyncListDiffer` veya background differ hiç yok) + `channels.toList()` tam kopyası.
  PERF-007 ile aynı yolda zincirleniyor.
  **Kabul:** Fark arka planda hesaplanır ve ana thread'e dağıtılır; DiffUtil payload ve stable
  ID davranışı korunur; arama sırasında odak sıçramaz.

- [ ] **PERF-009 (P2): IPTV→TIF eşitleme OFFSET paging ve satır başına binder yazması**
  `data/local/IptvDao.kt:51-65` indekslenemeyen `ORDER BY COALESCE(u.sortOrder, …), s.name,
  c.originalIndex` ile LIMIT/OFFSET kullanıyor; `tifinput/IptvInputChannelSyncRepository.kt:38-75`
  her kanal için ayrı ContentResolver yazması yapıyor (`bulkInsert` yok).
  **Kabul:** Sayfalama keyset'e döner veya sıralama indekslenebilir olur; yazmalar toplulaştırılır;
  senkronizasyon süresi binlerce kanalda ölçülür ve Room tek yetkili kalır.

- [ ] **PERF-010 (P2): 500 ms oynatma ticker'ı tam OSD yeniliyor**
  `MainActivity.kt:2216-2223, 2445-2509` saniyede iki tam kontrol/infobar güncellemesi;
  oynatma boyunca aralıksız ana iş parçacığı işi.
  **Kabul:** Yalnız değişen alanlar güncellenir (payload), görüş görünür değilken ticker durur.

- [ ] **PERF-011 (P2): Renk ipuçları her güncellemede yeniden üretiliyor**
  `MainActivity.kt:5031+` `removeAllViews()` + yeni `TextView` üretimi her infobar güncellemesinde
  ölçüm/layout maliyeti. Öğe yeniden kullanımı veya payload diff gerekli.
  **Kabul:** İpucu satırı öğe yeniden kullanımıyla güncellenir; `UI-005` punto değişikliği ile
  birlikte yapılır; teknik satır sırası (source, quality, audio, subtitles, TXT, lock) değişmez.

- [ ] **PERF-012 (P2): Headless sniffer ana iş parçacığında 1920×1080 WebView**
  `vod/WebVideoResolver.kt:266-517` her sniffingde WebView'i sıfırdan kurup DecorView'a
  ekliyor ve içinde 500 ms'de bir `querySelectorAll` taraması çalışıyor (`:441-445`);
  oynatma başlangıcında bellek ve CPU maliyeti.
  **Kabul:** Oynatma başlangıcında ölçülebilir kare düşmesi olmaz; WebView ömrü sınırlı ve
  her yolda destroy edilir; sniffer sırasında odak kaybı olmadığı `CRASH-001` ve `VOD-003` ile
  birlikte cihazda doğrulanır.

- [ ] **PERF-013 (P1): Multi-View oynatıcıları serbest bırakılmıyor**
  `playback/MultiViewController.kt:100-148` dört ayrı `IptvPlaybackController` üretiyor,
  `stop()` yalnız oynatmayı durduruyor; `release()` (`IptvPlaybackController.kt:585-598`)
  Activity dağıtımına kadar çağrılmıyor — donanım kodek ve tampon belleği grid dışında da tutuluyor.
  **Kabul:** Grid kapatılınca oynatıcılar ve yüzeyler derhal serbest bırakılır; tekrarlı
  Multi-View oturumlarında bellek birikmez; TIF + IPTV ve IPTV + IPTV kombinasyonları çalışır.

- [ ] **PERF-014 (P2): Her tune'da Media3 fabrika nesneleri yeniden ayrılıyor**
  `IptvPlaybackController.kt:263-341` (özellikle `:281-286`) her `playPrepared` çağrısında yeni
  `DefaultRenderersFactory`, `DefaultLoadControl` ve `AdaptiveTrackSelection.Factory` üretiyor.
  Ayrıca `:308` `EXTENSION_RENDERER_MODE_PREFER` kurulu ama `app/build.gradle.kts:153-158`
  içinde hiçbir decoder-extension bağımlılığı yok (`OPTIMIZATION_TASKS.md` OPT-1.5 "Review"
  durumunda) — mod işlevsiz, yalnızca renderer sorgulama maliyeti.
  **Kabul:** Yapılandırma değişmedikçe fabrikalar yeniden kullanılır; PREFER modu ya gerçek
  bir extension ile anlamlandırılır ya da ON'a döner; tune süresi ölçülür.

- [ ] **PERF-015 (P2): Harf filtresi non-sargable**
  `data/local/IptvDao.kt:143, 166, 273` `SUBSTR(TRIM(displayName),1,1) IN (...)` sütun üzerine
  fonksiyon uyguladığı için indeksi kullanamıyor; 15.000+ satırda her harf seçimi tam tarama.
  **Kabul:** Baş harf kalıcı/indexlenmiş bir kolona veya FTS'e taşınır (`DATA-004` FTS altyapısı
  değerlendirilir); 15.000 kayıtta harf seçimi ve toplam sayım süresi ölçülür.

- [ ] **PERF-016 (P2): EPG liste penceresi kanal başına ayrı sorgu**
  `data/ProgramRepository.kt:253-263` görünen her TIF kanalı için ayrı ContentResolver sorgusu
  (N+1; önbellek korumalı ama ilk doldurma yavaş, TV provider'ına tekrarlı binder yükü).
  **Kabul:** Pencere tek veya sınırlı sayıda sorguyla dolar; `EPG-002` odak öncelikli prefetch
  davranışı ve snapshot tutarlılığı bozulmaz.

- [ ] **PERF-017 (P2): Üyelik değişimi tüm birleşik listeyi yeniden yüklüyor**
  `MainActivity.kt:4708-4713` (çağrı `:4207`) tek bir IPTV kanalını listeye ekleme/çıkarma
  tüm birleşik kanal listesini baştan yüklüyor.
  **Kabul:** Hedefli tek kayıt güncellemesi yapılır; odak ve kaydırma konumu korunur; Room
  yetkili kalmaya devam eder.

- [ ] **PERF-018 (P2): TMDB zenginleştirme ardışık 15 istek**
  `VodHomeActivity.kt:478-523` ardışık tekil aramalar (toplam 15 × RTT) ve `tmdbJob?.cancel()`
  önceki sayfanın işini yarıda kesiyor.
  **Kabul:** İstekler sınırlı eşzamanlılıkla çalışır; sayfa değişiminde yarım iş görünümü
  bozmaz; çevrimdışıyken deneme yapılmaz.

- [ ] **PERF-019 (P2): Tam adapter yenilemeleri envanteri**
  `notifyDataSetChanged()` 11 dosyada 14 nokta: `IptvChannelSelectionActivity.kt:375, 466, 651`
  (15k katalog), `MainActivity.kt:4737, 4746` (ebeveyn kilidi), `ui/MultiViewChannelAdapter.kt:29`,
  `guide/GuideProgramAdapter.kt:26`, `guide/GuideScheduleAdapter.kt:56`,
  `ui/ChannelEditorAdapter.kt:34`, `XmlTvSourceAdapter.kt:28`, `GithubManifestAdapter.kt:67, 73`,
  `ui/VodRightPanelAdapters.kt:21, 81`. AGENTS.md "DiffUtil payload, stable ID, tam refresh yok"
  ilkesiyle çelişiyor.
  **Kabul:** En az 15k katalog seçim listesi ve ebeveyn kilidi yolu DiffUtil payload'a geçer;
  odak ve kaydırma konumu korunur; kalan noktalar için ya geçiş ya bilinçli gerekçe bırakılır.

- [ ] **PERF-020 (P3): Canlı akış yolunda tekrarlı dizi ayırmaları**
  `playback/BufferedLiveInputStream.kt:26, 48` her 32 KB chunk ve her tek byte okumada yeni
  dizi üretiyor; uzun yayınlarda sürekli GC baskısı.
  **Kabul:** Tampon yeniden kullanılır; tek byte okuma yolu toplu okumaya dönüşür; uzun
  oturumda GC duraklamaları ölçülür.

- [ ] **PERF-021 (P3): XMLTV sıcak yolunda syscall, N+1 sayaç ve gereksiz `runBlocking`**
  `data/XmlTvRepository.kt:740-741` her EPG okumasında `File.exists()`, `:53-59` kaynak başına
  ayrı sayaç sorgusu, `:87` IO içinde gereksiz `runBlocking`.
  **Kabul:** Dosya meta bilgisi oturum başına bir kez okunur; sayaçlar tek sorguda toplanır;
  `runBlocking` kaldırılır; `EPGNEXT-001` atomik yenileme davranışı değişmez.

- [ ] **PERF-022 (P3): Regex'ler fonksiyon içinde derleniyor**
  `data/TifProviderDataInspector.kt:45, 59`; `data/XtreamClient.kt:194`;
  `playback/StreamCandidates.kt:76`; `vod/WebVideoResolver.kt:566`; `vod/VodTitleCleaner.kt:46-47`;
  `diagnostics/CrashReportStore.kt:317` her çağrıda `Pattern.compile`.
  **Kabul:** Desenler `companion object` veya top-level sabit olur; liste doldurmada öğe başına
  derleme maliyeti kalmaz.

- [ ] **PERF-023 (P3): Boşta çalışan OSD ticker'ları**
  `MainActivity.kt:5330-5341` her saniye yeni `SimpleDateFormat`, `:5476-5483` 1 sn tanılama
  döngüsü, `vod/VodPlayerOverlayController.kt:452-456` 1 sn ticker.
  **Kabul:** Biçimleyici önbelleğe alınır; güncellemeler yalnız görüş görünürken çalışır;
  boşta CPU ölçülebilir biçimde düşer.

- [ ] **PERF-024 (P3): Veritabanı açılış tetikleyicileri ve logo loader kilidi**
  `data/local/TVAppDatabase.kt:603+` her bağlantı açılışında üç tetikleyici DROP+CREATE;
  `image/ChannelLogoLoader.kt:36, 93, 106` `@Synchronized` her bind'de çağrılıyor ve cache
  boyutu için dizin tam geziliyor.
  **Kabul:** Tetikleyiciler migration'a taşınır; loader kilidi bind sıcak yolundan çıkar;
  cache boyutu maliyetli işlem yalnız ayarlar ekranında hesaplanır; `DATA-007` bütçesi korunur.

### Epic REFACTOR — Yapısal borç

- [ ] **REFACTOR-001 (P0): `refactor/main-activity-split` dalını `main` üzerine al**
  Dal `main`'in 15+ commit gerisinde ve artık farklı davranıyor: WebView thread düzeltmesi
  (`CRASH-001`) dalda **bozuk**, Room sürümü 32 (`main` 33, `21c6d50`), EPG UNION/debounce
  (`7056a66`), dokunma/hareket dağıtım optimizasyonu (`da9c00b`), fare uzun-tık etkileşimleri
  ve TIF blackout mute (`8a147cc`) dalda yok. Bu dal üzerinde yeni iş yapmak main'deki çökme ve
  performans düzeltmelerini geri getirir.
  **Kabul:** Dal main üzerine rebase/merge edilir; Room migration numara sırası
  (`MISS-003`) ve `alternativeStreamsJson` alanı kontrol edilir; iki varyantın birim testleri ve
  debug derlemeleri geçer; MainActivity bölünmesi davranış değişikliği getirmez.

- [ ] **REFACTOR-002 (P2): MainActivity kabuğunu küçült (Faz 6) ve planı güncelle**
  Dalda MainActivity hâlâ ~5.856 satır, `main`'de daha büyük; `main/` paketindeki 11 sınıfın
  tamamı kullanımda (ölü sınıf yok) ama delegeler ince kalıyor. `OPTIMIZATION_TASKS.md`
  OPT-4.2 ile aynı iş. Faz 4/5'i tamamlanmış olarak işlemeyen plan dosyası
  (`C:\Users\pools\.opencode\plan\main-activity-split.md`) güncellenmeli.
  **Kabul:** Kalan büyük bloklar (kullanıcı kararına göre oynatma/OSD/kuyruk yönetimi)
  dar arayüzlü host'lara taşınır; bağımlılık yönü tek yönlü kalır (host'lar birbirini çağırmaz);
  kumanda ve odak sözleşmeleri cihazda doğrulanır.

### Denetim sonrası iş sırası

> **Kullanıcı kararı (2026-11-14):** satın alma ve yetkilendirme işleri **en sona** alındı.
> `MISS-001` (paid flavor erişim kapısı) ve `STORE-001` (gerçek Play Billing) yeni işlerin
> önünü kesmeyecek; önce stabilite (`CRASH-*`, `VOD-*` sağlamlık, bellek/thread) ve
> arayüz görevleri (`UI-*`) bitirilecek.

**Sıcak — çökme ve teşhis yeteneği:**
1. `MISS-005` (mapping arşivi olmadan CRASH-002/003 teşhis edilemez) ve `MISS-004`
2. `CRASH-003` (bellek sınırı), ardından `CRASH-001` kalıntısı ve `CRASH-002`
3. `VOD-004` (güvenlik) — `MISS-001` bu gruptan çıkarıldı, en sona taşındı

**Yüksek getiri / dar değişiklik:**
4. `MISS-002` (VOD birim testleri — AUDIT-000 tipini kalıcı kapatır)
5. `UI-001` (sistem odağı göstergesi) → `UI-002`, `UI-003`, `UI-004`, `UI-005`
6. `VOD-001` + `VOD-002` (görünür hata + başlık taşıma), `PERF-007` + `PERF-008`
7. `REFACTOR-001` (dal birleştirme — yeni refactor işinden önce)

**Orta:** `PERF-006`, `VOD-003`, `VOD-005`, `VOD-007`, `VOD-008`, `UI-006`..`UI-011`,
`PERF-013`, `PERF-019`.

**Son:** kalan `PERF-020..024`, `MISS-006`, `UI-012`, `REFACTOR-002`, `MISS-003`,
**ve en son** `MISS-001` + `STORE-001` (kullanıcı kararı).

Her maddede AGENTS.md gereği: birim testler geçtikten sonra doğrudan commit + push;
kumanda tuş ve odak sözleşmeleri ile VOD akışları Android 11 Google TV cihazında ayrıca
doğrulanır.
