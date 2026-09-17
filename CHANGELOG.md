# Değişiklik Günlüğü (Changelog)

Bu belgede TVApp uygulamasında yapılan tüm geliştirmeler, hata düzeltmeleri ve arayüz değişiklikleri sürüm ve tarih sırasıyla kaydedilir.

---

## [Geliştirme / En Son Değişiklikler]
### Performans Optimizasyonları, Hızlı Kanal Geçişi ve Bellek İyileştirmeleri
* **EPG ve Veritabanı Sorgu Hızlandırması (`nowAndNext`):** TIF (DVB/uydu) kanallarında mevcut ve sonraki yayın donanım tuner'ından başarıyla alındığında SQLite XMLTV veritabanı sorgusu tamamen atlanarak gecikme 0 ms'ye indirildi. XMLTV ve Xtream EPG için 78 saatlik tüm programları belleğe yüklemek yerine doğrudan anlık ve sıradaki programı çeken `LIMIT 2` Room sorguları (`nowAndNextPrograms`) entegre edildi.
* **Eski EPG Kayıtlarının Otomatik Temizlenmesi (`purgeExpiredPrograms`):** Süresi 24 saatten eski olan geçmiş EPG programlarının veritabanında şişmesini önlemek için otomatik temizleme mekanizması eklendi; kanal yükleme ve EPG senkronizasyonu sırasında eski kayıtlar temizlenerek veritabanı sorgu süreleri hızlandırıldı.
* **Asenkron ve Bloklamayan Loglama Sistemi:** `CrashReportStore.recordDebug()` metodu ana UI thread'ini ve kanal geçişlerini dondurmayacak şekilde arka plan I/O kuyruğuna geçirildi. Log dosyası için 2 MB sınır ve otomatik rotasyon eklenerek flaş bellek I/O baskısı giderildi.
* **Hızlı Kanal Değişiminde (`zap`) Debounce Koruması:** CH+/CH- ile hızlı kanal zaplamalarında Google TV sistem servisine (`HomeRecentChannelsPublisher`) giden yoğun IPC çağrıları debounce edilerek sistemin kilitlenmesi önlendi.
* **FinalizerWatchdogDaemon Çökme Koruması:** Düşük donanımlı Android TV işlemcilerinde bellek baskısı ve GC gecikmelerinde oluşan `FinalizerWatchdogDaemon TimeoutException` istisnası için koruma sağlandı.
* **EPG Negatif Önbellek Süresi:** EPG'si bulunmayan kanallarda işlemciyi ve diski sürekli meşgul etmemek için negatif önbellek süresi 2 saniyeden 60 saniyeye çıkarıldı.
### Mobil XMLTV Ekranları Dikey (Portrait) Mod ve Dokunmatik Buton Uyarlaması
* **Mobil Dikey Mod ve Serbest Yönlendirme:** `XmlTvSourcesActivity` ve `XmlTvEpgEditorActivity` mobil manifestinde (`android:screenOrientation="unspecified"`) tanımlanarak mobil cihazlarda ekranın zorla yatay dönmesi engellendi; telefonun dikey kullanımı sağlandı.
* **XMLTV Kaynakları Dikey Tasarımı (`layout-port/activity_xmltv_sources.xml`):** Mobil ekranda butonlar 46dp dokunmatik boyutta ikili yatay ("URL'den Ekle", "Dosyadan Ekle") ve tam genişlikte ("Eşleme Editörü") olacak şekilde düzenlendi; kaynak listesi dikey ekrana esnetilerek tek elle rahat kullanım sağlandı.
* **XMLTV EPG Eşleme Listesi Mobil Uyumu (`layout-port/item_xmltv_match.xml` & `layout-port/activity_xmltv_epg_editor.xml`):** Sabit 360dp genişlikteki durum sütunu mobil ekranda taşma yapmayacak şekilde esnek genişliğe kavuşturuldu; kanal adı, numara ve eşleşme bilgileri dikey ekranda tam görünür ve okunabilir hale getirildi.
* **Mobil Dinamik Ekran Yönlendirme ve Canlı Adaptasyon:** `TvRemoteActivity` içine `onConfigurationChanged` eklenerek, mobil cihazlarda (`MOBILE_UI_ENABLED`) ekran açıkken telefon dikten yataya veya yataydan dikeye çevrildiğinde arayüzün anında ilgili tasarıma (`layout-port` <-> `layout`) pürüzsüzce yeniden yerleşmesi sağlandı; `IptvChannelSelectionActivity` içinde ekran döndürüldüğünde kullanıcının seçtiği kanalların korunması için `onSaveInstanceState` / `onRestoreInstanceState` entegrasyonu yapıldı.
* **Mobil Kaynak Listesi Satır Boyutu (`layout-port/item_iptv_source.xml`):** Kayıtlı XMLTV ve IPTV kaynak öğelerinin yüksekliği mobil ekrana uygun şekilde optimize edildi.

### Mobil & TV Yönetim Ekranlarına Kapat / Geri (X) Butonu Entegrasyonu
* **Ayarlar Ekranı Kapat Butonu:** `DisplaySettingsActivity` başlık satırına dokunmatik mobil ve kumanda için şık `settings_btn_close` (`ic_close`) butonu eklendi; kullanıcılar mobil cihazlarda geri jestine ihtiyaç duymadan doğrudan ayarlar ekranından çıkabilir.
* **XMLTV ve EPG Editör Kapat Butonları:** `XmlTvSourcesActivity` ve `XmlTvEpgEditorActivity` başlık alanlarına standart `xmltv_btn_close` ve `epg_editor_btn_close` (X) butonları eklendi.
* **IPTV Kaynakları ve Kanal Seçimi Kapat Butonları:** `IptvSourcesActivity` (hem varsayılan yatay hem de dikey modlar) ve `IptvChannelSelectionActivity` (hem varsayılan hem de dikey modlar) başlık satırlarına standart `ic_close` butonları eklenerek ekran kapatma akışı tek dokunuşla tutarlı hale getirildi.

### Mobil VOD Seekbar / İleri-Geri Sarma & Multi-View Grid Stabilizasyonu
* **Mobil VOD ve Süre Kontrolleri:** Mobilde film, dizi ve VOD içeriklerde çalışan dinamik `SeekBar`, anlık geçen süre (`00:00`), toplam süre (`00:00`) ve 10 saniye ileri/geri sarma (`ic_forward_10`, `ic_replay_10`) kontrolleri eklendi. Canlı yayınlarda `SeekBar` ve sarma kontrolleri otomatik gizlenerek sade "CANLI" rozeti gösterilir; VOD yayına geçildiğinde "CANLI" rozeti gizlenip `SeekBar` ve süreler devreye girer.
* **Mobilde Çift Dokunuşla Sarma:** Oynatıcı ekranının sol yarısına çift dokunulduğunda 10 saniye geri sarma, sağ yarısına çift dokunulduğunda 10 saniye ileri sarma desteği ve ekranda beliren şık görsel geri bildirim rozeti (HUD) eklendi.
* **Multi-View Grid Stabil Mimariye Dönüş:** Yapay `gridFullscreenIndex` ve arka planda 4 oynatıcıyı açık tutarak hücre büyütme yaklaşımı kaldırıldı; eski stabil mimariye dönüldü. Grid hücresine tıklandığında veya kumandada `OK` basıldığında grid temizce durdurulup seçilen kanal doğrudan ana oynatıcıda tam ekran açılır.
* **Grid Seçici Bellek Temizliği:** Multi-View'dan çıkıldığında eski seçilen kanalların hafızada kalarak bir sonraki açılışta zorla yüklenmesi hatası giderildi (`gridSelectedKeys.clear()`). Seçici ilk açıldığında yalnızca o an izlenen canlı kanal ilk slota atanır, diğer slotlar temiz başlatılır. Modern yüksek kontrastlı slotlu seçici arayüzü (`dialog_multiview_picker.xml`) tamamen korundu.

