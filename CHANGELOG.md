# Değişiklik Günlüğü (Changelog)

Bu belgede TVApp uygulamasında yapılan tüm geliştirmeler, hata düzeltmeleri ve arayüz değişiklikleri sürüm ve tarih sırasıyla kaydedilir.

---

## [Geliştirme / En Son Değişiklikler]

### XMLTV EPG eşleştirme editörü
* Kanalın XMLTV ile otomatik olarak `tvg-id`/`channel id` veya sadeleştirilmiş kanal adı üzerinden eşleştiği artık ayrı editörde görülebiliyor.
* Kumandayla belirli bir XMLTV kaynağı ve kanal kimliği elle seçilebiliyor; tercih Room'da kalıcı saklanıyor ve aynı kimliğe sahip farklı EPG kaynakları birbirine karışmıyor.
* Kanal adları büyük/küçük harf, boşluk, noktalama, `HD`/`SD`/`FHD`/`UHD`/`4K` ve `.tr` benzeri ülke eki farklarından bağımsız eşleştiriliyor. Önceden alınmış XMLTV kayıtları bir defaya mahsus yeniden normalize ediliyor ve editör eşleşmeleri indeks üzerinden hesaplıyor.
* Kanal Liste Editöründe kumandanın GUIDE/EPG tuşu EPG eşleştirme editörünü doğrudan açıyor.

### Sıkıştırılmış XMLTV kaynakları
* XMLTV içe aktarıcısı, sunucu `Content-Encoding` veya doğru MIME türü göndermese bile akışın gzip imzasını algılayıp `.xml.gz` kaynaklarını otomatik açıyor. Bu davranış URL ve dosya kaynakları için ortaktır.

### Çoklu XMLTV kaynak yönetimi
* Ayarlar ve Kanal Listesi Editöründeki XMLTV pencereleri URL/dosya ekleme ile kayıtlı kaynak yönetimini ayrı ve görünür seçenekler olarak sunuyor. Kanal editöründeki URL alanı koyu TV teması, ipucu, doğrulama ve kumanda odağıyla yeniden düzenlendi.
* Birden fazla XMLTV URL veya dosya kaynağı Room'da ayrı kayıtlar olarak saklanıyor. Kaynak eklemek diğer EPG verilerini silmiyor; URL kaynakları tek tek güncellenebiliyor, kaynaklar ayrı ayrı silinebiliyor ve periyodik yenileme kayıtlı URL'lerin tamamını işliyor.
* TV temasında görünmeyen dialog liste satırları kaldırıldı. XMLTV yönetimi açıldığında URL alanı ile dosya, kayıtlı kaynak ve temizleme eylemleri artık doğrudan görünüyor.

### Sistem Live TV uygulaması seçimi
* TVApp, sistemin kanal URI'si için gösterdiği "Şununla aç" Live TV uygulamaları listesine yeniden eklendi. Kanal intentleri ana oynatıcıdan ayrı bir giriş activity'sinde karşılanıyor, istenen kanal ana ekrana aktarılıyor ve aynı kanal için yinelenen tune isteği bastırılıyor.
* Mochi Live TV'deki kanal görüntüleme kaydından yalnızca TVApp'in desteklediği kanal MIME filtreleri alındı; program, arama ve kaynak kurulum intentleri eklenmedi.

### Ham TIF kanal değerleri ekranı
* Sistem Bilgileri ekranı seçilen TIF kanalının `TvContract.Channels` satırını arka planda yeniden sorguluyor ve cihazın sunduğu bütün kolonları key/value biçiminde gösteriyor. Uygulama eşlemesi, ham kanal alanları ve canlı callback/track sonuçları kaydırılabilir ekranda ayrı bölümlere ayrıldı.
* MediaTek'in `TvTrackInfo.extra` Bundle'ında gönderdiği vendor alanları yalnız Bundle boyutu olarak değil, ayrı key/value satırları halinde hem ekranda hem debug günlüğünde gösteriliyor.
* Android 11'in erişime açtığı tune, retune, track seçimi/değişimi, video durumu/boyutu, içerik engeli, bağlantı ve timeshift callback geçmişi oturum boyunca tutuluyor; Sistem Bilgileri ekranında ve `TIF_CALLBACK_RAW` debug kayıtlarında gösteriliyor.

