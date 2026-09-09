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

- [x] **UIINPUT-005 (P2): Ayarlar canlı önizlemesi**
  Infobar/panel konumu ve saydamlığı değişirken video üstünde örnek göster.
  **Kabul:** Ayar uygulanmadan sonucu görülebilir; video kapanmaz.

## Epic FEATURE - Sonraki kullanıcı özellikleri

- [x] **FEATURE-001 (P1): Ana kanal listesinde hızlı arama**
  TV `SEARCH` tuşu ve liste başlığı üzerinden tüm geçerli kaynaklarda arama aç.
  **Kabul:** Sayı ile kanal seçimi korunur; IPTV-only modda arama tüm seçili kataloğu kapsar.

- [x] **FEATURE-002 (P2): Catch-up/arşiv**
  M3U catch-up öznitelikleri ve Xtream arşiv API'sini modelle, EPG programından oynat.
  **Kabul:** Arşivlenebilir program işaretlidir; canlıya dönüş tek eylemdir.

- [ ] **FEATURE-003 (P2): Dizi/sezon/bölüm**
  Xtream seri kataloğu, sezon-bölüm ekranı, devam et ve sonraki bölümü ekle.
  **Kabul:** Bölümler canlı kanal gibi ana TV geçmişine karışmaz.

- [ ] **FEATURE-004 (P2): Yayın sağlık bilgisi**
  Codec, çözünürlük, bitrate, buffer, dropped frame ve son hata nedenini OSD/teşhiste göster.
  **Kabul:** Hassas URL/credential loglanmaz; bilgi oynatmayı bloke etmez.

- [ ] **FEATURE-005 (P2): Alternatif akış önceliği**
  Kullanıcının alternatif stream sırasını düzenlemesine ve başarısız streami geçici atlamasına
  izin ver.
  **Kabul:** Failover kanal/listesini veya izleme geçmişini değiştirmez.

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

## Önerilen ilk sprint

İlk kodlama turunda yalnız şu görevler ele alınmalıdır:

1. `PERF-001` performans ölçüm altyapısı
2. `PERF-002` büyük veri test üreticisi
3. `PLATFORM-001` cihaz yetenek modeli
4. `PLATFORM-002` çalışma modu çözücü
5. `PLATFORM-003` IPTV-only açılış akışı
6. `DATA-001` sorgu envanteri ve query planı

Bu sprint mevcut TV görünümünü değiştirmeden ölçüm ve cihaz uyumluluğu temelini kurar.