### Multi-View 4'lü Grid ve Yüksek Kontrastlı Kanal Seçici İyileştirmeleri
* **4 Kanal Multi-View Kapasitesi ve Grid Yerleşimi:** TV'lerde `memoryClass <= 192` kontrolü nedeniyle `maximumGridStreams` değerinin 2'ye kısıtlanması sorunu giderildi (`DeviceResourcePolicy.kt`); sınır 4 akış olarak sabitlendi. 2x2 grid `GridLayout.LayoutParams` yapısında hücrelerin tam ekran yayılmasını sağlayan spec hizalaması (`FILL, 1f`) tamamlandı.
* **Hücre Seçiminde Kesintisiz Tam Ekran Geçişi:** Bölünmüş ekranda hücreye tıklandığında/OK basıldığında oluşan donma sorunu giderildi; kanal doğrudan ana player'a devredilerek pürüzsüz tam ekran oynatıma geçiş sağlandı.
* **Kumanda OK ile Seçim ve Odak Koruma:** `MultiViewChannelAdapter` içine `setHasStableIds(true)` eklenerek seçim sırasında odak kaybı önlendi; kumanda `KEYCODE_DPAD_CENTER` ve `KEYCODE_ENTER` tuşları için dinleyici eklenerek seçim sağlandı.
* **Kanal Seçici Yüksek Kontrast:** Koyu arkaplan üzerinde soluk kalan metinler bembeyaz ve kalın (`@color/text_primary`), kanal numaraları ise parlak mavi (`@color/accent`) yapılarak TV mesafesinden kristal netliğinde okunabilir hale getirildi.

### MultiView (Grid) Codec ve Stabilite İyileştirmeleri
* **Codec tabanlı grid limiti:** `MediaCodecList(REGULAR_CODECS)` üzerinden H.264 ve H.265 için
  ayrı eşzamanlı decoder instance sayıları algılanır; `maximumGridStreams` artık gerçek donanım
  kapasitesinden türetilir. HEVC-only cihazlarda genel kapasite sınırı uygulanır, rapor eksikse
  asgari 2 akış garanti edilir; "kapasite 1'e kilitlenme" sorunu engellenir.
* **Hücre sayısına göre oynatma profili:** 4 hücreli grid 960×540/1.5 Mbps GRID profili, 2-3
  hücreli düzenler 1280×720/3 Mbps SECONDARY profili kullanır; düşük donanımda 4 kanal birlikte
  daha stabil açılır, az kanalda görüntü kalitesi korunur. Yazılım extension decoder'ları mevcut
  `EXTENSION_RENDERER_MODE_ON` sıralamasıyla yalnız donanım decoder seçilemediğinde devreye girer.
* **Grid buffer azaltma:** Grid/secondary hücrelerde maksimum buffer 45 sn'den 10 sn'ye indirildi;
  hücre başına bellek kullanımı ve kanal değiştirme gecikmesi düşer.
* **TIF pencere yerleşimi güvenliği:** MultiView'daki DVB penceresi, grid yerleşim değişimlerini
  layout listener ile izler ve aynı konum için gereksiz surface güncellemesi yapmaz; siyah/yer
  değiştirmiş DVB penceresi düzeltilir.
* **Güvenli grid odak gezinmesi:** Odak matematiği `MultiViewFocusResolver`'a taşındı; 4 hücreli
  gridde yukarı/aşağı sınır dışına çıkmak yerine sütun içinde ilk/son satıra sarar, 2/3/4 hücreli
  düzenlerin gerçek geometrisine göre hareket eder.
* **Grid sessize alma koruması:** Grid kapalıyken hücrelerin ses durumu değiştirilmez; MultiView
  çıkışında ana yayın sesi her zaman geri açılır.
* **Seçici odak restorasyonu:** MultiView seçicide filtre değişimi ve arama sonrasında odak,
  görünen ilk öğeye sabitlenir.

### Program Rehberi (EPG) Tam Ekran Modern TV Arayüzü ve Üst Detay Kartı
* **Tam Ekran Smart TV Deneyimi:** Program rehberi ekranı tam ekran (16:9 safe margins) olarak yeniden modellendi; sağ taraftaki boşluk kaldırıldı.
* **Göz Hizasında Üst Detay Kartı:** Odaklanılan kanalın büyük logosu, numarası, adı; odaklanılan programın başlığı, "CANLI" rozeti, kalan süresi, zaman aralığı ve detaylı konusu ekranın üst kısmındaki modern koyu cam kartta dinamik ve anlık olarak sunuldu.
* **Kanal Numarası Kırılmalarının Giderilmesi:** Dar sabit piksel sınırları kaldırılarak tek satır (`maxLines="1"`, `singleLine="true"`, `minWidth="50dp"`) garantisi sağlandı; 3-4 basamaklı numaraların alt satıra bölünmesi engellendi.
* **Zaman Cetveli ve Canlı Çizgi Milimetrik Hizalaması:** Soldaki 240dp sabit kanal sütunu ile cetvelin "Kanallar" başlığı eşitlendi; canlı yayın ibresi (dikey mavi çizgi) cetvelden aşağıdaki satırlara kesintisiz aktarıldı.
* **Doğal Kumanda ve Odak Akışı:** `dispatchKeyEvent` içindeki yapay DPAD engellemeleri temizlenerek Android TV doğal odak sistemine geçildi; kumandayla satırlar ve programlar arasında gezinirken anında üst kart senkronizasyonu sağlandı.

### Program Rehberi (EPG) Tek Dikey Grid Mimarisi ve Kararlı Kaydırma
* **Tek Dikey Grid Mimarisi:** Sol kanal listesi ve sağ program listesini iki ayrı RecyclerView olarak senkronize etmeye çalışan yapı tamamen kaldırıldı; her satırda sol tarafta kanal hücresi, sağ tarafta program hücreleri bulunan tek birleşik `RecyclerView` (`program_list`) ve `GuideScheduleAdapter` mimarisine geçildi.
* **Kayma ve Zıplamaların Önlenmesi:** Odaklanma sırasında oluşan mikro dikey kaymalar, gecikmeler ve scale animasyon uyumsuzlukları giderildi. Dikey yukarı/aşağı ve yatay gezinme tek liste üzerinden donanımsal pürüzsüzlükle çalışır hale getirildi.
* **Kod Sadeleştirme:** Çift RecyclerView senkronizasyon mantığı ve gereksiz kalan `ProgramGuideChannelAdapter` repodan temizlendi.