### Canlı TIF video bilgisi
* `onVideoSizeChanged`, `onVideoAvailable` ve `onVideoUnavailable` callback sonuçları oynatma oturumu boyunca ayrı tutuluyor. Track listesi çözünürlük bildirmese bile infobar kalite rozeti ve Sistem Bilgileri canlı callback verisini kullanıyor; görüntü yok neden kodu da tanılama ekranında gösteriliyor.
* Görüntünün kullanılamaması tek başına şifreli yayın kabul edilmiyor; şifre durumu TIF/vendor kanal verisinden ayrı yönetiliyor.

### DTV açılış çökmesi
* Ham TIF track günlüğü artık video alanlarını yalnız video track'lerinden, ses alanlarını yalnız ses track'lerinden okuyor. MediaTek TIF ses track'i geldiğinde oluşan `Not a video track` çökmesi ve bunun ardından sistemin varsayılan Live TV uygulamasına dönmesi giderildi.

### EPG uygulaması yönlendirmesi
* TVApp'in sistem kanal URI'leri için Live TV/EPG uygulaması olarak kaydolmasına neden olan standart kanal MIME intent filtresi tamamen kaldırıldı. EPG verileri yalnızca `TvContract` sorgularıyla okunuyor; uygulama açılırken sistemin Live TV uygulamasına geçmesi veya "Şununla aç" penceresi göstermesi engelleniyor.

### Kanal sistem bilgileri
* Kanal listesindeki uzun OK menüsüne kanal/TIF alanlarını ve canlı track verilerini key/value biçiminde gösteren kaydırılabilir Sistem bilgileri paneli eklendi.

### Sistem TV seçicisi ve DTV video boyutu
* DTV kalite rozeti TIF video boyutu bildirimlerini de kullanır; video kullanılabilir olduğunda track bilgileri yeniden okunur.
* DTV teknik veri uyuşmazlıklarını cihaz üzerinde incelemek için kanal, track ve video boyutunun ham TIF alanları debug günlüğüne yazılır.

### Yayın kalite rozetleri, XMLTV girişi ve renk tuşu ipuçları
* DTV ve IPTV kalite rozetleri kanal adındaki `HD`, `UHD` veya `4K` metninden tahmin edilmiyor; oynatıcının gerçek video boyutu ve TIF `videoFormat` verisiyle SD/HD/FHD/4K olarak sınıflandırılıyor. Teknik veri henüz yoksa yanıltıcı rozet gösterilmiyor.
* Alternatif XMLTV URL penceresi koyu TV temasına uyarlandı; URL alanı, örnek adresi ve doğrulama hatası görünür hale getirildi. Geçersiz adres girildiğinde pencere artık kapanmıyor.
* Infobar ve IPTV oynatma çubuğundaki küçük metin noktaları, kanal listesindeki renk tuşlarıyla aynı boyutta sabit daire göstergelerine dönüştürüldü.

### Xtream sunucu EPG'si (get_short_epg)
* Xtream Codes API kaynaklarında, ana listeye eklenen canlı kanalların şimdi/sonraki program bilgisi XMLTV içe aktarımı olmasa bile sunucudan `get_short_epg` ile alınır ve EPG ile infobarda gösterilir.
* XMLTV içe aktarımı varsa öncelik her zaman XMLTV'dedir; sunucu EPG'si yalnızca eşleşme bulunamayan kanallar için devreye girer ve ayrı bir tabloda saklandığından XMLTV yenilemeleriyle silinmez.
* Sunucu EPG'si kaynak içe aktarımında/yenilemede ve EPG periyodik jobu ile 12 saatte bir tazelenir; ana listeye yeni kanal seçildiğinde arka plan işi olarak otomatik yeniden denenir. Seçim ekranı ağ isteklerini beklemez ve geçici sunucu hatalarında mevcut EPG önbelleği korunur. İstek sınırı (kaynak başına en fazla 250 kanal, kanal başına 4 program) büyük kaynaklarda aşırı yükü önler.

### Kanala özel ses/altyazı belleği
* Bir kanalda ses veya altyazı dili değiştirilince seçim o kanala özel hatırlanır; kanala dönüldüğünde otomatik uygulanır. Kanal için tercih yoksa global dil tercihleri kullanılmaya devam eder.
* Altyazı kapatma da kanal bazında hatırlanır; hem uydu/TV girişleri hem de IPTV oynatırken geçerlidir.