### MultiView/PiP Seçici Day/Night Desteği, Odak/Kontrast İyileştirmeleri ve 4 Kanal/1 TIF Kuralı
* **Açık/Karanlık Tema ve Yüksek Kontrast:** Diyaloglar için semantik renk tokenları (`dialog_bg`, `dialog_text_primary`, `dialog_text_secondary`, `badge_tif`, `badge_iptv`) ve `values-night` desteği tanımlandı; beyaz zemin üzerine beyaz metin okunmama sorunu giderildi.
* **Slot Numaralı Çoklu Seçim:** Kumanda ile odaklanılabilir `MultiViewChannelAdapter` ve `RecyclerView` yapısına geçildi; seçilen kanallar 1, 2, 3, 4 rozetleriyle numaralandırıldı.
* **4 Kanal ve Maksimum 1 TIF Kuralı:** Donanımsal tek tuner kısıtına uygun olarak en fazla 1 adet DVB/ATV (TIF) kanal seçimine izin verildi. 1 kanal seçildiğinde doğrudan PiP, 2-4 kanal seçildiğinde MultiView (Grid) başlatılması sağlandı.
* **Kumanda Renk Rehberi Uyumu:** AGENTS.md TV UI kurallarına aykırı olan büyük butonlar kaldırılarak odak almayan ince kumanda renk rehberi yerleştirildi (Kırmızı: Temizle, Yeşil: Başlat/PiP, Sarı: Kaynak Filtresi, Mavi: Ara).
* **MultiView Akış Limiti ve Cihaz Uyumluluğu:** Donanım dekoderi raporunun eksik veya 1 bildirdiği TV donanımlarında MultiView kapasitesinin 1 kanala kilitlenmesi önlendi; taban sınır 4 akış olarak sabitlendi ve seçici açılışındaki limit engeli kaldırıldı.

### PiP seçimi, TIF tanı alanları ve kanal editörü araç çubuğu
* Birleşik PiP/MultiView seçicisinin düşük kaynaklı cihazlarda da açılması sağlandı; başlangıç
  seçimi temizlenerek tek IPTV kanalının doğrudan PiP için seçilebilmesi sağlandı.
* TIF tanı panelinde ağ ve buffer alanları yerine TIF giriş kimliği, video durumu, track sayıları,
  ses örnekleme hızı ve piksel oranı gibi platformun gerçekten bildirdiği değerler gösteriliyor.
* Kanal Liste Editörü'ne renk işlemlerini çağıran, kumandayla odaklanabilen dikey ikon çubuğu
  eklendi; mevcut alt renk tuşu rehberi korundu.

### TIF Tanı (Diagnostics) Desteği, Net Çözünürlük Rozeti, EPG Yatay Kayma Düzeltmesi ve Birleşik PiP/MultiView
* **TIF Tanı Paneli:** Canlı tanı OSD paneli DVB/ATV (TIF) kanallarında da aktif hale getirildi; tuner video track çözünürlüğü, formatı ve ses bilgileri yansıtıldı.
* **Net Çözünürlük Rozeti:** İnfobardaki FPS rozeti yerine televizyon standardında net çözünürlük rozeti getirildi (TIF için `1080i`, `720p`, `576i`; IPTV için `1080p`, `720p`, `4K`).
* **EPG Yatay Gezinme Sabitlemesi:** Program rehberinde aynı satırda sağa/sola geçiş yaparken oluşan mikro dikey kaymalar önlendi; liste dikeyde sabit tutuldu.
* **Birleşik PiP ve MultiView Seçicisi:** Yeşil tuşa basıldığında açılan seçicide 1 IPTV kanalı seçildiğinde PiP açılması, 2 veya daha fazla kanal seçildiğinde MultiView (Grid) başlatılması sağlandı.

### İnfobarda FPS Rozeti ve Canlı Tanı (Diagnostics) Paneli
* İnfobara kalite rozetinin hemen sağına canlı kare hızını gösteren FPS rozeti eklendi (IPTV Media3 ve DTV/ATV TIF video track desteği).
* Ayarlar menüsüne ("Gelişmiş Tanı Bilgilerini Göster") ve ekranda transparan canlı yayın istatistik paneli (çözünürlük, FPS, bitrate, buffer boyutu, düşen kare sayısı) entegre edildi.

### Oynatma Motoru (Engine) Kontrollerinin Gizlenmesi
* ijkplayer kaldırıldığı ve Media3 tekil aktif motor olarak yapılandırıldığı için infobardaki Motor Değiştir butonu ve Ayarlar menüsündeki oynatma motoru seçeneği gizlendi.

### Program Rehberi (EPG) Senkronizasyonu
* Program rehberinde sol kanal listesi ile sağ program listesi dikeyde pürüzsüz biçimde senkronize kayacak şekilde eşitlendi.
* Kumanda ile gezinirken sol kanal kutusu ve sağ program satırının aynı dikey piksel hizasında kalması sağlandı; üstteki kanal başlığı anında odaklanılan kanalla güncellenecek şekilde düzeltildi.

### Android TV ana ekranı banner'ı
* Uygulamanın TV launcher'larında boş veya siyah kutu gibi görünmesine neden olan düz renk banner,
  TVApp simgesini taşıyan 16:9 ve yüksek kontrastlı bir launcher banner'ı ile değiştirildi.

### ijkplayer'ın kaldırılması ve Media3 oynatıcı motorunun güçlendirilmesi
* ijkplayer ve ilişkili yerel kütüphaneler (`gsyijkjava`, `gsyvideoplayer-ex_so`) projeden tamamen kaldırılarak APK boyutu ~35 MB hafifletildi.
* Media3 oynatıcı motoruna `DefaultRenderersFactory` üzerinden `enableDecoderFallback = true` ve `EXTENSION_RENDERER_MODE_ON` eklendi; donanım dekoderi kilitlenen veya desteklenmeyen yayınlarda alternatif dekodere otomatik geçiş sağlandı.
* Canlı yayın tamponlama (buffering) yönetimi baştan yapılandırıldı:
  - Sürekli tampona düşmeyi önlemek için oynatma başlangıç eşiği `500ms`'den `1500ms`'ye, yeniden başlama tamponu `2500ms`'den `4000ms`'ye çıkarıldı.
  - Asgari/azami tampon aralığı `15s - 45s` olarak genişletildi.
  - Canlı yayınlarda bellek şişmesini ve geriye zıplamaları önlemek için `setBackBuffer(0, false)` uygulandı.
* Watchdog toparlanması sırasında canlı yayının geriden tekrar izletilmesine neden olan `seekToDefaultPosition()` çağrısı canlı yayınlar için kaldırıldı.
* Canlı yayın oynatma hız toleransı `0.97f - 1.03f` aralığına ayarlanarak mikro ağ gecikmelerinde takılma olmadan canlı yayın noktasına (live edge) yumuşak geçiş sağlandı.

### IPTV kanal seçimi dikey araç çubuğu ve kalıcı kumanda rehberi
* IPTV kanal seçim ekranında (`IptvChannelSelectionActivity`) işlem butonları ekranın soluna simge tabanlı dikey araç çubuğu olarak taşındı.
* Kanal listesinde gezinirken Sol Ok (D-Pad Left) ile dikey bara geçiş, dikey bardan Sağ Ok (D-Pad Right) ile kalınan kanala geri dönüş sağlandı.
* Butonlar kumanda renk tuşlarıyla uyumlu şekilde renklendirildi (Kırmızı: Temizle, Sarı: Kategori, Mavi: Yalnızca Seçilenler, Yeşil: Kaydet, Turkuaz: Tümünü Seç).
* Sayfanın altına kumanda renk tuşlarının ve sol bar simgelerinin karşılıklarını sürekli gösteren kalıcı kumanda rehberi yerleştirildi.

### IJK ve birleşik MultiView
* IJK donanım çözme seçenekleri AVC, HEVC, MPEG-2 ve MPEG-4 için açıkça yapılandırıldı; akış
  metadata'sından codec, çözünürlük, bitrate, FPS, buffer, ses ve drop-rate bilgileri alınarak
  Sistem Bilgileri ile debug sağlık kayıtlarına eklendi.
* IJK oynatımı ilk kare, uzun buffering ve ilerlemeyen yayınlar için watchdog kapsamına alındı;
  yerel hata kodları ağ, HTTP, kaynak, decoder ve zaman aşımı olarak ayrıştırılıyor.
* Eski iki ekranlı Multi View ve IPTV Grid yolları tek MultiView altyapısında birleştirildi.
  İki yayın yan yana, üç yayın büyük sol ve iki sağ, dört yayın 2x2 gösteriliyor. DVB zorunlu
  değil; aynı anda en fazla bir DVB, kalan yuvalarda IPTV kullanılabiliyor.
* MultiView seçicisine Tümü, DVB, IPTV ve Seçilenler filtreleri; arama, renk tuşları ve sayfalama
  eklendi. Yön tuşları aktif görüntüyü, CH+/CH- aktif yuvadaki kanalı değiştiriyor; yalnız aktif
  yuvanın sesi açık kalıyor ve Back odaktaki kanalı tam ekrana döndürüyor.

### Release APK boyutu
* TV release derlemelerinde R8 ve kaynak küçültme etkinleştirildi. GitHub TV APK'sı yalnız
  fiziksel TV cihazları için ARM64 ve ARMv7 yerel kütüphanelerini içeriyor; emülatör için gereken
  x86_64 yalnız yerel debug derlemelerinde tutuluyor.

### Deneysel IJK fallback dalı
* Media3 ana IPTV motoru olarak korunurken yalnız ana tam ekran oynatıcıdaki decoder ve kaynak
  biçimi hataları için bir defalık IJK fallback eklendi. Ağ ve HTTP hataları fallback tetiklemez.
* IPTV oynatma ayarına Media3, otomatik IJK fallback ve doğrudan IJK seçenekleri eklendi. IPTV
  medya kontrol satırı etkin motoru gösterir ve aynı seçimi kanal üzerinde doğrudan değiştirebilir.
* Ayarlardaki motor seçimi genel varsayılan olarak bırakıldı; medya kontrolündeki seçim kanal
  bazında Room'a kaydedilir. Kanal kilidi kaldırıldığında yeniden genel varsayılan kullanılır.
* Kanal bazındaki motor seçiminin genel ayardan önce uygulanması tek çözümleyicide sabitlendi;
  motor seçici simgesi diğer kompakt medya kontrolleriyle aynı çizgi stiline getirildi.
* Motor satırı çalışan motor yerine seçilen kanal ayarını gösteriyor; otomatik fallback IJK'ye
  geçtiğinde durum ayrıca belirtiliyor ve kanal kilidi yanıltıcı bir genel ayarla eşleşmiyor.
* Önizleme, PiP, MultiView, TIF ve TV Input Service deneyin dışında bırakıldı; oynatıcı
  değişimi mevcut TVApp infobar ve medya kontrollerini kullanmaya devam eder.
* ARM64 ve ARMv7 içeren deney APK'sını 30 MiB sınırıyla ayrı prerelease olarak yayımlayan iş akışı eklendi.

### IPTV oynatma toleransı
* IJK donanım decoder'ında ses ve video bilgisi bulunduğu halde ilk kare 12 saniye içinde
  gelmezse yayın bir kez FFmpeg yazılımsal video decoder'ıyla yeniden açılıyor. Radyo ve gerçek
  ses yayınları bu denemeye alınmıyor; ikinci başarısızlık yeniden başlatma döngüsüne girmiyor.
* Media3 canlı yayınlarında hedef canlı-uç gecikmesi tanımlandı ve otomatik hızlandırma/yavaşlatma
  kapatıldı. Periyodik canlı-uç düzeltmesi buffer sırasında çalışmıyor ve yalnız oynayan yayın
  60 saniyeden fazla geride kaldığında uygulanıyor; buffer tükenirken oluşan tekrar hissi giderildi.
* IPTV HTTP isteklerine protokoller arası yönlendirme, tarayıcı uyumlu User-Agent ve canlı
  yayınlara uygun bağlantı/okuma zaman aşımı değerleri eklendi.
* IPTV oynatma ağı bağlantı havuzu, HTTP/2 ve daha tutarlı yönlendirme davranışı için Media3
  OkHttp data source üzerine taşındı; kanal bazlı User-Agent ve Referer başlıkları korunur.
* Progressive MPEG-TS akışlarında IDR olmayan karelere, erişim birimi algılamasına ve daha geniş
  zaman damgası aramasına izin veren ortak extractor yapılandırması ana oynatıcı ve TIF servisine uygulandı.
* Decoder ve kaynak biçimi hataları ağ hatalarından ayrılarak harici oynatıcı fallback tetikleyicisine bağlandı.

### Açılışta boş ekran düzeltmesi
* Büyük IPTV kataloglarında Room yükseltmesini kilitleyebilen toplu kategori güncellemesi
  kaldırıldı; mevcut kanal, seçim, sıralama ve EPG verileri korunur.
* Kanal yükleme başlangıcı debug loguna eklenerek veritabanı açılışı ile sorgu aşaması ayrıştırıldı.

### IPTV kaynak yenileme performansı
* Kaynak yenilemede mevcut kanal kimliğini çözme sırası indeksli `tvg-id` ve normalize ad/grup anahtarlarını önce kullanacak şekilde düzenlendi; değişmeyen listelerde pahalı ad taraması büyük ölçüde kaldırıldı.
* IPTV kanallarına `(sourceId, tvgId)` bileşik indeksi ekleyen Room 17→18 migration oluşturuldu. Migration mevcut kaynakları, seçili kanalları ve kullanıcı sıralamasını korur.
* Room sorgu planı denetiminde IPTV sıralama, seçili kanal ve kategori yolları ile XMLTV kanal kataloğu için eksik birleşik indeksler tamamlandı. Daha geniş indekslerin kapsadığı gereksiz tek sütunlu indeksler kaldırılarak kaynak ve EPG içe aktarma yazma maliyeti azaltıldı.
* IPTV arama FTS tetikleyicileri kanal `rowid` değerini kullanacak şekilde taşındı. Kaynak yenileme artık bütün IPTV kataloglarının arama indeksini yeniden kurmuyor; yalnız değiştirilen kanal satırlarını güncelliyor.
* Büyük ve yinelenen IPTV kataloglarında kanal kimliği eşleştirmesini dakikalara çıkaran sıralama sorguları kapsayıcı indekslere taşındı; hazırlama tablosundaki yinelenen kayıt temizliği de aynı şekilde indekslendi.
* Yenileme logunda HTTP yanıt, okuma, eşleştirme ve veritabanı değiştirme süreleri ayrı kaydediliyor.
* IPTV yönetiminde URL, dosya, Xtream ve Stalker kaynakları ayrı ikonlarla gösteriliyor; URL kaynak satırında adres yer alıyor ve güncelleme ilerlemesi işlenen listenin adını belirtiyor.

### Kanal listesi metin yerleşimi
* Kanal adı üst satırın tamamını kullanacak şekilde genişletildi; kanal türü, üyelik ve kilit simgeleri EPG satırının sağına taşındı. Seri kanal adlarının ayırt edici son bölümleri için daha fazla alan açıldı.
* Kanal listesi başlığındaki sabit genişlik kaldırıldı; başlık sağdaki sayaç, arama ve kaynak simgelerine kadar kullanılabilir alanı dinamik olarak dolduruyor.