### EPG canlı güncelleme
* Ana ekran ve program rehberi açıkken mevcut programlar her 15 saniyede arka planda yenilenir; kanal kapanıp yenisini başlatan programlar artık EPG veya infobar/paneli yeniden açmaya gerek kalmadan güncel görünür.
* Odak değişimini iptal etmeyen ince güncelleme, kanal listesinde program satırlarını ve açık kanalın infobar'ında saat/ilerlemeyi sessizce tazeler.
* TIF, XMLTV ve Xtream verileri aynı öncelik kurallarıyla birleştirilir; süresi biten önbellek kayıtları kaldırılarak kanal listesi ile infobarın farklı program göstermesi önlenir.

### EPG program hatırlatıcısı
* Program rehberinde gelecek bir programa uzun OK ile hatırlatıcı kurulur veya kaldırılır; kurulu hatırlatıcılar program satırında saat rozetiyle işaretlenir.
* Program başlama anında sistem bildirimi gösterilir; bildirime basıldığında TVApp açılır ve doğrudan ilgili kanala geçer.
* Hatırlatıcılar cihaz yeniden başlatıldığında otomatik yeniden kurulur; süresi geçmiş hatırlatıcılar temizlenir.
* Android 13 ve üzerinde ilk kurulumda bildirim izni istenir; izin reddedilirse hatırlatıcı kurulmaz ve açıklama gösterilir.

### EPG, kumanda tanılama ve çoklu IPTV oynatma düzeltmeleri
* Kanal listesi ve program rehberi EPG eşlemesi TIF/IPTV kimlik çakışmalarını önlemek için `sourceKey` temeline taşındı.
* XMLTV anlık programları kanal başına sorgulamak yerine sınırlı toplu Room sorgularıyla okunuyor; TIF EPG verisi olmayan uydu kanallarında da XMLTV yedeği kullanılıyor.
* Program rehberinde odak değişirken tüm listenin yenilenmesi kaldırıldı; yalnız eski ve yeni seçili satır güncelleniyor.
* IPTV program bilgileri program rehberi kanal satırlarında da gösteriliyor ve rehber süre metinleri uygulama diline göre biçimlendiriliyor.
* Kumanda tuşu tanılama kaydı yalnız debug derlemelerinde görünen ayardan açılıyor, varsayılan olarak kapalı geliyor ve tuş tekrarlarını dosyaya yazmıyor.
* Multiview teknik rozetleri odaktaki gerçek ana/ikincil oynatıcıdan okunuyor.
* İç PiP/multiview ikincil IPTV oynatıcısı 720p/3 Mbps, dört hücreli grid oynatıcıları 540p/1.5 Mbps ABR üst sınırı kullanıyor. Tek varyantlı akışlar bu sınırlardan etkilenmiyor.
* Kayıtlı IPTV listelerinde aynı uzun OK olayının iki işlem penceresi açabilmesine yol açan çift uzun-basış dinleyicisi kaldırıldı.

### ⚡ IPTV İnternet Hızına Göre Dinamik Performans & Adaptif Kalite (ABR)
* **Dinamik Bant Genişliği Ölçümü (DefaultBandwidthMeter)**:
  * Oynatıcı ve veri kaynağına (`DefaultDataSource.Factory`) anlık transfer ölçümü entegre edilerek indirme hızı gerçek zamanlı takip edilir.
* **Hıza Göre Otomatik Kalite Değişimi (Adaptive Bitrate Streaming - ABR)**:
  * 2.4 GHz Wi-Fi veya ağ hızında dalgalanma/düşüş yaşandığında 1 saniye içinde çözünürlük/bitrate otomatik düşürülerek yayının donması engellenir (`AdaptiveTrackSelection.Factory`).
  * Ağ hızı 2.5 saniye boyunca yüksek ve stabil kaldığında yayın kalitesi tekrar en yüksek seviyeye çıkartılır.
* **2.4 GHz Wi-Fi Dalgalanmalarına Karşı Güçlendirilmiş Tampon (Buffer)**:
  * Minimum tampon süresi 4 saniyeye çıkarılarak (`MIN_BUFFER_MS = 4000ms`), 2.4 GHz bant parazitleri ve anlık sinyal kayıplarında yayının donması önlendi.
  * Olası takılmalarda peş peşe donma döngüsünü engellemek için tekrar başlama tamponu 2.5 saniye (`BUFFER_AFTER_REBUFFER_MS = 2500ms`) olarak ayarlandı.
  * İlk zapping açılış süresi 500 ms korunarak kanal geçiş hızından ödün verilmedi.