### IPTV donma algılama ve kontrollü kurtarma
* İlk video karesi gelmemesi, uzun süren buffer, ilerlemesi duran oynatma ve beklenmedik canlı yayın sonu ayrı nedenler olarak izleniyor; radyo yayınları video karesi beklemediği için yanlış hata sayılmıyor.
* Kurtarma aynı oynatma oturumunda iki kontrollü yeniden hazırlamayla sınırlandırıldı; sonuç alınamazsa mevcut alternatif akış zincirine geçiliyor ve sonsuz yeniden bağlanma döngüsü önleniyor.
* Kanal değişimi, durdurma ve oynatıcı kapatma eski watchdog ve retry işlerini iptal ediyor. Kurtarma nedeni, eylemi ve deneme sayısı özel yayın adresi içermeden debug loguna yazılıyor.

### IPTV liste içe aktarma
* PHP tabanlı liste adreslerinin göreli adresler dahil HTTP 301/302/303/307/308 yönlendirmeleri sınırlı ve güvenli biçimde izleniyor.
* Büyük dosya aktarımlarının son eşleştirme aşamasına bileşik hazırlama indeksi eklendi; 15.000 civarı kayıtta yinelenen tam tablo taramaları kaldırıldı.
* IPTV içe aktarma tanılama kayıtlarından özel liste adresleri çıkarıldı.

### Kanal listesi gezinme ve renk tuşları
* Arama boşken kanal listesi gezinmesinde her odak hareketinde yinelenen Unicode normalizasyonu kaldırıldı; normalize edilmiş sorgu yalnız arama değiştiğinde hesaplanıyor.
* Normal kanal listesinde Mavi tuş kanal aramasını açıyor; tam IPTV kütüphanesinde mevcut filtre görevini koruyor.
* Yeşil tuş yayın ekranı ve kanal listesinde tutarlı hale getirildi: kısa basış IPTV PiP, uzun basış IPTV Grid seçimini açıyor.

### IPTV yayın sağlığı
* Media3 oynatma durumu; ilk kare süresi, oynatma ilerlemesi, buffer, bitrate, tahmini bant genişliği, çözünürlük, codec, atlanan kare, yeniden deneme ve son hata sınıfıyla ortak bir sağlık modelinde toplandı.
* IPTV kanallarının Sistem Bilgileri ekranı bu sağlık anlık görüntüsünü talep üzerine gösteriyor; normal izleme arayüzüne ek yük bindirmiyor.
* Anlamlı IPTV durum geçişleri kimlik bilgisi ve tam özel URL içermeden debug loguna yazılıyor. IPTV adresleri Sistem Bilgileri ekranında yalnız protokol ve sunucu düzeyinde maskeleniyor.

### Kanal arama ve IPTV arşivi
* Kanal paneline ad, numara ve kategori arayan başlık eylemi ile `SEARCH` kumanda tuşu desteği eklendi. Büyük IPTV kütüphanesi araması, bütün kataloğu belleğe almadan Room FTS ve keyset sayfalama üzerinden çalışıyor.
* M3U `catchup`, `catchup-source`, `catchup-days`/`timeshift` alanları ve Xtream `tv_archive` bilgileri içe aktarılıp kalıcı olarak saklanıyor.
* Arşiv süresi içindeki geçmiş EPG programları rehberde işaretleniyor ve doğrudan Media3 ile oynatılabiliyor. Kırmızı oynatıcı eylemi arşivden tek adımda canlı kanala dönüyor.
* Room 15→16 migration ve yedek içe/dışa aktarma arşiv alanlarını kullanıcı seçimlerini silmeden koruyor.

### Merkezi OSD durumu
* Kanal paneli, infobar, IPTV oynatma kontrolleri, son kanallar, ebeveyn kilidi ve hata/durum panelinin görünürlüğü `PlaybackUiState` ve `OsdCoordinator` üzerinden tek noktadan yönetiliyor.
* IPTV Grid, Multi View, IPTV küçük pencere ve dahili mini oynatıcı etkinliği aynı playback durum modeline alındı. Yeni birincil OSD açıldığında çakışan panel kontrollü kapanıyor; kanal paneliyle birlikte kullanılan infobar korunuyor.
* OSD geçiş kuralları kanal paneli, IPTV kontrolü, son kanallar, Grid ve ebeveyn kilidi senaryolarını kapsayan birim testlerle güvenceye alındı.

### Performans tabanı ve düşük kaynaklı cihazlar
* Startup, kanal yükleme/panel, IPTV sayfası, EPG ve tune-ready işlemleri süre, kayıt sayısı ve çalışma moduyla yapılandırılmış debug performans kaydına alındı.
* Soğuk başlangıç ile sıcak başlangıç + hızlı kanal paneli gezinimini ölçen Macrobenchmark modülü ve yeniden üretilebilir Perfetto çıktıları eklendi.
* Ana açılış, kanal repository'si, IPTV liste ve oynatma rotaları için Baseline Profile eklendi; profil local APK ve paid AAB içinde doğrulandı.
* Bellek sınıfı, düşük RAM işareti ve donanım video decoder kapasitesi cihaz yetenek modeline eklendi. Logo cache/prefetch, IPTV Grid ve Multi View limitleri bu kaynak bütçesine göre ayarlanıyor; kritik bellek baskısında ikincil oynatıcılar kontrollü kapatılıyor.
* `Ayarlar > Sistem` bölümüne kalıcı Otomatik, Hibrit ve IPTV-only çalışma modu seçimi ile algılanan mod/kaynak özeti eklendi. Desteklenmeyen Hibrit tercihi güvenli biçimde IPTV-only moda düşüyor.
* IPTV-only cihazlarda Uydu/Radio döngüsü ile TIF eşitleme ve fiziksel tuner kaynak seçenekleri gizlenirken IPTV, VOD, XMLTV, kanal sıralama ve yedek işlemleri korunuyor.
* Kanal ve rehber satırı logoları yalnız odak çevresindeki sınırlı pencere için önceden yükleniyor; bellek ve disk cache bütçesi cihaz sınıfına göre uygulanıyor.