* **Kumandadan Video Kalitesi (Çözünürlük) Seçimi**:
  * Canlı yayında veya kanal listesinde `OK` tuşuna basılı tutulduğunda açılan menüye *"Görüntü kalitesi (Çözünürlük)"* seçeneği eklendi.
  * Kullanıcı dilediğinde *"Otomatik (Hıza göre adaptif)"* seçebilir veya manuel olarak belirli bir çözünürlüğü (1080p, 720p, 576p vb.) sabitleyebilir.
* **Infobar Canlı ABR Göstergesi**:
  * Yayın adaptif modda izlenirken Infobar teknik rozetinde güncel kalite ile birlikte `ABR` (Örn. `FHD · ABR`, `HD · ABR`) rozeti gösterilir.

### 📺 Canlı TV & IPTV Harmanlaması ve Akıcılık İyileştirmeleri
* **Görsel Geri Bildirim (IPTV Yükleniyor / Buffering Göstergesi)**:
  * Uydu kanalından IPTV kanalına geçildiğinde yaşanan 1-2 saniyelik siyah ekran belirsizliği giderildi.
  * Ekranın merkezinde yarı saydam cam zeminli, accent vurgulu dairesel dönen yüklenme animasyonu (`iptv_buffering_container`) ve *"Bağlanıyor…"* / *"Yükleniyor…"* durumu gösterilir. Yayın ilk kareyi verdiği anda (`STATE_READY`) gösterge otomatik olarak kaybolur.
* **Infobar Canlı IPTV Teknik Rozetleri (HD/FHD/4K, Dolby, Ses, Subtitle)**:
  * IPTV yayını başladığında ExoPlayer'ın tespit ettiği gerçek akış çözünürlüğü (4K, FHD, HD, SD), ses dili, Dolby/AC3 ses formatı ve altyazı izleri Infobar'daki teknik rozetlere canlı olarak aktarılır (`updateTechnicalBadgesForIptv`). Tıpkı normal bir uydu kanalı izleniyormuş gibi zengin ve doğru teknik bilgi sunulur.
* **Zapping Hızı ve Tampon (Buffer) Optimizasyonu**:
  * Canlı yayın ExoPlayer tampon süreleri (`BUFFER_FOR_PLAYBACK_MS = 500ms`, `BUFFER_AFTER_REBUFFER_MS = 1000ms`) optimize edilerek ilk karenin ekrana basılma süresi (Time-To-First-Frame) belirgin biçimde kısaltıldı.
* **Kanal Listesinde ve EPG'de Toplu IPTV Program Bilgisi**:
  * Kanal listesi ve Program Rehberi (EPG) ilk açıldığında hem TIF uydu kanallarının hem de IPTV kanallarının o an yayında olan dizi/program bilgileri tek seferde veritabanından çekilip harmanlanır (`currentProgramsForChannels`). IPTV kanallarının satırları artık açılışta boş kalmaz.
* **Genişletilmiş Kumanda Ses ve Altyazı Uyumluluğu**:
  * Kumandadaki ses ve altyazı tuşları için `KEYCODE_CAPTIONS` haricinde raw `175` tuş kodu ve `KEYCODE_TV_AUDIO_DESCRIPTION` dinlenerek farklı marka Android TV kumandalarında doğrudan IPTV ses/altyazı pencerelerinin açılması sağlandı.

### ✨ Yeni Özellikler (Multi-View Çift IPTV)
* **2 IPTV Kanalı Eşzamanlı Oynatma (IPTV + IPTV)**:
  * Multi-View modu yalnızca Uydu+IPTV ile sınırlı kalmaktan çıkarıldı. Ekranın solunda ana IPTV oynatıcısı (`iptv_player_view`), sağında ise ikincil IPTV oynatıcısı (`secondary_iptv_player_view`) olmak üzere iki canlı IPTV akışı yan yana oynatılabilir hale getirildi.
* **Akıllı Ses ve Odak Yönetimi**:
  * Kumandadan `SOL` ve `SAĞ` yön tuşlarıyla ekranlar arasında geçiş yapıldığında; odakta olan tarafın sesi anında açılırken diğer taraf susturulur (mute).
  * Bilgi çubuğu ve teknik rozetler (çözünürlük, ses, altyazı vb.) anlık olarak odaktaki kanala senkronize edilir.
* **Odaklı ve Bağımsız Kanal Değiştirme (Zapping)**:
  * Kumandadan `CH+ / CH-` veya `D-Pad Yukarı / Aşağı` tuşlarına basıldığında, yalnızca **odakta olan ekranın** kanalı bir sonraki/önceki kanala geçer.
* **Hızlı IPTV Kanal Seçici**:
  * Multi-View açıkken kumandanın `OK` tuşuna uzun basıldığında, odaktaki ekran için canlı IPTV kanal seçim diyaloğu açılır.