### Büyük veri performans testi
* IPTV yedek dışa/içe aktarma, tüm kataloğu tek bir `JSONObject`, `ByteArray` veya liste halinde belleğe almak yerine akışlı JSON ve 500 satırlık Room parçalarıyla çalışıyor. İçe aktarma önce dosyanın tamamını doğruluyor; veritabanı değişiklikleri tek transaction içinde yapıldığı için bozuk veya yarım dosya mevcut veriyi silmiyor.
* Xtream Codes ve Stalker Portal kanal katalogları ağ yanıtından akışlı ayrıştırılıyor; 15.000+ kayıtta dev JSON dizisi ve kanal listesi bellekte tutulmuyor.
* TVApp TIF kanal yayını seçili IPTV kanallarını 500 satırlık sayfalarla işliyor. Kullanılmayan tam katalog DAO/repository yolları kaldırıldı; TIF ve XMLTV/EPG veri yolları kaynak ayrımı korunarak devam ediyor.
* M3U/M3U8, Xtream ve Stalker kaynak yenilemeleri kalıcı staging tablo + atomik diff modeline geçirildi. Yeni katalog tamamen ayrıştırılıp doğrulanmadan çalışan kaynak değişmiyor; hata veya iptalde eski kanal listesi korunuyor.
* Yenileme yalnız eklenen, değişen ve kaldırılan IPTV kayıtlarını işler. Değişmeyen satırlar ile FTS kayıtları yeniden yazılmaz; kanal seçimi ve Room'daki özel sıra, ad ve EPG override bilgileri kararlı kanal kimliği üzerinden korunur.
* Dosya, Xtream ve Stalker kaynaklarının Yenile işlemi artık mevcut kaynak kimliğini açıkça kullanıyor; yarım kalan staging oturumları otomatik temizleniyor.
* IPTV kanal adı, `tvg-name` ve kategori araması, başında joker bulunan `LIKE` taramasından Unicode Room FTS4 indeksine taşındı. Room 13→14 migration mevcut kanalları indekse alır; ekleme, yenileme, yedekten geri yükleme ve silme işlemleri SQLite tetikleyicileriyle indeksi güncel tutar.
* Arama metni FTS sözdizimi enjekte edemeyecek şekilde harf/rakam terimlerine ayrılıyor ve kelime öneki olarak aranıyor. 15.000 satırlık test veritabanında 100 tekrarlı ölçümde arama p95 değeri 25,2 ms oldu.
* IPTV kütüphanesi ve kanal seçim sayfaları hafif Room projection'larına geçirildi. Liste satırları artık yayın URL'si, User-Agent, referrer ve altyazı adresini taşımaz; tam kayıt yalnız önizleme, oynatma veya kanal işlem menüsü açılırken alınır. XMLTV eşleştirmesi için gereken `tvg-id/tvg-name` korunur ve TIF yolu değişmez.
* IPTV kanal seçimindeki belirsiz `Seçililer` kontrolü `Yalnız seçilileri göster` olarak netleştirildi; filtre açıkken `Tümünü göster` yazar. `Seçimi temizle` sonrasında seçili filtresi kapanır ve boş ekran yerine tüm filtrelenmiş kanallar yeniden görünür.
* Tam IPTV kütüphanesi ve IPTV kanal seçim ekranı yüksek `OFFSET` yerine `(originalIndex, sourceKey)` çıpalı ileri/geri pencerelere geçirildi. Ekranlar en fazla tek sayfa tutuyor; ilk/son sarma, CH+/CH- ve sayı ile sıçrama tüm kataloğu belleğe almadan çalışıyor.
* IPTV filtreleri değişirken eski sayfa yanıtları nesil kimliğiyle reddediliyor; her yeni pencere odaklandıktan sonra EPG yalnız görünür kanal çevresi için yükleniyor.
* Debug sorgu analizine 14.000'inci kayıt çevresinde ileri ve geri keyset ölçümleri eklendi; kanal listesi EPG logu TIF/IPTV istek sayılarını ve toplam sorgu süresini ayrı gösteriyor.
* Kayıtlı IPTV listesinin kanal seçim ekranına, etkin kategori ve arama filtresinin tamamını veritabanında işleyen `Tümünü seç` ve `Seçimi temizle` kontrolleri eklendi; işlem için bütün katalog belleğe yüklenmiyor.
* Debug araçlarına gerçek kullanıcı veritabanından tamamen ayrı çalışan performans veri üreticisi eklendi.
* Emülatör ve fiziksel cihazlarda aynı şekilde tekrarlanabilen 500, 15.000 ve 50.000 IPTV kanalı ile XMLTV programı senaryoları eklendi.
* Büyük veri setleri bellekte topluca kurulmadan 500 kayıtlık parçalarla yazılıyor; üretim süresi debug loguna kaydediliyor ve test verisi tek eylemle güvenle temizlenebiliyor.
* İlk/yüksek offset sayfaları, kategori, seçili kanal, arama, kategori listesi ve XMLTV now/next sorguları gerçek süreleriyle çalıştırılıp `EXPLAIN QUERY PLAN` çıktıları ve tam tarama uyarıları debug loguna yazılıyor.
* İlk 15.000 kayıtlık sorgu envanteri ve keyset paging, seçili kanal composite indeksi, FTS ile normalize kategori önerileri `PERFORMANCE_QUERY_REPORT.md` içinde kaydedildi.

### Cihaz yetenekleri ve IPTV-only çalışma
* Uygulama artık yalnız `FEATURE_LIVE_TV` değerine güvenmiyor; kullanılabilir sistem/vendor tuner girişlerini ölçerek Hibrit TV, IPTV-only TV veya Mobil Test çalışma modunu seçiyor.
* Tuner bulunmayan TV stick/box ve emülatörlerde TIF sorgusu ile tuner izin akışı atlanıyor; kayıtlı IPTV kanalları doğrudan yükleniyor ve boş durumda IPTV liste yönetimine erişim sunuluyor.
* IPTV-only modda kanal kaynak seçimi Tümü ve tam IPTV kütüphanesi arasında çalışıyor; geçersiz Uydu/Radio seçenekleri gösterilmiyor.
* Açılış, kanal yükleme ve kanal paneli süreleri çalışma modu ve kayıt sayısıyla birlikte debug performans loguna yazılıyor.

### Sekmeli ayarlar
* Görünüm ve Kanallar sekmelerindeki temsili canlı önizleme kaldırıldı; ayar listesi daha fazla gerçek seçeneği aynı anda gösterecek şekilde sadeleştirildi.
* Ayarlar OSD'si Görünüm, Kanallar, IPTV ve EPG, Sistem sekmelerine ayrıldı. Sekmeler arasında Sol/Sağ ile geçiliyor; Aşağı içerikteki ilk ayara giriyor, içerikte Sol/Sağ veya OK değeri değiştiriyor.
* IPTV liste yönetimi ve XMLTV EPG yönetimi, IPTV ve EPG sekmesinden doğrudan açılabiliyor.
* `tools/TVAppRemote.cmd`, emülatör testleri için D-pad, TV, medya, sayı, renk tuşları ve uzun basış gönderen masaüstü kumanda panelini açıyor.
* Fiziksel klavyede Esc, F1-F12 ve PageUp/PageDown tuşları uygulama genelinde Back, TV işlevleri, renk tuşları ve kanal değiştirme komutlarına eşleniyor.
* OSD kapalıyken Sol ok artık kanal değiştirmiyor; Back son kanallar kartını açıyor. Last Channel kısa basışta önceki kanala geçiyor, uzun basışta son kanallar kartını açıyor.

### TIF ve EPG tanılama
* Program Rehberine ortak zaman cetveli ve kanal satırları boyunca uzanan canlı saat çizgisi eklendi; şu anda yayınlanan program hücresi ayrı renkle vurgulanıyor.
* Program Rehberi bütün kanalları solda, süreyle orantılı programları ortak saat cetvelinde sağda gösteren çok kanallı grid düzenine geçirildi. Seçili programın tam açıklaması alttaki kaydırılabilir panelde gösteriliyor.
* Rehber yalnız odak çevresindeki kanal penceresinin programlarını yükler; hızlı gezinmede eski EPG isteği iptal edilir ve boş bir kanalın sonucu diğer kanal satırlarını kapatmaz.
* Infobar, kanal listesi ve rehber aynı süreç geneli EPG snapshot önbelleğini kullanıyor. Bulunan program bitiş sınırına kadar, boş sonuç iki saniye saklanıyor; TIF/XMLTV sorgu süreleri ve cache isabetleri debug performans loguna yazılıyor.
* XMLTV eşleştirme editörü kesin `tvg-id`, kesin kanal adı, normalize edilmiş ad ve elle eşleşmeyi ayrı güven düzeyleriyle gösteriyor; belirsiz yinelenen adaylar otomatik eşleştirilmiyor.
* Program Rehberi sabit kanal kolonu, süreyle orantılı yatay program zaman çizelgesi, yarım saatlik zaman cetveli ve kalıcı açıklama paneliyle yenilendi. Çizelgede Sol/Sağ program, Yukarı/Aşağı kanal değiştiriyor ve aynı zaman noktası korunuyor.
* Kanal Sistem Bilgileri, `internal_provider_data` alanını ham hex, UTF-8, UTF-16LE ve okunabilir metin olarak gösteriyor; MediaTek blobunda bulunabilen frekans, sembol oranı, polarizasyon, uydu/transponder, frontend, LNB ve servis anahtarları ayrıca ayrıştırılıp debug loguna yazılıyor.
* XMLTV eşleştirmesi kalite eklerini kesin karşılaştırmada koruyor; `HD`, `SD`, `FHD` ve `4K` farklarını yok sayan gevşek eşleşme yalnız tek bir aday bulunduğunda kullanılıyor. Büyük EPG tablolarını açılışta kilitleyen toplu yeniden indeksleme kaldırıldı.
* Infobar EPG ilerleme çubuğuna dar alanlarda kaybolmaması için asgari genişlik verildi ve yatay boşluğu azaltıldı.
* MediaTek TIF sağlayıcısında kanal listesi ve Program Rehberi, güvenilmez global program sorgusu yerine kanal URI'si üzerinden tekil sorgu kullanıyor. Odaktaki kanal önce yükleniyor, geçici boş sonuç cache'i 2 saniyeye indirildi ve geciken eski gezinme sorgularının yeni odağı ezmesi engellendi.

### Mobil test varyantı
* `mobileDebug`, TV dağıtım paketlerinden ayrı `com.tvapp.livetv.mobile` kimliğiyle eklendi. Telefonlarda normal başlatıcıdan açılır; TIF bulunmayan cihazlarda DVB izin ve kanal sorgusunu atlayarak IPTV/VOD testlerine devam eder.
* Mobil APK, Releases sayfasında `TVApp-Mobile-Test.apk` dosyalı ayrı bir prerelease olarak yayımlanıyor. `version.json` oluşturmaz ve TV release/güncelleme hattına katılmaz.
* Mobil video yüzeyinde tek dokunma bilgi çubuğunu açıp kapatır, dikey kaydırma kanal değiştirir ve yatay kaydırma kanal listesini açar. Liste ve dialog dokunmaları bu hareketlerden ayrı tutulur.
* Kanal listesi ve infobardaki renk eylemleri yalnız mobil varyantta en az 48dp dokunma hedeflerine dönüştürüldü. TV görünümü, D-pad odağı ve kumanda renk tuşları değiştirilmedi.
* Mobil ana kanal paneli yalnız tam IPTV kütüphanesini açıyor; Tümü/Uydu/Radio kaynak döngüsü mobil akıştan çıkarıldı. Kanal editörü ve IPTV kanal seçimindeki diğer renk eylemleri de dokunmatik kullanıma bağlandı.

### Dialog görünürlüğü
* Koyu TV dialogu yalnız IPTV/XMLTV URL, kaynak adı, Xtream, Stalker ve kanal düzenleme gibi metin girişi içeren pencerelere uygulandı. Kanal uzun-OK işlem menüsü ile diğer seçim/onay pencereleri özgün hafif görünümünü koruyor.

### Editör ve kaynak yönetimi performansı
* URL tabanlı IPTV kaynağında Yenile işleminin kaynağın kendi adresini yinelenen liste sanması düzeltildi; eklenen ve kaldırılan içerikler aynı kaynakta güncelleniyor.
* Kanal uzun basış menüsüne seçilen kanaldan Program Rehberini açma eklendi. Rehberde Kırmızı TIF, Yeşil XMLTV ve Mavi birleşik sonucu ayrı ayrı gösteriyor.
* Genel Program Rehberi açılışta bütün kanal kataloğunu sorgulamak yerine seçili kanal çevresindeki 13 kanallık pencereyi yükleyerek ilk açılış ve gezinme gecikmesini azalttı.
* URL tabanlı IPTV listelerinin adresi, kaynak kimliği ve seçili kanallar korunarak düzenlenebilir hale getirildi; kaynak satırında son başarılı güncelleme zamanı gösteriliyor.
* XMLTV yönetimine adres düzenleme, etkinleştirme/devre dışı bırakma, son güncelleme zamanı ve son yenileme hatası eklendi. Başarısız yenileme mevcut EPG verisini koruyor.
* XMLTV kaynak durumları Room 12→13 migrasyonuyla kullanıcı verisi silinmeden kalıcılaştırıldı; devre dışı kaynaklar eşleştirme ve EPG sorgularından çıkarılıyor.
* Kanal listesinin ±5 EPG penceresine program-bitiş süreli pozitif cache ve 20 saniyelik boş-sonuç cache'i eklendi.
* Pencere sorgusunda TIF verisi yalnız eksik kanallardan alınırken XMLTV fallback tek toplu Room sorgusuna indirildi.
* IPTV kaynak işlem diyaloğu TV temasında görünmeyen standart liste yerine ayrı, odaklanabilir işlem satırlarıyla yeniden kuruldu.
* XMLTV için URL/dosya ekleme, kaynak istatistikleri, yenileme, yeniden adlandırma, eşleştirme ve silmeyi birleştiren merkezi yönetim ekranı eklendi.
* Ayarlar ve Kanal Editörü XMLTV işlemleri aynı merkezi yönetim ekranına bağlandı.
* EPG tanılama son izlenen kanalı kullanıyor; cihazın reddettiği global TIF sorgusu yerine kanal URI sorgusu esas alınıyor.
* Tekrarlanan TIF callback/track logları ve bulunmayan kanal logosu denemeleri sınırlandı.
* Kanal Editörü liste farkı hesabı arka plana alındı ve depo/çizim süreleri debug loguna eklendi.
* URL tabanlı IPTV kaynak işlemleri kısa OK ile görünür hale getirildi; uzun basış menüsü korunuyor.
* XMLTV kaynaklarına kanal/program istatistikleri eklendi ve büyük rehberlerde eşleştirme indeksli hale getirildi.
* URL yenilenirken kullanıcının verdiği XMLTV kaynak adının sıfırlanması engellendi.

### IPTV medya tuşu kontrol modu
* IPTV VOD oynatılırken ekranda OSD yoksa Sol/Sağ, seekbarı doğrudan etkileşimli olarak açıp seçilen yönde 30 saniye sarıyor. Canlı IPTV, kanal listesi ve diğer açık OSD'lerin yön tuşu davranışları değişmiyor.
* IPTV kontrol düğmeleri eşit sütunlara yayılmak yerine içerik genişliğinde ve soldan başlayan kompakt bir grup haline getirildi.
* Kontrol simgeleri metinlerine yaklaştırıldı; kanal değişimi veya infobar zaman aşımında medya kontrolleri pasif duruma dönüyor ve gereksiz mavi odak çerçeveleri temizleniyor.