* **Kesintisiz Tam Ekrana Dönüş**:
  * Multi-View'dan çıkıldığında (Back tuşu veya menü ile) son odakta olan kanal tam ekranda kesintisiz izlenmeye devam eder.
* **Donanım Koruması (Tek Tuner Uyarısı)**:
  * Tek tunerli donanımlarda iki farklı uydu (TIF) yayınının aynı anda açılamayacağı ve oturum çakışması yaşanmaması için kullanıcıya açıklayıcı uyarı (`multiview_requires_dual_tuner`) eklendi.

### 🎨 Arayüz İyileştirmeleri (Infobar Renk Kodları)
* **Kompakt ve Hizalı Renk Kodları**:
  * Infobar'ın dışına taşınmış olan renkli kumanda kısayolları (`info_color_actions`), `info_bar` içindeki en alt satır olan `technical_row` içerisine alındı.
  * Sol taraftaki teknik rozetler (kaynak, çözünürlük, ses, altyazı, TXT, kilit) korunarak araya esnek boşluk (`weight=1`) eklendi ve renk kodları en sağa hizalandı (`sağa dayalı`).
  * Harici marjin hesaplamaları kaldırıldı; ekranın altında gereksiz boşluk kalması önlendi.

### ⚙️ Kumanda Ayarlar & Hızlı Ayarlar (Settings & Quick Settings)
* **Kısa Basış (Short-press) - TVApp Ayarları**:
  * Kumandadaki Settings (`KEYCODE_SETTINGS`), Quick Settings (`KEYCODE_QUICK_SETTINGS`) veya TV Contents Menu (`KEYCODE_TV_CONTENTS_MENU`) tuşlarına kısa basıldığında TVApp görüntü ve uygulama ayarları açılır (`openDisplaySettings()`).
* **Uzun Basış (Long-press) - Google TV Sistem Ayarları**:
  * Ayarlar tuşlarına basılı tutulduğunda (long-press), Android TV / Google TV'nin ana sistem ayarları (`Settings.ACTION_SETTINGS` veya `android.settings.QUICK_SETTINGS`) açılır.
* **Gelişmiş Tuş Tanılama ve Loglama**:
  * Kumandadan basılan her tuşun adı (`name`), tuş kodu (`code`), donanım tarama kodu (`scan`) ve tekrarlama sayısı (`repeat`) anlık olarak `.log` kayıtlarına yazılır; böylece kumandanın gönderdiği tuş kodları net biçimde izlenebilir.
* **Menü Tuşu Davranışı**:
  * Kumandadaki `KEYCODE_MENU` tuşu kanal listesini açıp kapatma görevinde (`toggleChannelPanel()`) korunmuştur.

### 📅 Program Rehberi (EPG) Sola Yaslı Cam Tasarımı ve Seçici Düzeltmesi
* **Sola Yaslı Şık Cam Panel (%68 Genişlik)**:
  * EPG ekranını tamamen karartan siyah katman kaldırıldı. Ekranın sağ tarafındaki %32'lik alan tamamen şeffaf bırakılarak canlı TV yayınının kesintisiz izlenmesi sağlandı.
  * Sol panel için yarı saydam koyu cam efekti (`bg_guide_overlay` - %83 opaklık) ve ince cam ayırıcı bordürler uygulandı.
  * İç sütunların katı siyah zeminleri temizlenerek cam panel bütünlüğü sağlandı, gereksiz XML çoğaltılması önlendi.
* **Akıcı Liste ve Seçici Hareketi**:
  * Seçiciyi en üst satırda kilitleyen offset sorunu giderildi; `scrollToPosition` ile kumanda hareketlerine anında ve pürüzsüz yanıt veren doğal liste akışı sağlandı.
* **Modern Cam Kartlar (`bg_guide_item`)**:
  * Odaksız durumlarda hafif parıltılı cam çerçeve, odak durumunda ise parlak belirgin vurgu çizgisi eklendi.

### 🎛️ IPTV Grid Kanal Seçimi ve Kumanda Gezintisi
* **Renksiz Kumandalar İçin Kanal Yönetim Menüsü Erişimi**:
  * Kumandasında renkli tuş bulunmayan kullanıcılar için kanal listesinde kanal üzerinde `OK` tuşuna basılı tutulduğunda açılan Kanal Yönetim Menüsüne *"IPTV Grid (Çoklu Ekran)"* / *"IPTV Grid’i Kapat"* seçeneği eklendi.