* IPTV seekbar, üst kanal/EPG/saat satırının altında bağımsız tam genişlikli bir satıra; oynatma işlemleri de ayrı bir alt satıra yerleştirildi. TIF yayınları eski kompakt infobar düzenini koruyor.
* Medya tuşları IPTV kontrol modunu açıyor. Yukarı/Aşağı zaman çizgisi ile işlem satırı arasında geçiyor; kanal değiştirme kontrol açıkken CH+/CH- ile yapılıyor. Seekbar seçiliyken Sol/Sağ sarıyor.
* IPTV kontrol bantları büyütüldü; oynatma eylemleri ikonlu ve yalnız odakta belirginleşen kontrollere dönüştürüldü.
* Çalışma anındaki infobar oranı yeni tasarımla eşitlendi; üst kanal/EPG/saat alanının sıkışması giderildi ve kumanda yardım şeridi ana bilgi kartının altına ayrıldı.
* Infobar yüksekliği içeriğe göre dinamik hale getirildi: TIF görünümü kompakt kalıyor, IPTV görünümü yükleme durumundan bağımsız olarak seek ve işlem satırlarını gösterip ekran merkezine doğru genişliyor.
* IPTV'nin üst kanal/EPG/saat satırı eski infobar yüksekliğinde sabitlendi; seek ve yardım bantları toplam yüksekliğe ayrıca ekleniyor. Kontroller medya Play/Pause tuşuna kadar pasif kalıyor ve bu durumda Yukarı/Aşağı kanal değiştiriyor.
* IPTV orta bandı daha kompakt hale getirildi. Oynat/Duraklat, Buffer, Hız ve Ses/Altyazı/Kalite kontrolleri eşit ikonlu alanlarda gösteriliyor; değişken değerler vurgu rengiyle ayrılıyor. Kumanda ipuçları kaybolmaması için teknik satırın sağ alanına taşındı.
* Oynatma satırı Oynat/Duraklat, Buffer, VOD Hızı ve Ses/Altyazı/Kalite birleşik menüsünden oluşuyor. Buffer ve hız OK ile açılan seçim listesinden ayarlanıyor.

### Kanal listesi performansı
* Debug derlemesinde kanal uzun basış menüsüne Uygulama Tanılama ekranı eklendi. EPG yollarının yanında cihaz yetenekleri, izinler, TIF girişleri, ham kanal sütunları, logo erişimi, Room/IPTV sayıları ve canlı playback callback özeti süre ve sonuçlarıyla ekranda gösterilip debug loguna yazılıyor.
* Tanılama merkezi GitHub'da yayımlanan local release APK'da da kullanılabilir hale getirildi ve Ayarlar içindeki Debug araçları başlığına yatay, Sol/Sağ ile gezilen işlem şeridi eklendi. Play varyantında tanılama kapalı kalıyor.
* EPG güncellemeleri artık büyük kanal listelerinin tamamını her odak hareketinde taramıyor; yalnız program bilgisi değişen satırlar indeks üzerinden güncelleniyor.
* EPG bilgisinin hiç gelmemesine yol açan kaydırmalı pencere denemesi geri alındı; çalışan toplu sorgu ve yalnız değişen satırı yenileyen adapter davranışı korundu.
* Kanal listesi EPG sorgusu odaktaki kanalın 5 öncesi ve 5 sonrasıyla sınırlandı. Hızlı gezinirken sorgu erteleniyor; odak 300 ms sabit kaldığında veya kaydırma durduğunda görünür pencere yükleniyor.
* Periyodik EPG yenilemesi 15.000 kanallık kataloğun tamamı yerine yalnız açık listedeki 11 kanallık pencereyi ve infobardaki etkin kanalı güncelliyor.
* Bazı MediaTek TIF sağlayıcılarının küçük kanal kümelerinde boş döndürdüğü genel program sorgusu liste penceresinde kullanılmıyor; 11 kanal güvenilir kanal-URI sorgusuyla ayrı ayrı okunuyor.
* TIF/DTV kanal logoları ana iş parçacığındaki doğrudan URI çözümlemesi yerine Coil bellek ve sınırlı disk cache hattından yükleniyor.

### Yedekleme
* Bazı Google TV belge sağlayıcılarında görülen `No root for primary` hatasına karşı yedek dışa aktarımı sağlamlaştırıldı. Seçilen URI yazılamazsa yedek otomatik olarak `Downloads/TVApp` klasörüne kaydediliyor.

### IPTV & VOD Seekbar ve Kontrollerinin Infobar'a Entegrasyonu
* Infobar orta alanındaki metinli butonlar kaldırılarak dikey sıkışıklık giderildi; zaman çizgisi (Seekbar) ferah ve temiz bir görünüme kavuşturuldu.
* Kontrol butonları metin içermeyen saf vektör ikonlar (`ic_play`/`ic_pause`, `ic_buffer`, `ic_speed`, `ic_quality`, `ic_audio`, `ic_subtitle`) olarak Infobar altındaki teknik ikon barına (`technical_row`) taşındı.
* IPTV kanallarında bu ikonlar daima görünür ve hazır bekler. Kanal listesi açıkken ikonlar pasif (`alpha = 0.35`, dokunulamaz) hale gelerek yön tuşlarının çakışmasını önler.
* Kumanda yön tuşlarıyla: Zaman çizgisindeyken Sol/Sağ ile sarma, Aşağı tuşuyla alt ikon barına odaklanma, ikonlardayken Sol/Sağ ile gezinme ve `OK` ile işlem yürütme sağlandı. Tampon ve hız değişimleri zarif anlık bildirimle ekranda gösterilir.

### Son İzlenen Kanallar Paneli Modernizasyonu
* Geri tuşuyla açılan son 5 kanal paneli yatay kaydırma gerektirmeyecek şekilde ekran genişliğine eşit olarak paylaştırıldı (`weight=1`).
* Kartlar modern cam panel (`bg_glass_panel`) temasına uyumlu hale getirildi; kanal numarası, kaynak rozeti (DVB/TIF veya IPTV), kalın kanal adı ve alt satırda yayınlanan program başlığı eklendi. Kumanda odağı ve görsel seçilebilirlik güçlendirildi.

### IPTV oynatma OSD ve genel ayarlar
* Oynatma OSD'si satır seçmeli kumanda düzenine geçirildi: Yukarı/Aşağı zaman çizgisi, buffer ve VOD hız satırlarında gezinir; Sol/Sağ seçili satırı değiştirir, buffer `OK` ile uygulanır ve kanal değiştirme yalnız CH+/CH- ile yapılır.
* IPTV buffer hedefi (Otomatik/5-60 saniye) ve varsayılan VOD oynatma hızı genel Oynatma ayarlarına eklendi. OSD ile genel ayarlar aynı kalıcı tercihleri kullanır.

### IPTV buffer ve son kanallar
* Birincil IPTV oynatıcının buffer hedefi Otomatik (0) veya 5-60 saniye seçenekleriyle kalıcı saklanıyor. Otomatik seçim Media3 LoadControl varsayılanlarını, görüntü kalitesi ise her durumda ABR'yi kullanıyor.
* Normal yeniden buffer alma artık donma/yeniden bağlanma sayılmıyor; küçük köşe bildirimi “Buffer alınıyor” gösteriyor. Ortadaki yükleme durumu ilk açılış ve gerçek tekrar yükleme işlemlerine ayrıldı.
* Ana yayında başka OSD yokken Back, altta kumandayla gezilebilen son 5 kanal kartlarını açıyor. LAST CHANNEL/RECALL tuşu önceki kanala doğrudan dönmeye devam ediyor.

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
## Unreleased

- UIINPUT grubunu tamamladık: remote olay sahipliği için `RemoteActionRouter`, güvenli Back/dialog yönlendirmesi ve Android TV UiAutomator smoke testleri eklendi.
- Ayarlar Görünüm ve Kanal sekmelerine video üstü canlı görünüm önizlemesi eklendi; ortak odak çerçevesi, renk eylemi ve ikon yuvası bileşenleri kullanıma alındı.