* **Sağ/Sol Ok ile Sekme (Tab) Benzeri Gezinti**:
  * Grid kanal seçim diyaloğunda liste üzerindeyken `SAĞ OK` tuşuna basıldığında doğrudan *"Grid’i aç"* butonuna odaklanılır; butonlardayken `SOL OK` tuşuna basıldığında listeye geri dönülür.
* **Anlaşılır Buton Etiketleri**:
  * Renksiz kumandaları yanıltmamak adına buton metinleri doğrudan *"Grid’i aç"* ve *"Kapat"* olarak düzenlendi, renkli kumandası olanlar için yeşil/kırmızı kısayollar korunmaya devam etti.

### 📋 Kayıtlı IPTV Listeleri Yönetim Menüsü (`IptvSourcesActivity`)
* **Kumanda OK Uzun Basış Desteği**:
  * Kayıtlı IPTV listelerindeyken TV kumandasında `OK` (`DPAD_CENTER` / `ENTER`) tuşuna uzun basıldığında Liste İşlemleri menüsü açılır hale getirildi (`dispatchKeyEvent` eklendi).
* **Tam Yönetim Desteği**:
  * Kullanıcı kumandasından tek bir uzun basışla:
    * **Kanalları Seç**: Listeye ait kanal seçim ekranını açma,
    * **Listeyi Güncelle**: Kaynağı yeniden indirip yenileme (Refresh),
    * **Yeniden Adlandır**: Liste adını değiştirme (Rename),
    * **Sil**: Onay penceresiyle listeyi ve kanallarını temizleme (Delete)
    işlemlerini kumandadan kolayca yönetebilir.
* **Canlı Yayın Vurgusu ve Süre Gösterimi**:
  * O an yayında olan programlar için parlak kırmızı "CANLI" rozeti ve detay panelinde süre ile birlikte *"Bitmesine X dk"* gösterimi eklendi.
* **Kumanda İpuçları Çubuğu**:
  * Detay panelinin altına `OK: İzle`, `▶: Programlar`, `◀: Kanallar`, `BACK: Kapat` kumanda kısayolları yerleştirildi.

---

## [Önceki Sürüm Temelleri]

### 📺 Canlı TV & TIF
* Android TV Input Framework (TIF) üzerinden donanımsal DVB tuner kanallarını listeleme ve `TvView` ile canlı oynatma.
* TKGS ve kanal taraması sonrası DVB servis kimliğiyle kullanıcı kanal sıralamasını koruyarak eşitleme.
* MediaTek tuner ayar ekranını uygulama içinden açabilme.

### 🌐 IPTV & Oynatıcı
* M3U / M3U8 listeleri, Xtream Codes API (Live & VOD) ve Stalker Portal (MAC adresi tabanlı) kaynak desteği.
* Binlerce IPTV içeriği için 200-250 öğelik Room sayfalaması ve 500 kayıtta bir toplu veritabanı yazımı.
* Canlı yayınlarda otomatik yeniden deneme (retry logic: 1s, 2s, 4s), VOD içeriklerinde ileri/geri sarma ve "İzlemeye devam et" geçmişi.
* TVApp IPTV kanallarını cihaz sistemine yayımlayan özel `TvInputService` yayını.

### 🎛 Kanal Yönetimi & Kumanda
* Yerel Room veritabanında özel kanal sırası, numarası, adı, favoriler, kanal gizleme/atlama.
* Dört haneli PIN ile kanal kilidi (çocuk kilidi).
* TV kumandası odak yönetimi (D-Pad, CH+/CH-, Renkli tuşlar, INFO, AUDIO, SUBT/CAPTIONS).
* Kanal düzenleyicide seçili kanalı sağdaki mini canlı pencerede önizleme.
* Son 50 kanal yerel izleme geçmişi.

### 📅 EPG, PiP ve Sistem
* TIF EPG ve harici XMLTV (URL/dosya) program rehberi desteği (`tvg-id` eşleştirme).
* Sistem Picture-in-Picture (PiP) ve PiP desteklemeyen cihazlar için uygulama içi mini pencere fallback desteği.
* Google TV Home "Son İzlenen Kanallar" satırı entegrasyonu.
* Sürümlü JSON dosyası olarak tüm ayarları ve kanal düzenini yedekleme/geri yükleme (Backup & Restore).
* GitHub Actions üzerinden otomatik derleme (`dev-r<run>`) ve uygulama içi SHA-256 kontrollü self-update.
