# Değişiklik Günlüğü (Changelog)

## 2026-10-29 - VOD Sniffer Fallback Kullanıcı Onayı, HTTP 404 Koruması ve VOD Ekranına Dönüş Güvencesi

- **VOD Tarayıcı Fallback Kullanıcı Onayı (`VodHost.kt` & `MainActivity.kt`):**
  - Sniffer akış bulamadığında kullanıcı doğrudan tarayıcıya fırlatılmaz; TV kumandasına uygun şık bir diyalog açılarak kullanıcıya onay sorulur (*"Web sayfasına gidip kumandayla başlatmak ister misiniz?"* -> [Tarayıcıda Aç] [VOD Ekranına Dön]).
- **HTTP 404 (Kaynak Yok) Koruması (`WebVideoResolver.kt`, `VodPageResolver.kt` & `VodHost.kt`):**
  - Web sayfası veya medya kaynağı HTTP 404 (Not Found) verdiğinde içerik silinmiş/mevcut olmadığından fallback kesinlikle teklif edilmez; doğrudan "İçerik kaynak sunucuda bulunamadı (HTTP 404)" hatası gösterilir.
- **Geri (Back) Tuşunda Canlı TV Yerine Doğrudan VOD Ekranına Dönüş (`TvBrowserActivity.kt` & `RemoteActionRouter.kt`):**
  - VOD fallback ile açılan web tarayıcısından veya VOD oynatıcı hata ekranından Geri (Back) tuşuna basıldığında Canlı TV'nin arkada açığa çıkması engellendi; doğrudan VOD Ana Ekranı'na (`VodHomeActivity`) dönülmesi sağlandı.

## 2026-10-29 - VOD Sniffer Çözümleyemediğinde Web Tarayıcısına Otomatik Fallback Desteği

- **VOD Web Tarayıcı Fallback Mekanizması (`VodHost.kt` & `MainActivity.kt`):**
  - Arka plandaki sniffer video akışı bulamadığında veya bot/etkileşim korumasına takıldığında oynatıcıyı hata ekranına kilitlemek yerine sayfayı otomatik olarak `TvBrowserActivity` içinde açma desteği getirildi.
  - Kullanıcı TV kumandasıyla sayfadaki Oynat butonuna bastığında veya reklamı geçtiğinde, sniffer videoyu algıladığı anda sayfayı kapatıp doğrudan ana TVApp oynatıcısına devrederek tam ekran oynatır.
- **Ayarlardan Yapılandırılabilirlik (`DisplayPreferencesStore.kt` & `DisplaySettingsActivity.kt`):**
  - Ayarlar > Oynatma Ayarları altına "VOD Web Sayfası Tarayıcı Desteği" seçeneği eklendi. Kullanıcı bu davranışı dilediğinde açıp kapatabilir (varsayılan: açık).
- **Tarayıcı İpuçları ve Temiz Geçiş (`TvBrowserActivity.kt` & `values/strings.xml`):**
  - VOD fallback ile tarayıcı açıldığında kullanıcıya rehberlik eden bilgilendirici bildirim ("Video başlatmak için sayfadaki Oynat tuşuna basın") gösterilir.

## 2026-10-28 - Hata Ekranı Kumanda Kilitlenmesinin Çözümü, İlk Açılış Animasyon Optimizasyonu, Sniffer Koruma & Gelişmiş Tanılama OSD

- **Hata (Source Error) Ekranında Kumanda Kilitlenmesinin Çözümü (`RemoteInputRouter.kt`):**
  - Hata ekranı (`isPlaybackErrorVisible`) açıkken kumanda yön tuşlarının (`DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`) yutulması engellendi.
  - VOD modunda hata anında olaylar doğrudan `VodPlayerOverlayController`'a aktarılarak Yenile butonundan çıkıp alt çubuktaki Stop, Bölümler veya Çıkış butonlarına serbestçe geçilmesi sağlandı.
  - Canlı TV modunda hata anında D-pad Sol veya Menü tuşuyla kanal panelinin açılabilmesi, Yukarı/Aşağı ile zap yapılabilmesi ve Geri tuşuyla hatadan güvenle çıkılabilmesi sağlandı.
- **Oynatıcı İlk Açılışındaki Duraksamanın / Animasyon Donmasının Giderilmesi (`MainActivity.kt` & `activity_main.xml`):**
  - `blackout_animation` için `app:lottie_rawRes="@raw/channel_tuning"` doğrudan layout XML'ine entegre edildi.
  - `MainActivity.onCreate` anında `LottieCompositionFactory.fromRawRes` ile animasyon arka planda önceden ısıtıldı (preload). Böylece ilk açılışta ve karartma anında animasyon sıfır duraksama ile anında akıcı şekilde oynamaya başlar.
- **Web Sniffer Akış Bulunamama Koruması (`VodHost.kt` & `values/strings.xml`):**
  - Web sayfası veya sniffer geçerli bir video akışı (m3u8, mp4 vb.) yakalayamadığında ham HTML sayfasının ExoPlayer'a verilmesi engellendi (`VodCandidateDecision.Failure`).
  - Çözümlenemeyen web sayfalarında gereksiz hata döngüsü yerine doğrudan "Web sayfasında oynatılabilir video akışı bulunamadı" açıklaması gösterilir.
- **Gelişmiş Tanılama ve Hata Detayı OSD'si (`MainActivity.kt` & `DisplaySettingsActivity.kt`):**
  - Ayarlar > Gelişmiş Tanılama açıkken hata ekranına yayının çalıştığı metot (`[Metot: Web Sniffer]`, `[Metot: HLS (m3u8)]`, `[Metot: Dosya (MP4/MKV)]`, `[Metot: DVB/TIF]`), tam HTTP durum kodu (`[HTTP 404 (Bulunamadı)]`, `[HTTP 403]`, `[HTTP 500]`), hata kodu adı ve maskelenmiş kaynak URL'si eklenir.
  - Ayar kapalıyken sade ve temiz kullanıcı dostu hata mesajı gösterilir.
- **Hata Anında Canlı TV Infobar'ının Kalıcı Gösterimi ve Otomatik Gizlenme Dengesi (`MainActivity.kt`):**
  - Hata oluştuğunda infobar'ın zaman aşımıyla kaybolması engellendi; hata ekranda kaldığı sürece infobar kalıcı olarak görünür ve kullanıcı kanal ile yayın akışı bilgisini altta her an görebilir.
  - Yayın başarıyla oynamaya başladığında veya hata kapandığında infobar normal süre (5-8 sn) sonunda otomatik olarak kaybolur.
- **Modern ve Sade Yenileme Simgesi (`ic_replay_modern.xml`, `bg_replay_play_focused.xml` & `activity_main.xml`):**
  - Ortadaki kaba bileşik ok-üçgen simgesi yerine modern, estetik, yuvarlatılmış kıvrımlı TV standartlarında dairesel yenileme ikonu (`ic_replay_modern.xml`) ve cam efektli zarif odak seçicisi yerleştirildi.

## 2026-10-28 - VOD Yüklenirken Stop Butonu, Sağ Bölümler Paneli, Yenileme Odak Tuzağının Çözümü ve Dizi Resume Desteği

- **Hata ve Yenile Butonunun Kumanda Odağından Çıkabilmesi (`VodPlayerOverlayController.kt` & `activity_main.xml`):**
  - Yenile butonu (`playback_error_retry_btn`) üzerindeki hard-coded odak kilitleri temizlendi; buton kumanda navigasyonuna (`VodControlTarget.RETRY`) dahil edildi.
  - Hata ikonundan D-pad Aşağı ile zaman çubuğu ve oynatma butonlarına, D-pad Yukarı ile üst bara, Sol/Sağ ile diğer butonlara serbestçe geçiş sağlandı.
- **Yüklenirken Stop Butonu ve Bağlantıyı Kesme (`VodPlayerOverlayController.kt` & `MainActivity.kt`):**
  - Akış yüklenirken (`PREPARING` / `BUFFERING` / `blackoutView`) alt kontrol çubuğundaki oynat butonu dinamik olarak kare `Stop` simgesine (`ic_stop.xml`) dönüşür.
  - Yükleme esnasında Stop butonuna tıklandığında video bağlantısı iptal edilir (`iptvPlayback.stop()`, `endBlackout`, `cancelResolve`), oynatıcı açık kalır ve kullanıcı beklemeden sağdaki Bölümler butonuna geçebilir.
- **Yandan Açılan Modern Dizi Bölümleri Paneli (`VodEpisodePanelAdapter.kt`, `activity_main.xml` & `VodPlayerOverlayController.kt`):**
  - Eski ilkel `AlertDialog` kaldırıldı; Bölümler butonuna tıklandığında ekranın sağ tarafından kayan şık `vod_episodes_panel` açılır.
  - Panelde aktif izlenen bölüm ("Oynatılıyor" rozeti) belirginleştirilir; D-pad Yukarı/Aşağı ile liste içi akıcı kaydırma, D-pad Sol veya Back ile paneli kapatıp oynatıcıya dönme sağlandı.
  - Tekil filmlerde veya bölümü olmayan VOD içeriklerinde `Bölümler` butonu otomatik gizlenir (`View.GONE`).
- **Tüm Yayınlar İçin Evrensel Resume & Sıfır Yükle Hafif Kayıt Stratejisi (`MainActivity.kt`, `PlaybackRules.kt`):**
  - Sadece dizi değil; süresi olan veya VOD olan tüm yayınlar (filmler, diziler, arşiv/timeshift) için izleme pozisyonu otomatik hatırlanır ve açılışta kalınan yerden devam edilir.
  - Her 5 saniyede bir çalışan SQL sorguları kaldırıldı; dizi bilgisi oynatmanın başında 1 defa önbelleğe alınır.
  - Oynatma sırasında diske yazma işlemi 25-30 saniyelik aralıklara çekildi (throttle); duraklatma, durdurma ve çıkış anlarında `force = true` ile tek seferde güvenle kaydedilerek sistem işlemcisi ve flash depolaması korunur.
- **Afiş Kartlarında İzleme İlerleme Çubuğu (`item_vod_poster_card.xml`, `VodPosterAdapter.kt`, `VodHomeActivity.kt`):**
  - VOD afişlerinin alt tabanına modern, şık bir ilerleme çubuğu (`vod_card_resume_progress`, `bg_vod_card_progress.xml`) eklendi.
  - Hem "Devam Et" rayında hem de ana ızgara/katalogda izlenen yayının yüzde kaçında kalındığı anında görsel olarak gösterilir.
- **Dizi İzleme Pozisyonu ve Bölüm Hatırlama (Continue Watching) (`IptvResumeStore.kt`, `MainActivity.kt` & `VodHomeActivity.kt`):**
  - Dizi bölümleri izlenirken kalınan saniye ve son izlenen bölüm bilgisi ana dizi kaydıyla (`saveSeriesEpisode`) ilişkilendirilerek kalıcı olarak saklanır.
  - VOD Ana Ekranı'nda veya Devam Et rayında bir diziye tıklandığında doğrudan son kalınan bölüme ve son saniyesine kesintisiz devam edilir (`playLastWatchedEpisodeOrDialog`).

- **Browser Video Oynatma Crash'i ve Sniffer Düzeltmesi (`TvBrowserActivity.kt` & `StreamSniffer.kt`):**
  - Chromium `shouldInterceptRequest` arka plan iş parçacığından `webView.settings` okunması nedeniyle oluşan `RuntimeException` (crash) kökten giderildi; User-Agent ana iş parçacığında güvenli olarak önbelleğe alındı (`cachedUserAgent`).
  - Sayfadaki video adaylarının yakalanması ve duyurulması anlık debug loglarıyla (`SNIFFER_DETECT`, `SNIFFER_ANNOUNCE`) kayıt altına alındı.
- **Canlı Kanaldan VOD Açılışındaki Diyalog, Flash ve Başlık Çakışmasının Çözümü (`MainActivity.kt` & `VodPlayerOverlayController.kt`):**
  - Canlı kanaldan VOD içeriğine geçildiğinde önceki kanal adının arayüzde kalması engellendi; `VodPlayerOverlayController` içinde `pendingTitle` önceliklendirildi ve canlı kanal adının sızması önlendi.
  - VOD modunda eski gri arabelleğe alma kutusu (`iptv_buffering_container`) tamamen devre dışı bırakılarak ekrandaki titreme/flaş (flash) ortadan kaldırıldı.
  - VOD açılışında `blackScreenWhileTuning` tercihi kapalı olsa dahi Lottie yükleniyor animasyonu (`channel_tuning.json`) ve film adıyla birlikte durum bilgilendirme metni ortalanmış olarak zorunlu çalıştırıldı.
- **VOD Player Başlığı Anında Senkronizasyonu (`VodHost.kt` & `VodPlayerOverlayController.kt`):**
  - VOD oynatıcı ilk açıldığında arka plandaki DB çözümlemesi tamamlanana kadar başlığın boş veya önceki içeriği göstermesi engellendi; aktarılan `payload.title` ve `payload.episodeTitle` bilgisi `pendingTitle` ile oynatıcı arayüzüne anında basıldı.
- **Lottie Yükleniyor Animasyonu ve Tek Satır Bilgilendirme Metni (`activity_main.xml` & `MainActivity.kt`):**
  - Kanal değişimi ve VOD yükleme karartması (`blackout_view`) sırasında ortada yer alan `LottieAnimationView` (`channel_tuning.json`) altına tek satırlık modern durum metni (`blackout_status_text`) eklendi ("VOD açılıyor...", "Bağlanıyor...", kanal adı).
  - Yükleme sırasında eski gri arabelleğe alma kutusu (`iptv_buffering_container`) tamamen gizlenerek Lottie üzerine binmesi ve çift yazı basılması engellendi.
- **Kutusuz Şeffaf Hata Arayüzü & Odaklanabilir Replay-Play İkonu (`activity_main.xml`, `ic_replay_play.xml`, `bg_replay_play_focused.xml`):**
  - Hata ekranındaki tüm kutu, kart ve çerçeve arka planları kaldırıldı (`@android:color/transparent`); sadece üstte gölgeli hata sebebi metni ve ortada beyaz dairesel ok + play simgesi (`ic_replay_play.xml`) yerleştirildi.
  - İkon kumanda ile odaklanabilir yapıldı (`bg_replay_play_focused.xml`); odak yokken şeffaf, odaklandığında zarif dairesel vurgu alır.
- **VOD Kontrollerinin Yalnızca Oynatılırken Gizlenmesi Kuralı (`VodPlayerOverlayController.kt`, `IptvPlaybackController.kt` & `MainActivity.kt`):**
  - VOD oynatıcı kontrolleri artık yalnızca video aktif olarak çalarken (`isPlaying == true && phase == READY`) 8 saniye sonra otomatik gizlenir.
  - Video duraklatıldığında (PAUSE), tamponlanırken, yüklenirken veya hata durumunda kontroller asla otomatik gizlenmez, ekranda sabit ve görünür kalır (`onIsPlayingChanged`).
- **Ayarlar Ekranının Üst Sekmeli Sade Düzenine Kavuşturulması (`DisplaySettingsActivity.kt` & `activity_display_settings.xml`):**
  - Çift sütun (master-detail) yapısı kaldırılarak kategoriler ekranın üst tarafında yatay kayan çip çubuğuna alındı; TV panel genişliği %48'e (`SETTINGS_PANEL_WIDTH_FRACTION = 0.48f`) optimize edildi.
  - Değerleri D-pad Sol/Sağ ile değiştirilen ayarlarda odak karmaşası giderildi; Sol/Sağ tuşları ayarın değerini değiştirir, D-pad Yukarı ile üst sekmelere geçilir.
  - Satır başlıklarının kelime ortasından hecelenerek iki satıra bölünmesi engellendi (`isSingleLine = true`, `ellipsize = END`); değer kapsülüne maksimum 240dp genişlik sınırı verildi.
  - Güncelleme ve eşitleme butonlarına tıklandığında görünümün devre dışı bırakılması (`isEnabled = false`) kaldırılarak odağın kilitli kalması sağlandı, odağın üstteki `X` kapat butonuna fırlaması engellendi.
- **GitHub Actions ve Release 7 Gün Budama Kuralı (`release.yml`):**
  - 7 günden eski tüm geçmiş workflow çalıştırmaları, loglar, eski release'ler ve içindeki APK artifact'leri temizlendi.
  - `release.yml` CI/CD hattına 7 günden eski release'leri otomatik silen adım eklenerek GitHub depolama kotası güvenceye alındı.

## 2026-10-27 - Ayarlar Ekranı Modernizasyonu, Dikey Telefon Dokunmatik ve D-Pad Chip Tasarımı

- **TV İçin Master-Detail (Çift Sütun) Mimarisi ve Dikey Telefon Portre Ayrımı (`layout/` & `layout-port/`):**
  - TV ve yatay ekranlarda ayarlar ekranı gerçek Master-Detail düzenine kavuşturuldu: Sol sütunda dikey kategoriler (`bg_settings_master_tab.xml`), sağ sütunda ise seçili kategorinin detay ayarları listelenir.
  - OSD genişliği %58'e (`SETTINGS_PANEL_WIDTH_FRACTION = 0.58f`) optimize edildi; sol %42 alanda arka plan TV videosu kesintisiz izlenmeye devam eder.
  - Kumanda D-pad Yukarı/Aşağı ile kategoriler gezilirken sağ panel anında güncellenir; D-pad Sağ veya OK ile ayarlara inilir.
  - Ayarlar listesindeyken `BACK` veya değer değiştirmeyen satırlarda Sol tuşuna basıldığında odak sol kategori menüsüne döner; sol menüde `BACK` ayarları kapatır.
  - Dikey telefonda (`layout-port`) ise üstte yatay kayan çip çubuğu + altta kartlar yapısı korunarak ergonomik mobil kullanım sağlandı.
- **Ayarlar Ekranı Chip/Pill Sekme Çubuğu ve Kart Düzeni (`DisplaySettingsActivity.kt` & `activity_display_settings.xml`):**
  - Üst sekmeler (`Görünüm`, `Kanallar`, `IPTV & EPG`, `Sistem`) yatay kaydırılabilir modern çip (chip/pill) çubuğuna dönüştürüldü (`bg_settings_chip.xml`).
  - Dikey telefonda sekmeler dar ekranlarda sıkışmaz, taşma yapmaz ve dokunarak akıcı biçimde kaydırılabilir; TV kumandasında ise D-pad Sol/Sağ gezinimi aktif çipi otomatik ortalar (`smoothScrollTo`).
  - Ayar grupları modern yarı saydam koyu cam kartlar (`bg_settings_card.xml`) ve ince ayırıcılar içine alındı.
  - Açık/Kapalı anahtarları renkli durum hapları (`bg_settings_pill_on.xml` / `bg_settings_pill_off.xml`), değer seçiciler ve aksiyon butonları modern kapsül etiketler (`bg_settings_pill_neutral.xml`) ile görselleştirildi.
  - Başlık alanına şık ayarlar ikonu eklendi; kumanda ipucu yazısı mobil dokunmatik modda ekranda yer kaplamaması için otomatik gizlendi.

## 2026-10-26 - VOD Player Yükleme UI Kararlılığı, Bağımsız Seekbar, Ses Odağı, Kompakt Sıralama ve Renkli Klasik İkon

- **VOD Player Yükleme ve Hazırlık Sırasında UI Görünürlüğü (`VodPlayerOverlayController.kt` & `MainActivity.kt`):**
  - İçerik yüklenirken veya arabelleğe alınırken ("Açılıyor..." / buffering göstergesi) oynatıcı kontrol arayüzünün otomatik gizlenmesi engellendi; yükleme diyaloğu ekrandayken oynatıcı başlığı, üst bar ve zaman çubuğu açık kalır.
  - Video hazır olup akıcı biçimde oynamaya başladığında (`IptvPlaybackPhase.READY`) 8 saniyelik otomatik gizleme (`scheduleAutoHide`) devreye girerek ekranı temizler.
- **Play/Pause Butonunun Alt Kontrol Satırına Taşınması ve Bağımsız Seek Çubuğu (`activity_main.xml` & `VodPlayerOverlayController.kt`):**
  - Play/Pause butonu zaman çubuğu satırından çıkarılarak aşağıdaki kontrol butonları satırının başına taşındı.
  - Zaman çubuğunda (seek) kumanda ile sağa/sola sarma yaparken oynat butonuna takılma veya yanlışlıkla durdurma sorunu tamamen giderildi; zaman çubuğu bağımsız ve akıcı hale getirildi.
- **Ses Kontrolü Odaklanması ve D-Pad Entegrasyonu (`VodPlayerOverlayController.kt` & `activity_main.xml`):**
  - Alt bardaki ses çubuğu kapsülü (`vod_volume_container`) odaklanabilir hale getirildi ve `VodControlTarget.VOLUME` hedefiyle D-pad navigasyonuna dahil edildi.
  - Odaklandığında belirgin vurgu alır; kumandadan ses tuşları veya OK ile mute/unmute yapılabilir ve ses çubuğu anlık olarak güncellenir.
- **Bölüm Listesi Butonu İyileştirmesi (`activity_main.xml` & `VodPlayerOverlayController.kt`):**
  - Raw metin oku (`⌄`) yerine modern vektör ikon (`ic_arrow_drop_down`) eklendi.
  - Tekil filmlerde veya dizi bölümü olmayan içeriklerde buton tamamen gizlenerek (`View.GONE`) gereksiz arayüz kalabalığı önlendi.
- **VOD Kütüphanesinde Kompakt Sıralama Combobox'ı (`activity_vod_home.xml` & `VodHomeActivity.kt`):**
  - Sol panelde 120dp yer kaplayan 4 dikey sıralama butonu kaldırılarak tek satırlık şık ve modern `vod_sort_combo` açılır menüsüne dönüştürüldü.
  - Tıklandığında diyalog ile sıralama seçimi sunulur; sol panelde kategoriler listesi için geniş dikey alan kazanıldı.
- **Klasik TV Uygulama Simgesinin Renklendirilmesi (`ic_app_launcher.xml`):**
  - Sevilen klasik TV ikonu formu korunarak koyu gece mavisi zemin, neon turkuaz antenler, canlı okyanus mavisi ekran ve oynat üçgeni ile modern ve göz alıcı bir launcher simgesine dönüştürüldü.

## 2026-10-26 - VOD Oynatıcı Doğrudan Akış İzolasyonu, HTTP 403 ve UI Titremesi (Flash) Düzeltmesi

- **VOD Oynatıcının Web Çözümleyiciden İzolasyonu & HTTP 403 Koruması (`MainActivity.kt` & `WebVideoResolver.kt`):**
  - M3U ve IPTV listelerindeki doğrudan medya akışları (`.m3u8`, `.mp4`, `.mkv`, `.ts`, `.mpd`, `.avi`, `.webm`, `.flv`, `/movie/`, `/series/`, `/live/`), Web tarayıcısı / `WebVideoResolver` sürecine girmeden doğrudan ExoPlayer'a aktarılacak şekilde ayrıştırıldı.
  - `WebVideoResolver`'ın doğrudan akışlara varsayılan Chrome Masaüstü `User-Agent` zorlaması kaldırıldı (`userAgent = null`). Böylece IPTV sağlayıcısının kendi User-Agent ve Referer başlıklarının ezilmesi ve sunucuların HTTP 403 Forbidden dönmesi engellendi.
  - JSON listelerindeki web tabanlı VOD içerikleri (`ha.vixolity.com` vb.) arka planda izole biçimde çözümlenmeye ve ExoPlayer'a aktarılmaya devam eder.
- **VOD Başlatılırken Oluşan Arayüz Titremesinin (Flash Aç/Kapa) Giderilmesi (`MainActivity.kt`):**
  - Film açılırken kanal henüz arka planda çözümlenirken yapılan erken `currentChannel = pendingChannel` ataması ve zamansız `showVodPlayerControls` çağrısı kaldırıldı.
  - Oynatıcı yükleme esnasında yalnızca arabelleğe alma animasyonu gösterilir ve akış hazır olduğunda `selectChannel` tek seferde kararlı biçimde başlatılarak arayüzdeki çift açılıp kapanma etkisi tamamen ortadan kaldırıldı.

## 2026-10-26 - VOD Player Kapanma Düzeltmesi, Web Tarayıcı Üst Bar Auto-Hide, Fare Silikleşme & Modern İkon

- **VOD Player Anında Kapanma / Kütüphaneye Geri Dönme Sorununun Giderilmesi (`MainActivity.kt`):**
  - VOD kütüphanesinden bir film veya dizi seçildiğinde `WebVideoResolver` çözümlemesi başarısız olsa dahi akış doğrudan oynatıcıya (`Media3/ExoPlayer`) aktarılarak fallback oynatma sağlandı.
  - Oynatıcı açılır açılmaz anında VOD kütüphanesine geri dönülmesine neden olan zoraki `openVodLibrary()` çağrıları kaldırıldı; kullanıcı kütüphaneye sadece kumandadan `BACK` tuşuna basarsa dönüyor.
- **Web Tarayıcısı Üst Toolbar Otomatik Gizleme (Auto-Hide) ve Ayarlar (`TvBrowserActivity.kt`):**
  - Web sayfasında gezinirken üst toolbar 3.5 saniye hareketsizlik sonrası otomatik olarak gizlenir.
  - Tarayıcı Ayarları diyaloğuna "Üst Çubuğu Otomatik Gizle" (Auto-hide Top Bar) seçeneği eklendi, tercihler `SharedPreferences` ile saklanır.
  - Kumandadan `MENU`, `SETTINGS` tuşuna basıldığında veya fare ekranın en üstüne ulaştığında üst toolbar anında geri açılır.
- **Sanal Fare (Mouse) Hareketsizlik Silikleşmesi ve Üst Bara Geçiş İyileştirmesi (`CursorLayout.kt` & `CursorController.kt`):**
  - Fare 2.5 saniye hareketsiz kaldığında ekranı kapatmaması için otomatik olarak silikleşir / görünmez olur (`idle fade-out`), kumanda hareket ettirildiği anda anında canlanır.
  - Farenin ekranın en üstüne ulaştığında üst araç çubuğuna geçişini engelleyen sınır kontrolleri ve `dispatchKeyEvent` yönlendirmesi düzeltildi.
- **Webview Üzerindeki Yüzen Oynat Butonunun Kaldırılması (`activity_tv_browser.xml`):**
  - Ekranın alt kısmında görünen yeşil yüzen buton (`browser_floating_play_chip`) kaldırıldı; video oynatma kontrolleri yalnızca üst araç çubuğunda tutuldu.
- **Beyaz Zeminlerde Net Görünen Modern TVApp İkonu (`ic_app_launcher.xml` & `AndroidManifest.xml`):**
  - Açık ve beyaz zeminli arayüzlerde kaybolan beyaz ikon yerine koyu lacivert/gece mavisi gradyan arka plan, neon turkuaz hatlar ve canlı TV sembolü içeren modern `ic_app_launcher.xml` oluşturuldu ve uygulama launcher ikonu olarak tanımlandı.

## 2026-10-25 - Quick Hub 2x4 Menüsü, VOD Hata Döngüsü Koruması ve Odak İyileştirmeleri

- **Canlı TV Ekranında OK Tuşuna Uzun Basma ile Modern Quick Hub Menüsü (`MainActivity` & `dialog_quick_hub.xml`):**
  - Canlı TV oynatılırken OK tuşuna basılı tutulduğunda (500 ms) açılan modern, koyu cam zeminli 2 satır x 4 sütun Quick Hub menüsü eklendi.
  - 8 TV kısayolu entegre edildi: Canlı TV (Kanal Paneli), VOD Arşivi, IPTV Listeleri, XMLTV / EPG Ayarları, Web Tarayıcı (Mrowser), Program Rehberi, Çoklu Ekran (Multi-View), Görüntü ve Sistem Ayarları.
  - Odaklanan karta göre dinamik güncellenen 1 satırlık bilgilendirici ipucu (hint) metni eklendi.
- **VOD Hata Döngüsünün Kesilmesi ve Mod Koruma (`MainActivity`):**
  - VOD akışlarının açılamaması veya oynatılamaması durumunda player hatalarının ana sayfalara sızarak arayüzü kilitlemesi engellendi; arka plan işlemleri temizce sonlandırıldı.
  - Status diyaloğunda "Kapat" butonuna veya kumandadan "Geri (Back)" tuşuna basıldığında çalışan merkezi `dismissStatusDialog()` mekanizması kuruldu:
    - Arka plandaki stream çözümleme (`vodResolveJob`), oynatıcı akışı (`iptvPlayback.stop()`) ve arabelleğe alma animasyonu derhal durdurulur.
    - Hata diyaloğu kapatılır ve kullanıcının mevcut modu korunur (VOD'dan gelindiyse VOD kütüphanesine, Canlı TV'de ise canlı yayına temiz dönüş yapılır).
- **Status Diyaloğunda ("Yeniden Dene / Kapat") Odaklanma Probleminin Giderilmesi (`activity_main.xml` & `MainActivity`):**
  - `status_panel` için `descendantFocusability="afterDescendants"`, butonlar için açık `isFocusable = true`, `focusableInTouchMode = true` ve `nextFocus` yönlendirmeleri tanımlandı.
  - Panel açıldığında `binding.statusPanel.post { binding.retryButton.requestFocus() }` ile kumanda odağının buton üzerinde hazır bulunması garanti altına alındı.
- **Mrowser Web Tarayıcısı Live TV Dönüş Butonu İyileştirmesi (`activity_tv_browser.xml`):**
  - TV'ye dön butonu (`btn_browser_exit_tv`) diğer yuvarlak kapsül butonlarla aynı boyuta (`bg_browser_bar_icon`), `padding="9dp"` ve ortalanmış TV simgesine kavuşturuldu.

## 2026-10-24 - VOD Akış Çözümleme, Çerez (Cookie) Desteği, SSL Bypass ve Loop Önleme

- **ExoPlayer HTTP Veri Kaynağına Cookie ve SSL Trust-All Desteği (`IptvDataSourceFactory` & `LiveChannel`):**
  - Web tabanlı video akışlarında CDN'lerin ve korumalı sunucuların talep ettiği oturum/Cloudflare çerezlerinin (`Cookie`) ExoPlayer HTTP başlıklarına eklenmesi sağlandı.
  - SSL sertifikası süresi dolmuş veya geçersiz web sitelerinde oluşan `SSLHandshakeException` ve buna bağlı `Source error / Kanal açılamadı` hatası, OkHttp istemcisine `trustAll` SSL soket desteği eklenerek tamamen çözüldü.
- **Sniffer Pencereye Bağlantı ve Çerez Yakalama (`WebVideoResolver`):**
  - `mrowser` mimarisi referans alınarak, arka planda çalışan WebView Activity DecorView'a iliştirildi (`attach`); böylece modern web sitelerindeki oynatıcıların görünmeyen pencerelerde JavaScript ve video oynatmayı dondurması engellendi.
  - Ağdan geçen akışlara ait `Referer`, `Origin` ve `CookieManager` çerezleri anında toplanıp ExoPlayer'a teslim edilecek yapı kuruldu.
- **VOD Intent Tüketimi ve Canlı TV Temizliği (`MainActivity`):**
  - `handleVodSourceDeepLink` içerisinde gelen VOD extra'ları (`EXTRA_VOD_STREAM_URL`, `EXTRA_VOD_SOURCE_KEY`, vb.) anında silinerek `onResume` / odak değişimlerinde tekrar tetiklenen sonsuz döngü (loop) engellendi.
  - "Kanal açılamadı" diyaloğunda "KAPAT" butonuna basıldığında siyah ekranda asılı kalmak yerine doğrudan VOD kütüphanesine dönülmesi sağlandı.
  - Canlı TV'ye geçişte VOD kanal referansı temizlenerek boş infobar çıkması önlendi.
- **VOD Film Akış Çözümleme ve Loading Deneyimi (`VodHomeActivity` & `MainActivity`):**
  - Film listesinden bir içeriğe tıklandığında siyah ekranda bekleme ve boş canlı TV infobarı gelmesi sorunu giderildi.
  - Tıklanır tıklanmaz `MainActivity`'de VOD oynatıcı arayüzü ve TV buffering loading simgesi hemen ekrana gelir.
  - Web tabanlı film sayfaları (filmmodu, web embed vb.) ExoPlayer'a verilmeden önce arka planda `WebVideoResolver` ile m3u8/mp4 akışına çözülür; çözülemeyen veya hatalı sertifikalı sayfalarda WebView SSL bypass ve timeout koruması ile kilitlenme önlenir.
- **VOD Başlık Formatı ve Sezon/Bölüm Entegrasyonu (`VodPlayerOverlayController` & `VodHomeActivity`):**
  - Oynatıcı üst başlığında dizi adının sonunda sezon ve bölüm bilgisi (`Dizi Adı · 1. Sezon 1. Bölüm`) birleşik olarak gösterilir.
  - Dinamik JSON dizilerinde bölüm başlığı açıkça `X. Sezon Y. Bölüm` olarak formatlanır.
- **Oynatıcı İçi Bölüm Değiştirme Desteği (`MainActivity`):**
  - Oynatıcıdaki "Liste" butonundan veya bölüm seçicisinden başka bir bölüm seçildiğinde yayının değişmemesi hatası giderildi (bölüm bazlı tekil anahtar yapısı oluşturuldu).


- **JSON VOD Kaynaklarının Hafifletilmiş On-Demand Mimarisi (`IptvRepository`, `IptvSourcesActivity`, `VodHomeActivity`):**
  - Toplu içe aktarma sırasında on binlerce satırlık JSON VOD listelerinin uygulamayı dondurmasını engellemek üzere hafifletilmiş kayıt (`KIND_JSON_VOD`) yapısı kuruldu.
  - Listeler ana IPTV kanal listesine anında saniyeler içinde eklenir; içerik veritabanı kilitlenmesi önlenir.
  - VOD ekranında bir JSON VOD arşivi ilk kez seçildiğinde içerikler arka planda on-demand olarak indirilerek ("VOD Arşivi hazırlanıyor, lütfen bekleyin...") afiş ızgarası doldurulur.
  - IPTV Listeleri ekranında JSON VOD kaynakları için "VOD Arşivi" etiketi, özel simge ve tıklandığında "VOD Kütüphanesinde Aç", "İçerikleri Yenile" gibi seçenekler sunan aksiyon menüsü eklendi.
- **Web Akış Başlıkları (Referer/User-Agent) ve Çözümleme Koruması (`WebVideoResolver` & `MainActivity`):**
  - Web sayfalarından çözülen `.m3u8` akışlarının CDN engellerine (403 Forbidden vb.) takılmaması için asıl sayfa URL'si `Referer` ve `Origin`, Android TV tarayıcı kimliği ise `User-Agent` başlığı olarak ExoPlayer'a iletildi.
  - Çözülemeyen web sayfalarının doğrudan video oynatıcıya verilip oynatıcıyı hata döngüsüne sokması engellendi; kullanıcıya bilgilendirici uyarı mesajı gösterildi.
- **VOD Player Başlık Hiyerarşisi ve "Liste" Bölüm Seçici (`VodPlayerOverlayController` & `MainActivity`):**
  - Dizi oynatılırken üst barda dizi adı (`displayName`) büyük ana başlık, sezon ve bölüm bilgisi (`epgTitle`) ise zarif bir alt bilgi olarak konumlandırıldı.
  - Oynatıcı alt barda sağdaki "Liste ⌄" butonuna tıklandığında dizi için sezon ve bölümleri listeleyen TV uyumlu bölüm seçici açılır; seçilen bölüm oynatıcıdan çıkmadan doğrudan başlatılır.

## 2026-10-24 - Yardımcı JSON VOD Kaynakları, Akıllı Web Video Çözümleyici (WebVideoResolver) ve Dizi Bölüm Seçici

- **Web Sayfalarından Otomatik Medya Akışı Çözümleme (`WebVideoResolver`):**
  - Web sayfası bağlantılarını (`kanald.com.tr`, `filmmodu`, `hdfilmcehennemi`, `dizibox` vb.) arka planda çözümleyen akıllı HTTP çözümleyici motoru geliştirildi:
    - Sayfa HTML'i içindeki `<script type="application/ld+json">` Schema.org `VideoObject` nesnelerinden `"contentUrl"` doğrudan okunarak milisaniyeler içinde `.m3u8` akış adresi elde edilir.
    - JSON-LD bulunamayan sayfalarda Regex tabanlı derin HLS (`.m3u8`) ve MP4 taraması yapılır.
    - İframe ve harici oynatıcı yerleştirmeleri takip edilerek oynatılabilir asıl akış URL'si dinamik olarak ayrıştırılır.
  - VOD derin bağlantısı (`MainActivity.kt`) açılırken linkin web sayfası olması durumunda oynatıcı başlamadan önce arka planda `WebVideoResolver.resolve` çalıştırılarak canlı oynatıcıya doğrudan çözülmüş akış iletilir.
- **Diziler İçin Bölüm Seçici Diyaloğu (`VodHomeActivity` & `VodDao`):**
  - JSON VOD arşivlerinde yer alan binlerce dizi bölümü için `episodesForSeries` Room sorgusu eklendi.
  - Dizi kartına tıklandığında doğrudan ilk linki açmak yerine sezon ve bölüm bilgilerini listeleyen akıcı bir TV diyalogu açılır; kullanıcı kumandadan istediği bölümü seçip izleyebilir.
- **IPTV Listeleri Ekranında VOD İçerik Sayımı (`IptvDao`, `IptvRepository`, `IptvSourcesActivity`):**
  - Yalnızca `iptv_channels` tablosunu sayan ve JSON VOD listelerinde "0 kanal (0 seçili)" görünmesine neden olan istatistik yapısı düzeltildi.
  - Kaynak bir VOD listesi ise veya Canlı TV kanalı içermiyorsa, kartta doğrudan `vod_metadata` tablosundaki kayıt sayısı (`"%1$d VOD içerik · %2$s"`) gösterilecek şekilde güncellendi.
- **VOD Ana Ekranı ve Katalog Sorgusu Düzeltmesi (`VodCatalogQuery` & `VodHomeActivity`):**
  - `VodCatalogQuery.CATALOG` SQL birleşiminde `vod_metadata` tablosuna `'MOVIE'` türü de dahil edildi (`kind IN ('MOVIE','SERIES','EPISODE')`).
  - `VodHomeActivity` varsayılan filtre bölümü `gridSection = "ALL"` yapılarak dizitime gibi dizi arşivlerinin ve film arşivlerinin tek ekranda eksiksiz listelenmesi sağlandı.
  - Kaynak değiştirildiğinde veya kategori sıfırlandığında ekranın eski listeden kalan afişleri temizlemesi ve yeni kaynağın afişlerini yüklemesi güvenceye alındı.
- **Headless WebView ile Dinamik Video Akışı Yakalama (`WebVideoResolver`):**
  - Güneş TV decompile bulguları doğrultusunda, HTTP GET ile çözülemeyen dinamik JavaScript, karmaşık iframe ve token korumalı sayfalar için arka planda çalışan görünmez bir `WebView` sniffer motoru eklendi.
  - `shouldInterceptRequest` ve DOM enjeksiyonu ile `.m3u8` / `.mp4` akışları yakalanıp zaman aşımı korumasıyla ExoPlayer'a teslim edilir.


- **VOD Player Arayüzünün Modern Simgelerle Yeniden Tasarımı (`activity_main.xml` & `VodPlayerOverlayController`):**
  - Oynatıcı arayüzü referans tasarıma uygun şekilde simge tabanlı şık kontrollere dönüştürüldü:
    - **Üst Bar:** Geri butonu (`ic_arrow_back`), İçerik Başlığı ve meta bilgileri (Yıl/Türler/Puan), sağ tarafta Altyazı (`[CC]`), Ayarlar (`⚙`), Tam Ekran (`⤢`) simgeleri ve saat.
    - **Alt Bar 1. Satır (Zaman Çizelgesi & Oynat):** Sol başta büyük Oynat/Duraklat dairesel simgesi (`▶` / `⏸`), geçen süre (`vod_time_current`), mavi dolgulu modern SeekBar/ProgressBar ve toplam süre (`vod_time_duration`).
    - **Alt Bar 2. Satır (Kontroller):** 10 saniye geri sarma (`↺10`), 10 saniye ileri sarma (`↻10`), ses simgesi (`🔊`) ve seviye göstergesi, sağ tarafta ise doğrudan VOD kütüphanesine dönen "Liste ⌄" butonu.
  - Kumanda D-Pad navigasyonu iki satırlı ve üst barlı yeni düğme hiyerarşisine göre uyarlandı.
- **VOD Ekranı Seçimlerinin Korunması & Arama Kutusuna Odak Gitmesinin Önlenmesi (`VodHomeActivity`):**
  - Player'dan geri dönüldüğünde seçili kategori, sıralama, aktif kaynak ve yüklenmiş ızgara verisi sıfırlanmadan korundu.
  - Sayfa açılışında ve geri dönüşte odağın otomatik olarak arama kutusuna (`vod_search_box`) gitmesi engellendi; arama kutusu yalnızca kullanıcı kumandadan bilinçli olarak YUKARI bastığında odak alacak şekilde sınırlandırıldı.
- **Akıllı Odaklanma (Smart Focus):**
  - Player'dan dönüldüğünde, izlenen içerik "İzlemeye Devam Et" listesine eklenmişse kumanda odağı doğrudan o içeriğin devam et kartının üzerine konumlandırılır.
  - İzlenen içerik devam ete eklenmemişse veya devam et listesi boşsa, odak afiş ızgarasındaki son seçilen/kaldığı karta yönlendirilir.
- **Afişlerde OK Uzun Basma (Long Click) -> "Sonra İzle" (İzlemeye Devam Ete Ekle):**
  - Film ve dizi afişlerinin üzerinde kumandadan OK tuşuna uzun basıldığında açılan bağlam menüsü eklendi (`VodPosterAdapter` & `VodHomeActivity`).
  - Menüden "Sonra İzle" seçildiğinde içerik `IptvResumeStore.markWatchLater` ile anında "İzlemeye Devam Et" rayına eklenir ve odak o karta verilir.
  - "İzlemeye Devam Et" rayındaki kartlara uzun basıldığında ise listeden kaldırma seçeneği sunulur.

## 2026-10-23 - VOD Grid Keşif Ana Ekranı, Devam Et Rayı, LiveTV Mod Ayrımı ve Favoriler Filtresi

- **VOD Grid Keşif Ekranı Varsayılan Ana Ekran Yapıldı:** Önceki kullanışsız 3 sütunlu dashboard kaldırılarak, zengin ve modern Grid Keşif arayüzü doğrudan VOD ana ekranı haline getirildi:
  - **Sol Panel:** Tek tıkla açılan "Playlist / Liste" (Combo seçici) butonu, Canlı TV'ye dönüş butonu, 4 sıralama seçeneği (Varsayılan, A-Z, Z-A, En Yeni Eklenenler) ve dinamik kategoriler listesi.
  - **Playlist / Liste Combo Seçimi:** IPTV sağlayıcıları arasında tek tıkla geçiş sağlayan açılır diyalog; tüm kaynakları veya belirli bir listeyi filtreleme imkanı.
  - **Üstte "İzlemeye Devam Et" (Devam Et) Yatay Rayı:** VOD ekranının üst kısmına yarım kalan film ve dizilerin gösterildiği yatay afiş rayı eklendi (`vod_continue_container` & `vod_recycler_continue`); içerik yoksa alan otomatik gizlenir.
  - **Sağ Alan:** 5 sütunlu akıcı ve sayfalı (Pagination) afiş ızgarası ile binlerce içerik takılmadan listelenir.
  - **Doğrudan Canlı TV'ye Dönüş:** Kumanda Geri (Back) tuşu, sol paneldeki "Canlı TV'ye Dön" butonu ve Kırmızı renk tuşu ile VOD'dan doğrudan Canlı TV'ye dönülür.
- **LiveTV & VOD Modu Kesin Ayrımı ve Infobar Çakışmasının Giderilmesi:**
  - VOD oynatılırken LiveTV infobarı kesin olarak engellendi (`binding.infoBar.visibility = View.GONE`), VOD infobarı tek yetkili kılındı.
  - LiveTV moduna dönüldüğünde VOD oynatıcı arayüzü tamamen gizlendi (`vodPlayerOverlayController?.hideControls()`).
  - VOD'dan Canlı TV'ye dönüldüğünde (`switchToLiveMode`) Canlı TV her zaman kanal listesi açık modda (`showChannelPanel(expanded = false)`) başlatılarak kullanıcı dostu hızlı zapping sağlandı.
- **Kanal Listesi Filtrelerine "Favori Kanallar" Bağımsız Listesi Eklendi:**
  - `ChannelSourceFilter.FAVORITES` eklendi; Sarı tuş kısa basış döngüsüne ve Sarı tuş uzun basış kaynak seçici diyaloğuna "Favori Kanallar" seçeneği dahil edildi.
  - Favoriler seçildiğinde listede yalnızca favorilere eklenmiş kanallar filtrelenir ve başlıkta sarı yıldız ikonu görüntülenir.
- **`BufferedLiveInputStream` Yarış Durumu (Race Condition) Düzeltmesi:**
  - Akış kapatıldığında worker iş parçacığının erken sonlanması ve consumer thread'in beklemeden -1 dönmesi sonucu CI testinde (`BufferedLiveInputStreamTest`) oluşan hata giderildi; `closed` durumu kontrol edilerek `IOException` fırlatılması güvenceye alındı.
- **Açılışta Doğrudan Çökme (Fatal Crash) Hatasının Giderilmesi:**
  - `MainActivity.onCreate()` aşamasında `restoredMultiViewKeys` alanına henüz `multiViewController` ilklendirilmeden erişilmesi sonucu oluşan `UninitializedPropertyAccessException` hatası giderildi; `restoredMultiViewKeys` doğrudan `MainActivity` içinde saklanıp denetleyici kurulduğunda aktarıldı.
- **IPTV Listelerinde Güncelleme Sonrası Odak Korunması:**
  - Tek bir liste güncellendiğinde (`SourceAction.REFRESH`) veya liste durumu değiştirildiğinde, `selectedSourceId` takip edilerek `sourceList.post { requestFocus(); setSelection(pos) }` ile odağın doğrudan güncellenen listenin üzerinde kalması sağlandı.
- **Seçimli Toplu Liste Güncelleme (Bulk Refresh):**
  - IPTV Kaynakları ekranına "Listeleri Toplu Güncelle" butonu eklendi.
  - Açılan çoklu seçim diyaloğu üzerinden seçilen ağ tabanlı (URL, Xtream, Stalker) listelerin sırayla arka planda güncellenmesi ve ilerlemenin canlı izlenmesi sağlandı.
- **GitHub Manifest Akıllı Senkronizasyonu & Kaldırılan Listeler Onayı:**
  - GitHub üzerinden eklenen listelerin kullanıcı adı bilgisi (`github:username`) kaynak meta verisine işlendi ve listede `[GitHub: username]` rozetiyle gösterildi.
  - GitHub manifesti tekrar güncellendiğinde, manifest'ten kaldırılmış olan listeler doğrudan silinmek yerine kullanıcıya onay diyaloğu sunularak onay verilirse silinmesi, onay verilmezse korunması sağlandı.
- **JSON Formatlı VOD / Dizi ve Film Paketleri Desteği (`JsonVodImporter`):**
  - `vod2.json` içindeki `"kaynak": "json"` tipindeki dizi arşivleri (`dizitime.json` gibi) için JSON VOD ayrıştırıcı eklendi; dizi, sezon, bölüm ve videoUrl meta verileri doğrudan `vod_metadata` tablosuna aktarılarak oynatılabilir kılındı.
- **IPTV Listeleri Buton Simgelerinde Kenar Boşluğu (Padding) Düzeltmesi:**
  - Sol paneldeki tüm butonların (`import_url_button`, `import_file_button`, `import_xtream_button`, `import_stalker_button`, `import_github_manifest_button`, `bulk_refresh_button`, `web_manage_button`, `close_button`) sol kenara yapışık simgeleri `paddingStart="16dp"` ve `paddingEnd="16dp"` ile ferahlatıldı.
- **VOD Listesinde Pasif Kaynakların ve Gizlenen İçeriklerin Filtrelenmesi:**
  - `VodCatalogQuery.kt`: `CATALOG` SQL birleşimine `iptv_sources` tablosu bağlanarak `s.enabled = 1` (devre dışı bırakılmış IPTV kaynaklarının VOD film ve dizileri listelenmez) ve `COALESCE(u.hidden, 0) = 0` (kullanıcının gizlediği/atladığı içerikler listelenmez) koşulları getirildi.
  - `VodHomeActivity.kt`: Kaynak seçim menüsünde (`vodSourceCombo`) pasif kaynaklar filtrelenerek yalnızca aktif listelerin görünmesi sağlandı; önceden seçilmiş kaynak pasif duruma getirilmişse otomatik olarak "Tüm Liste" (`-1L`) varsayılanına dönülmesi sağlandı.
- **VOD Seçim Durumunun (State) Korunması, Odak Sıçramasının ve Üzerine Binmenin (Overlap) Önlenmesi:**
  - VOD ızgara seçimleri (aktif kaynak, seçili kategori, sıralama ölçütü) kalıcı olarak saklandı; içerik açılıp geri dönüldüğünde kullanıcının son seçimleri ve kategori odağı aynen geri yüklendi.
  - Kategori seçiminde odağın ekranın üstündeki arama kutusuna sıçraması engellendi; `VodCategoryAdapter`'a `stableIds` ve tekil öğe güncellemesi (`setSelectedCategory`) eklenerek odak seçilen kategoride tutuldu.
  - Izgarada aşağı kaydırma yapıldığında kartların "İzlemeye Devam Et" alanının üzerine taşması kırpma (clipping) sınırları düzenlenerek giderildi.
  - "Tüm Liste" (`sourceId <= 0L`) seçildiğinde içeriklerin boş dönmesi sorunu `VodCatalogQuery` WHERE koşulu düzeltilerek giderildi.
- **VOD Kart Boyutlarının Optimize Edilmesi ve Seçici Çerçeve Kırpılmasının Önlenmesi:**
  - VOD ızgarasındaki kartların yüksekliği ekran oranına göre optimize edildi (poster 124dp, genişlik 106dp, başlık 11sp, yıl/puan 10sp); 2 tam satırın ekrana tam ve ferah şekilde sığması sağlandı.
  - Kartların üstten ve alttan odaklandığında (`scale 1.03x` + `2.5dp` neon mavi çerçeve) sınırlarının kesilmesini önlemek amacıyla `vod_recycler_grid` üzerine `paddingTop="8dp"` / `paddingBottom="24dp"` ve kapsayıcılara `clipToPadding="false"` uygulandı; seçici çerçeve tüm kenarlarıyla net ve pürüzsüz görünür hale getirildi.

## 2026-10-23 - MainActivity Modülerleştirme ve Mimari Ayrıştırma (Refactoring)

- **MainActivity Parçalama ve Sorumluluk Ayrımı:** Monolitik `MainActivity.kt` sınıfı, işlevsel sorumluluklarına göre 5 bağımsız denetleyiciye (controller/helper) ayrıştırıldı; kod tabanı sadeleştirilip modüler hale getirildi:
  - **`VodPlayerOverlayController`:** Netflix tarzı bağımsız VOD OSD arayüzü, timeline kontrolü, sarma/seek, oynatma hızları, ses/altyazı panelleri ve VOD kumanda tuş döngüleri bağımsız denetleyiciye taşındı.
  - **`MultiViewController`:** 2, 3 ve 4 hücreli IPTV + TIF Multi-View ızgarası, PiP penceresi, dinamik ses odaklama/susturma, hücre zapping ve DPAD/renk tuşu etkileşimleri taşındı (~830 satır).
  - **`ChannelZapper`:** 0-9 sayısal kanal tuşlama (5 hane, 1.5s zaman aşımı), commit, sonraki/önceki zap ve Recall (önceki farklı kanala dönüş) mantığı ayrıştırıldı.
  - **`TrackSelectionHelper`:** DVB/TIF ve IPTV ses/altyazı parça diyalogları, dil eşleme ve etiket formatlayıcıları taşındı.
  - **`TvOverlayLayoutHelper`:** OSD responsive ekran geometrisi, dikey/yatay safe margin hesaplamaları, kanal listesi genişlik ve dinamik infobar yükseklik yerleşimleri taşındı.
- **Sıfır Davranış Değişikliği ve Tam Geriye Uyumluluk:** Tüm kumanda tuş sözleşmeleri (`AGENTS.md`), D-Pad gezinimleri ve OSD koordinasyonu birebir korundu; birim testler ve derlemeler başarıyla doğrulandı.

## 2026-10-23 - 3 Sütunlu Modern Sinematik VOD Ana Sayfası ve TMDB Meta Veri Entegrasyonu

- **3 Sütunlu Sinematik Smart TV Arayüzü:** VOD ana ekranı Stremio / Apple TV / Netflix standartlarında 3 sütunlu modern bir dashboard'a dönüştürüldü:
  - **Sol Gezinme Menüsü:** TVApp VOD logosu, canlı saat, "Ana Sayfa", "Canlı TV", "Sinema", "Dizi", "Devam Et" ve "Filtrele" hızlı menüleri.
  - **Orta Alan (Hero Banner & Sinematik Raylar):** En üstte odaklanılan filmin büyük arka plan görseli, yüksek çözünürlüklü afişi, başlığı, puanı (★), yılı, türleri, konusu ve "Şimdi İzle" ile "Detaylar" eylem butonları; altında "Popüler Filmler" ve "Son Eklenenler" yatay kayan poster rayları.
  - **Sağ Bilgi Paneli:** Kullanıcının tüm kayıtlı IPTV kütüphanelerini ("Mevcut Listeler") ve seçilen listenin kategorilerini ("Kategoriler") dengeli şekilde listeleyen, tek tıkla liste ve kategori geçişi sağlayan modern panel.
- **The Movie Database (TMDB) API & Akıllı Başlık Temizleme Motoru:**
  - IPTV sağlayıcılarından gelen karmaşık ve gürültülü başlıkları (`1080p`, `4K`, `TR DUBLAJ`, `(2024)` vb.) otomatik ayıklayan, film/dizi adını ve yayın yılını tespit eden `VodTitleCleaner` motoru eklendi.
  - TMDB v3 API entegrasyonu ile eksik IPTV afişleri, yüksek çözünürlüklü arka plan görselleri (backdrop), resmi Türkçe özetler, türler ve TMDB puanları arka planda otomatik çekilip Room veritabanında (`VodTmdbEntity`) önbelleğe alındı.
- **Kusursuz Seçici Mavi Çerçeve ve Padding İyileştirmesi:** Film kartlarına kumandayla odaklanıldığında mavi neon çerçevenin ebeveyn sınırları tarafından kırpılması/yarım görünmesi sorunu giderildi; RecyclerView ve kapsayıcılara `clipChildren="false"`, `clipToPadding="false"` ve dengeli dikey/yatay margin ve padding verilerek çerçevenin tam ve net görünmesi sağlandı.
- **Sinematik Default Kapak (Placeholder & Error Drawable):** Afişi henüz yüklenmeyen veya internet/kaynak kaynaklı afişi bulunmayan filmler için şık koyu lacivert degrade ve ortalanmış modern mavi film sembolü içeren `bg_vod_default_poster` oluşturuldu; hem poster raylarında hem de Hero Banner'da devreye alındı.
- **Çalışma Moduna ve Donanıma Göre Dinamik VOD Menüsü:**
  - VOD ana ekranındaki sol menü butonları yalnızca donanım kapasitesine göre değil, Ayarlar'daki çalışma modu tercihine (`ExperienceModeOverride`: Otomatik, Hibrit, Sadece IPTV) göre dinamik uyarlandı.
  - Cihaz tuner/uydu destekliyor ve ayar Hibrit ise sol menüde **"Canlı TV"** (TIF / Uydu yayınları) ve **"IPTV"** (IPTV Canlı Yayınları) ayrı ayrı sunulur; kumandada Kırmızı tuş uyduya, Sarı tuş IPTV yayınına doğrudan geçiş yapar.
  - Sadece IPTV modunda çalışan cihazlarda veya ayarlarda Sadece IPTV seçildiğinde "IPTV" butonu gizlenir ve tek "Canlı TV" butonu doğrudan IPTV canlı yayınını başlatır.
- **Tam Ekran VOD Grid (Izgara) Keşif Modu ve Zengin Sıralama:**
  - "🔥 Popüler Filmler" ve "➕ Son Eklenenler" raylarının yanındaki **"Tümünü Gör ›"** butonları ile sol menüdeki **"Sinema"** ve **"Dizi"** butonları tam ekran interaktif Grid Keşif moduna bağlandı.
  - **Sol Panel (Kategoriler & Sıralama):** Sol tarafa tek tıkla kategori filtreleme listesi (`vod_grid_recycler_categories`), "Tüm Kategoriler" seçeneği ve 4 farklı sıralama çipi (`Varsayılan`, `A - Z`, `Z - A`, `En Yeni Eklenenler`) eklendi.
  - **Orta/Sağ Alan (5 Sütunlu Sayfalı Afiş Izgarası):** Binlerce içeriği takılmadan, bellek şişmesi olmadan akıcı şekilde gezdiren 5 sütunlu `GridLayoutManager` ve sonsuz kaydırma (Pagination onScrollListener) mimarisi kuruldu.
  - **Kusursuz Kumanda ve Geri Tuşu Yönetimi:** Grid modundayken kumandadan `BACK (Geri)` tuşuna basıldığında veya `[← Ana Sayfaya Dön]` butonuna tıklandığında VOD'dan çıkmadan anında ana sayfa panosuna dönülür.

## 2026-10-23 - Netflix Tarzı Sinematik VOD Player Barı ve Kumanda Kontrolleri

- **Bağımsız Sinematik VOD Player Barı:** VOD (film/dizi) oynatılırken canlı TV infobarı tamamen devre dışı bırakıldı. Netflix tarzı modern, şık ve sinematik degrade arkaplana sahip bağımsız bir OSD (`vod_player_overlay`) geliştirildi.
- **Tam OSD İzolasyonu ve Çakışma Önleme:** Canlı TV infobarı ve kontrolleri VOD modunda kesin olarak gizlenerek çift bar binmesi sorunu giderildi; kumanda tuş bırakma (`ACTION_UP`) olayları izole edilerek canlı TV menülerine sızıntı önlendi.
- **Sol Üstte Geri Butonu & Başlık, Sağ Üstte Bağımsız Saat:** Ekranın sol üst köşesine kumandayla doğrudan odaklanılabilen `[← VOD Listesi]` geri butonu, film başlığı ve liste adı eklendi; sağ üst köşeye ise ferah ve okunaklı 24sp dijital saat yerleştirildi.
- **Kumanda Odaklı Kontrol Çubuğu:** Ekranın alt kısmına geçen/kalan süreyi gösteren ince zaman çizelgesi (timeline) ile birlikte kumanda ile kolayca yönetilebilen `-10s`, `Oynat / Duraklat`, `+10s`, `Hız (0.5x - 2.0x)`, `Ses / Altyazı` ve `Baştan Başlat` butonları eklendi.
- **Kusursuz Simetrik Buton ve Zaman Çizelgesi Tasarımı:** Zaman çizelgesinin arkaplanı seçili değilken şeffaf yapılarak sol süredeki kutu çakışması giderildi; odaklandığında modern neon çerçeveyle belirginleşmesi sağlandı. Oynatma butonlarındaki `minWidth` ve paylaşılan tekil `Drawable` referansı kaynaklı sağ kenar kırpılma/kesilme (clipping) hatası giderildi; `setBackgroundResource` ile her buton bağımsız çizilerek tüm butonlar pürüzsüz, simetrik 44dp hap (capsule) formuna getirildi.
- **Sezgisel D-Pad Gezinimi:** Kumandada D-Pad Yukarı tuşuna basıldığında odak doğrudan sol üstteki `[← VOD Listesi]` butonuna geçer; D-Pad Aşağı tuşuna basıldığında alt kontrollere geri döner. Geri butonuna basıldığında veya film ekranındayken kumandadan `BACK` basıldığında izleme süresi kaydedilerek anında VOD kütüphanesine dönülür.
- **Tek Tıkla Ses ve Altyazı:** VOD kontrol çubuğu üzerindeki `Ses / Altyazı` butonuyla harici menülere girmeden film ses parçaları ve altyazıları anında seçilebilir.

## 2026-10-23 - IPTV kopma koruması, canlı bağlantı iyileştirmeleri ve varsayılan ayar

- **IPTV Kopma Koruması ve Bağlantı İyileştirmeleri:** Canlı yayın yeniden bağlantı (`canReconnectLiveRequest`) mekanizması esnetildi; standart dışı `Content-Type` gönderen IPTV sağlayıcılarında (`application/octet-stream`, `video/ts`, `video/mpeg` veya eksik header) ve canlı TS URL yapılarında otomatik yeniden bağlantı devreye sokularak kopmalar ve siyah ekrana düşmeler önlendi. `LiveTsContinuity` motoruna sync byte (`0x47`) arama/hizalama (resync) yeteneği eklendi.
- **Kullanıcı Dostu İsimlendirmeler:** Canlı bağlantı yöntemleri daha anlaşılır şekilde güncellendi: "Standart (Doğrudan Akış)" ve "Kesintisiz Akış (Kopma ve Tekrar Korumalı)".
- **Ayarlarda Varsayılan Canlı Bağlantı Yöntemi:** Ayarlar > Oynatma sekmesine "Varsayılan Canlı Bağlantı Yöntemi" tercihi eklendi. Kanala özel bir seçim yapılmadığında bu genel ayar otomatik uygulanır.


- **Infobarda IPTV Liste Adı:** IPTV kanalları izlenirken infobarda kanal adının hemen altında ait olduğu IPTV listesinin adı (`iptv_sources.name`) gösterilir. TIF/karasal/uydu yayınlarında bu alan otomatik gizlenir.
- **Web Yönetiminde Öncelikli Sabit Portlar:** Web LAN sunucusu başlatılırken rastgele port ataması yerine sırasıyla `8050`, `8060` ve `8070` sabit portları denenir; tümü meşgul ise otomatik boş porta geçilir.
- **Web Arayüzünde Sayfa Boyutu Seçimi:** Hem IPTV Kanal Seçim modalına hem de Kanal Düzenleyicisi tablosuna sayfa başına gösterilecek öğe sayısı (50, 100, 250, 500) seçeneği eklendi.
- **Web Arayüzünde Tümünü Seç (Select All):** Tablo başlıklarına sayfadaki tüm kanalları tek tıkla işaretleyen/kaldıran onay kutusu eklendi.
- **Web Kanal Düzenleyicide Toplu İşlemler:** Seçilen kanallar için tek işlemde toplu Favori Ekle / Kaldır, Gizle / Gizliliği Kaldır ve Ana Listeden Çıkar araç çubuğu eklendi.


- **IPTV Listelerini Pasife Alma / Aktifleştirme:** Hem TV OSD arayüzünden (`IptvSourcesActivity` kaynak işlemleri menüsü) hem de Web arayüzünden kaynaklar tek tıkla pasife alınabilir veya aktifleştirilebilir. Pasife alınan kaynakların kanalları TV ana listesinde ve OSD'de gizlenir, kaynak listesinde durum rozetiyle (`[Pasif]`) ayırt edilir.
- **Web Kanal Seçim Yöneticisi:** Web yönetim panelinde kayıtlı IPTV listelerinin yanına "Kanalları Seç" butonu eklendi. 15.000+ kanallı devasa kütüphanelerde dahi sıfır donma yaşanacak şekilde 100'lük sayfalı hafif DTO mimarisi, SQLite FTS tabanlı anlık arama, kategori filtresi, tekli veya filtrelenen kanalları toplu seçme/kaldırma özellikleri getirildi.
- **Web Kanal Liste Editörü:** Web arayüzüne eklenen "Kanal Düzenleyici" sekmesiyle TV'deki Kanal Liste Düzenleyici işlevleri tarayıcıya taşındı. TV kanallarının sıra numarası (`customNumber`), kanal adı (`customName`), favori durumu, gizleme/atlama durumu, ebeveyn kilit kontrolü ve IPTV kanallarını ana listeden çıkarma işlemleri sayfalı ve filtrelenebilir tablo üzerinden yönetilebilir hale getirildi.

## 2026-10-22 - GitHub üzerinden toplu IPTV liste aktarımı

- Hem TV kaynak yönetiminde hem de Web yönetim arayüzünde GitHub üzerinden toplu liste içe aktarma desteği eklendi (`live2.json` ve `vod2.json` uyumlu).
- TV Arayüzü: `IptvSourcesActivity` içine "Toplu Liste Ekle (GitHub)" seçeneği eklendi. Kullanıcı adı ve dosya seçimi sonrasında paketler logo, açıklama ve onay kutuları içeren TV kumandası uyumlu listede gösterilir; seçilen listeler tek tıkla topluca içe aktarılır.
- Web Arayüzü: Web yönetim paneline GitHub sekmesi eklendi; tarayıcıdan kullanıcı adı ve canlı/VOD türü seçilerek paketler taranabilir, istenenler seçilip TV'ye tek işlemde eklenebilir.
- Önceden eklenmiş olan listeler atlanarak mükerrer kayıtlar engellenir; aktarım ilerlemesi anlık olarak izlenebilir.

## 2026-10-14 - Kanal editöründe IPTV kaynakları ve toplu çıkarma

- IPTV kanal adının altında kaynak liste adı küçük yazıyla gösterilir. Kaynak adları tek metadata sorgusunda alınır; satır başına veritabanı sorgusu yapılmaz.
- Çoklu seçimde sol araç çubuğuna ana listeden çıkar eklendi. Onaylanan seçimin yalnız IPTV kanallarında üyelik kaldırılır; kaynak içerikleri, kullanıcı tercihleri ve uydu kanalları korunur. Toplu üyelik değişikliği tek transaction içinde uygulanır.
- Soldaki araç odaklandığında altta görevi gösterilir. Renk tuşları satırı ve alt alanın toplam yüksekliği korunur. Son kanal çıkarıldığında geçersiz kanal odağı/önizlemesi temizlenir.

## 2026-10-12 - Web oturumu boşta kalma süresi

- Sabit 15 dakikalık oturum yerine 15 dakikalık boşta kalma süresi kullanılır. TV/web veri işlemi sürerken sunucu otomatik kapanmaz. Başarılı eşleştirme, kabul edilen web komutu ve web işleminin bitişi süreyi yeniler; otomatik durum sorguları süreyi uzatmaz. Elle durdurma ve IPTV yönetiminden ayrılma davranışı değişmez.

## 2026-10-12 - TV OSD tema seçenekleri

- Kanal listesi ve infobara ortak tema seçimi eklendi: Varsayılan mevcut görünümü korur; Glass yarı saydam yüzey ve açık odak çerçevesi, Yüksek Kontrast opak siyah yüzey ve belirgin odak sağlar. Ayarlar > Görünüm üzerinden seçilir, dönüşte uygulanır ve yedeğe dahil edilir. Eski yedeklerde Varsayılan kullanılır.
- Yerleşim, kumanda eşlemeleri, video oynatımı, mobil ve web arayüzü değiştirilmez. Gerçek zamanlı blur, yeni bağımlılık veya veritabanı değişikliği yoktur.

## 2026-10-12 - Web kaynak adı ve kısa eşleştirme

- Web yönetiminde her kaynak için Yeniden adlandır eklendi. Mevcut adla açılan koyu pencerede Kaydet/İptal kullanılır; boş veya 200 karakteri aşan adlar reddedilir. Mevcut repository ad güncellemesi kullanılır; kanal seçimleri ve tercihler korunur, TV/web işlem kilidi uygulanır.
- Eşleştirme kodu 4 haneye indirildi; baştaki sıfırlar korunur. Oturum tokenı, 15 dakikalık oturum ve 5 hatalı denemede 60 saniyelik bekleme korunur. Yalnız güvenilir LAN kullanımına yöneliktir.

## 2026-10-12 - Yerel ağdan IPTV kaynak yönetimi

- Web sunucusu açıkken TV kaynak yönetimi kullanılabilir. Adres/kod penceresini Kapat veya Back ile kapatmak sunucuyu durdurmaz; Web’den yönet ile yeniden açılır. Yalnız gerçek veri işlemi sırasında TV ve tarayıcı işlem kontrolleri birlikte kilitlenir; eşzamanlı komut reddedilir, işlem bitince açılır.
- IPTV yönetimine Web’den yönet / Sunucuyu başlat eklendi. Aynı ağdaki tarayıcı TV’deki adres ve her oturumda yenilenen 8 haneli kodla eşleştirilir; ek bağımlılık veya Room şema değişikliği yoktur.
- Tarayıcıdan URL, UTF-8 M3U dosyası (en fazla 12 MB), Xtream ve Stalker eklenebilir; kaynaklar silinebilir ve URL kaynakları güncellenebilir. Mevcut TV repository/staging akışı kullanılır; seçimler korunur, içe aktarılan kanallar otomatik ana listeye eklenmez. Durum ve gerçek içe aktarma ilerlemesi TV ve tarayıcıda gösterilir.
- Sunucu yalnız seçilen yerel IPv4 adresini dinler; eşleştirme öncesi kaynak/kimlik bilgisi vermez. Yanlış kod denemeleri sınırlandırılır, çapraz origin/yanlış Host istekleri engellenir ve gövde/istemci/süre sınırları uygulanır. Sunucuyu durdur, ekrandan çıkma veya 15 dakikalık oturum sonu erişimi kapatır. HTTP şifrelenmez; yalnız güvenilir LAN kullanımı içindir.

## 2026-10-11 - Kanal bazlı canlı bağlantı yöntemi

- Son beş commit incelendi: `bcc1083` ile eklenen paket sınırı hatası → temiz MediaSource ile yeniden açılma yolu bazı gerçek TV yayınlarında siyah ekran ve açılış döngüsü oluşturuyor. Önceki veri testleri TV decoder davranışını doğrulamıyordu.
- Tekrar ayıklama artık tüm sürekli bağlantılı kaynaklara uygulanmaz. Varsayılan Standart seçeneği `5ba0ea8` veri akışını korur; yararlı olduğu kanallarda uzun OK → Canlı bağlantı yöntemi → Tekrar ayıklama (deneysel) seçilebilir. Kayıt sonraki kanal açılışında uygulanır, aktif yayın kesilmez.
- Room 30 → 31 migration ile kanal tercihi varsayılan kapalı saklanır; kaynak güncellemesinde diğer kullanıcı tercihleriyle birlikte korunur ve yedek aktarımına dahildir. HLS/DASH ve TIF oynatımı değişmez.
- Infobarda Standart / Tekrar ayıkla seçimi eklenir; buradan Kaydet izlenen kanalı yeni tercihle hemen yeniden açar. Canlı IPTV'de seek çizgisi gizlenir, CANLI düğmesi bildirilen canlı konumuna yakınken kırmızı, geride veya bilinmiyorken gri görünür. VOD'da gerçek konum/süre seekbarı ve hız kontrolü korunur. Buffer düğmesi hedef yerine gerçek hazır süreyi, hız düğmesi gerçek oynatma hızını gösterir; görünür infobarda 500 ms aralıkla güncellenir.

## 2026-10-11 - Örtüşen canlı TS yanıtlarında tekrar düzeltmesi

- Gerçek kaynak ölçümünde ardışık HTTP yanıtlarının yaklaşık 5–27 saniyelik ses/video bölümünü tekrar gönderdiği doğrulandı. Sadece daha büyük tampon veya sabit yeniden bağlantı gecikmesi bu örtüşmeyi gidermiyor.
- Sürekli bağlantı modunda her elementary stream için son PES başlangıcı, paket sırası ve son tam TS paketi korunur. Sonraki yanıtta bu sınır birebir doğrulanana kadar tekrar bölümü oynatıcıya verilmez; ses ve video ayrı hizalanır. HTTP sonunda kesilmiş TS paketi teslim edilmez, sonraki yanıttaki tam kopyasından devam edilir.
- Zaman damgası değiştirilmez ve süre tahminiyle veri kırpılmaz. Sınır bulunamaz/değişirse kör birleştirme yerine Media3 hata kurtarmasına dönülür. Tanılama kaydına atlanan paket sayısı eklendi; URL veya erişim bilgisi kaydedilmez.

## 2026-10-11 - Sınırlı canlı indirme tamponu

- Liste bazlı sürekli canlı bağlantı seçeneği açıkken progressive MPEG-TS verisi bağımsız üretici kuyruğuna indirilir. Kuyruk ana oynatmada yaklaşık 8 MiB, PiP/Multi-View hücrelerinde 2 MiB ile sınırlıdır; dolu kuyruk indiriciyi bekletir, veri atılmaz.
- Ana oynatmada başlangıç rezervi buffer üst sınırı içinde en fazla 10 saniyedir; ilk kare watchdog süresi bu rezervi hesaba katar. Kanal değişiminde bekleyen okuyucu ve indirici iptal edilir. HLS/DASH segmentleri, VOD, veritabanı ve kaynak güncelleme akışı değişmez.
- Kuyruk sırası, dolulukta geri basınç, hata öncesi verinin tüketilmesi ve kapanış davranışı için birim testleri eklendi. Ek sunucu veya bağımlılık gerekmez; sağlayıcıdaki yayın boşlukları için kesintisizlik garantisi verilmez.

## 2026-10-11 - Canlı TS bağlantı sürekliliği

- Kaynağın sürekli canlı bağlantı ayarı açıkken progressive MPEG-TS yanıtının bitiminde HTTP bağlantısı veri katmanında yeniden açılır. HLS/DASH, VOD, sınırlı byte-range ve altyazı istekleri bu döngüye alınmaz.
- Ağ hatalarında sınırlı artan bekleme uygulanır; başarısızlık sınırından sonra Media3 kurtarma akışı devreye girer. Tanılama kayıtları URL yolu, kullanıcı bilgisi ve sorgu anahtarlarını içermez. Kaynakta oluşan yayın boşluğu/tekrarı için kesintisizlik garantisi verilmez.

## 2026-10-11 - Stalker bağlantı açılışı ve canlı yayın timeout'u

- Stalker portal handshake tokenları endpoint+MAC bazında 3 dakika önbelleğlenir; kanal açılışı ve sıraya alınan canlı devam parçaları portal'a tekrar tekrar handshake atmaz. Token reddedilirse bağlantı bir kez yeni token ile tekrar denenir, orijinal hata korunur.
- Canlı IPTV akışlarında OkHttp read timeout 30 saniyeye çıkarıldı; VOD 20 saniyede kalır. Stalker kaynak doğrulama akışı değişmedi.

## 2026-10-08 - VOD kumanda gezinmesi

- Kategoriler solda doğrudan listelenir; liste, Sinema/Dizi, Devam Et, arama ve Canlı TV eylemleri yan alandadır. Sol kenarda Sol veya içerikte Back kategori alanına, Sağ hatırlanan içerik konumuna döner; MENU alan değiştirir.
- Yeşil tüm devam kayıtlarını açar; seçili kaynağın filmleri ve bölümleri birlikte gösterilir. Küçük TV ekranlarında gizlenen devam kartları açıldı; son içerik hariç tutulmaz, aynı dizi yine tek karttır.
- VOD modunda Sol/Sağ sarma, pasif infobar görünürken ve içerik türü henüz çözümlenmemişken de çalışır. Diyaloglar ve etkin işlem düğmeleri önceliğini korur.

## 2026-10-08 - VOD devamı ve IPTV uyumluluğu

- Dizi devam kayıtları en son izlenen bölümle tek karta indirilir; oynatma menüsünden sezon sınırını da geçen sonraki bölüm açılabilir.
- VOD arama kapsamı kategori veya seçili kaynak olarak ayarlanır; kategori tercihi korunur. Sorgular sayfalı ve sınırlı kalır.
- IPTV altyazı boyutu, renk, arka plan, kenarlık, konum ve yazı tipi için kumandayla kullanılabilir pencere eklendi. Varsayılan sistem/kaynak görünümü ve TIF altyazıları korunur.
- HLS uzantılı ama progressive içerik veren adresler belirli manifest/format hatalarında aynı URL ile bir kez denenir; header ve harici altyazılar korunur. Ağ/codec/DRM hatalarında format fallback'i yapılmaz.

## 2026-10-08 - Infobar video bitrate

- IPTV infobarı yalnız oynatıcının video formatında bildirdiği bitrate değerini gösterir; bilgi yoksa alan gizlenir. Tahmini ağ indirme hızı video bitrate'i yerine kullanılmaz; diagnostikte ayrı kalır.

## 2026-10-08 - XMLTV güncelleme sayıları

- Güncelleme sonucu yalnız saklanan programları sayar. Tamamen eski tarihli XMLTV dosyası mevcut rehberi silmez; kaynak tarihleri ve cihaz saatinin kontrol edilmesini bildirir.

## 2026-10-08 - Emülatör kumandası

- Masaüstü kumandasında açık emülatörler ad ve ADB kimliğiyle seçilebilir. Yenile seçimi korur; kapanan cihaz yerine başka emülatöre otomatik geçilmez.

## 2026-10-07 - IPTV kaynak bağlantıları ve Xtream bilgileri

- M3U `http-origin` ve URL header'ı, kaynak varsayılan User-Agent/Referer/Origin ayarları, ana/çoklu oynatıcı ve TVApp TIF aktarımında desteklenir. Kanal header'ı önceliklidir; harici oynatıcıya header gönderilir (destek harici uygulamaya bağlıdır).
- Xtream hesap ekranı durum, bitiş tarihi, etkin bağlantı ve azami bağlantıyı gerçek sunucu yanıtından gösterir. Sunucu limiti ve isteğe bağlı kullanıcı limiti PiP/Multi-View başlangıcında/değiştirmede kontrol edilir; başka cihaz oturumları yerel sayıya katılmaz.
- Xtream yalnız küçük kategori isteklerini en fazla iki bağlantıyla paralel alır; büyük canlı/VOD katalogları akışlı okumaya devam eder. Atomik staging aktarımı, sourceKey eşleştirmesi ve FTS/sayfalama değiştirilmedi.
- Room 29 → 30 yalnız nullable alanlar ekler; liste ve tercihleri silmez. Yedekler yeni alanları taşır; eski yedekler varsayılan davranışı korur. Kayıt/restream bağımlılığı eklenmedi.

## 2026-10-07 - Liste bazlı canlı akış devamı

- Erken bağlantı süresi liste bazında 250 ms adımlarla 250–5000 ms arasında ayarlanabilir (varsayılan 3000 ms); aynı süre Media3 ön yüklemesinde kullanılır. Room 28 → 29 migration ve yedek desteği eklendi.
- İlk yüklemeden sonraki arabelleğe alma ortada yazı yerine sağ üstte transparan zeminli küçük bir simgeyle gösterilir; ana PlayerView'in ikinci merkez göstergesi kapatıldı.

- Süreli canlı akış ayarı kaynak oynatma seçeneklerine eklendi; varsayılan kapalıdır, yenileme ve yedeklemede korunur. Room 27 → 28 migration mevcut listeleri silmeden alanı ekler.
- Tamamen indirilmiş canlı parçanın son 3 saniyesinde yeni MediaSource sıraya alınır ve Media3 playlist preloading ile hazırlanır. Tek oynatıcıda en fazla iki parça tutulur; oynanan parça geçiş sonrası bırakılır.
- VOD, catch-up, duraklatılmış yayınlar ve dinamik HLS/DASH akışları hariçtir. Çok kısa parçaların tekrarında sınır uygulanır. Kanal değişimi/çıkış eski kuyruğu bırakır. Sağlayıcı akışlarının zaman damgaları farklıysa atlama/tekrar olabilir.

## 2026-10-03 - VOD kumanda gezinmesi

- VOD ilk açılışta içerik odağına geçer. Üst sekme/kategoriden Aşağı son karta, ilk satırdan Yukarı veya katalogda Back aktif sekmeye döner; Back arama/kategori filtresini silmez.
- Son satırdan Aşağı sayfa eylemlerine ulaşılır; sayfa değişimi CH+/CH- ve sayfa düğmeleriyle yapılır. Yatay oklar kart ızgarasının kenarından ilgisiz düğmelere atlamaz.
- Filtre/sıralama satırları tekrarlanan OK ile değer döndürmek yerine görünür seçenek menüsü açar. Filtre uygulandığında katalog odağına dönülür; dialog kapatıldığında açan kontrolün odağı korunur.
- Detay dialogu Oynat/Devam et/Sezonlar üzerinde açılır; Yukarı açıklama alanına, açıklamadan Aşağı oynatma eylemine geçer. Fiziksel TV doğrulaması ayrıca gereklidir.

## 2026-10-02 - Kaynak bazlı VOD kütüphanesi

- Sinema/Dizi sekmeleri, liste/kategori seçimi, arama, favoriler, devam edilecekler, son izlenenler ve sıralama eklendi. Kaynak başına filtre, sayfa ve kart odağı korunur.
- İsimli son içerik alanı, poster kartları ve kaydırılabilir gerçek açıklamalı detay penceresi eklendi. Kırmızı Canlı TV, yeşil son içerik, sarı kaynak, mavi arama/filtre görevindedir.
- Katalog 60 kayıtlık DAO sayfalarıyla tutulur; CH+/CH- ve dokunulabilir sayfa okları kullanılabilir. Klavye kataloğu daraltmaz; geç gelen sorgular güncel görünümü değiştiremez.
- Xtream sezon ve bölümleri isteğe bağlı indirilir; metadata altı saat önbellekte tutulur. Room 26 → 27 geçişi mevcut verileri silmeden yalnız metadata tablosu ekler.
- Görseller mevcut sınırlı Coil önbelleğini kullanır; olmayan poster/açıklama üretilmez. Otomatik sonraki bölüm oynatma ertelidir; gerçek TV ve sağlayıcı üzerinde ayrıca doğrulama gerekir.
- 15.000 kayıt, migration, Xtream yanıtları, klavye yerleşimi ve dolu katalog/detail dönüşü için beş emülatör testi geçti. Dokunmayla açılan kart da dönüş odağı olarak korunur; yüklenmekte olan eski sayfadaki kartlar açılmaz.

## 2026-10-02 - Diyalog okunabilirliği ve VOD liste kararlılığı

- Standart OSD diyalogları sistemin gündüz/gece seçiminden bağımsız koyu temayı kullanır. Liste seçenekleri ve alt eylem düğmeleri okunabilir renklerle gösterilir; özel içerik tasarımları değiştirilmez.
- VOD araması sırasında eski yükleme işi yeni aramanın imlecini değiştiremez veya listeyi beklemede bırakamaz. Boş durum, adapter güncellemesi tamamlandıktan sonra gösterilir; yükleme göstergesi ve hata durumunda yeniden deneme eklendi.
- Klavye VOD kataloğunun ölçülerini değiştirmez; arama klavyesindeki Tamam klavyeyi kapatır ve odağı kataloğa taşır. Sütun sayısı ekran genişliğine göre belirlenir.
- Gündüz modunda diyalog metinleri ve VOD arama/klavye yerleşimi için emülatör testleri eklendi. Fiziksel TV kumandasıyla doğrulama ayrıca gereklidir.

## 2026-10-02 - MultiView tam ekran dönüşü

- TIF yüzeyinde eski vendor dikdörtgeni yeni tam ekran ölçüsünden önce uygulanmaz. Görünür alan değiştiğinde TIF oturumu son yerleşim ölçüsüyle yeniden kurulur; gecikmiş eski yerleşim işleri iptal edilir.
- Tam ekranda gizlenen IPTV hücreleri duraklatılır; dönüşte yüzeyleri yeniden bağlanıp canlı uçtan devam edilir. Yüzey dönüşünün ilk anları donma olarak değerlendirilmez; seçim ve ses odağı korunur.
- Yüzeyin ara ölçülere geçmeden doğru en-boy oranıyla büyütülüp küçültülmesi için test genişletildi. Fiziksel MediaTek TV üzerinde görüntü ölçekleme ve çoklu yayın dönüşü ayrıca doğrulanmalıdır.

## 2026-10-02 - Canlı TV / VOD mod ayrımı

- VOD oynatırken OK/MENU filmi duraklatıp kütüphaneyi açar. Back önce kontrolleri ve infobarı kapatır; canlı kanallara otomatik dönüş yapılmaz.
- Film modunda kanal numarası, CH+/CH- ve LAST canlı yayını açmaz. Canlı kanal listesi VOD içeriklerini göstermez.
- Kırmızı Mod menüsü ve VOD kütüphanesindeki Canlı TV / Devam Et eylemleri eklendi. Son canlı kanal ile son film ayrı hatırlanır; film geçmişi canlı kanal geçmişine karışmaz.
- Film değiştirilirken devam konumu eski filme kaydedilir; geciken VOD sorgusu açık mod değişimini geri çeviremez. Kumanda yönlendirmesi ve içerik türü için testler eklendi.

## 2026-10-02 - MultiView seçim özeti ve TIF yüzey ölçümü

- MultiView seçici artık bütün katalog yerine yalnız eklenen kanalları gösterir. Kanal ekle mevcut kanal listesini açar; kaynak, arama ve harf filtreleriyle seçim yapılır. OK ekler/çıkarır, Yeşil veya Back özete döner. Seçim sırasında otomatik kanal açma ve liste zaman aşımı kapalıdır.
- Özette Yeşil başlatır, Sarı kanal ekler, Kırmızı temizler. Tek TIF ve cihaz yayın sınırı korunur; geçici seçim anahtarları ekran yeniden oluşturulurken saklanır.
- TIF iç video yüzeyi dış çerçeveyle birlikte yeniden ölçülür ve yüzey buffer boyutu yerleşime bağlanır. Ölçüm testi eklendi; vendor görüntüsünün tam ekran büyümesi gerçek TV üzerinde ayrıca doğrulanmalıdır.
- VOD ekranının kaynak/kategori, gezinme, detay, sayfalama ve hata davranışı için beş aşamalı plan TASKS.md içine kaydedildi; VOD arayüzü bu değişiklikte yenilenmedi.

## 2026-10-02 - Harf filtresi

- Harf çubuğu ilk kayda atlamak yerine seçilen harfle başlayan kayıtları mevcut sırayla gösterir; Tümü harf filtresini temizler. Normal kanal listesi, IPTV kütüphanesi ve Devam Et görünümü desteklenir.
- IPTV sayfalaması, kayıt sayısı ve filtreli sayı ile seçim veritabanında çalışır. Türkçe harflerin büyük/küçük biçimleri birlikte eşleşir; hızlı harf değişiminde eski sorgular yeni listeyi ezmez.
- Uzun sağ ok, kısa sağ/sol sayfa geçişi ve sol/Back dönüş korunur; harf seçimi yayını otomatik değiştirmez.

## 2026-10-01 - MultiView ve PiP geçişleri

- Tek kanal MultiView olarak açılabilir; uygun tek IPTV seçimi PiP/MultiView görünüm seçimi sunar. Mevcut MultiView/PiP kanalları seçici yeniden açılırken korunur.
- MultiView’de yeşil tuş ve uzun OK menüsü kanal ekleme/seçim düzenleme açar. Bir/iki yayınlı görünümde IPTV hücresi PiP görünümüne çevrilebilir; aynı kanal çift oynatılmaz ve tek tuner sınırı korunur.
- Seçimler güncellenirken değişmeyen hücreler yeniden başlatılmaz; kapasiteyi aşan seçimler sessizce kırpılmaz.
- TIF iç SurfaceView ölçüsü MultiView hücresi/tam ekran geçişinde yeniden hesaplanır; yalnız dış TvView çerçevesinin büyümesi engellenir. Bildirilen video oranı korunur; normal görünüm vendor yerleşimini kullanır.

## 2026-10-01 - IPTV kaynak oynatma profilleri

- Kaynak işlemlerine canlı/VOD buffer, azami çözünürlük ve otomatik kurtarma ayarları eklendi. Boş tercihler genel ayarı kullanır; genel kalite ve kurtarma seçenekleri IPTV ayarlarında bulunur.
- Kaynağa özel buffer varsa oynatma çubuğundaki değişiklik o kaynağa, yoksa genel ayara kaydedilir. Kalite tercihi cihazın MultiView sınırını aşamaz.
- Room 24/25 -> 26 veri koruyan geçişleri ve yedek desteği eklendi. Kaynak yenilemesi oynatma tercihlerini korur.
- Header/DRM, medya/ses entegrasyonu, güncelleme özeti, alternatif yayınlar, ayar araması, dizi ve ilk kurulum işleri TASKS.md içinde sıraya alındı; bu özellikler henüz tamamlanmadı.

## 2026-10-01 - Dialog düğmesi odağı

- Koyu dialogların alt eylem düğmeleri kumanda odağında mavi dolgu, açık kenarlık ve koyu yazıyla belirginleşir. Dialog listelerinin, bağlam menülerinin ve giriş alanlarının görünümü değiştirilmedi.
- Ayarlar > Kanallar altında isteğe bağlı harfle gezinme eklendi. Normal kanal listesi ve tam IPTV kütüphanesinde uzun sağ ok harf çubuğunu açar; mevcut harfler filtreye göre hesaplanır. Aktif harf listeye doğru kavisli vurgulanır. Harfle gezinme otomatik kanal açmaz, kısa sağ/sol sayfa geçişini korur.

## 2026-10-01 - Açılış, TIF altyazı ve XMLTV güvenliği

- Siyah kanal geçişi ekranındaki yazı seçilen Lottie yükleme animasyonuyla değiştirildi. Animasyon APK içinde bulunur, ilk kullanımda yüklenir ve geçiş bitince durur.

- Açılışta Multi-View hücreleri hazırlanmıyor; ilk Multi-View kullanımında oluşturuluyor. Tuner giriş sorgusu arka planda çalışıyor ve kanal birleştirmede yalnız mevcut kanalların kullanıcı tercihleri okunuyor.
- Kanal editöründeki kalite etiketleri kanal adından tahmin edilmiyor; yalnız bildirilen video biçimi kullanılıyor. Bilgi yoksa etiket gösterilmiyor.
- TIF altyazı kapatma işleminde parça seçiminin yanında caption gösterimi de kapatılıyor; kanal açıldığında kayıtlı altyazı tercihi hemen uygulanıyor.
- Infobar tarihi tek satırda gösteriliyor; alan daraldığında yazı boyutu otomatik küçülüyor.
- XMLTV güncellemeleri görünmeyen geçici kaynakta hazırlanıp başarı sonunda tek veritabanı işlemiyle yayımlanıyor. Bozuk veya boş XMLTV mevcut EPG'yi silmiyor; kaynak kimliği ve elle eşleştirmeler korunuyor.

Bu belgede TVApp uygulamasında yapılan tüm geliştirmeler, hata düzeltmeleri ve arayüz değişiklikleri sürüm ve tarih sırasıyla kaydedilir.

---

## [Geliştirme / En Son Değişiklikler]
### IPTV Watchdog Kurtarma Denemelerine Geri Çekim (Backoff)
* **Yavaş sunucuya anlık tekrar tekrar vurma yok:** İzleyicinin (watchdog) yeniden hazırlama (re-prepare) denemeleri artık geri çekimli: 1. deneme anında yapılır, sonraki denemeler 2 sn, 4 sn, 6 sn… şeklinde gecikmeli (8 sn üst sınır). Sunucu yavaşladığında veya kısıtladığında oynatıcı üst üste anında bağlanma denemeleriyle sunucuyu zorlamaz; yayın birkaç saniye içinde geri döner.
* **Kendiliğinden düzelme beklemeyi iptal eder:** Geri çekim beklerken yayın kendiliğinden iyileşirse (READY durumu, ilk kare) bekleyen gecikmeli deneme iptal edilir; kanal değişimi, stop/release ve manuel Tekrar Dene de bekleyen denemeyi temizler. Bekleme sürerken watchdog yeniden sayım yapmaz, deneme sayacı şişmez. Deneme gecikme hesabı test edilebilir saf fonksiyona taşındı (`IptvPlaybackHealthTest`).
### Yayın Donmalarına Karşı Otomatik Kurtarma
* **Donma sonrası sessiz ölüm engellendi:** IPTV yayın donduğunda izleyici (watchdog) zaten birkaç kez yeniden hazırlıyor ve tüm alternatif akışları deniyordu; hepsi tükenirse kanal ölü karede kalıyordu. Artık bu noktada aynı kanal için sınırlı sayıda (2) tam otomatik yeniden ayarlama (re-tune) yapılır; bunlar da yetmezse hata mesajı + Tekrar Dene düğümü kalıcı kurtarma yolu olarak kalır. Uygulama hiçbir koşulda kendi kendini kapatmaz.
* **Crash güvenliği:** Alternatif akışa geçiş ve alternatif akış listesi veritabanı okuması artık yakalanan hataya dönüşür; beklenmedik bir oynatıcı hatası çökme yerine `IPTV_ALTERNATIVE_PLAY_FAILURE` / `IPTV_ALTERNATIVES_FAILURE` kaydı + ekranda hata iletisi olarak görünür. Süreç boyunca `IPTV_EXHAUSTED_RETUNE` kayıtları yazılır; başarılı oynatma sayaçları sıfırlar.
### Kanal Değişiminde Siyah Ekranda Yayın Mesajı
* **“TV yayını birazdan sunulacak” bildirimi:** “Kanal değişiminde siyah ekran göster” açıkken siyah ekranın ortasında artık bilgi mesajı gösterilir; yeni kanalın görüntüsü gelince mesaj da siyah ekranla birlikte kalkar. Kullanıcı ekranın bozuk değil, kanalın bağlanmakta olduğunu net anlar. Mesaj İngilizce arayüzde “TV broadcast starting soon” olarak görünür.
### XMLTV Gece Yenilemesi Ayarları (Ayarlar > Kanallar)
* **Aç/kapa + saat seçimi:** Kanallar sayfasına "XMLTV gece yenileme" bölümü eklendi: "Gece yenileme (EPG taze tut)" düğmesi ve 00:00–23:00 arası yenileme saati seçimi. Her iki ayar da TV ve mobil paketlerinde aynı şekilde çalışır; saat seçimi mevcut kumanda uyumlu choice satırıdır (sol/sağ ile değiştirilir).
* **Anında uygulama:** Düğme kapatıldığında JobScheduler job'u hemen iptal edilir; açıkken veya saat değiştiğinde job yeni pencereye yeniden planlanır. Kayıtlı URL kaynağı yoksa job planlanmaz. Ayar `nightly-refresh` preference dosyasında saklanır; uygulama yeniden başlatmalarında korunur.
* **Ayar değişikliklerinde hata kaydı:** Job planlaması beklenmedik şekilde başarısız olursa `NIGHTLY_REFRESH_SETTING_FAILURE` debug loga yazılır.
### XMLTV Gece Yenileme Politikası (JobScheduler)
* **EPG hep taze, kullanıcı etkileşimi bozulmaz:** Kayıtlı XMLTV URL kaynakları artık her gece yerel saat ~04:00 penceresinde cihaz boşken sessizce yenilenir. Job minimum 6 saatlik flex penceresiyle planlanır; cihaz o sırada uyanıksa pencere içinde, değilse sabah ilk açılışta koşar; `setPersisted(true)` ile yeniden başlatmalarda kaybolmaz. Pil düşükse bekler (`setRequiresBatteryNotLow`).
* **Kapsam:** Gece işi tüm etkin URL kaynaklarını yeniler, ardından Xtream kısa EPG'yi tazeler ve süresi geçmiş programları temizler; tek kaynağın hatası diğerlerini engellemez. Başarı (`NIGHTLY_REFRESH_SUCCESS`) ve hata (`NIGHTLY_REFRESH_FAILURE`) mevcut debug log tesisine yazılır.
* **Yaşam döngüsü:** Job, XMLTV URL kaynağı eklenince/güncellenince planlanır; son URL kaynağı silindiğinde veya tüm veriler temizlendiğinde iptal edilir — boşta gereksiz iş yapmaz. Mevcut 12 saatlik periyodik job, gece penceresinin yedek güvencesi olarak kaldı.
* **Test edilebilirlik:** Pencere gecikmesi hesabı saf `NightlyRefreshWindows` nesnesine taşındı (java.time, DST dahil) ve birim testiyle sabitlendi.
### IPTV/XMLTV İçe Aktarma Performansı ve Ana İş Parçacığı Denetimi
* **XMLTV zaman çözümleme hızlandırıldı:** Programme `start/stop` ayrıştırma her program satırında üç `SimpleDateFormat` kuruyordu; artık önceden derlenmiş, thread-safe `java.time` biçimlendiricilerle (`XmlTvTime`) yapılıyor. Ofsetsiz zaman damgaları artık cihaz yereline değil UTC'ye düşer (XMLTV belirtimiyle uyumlu, cihazdan bağımsız kararlı sonuç).
* **EPG anahtar normalizasyonu:** `normalizeEpgKey` içindeki üç regex her çağrıda yeniden derleniyordu; 100.000+ programlı içe aktarmada yüz binlerce `Pattern` derlemesi kesildi (kalıplar önceden derlenir, eşleştirme kuralları birebir korunur).
* **Progress seli kısıtlıldı:** XMLTV sayacı artık zaman bazlı kıstırıcıyla (`XmlTvProgressThrottle`) en fazla ~8 güncelleme/sn iletir; IPTV tarafında okuma aşaması UI güncellemeleri aynı aralıkta kıstırılır. Aşama değişimleri gecikmeden iletilir.
* **EPG hedef anahtarları projection ile okunur:** `activeChannelKeys` tam `user_channels`/`iptv_channels` varlıkları yerine yalnız EPG anahtar aday kolonlarını okur; 15.000+ kanallı kütüphanede içe aktarma öncesi tarama belirgin hafifler.
* **IPTV staging yazımı:** SHA-256 özeti import mutex'i altında paylaşılan `MessageDigest` ile hesaplanır ve batch boyutu 500'den 1000'e çıkarıldı; 15k+ liste için daha az veritabanı turu.
* **Ana iş parçacığı ihlalleri artık kanıtlanabilir:** Yalnızca debug derlemelerinde `StrictMode` ana iş parçacığı disk/ağ ihlallerini `MAIN_THREAD_VIOLATION` kaydıyla `Download/TVApp/TVApp-debug-*.log` dosyasına yazar; release davranışı değişmez. Dosyadan liste adı okuma (IPTV içe aktarma) ana thread'den IO'ya taşındı. Cihazdaki "yavaşlık" şikâyeti bu kayıtlarla somut olarak doğrulanabilir.
* **Zamanlama ölçümü:** XMLTV içe aktarma süresi `XMLTV_IMPORT_TIMING` kaydıyla debug loga yazılır (IPTV'deki `IPTV_IMPORT_TIMING` karşılığı); darboğaz cihazda ölçülebilir.
### REMOTEEDIT Denemesi Tamamen Kaldırıldı (DB v25 → v24 geri dönüşü dahil)
* **Neden:** Telefon/web üzerinden uzaktan kanal düzenleme denemesi sistemde yavaşlığa ve kararsız davranışa yol açtı; deneme iptal edildi. Gömülü HTTP sunucusu, eşleştirme, web panosu, telefon client, NSD duyurusu ve uzaktan import kuyruğu dahil REMOTEEDIT'in tamamı kod tabanından çıkarıldı. OPT-1.7 EPG önbellek iyileştirmesi korundu.
* **Veritabanı geri dönüşü:** Sürüm 25'ten 24'e inildi; `paired_devices` tablosu ve `user_channels.revision` sütunu kaldırıldı. Yeni `MIGRATION_25_24` yıkıcı değildir: deneme sürümlerini kurmuş cihazlarda tüm kullanıcı verileri (özel sıra/numara/ad, favori, gizli, gruplar, IPTV kaynakları) korunarak düşürülür.
* **Kaldırılan arayüzler:** Ayarlar > Sistem > "Telefonla yönetim" bölümü, mobil ana ekrandaki "TV yönetimi" toolbar ikonu ve 8890 port dinleyicisi kalktı; REMOTE_EDIT_ENABLED BuildConfig alanı ve ilgili manifest izin/exportları temizlendi.
### Seçici EPG Önbellek Geçersizleştirme (OPT-1.7)
* **Toplu geçersizleme artık sürüm tabanlı ve hafif:** XMLTV/Xtream EPG verisi değiştiğinde `EpgSnapshotCache` tümünü boşaltan 6 nokta yeni `invalidateAll()` çağrısına bağlandı; önbellek sürüm sayacıyla eski anlık görüntüleri tek seferde geçersiz kılar (iki harita temizliği + sayaç artışı). Kanal başına `invalidate(sourceKey)` davranışı korundu: tek kanalın EPG eşlemesi değiştiğinde yalnız o kanalın önbelleği düşer.
* **Fayda:** EPG kaynağı güncellemeleri artık tüm kanalların şu anki/sonraki program önbelleğini boşaltır; bir sonraki okumada yalnızca gerçekten değişen veriler yeniden hesaplanır. Kanal listesi gezinirken gereksiz veritabanı sorguları azalır.
### XMLTV Güncellemesinde Aşamalı İlerleme, Ekran Koruyucu Engeli ve Ayrıştırma Hızı
* **Aşamalı ilerleme gösterimi:** XMLTV kaynak güncellemesi artık ekranda aşama aşama izleniyor: "bağlanılıyor → işleniyor → kaydediliyor → bitiriliyor" ve o ana kadar işlenen program sayısı (örn. "XMLTV güncelleniyor: işleniyor (24.500 program)"). Güncellemenin gerçekten çalıştığı, tıkandığı mı yoksa bittiği mi artık net görülüyor; liste satırındaki program sayısı da işlem bitince güncel toplamı gösterir.
* **Güncelleme sırasında ekran koruyucu devreye girmez:** İşlem sürerken ekranda `FLAG_KEEP_SCREEN_ON` tutulur; işlem tamamlanınca veya hata verince bayrak kaldırılır. Böylece uzun (15.000+ program) güncellemeler ortasında ekran koruyucu devreye girip akışı bölmez.
* **Ayrıştırma hız iyileştirmesi:** Program/kanal anahtar normalizasyonu (`normalizeEpgKey`: üç regex) artık program başına iki kez değil, kanal kimliği/adı başına bir kez hesaplanıp önbellekten okunuyor; 60.000 programlık dosyalarda işlemci süresi belirgin azalır (aynı kanal binlerce programda tekrarlanır). Eşleştirme kuralları harfiyen korunmuştur.
### Kanal Değişiminde Siyah Ekranda Sessizlik
* **Siyah ekran altında eski kanal sesi kesiliyor:** "Kanal değişiminde siyah ekran göster" açıkken kanal geçişi başladığında eski kanalın sesi de kesilir; yeni kanalın görüntüsü geldiğinde (TIF video hazır / IPTV ilk kare) ses otomatik açılır. Yayın açılamazsa hata yolunda ses geri açılır; TIF donanım yolu için güvenli 0.001 ses düzeyi kullanılır (MediaTek sıfırlama sorununa karşı mevcut koruma korunur).
### OPT Hızlı Kazanımlar: Yazılım Decoder Önceliği ve Ölü IJK Kodunun Temizliği (OPT-1.5, OPT-1.6)
* **Yazılım extension decoder'ları öne alındı (OPT-1.5):** Media3 `DefaultRenderersFactory` artık `EXTENSION_RENDERER_MODE_PREFER` ile yapılandırılıyor; donanım decoder'ın seçemediği veya çözümlemede başarısız olduğu akışlar yeniden başlatma olmadan paket içindeki yazılım decoder'lara düşüyor. Multi-View hücreleri mevcut çözünürlük/bitrate limitleriyle korunuyor. Cihaz doğrulaması önerilir.
* **Ölü IJK kodu tamamen kaldırıldı (OPT-1.6):** `enableIjkFallback` parametresi, `IptvPlaybackEngine`/`IptvPlaybackEngineMode` türleri, `setPlaybackEngineMode`/`onEngineChanged`/`onExternalFallbackRecommended` callback'leri, `classifyIjkPlaybackFailure`/`shouldUseIjkFallback`/`ijkPlaybackExceptionCode` yardımcıları, oynatma kontrollerindeki Motor butonu, kanal motoru seçim diyaloğu, Ayarlar'daki devre dışı motor seçim bloğu, ilgili string'ler, `ic_engine_small` ikonu ve proguard IJK keep kuralları silindi. Kanal başına motor kilit verisi kullanıcı verisi olduğu için Room'daki `playbackEngineOverride` kolonu geriye dönük uyumlulukla korunuyor; arayüzde artık hiçbir yerde kullanılmıyor.
* Phase 1 optimizasyon dökümü güncellendi: OPT-1.1 (HTTP zaman aşımları), OPT-1.2 (merkezi HTTP header'ları), OPT-1.3 (başarısız logo TTL/LRU), OPT-1.4 (ABR bant genişliği katsayısı), OPT-1.8 (program tick) ve OPT-1.9 (yön duyarlı prefetch) önceki paketlerle tamamlanmış olarak işaretlendi.
### Planlama: TV Sunucu / Telefon Client Liste Yönetimi (REMOTEEDIT)
* **TASKS.md sonuna yeni epic eklendi:** TV sürümü sunucu (server) modunda yerel ağda kanal/liste verisini yayınlayacak; telefon sürümü client modunda veriyi çekecek, telefondan düzenleyecek ve sunucuya senkron edecek. Plan ilkeleri: Room yetkili veri kaydı olarak kalır, tek seferlik eşleştirme kodu + loglanmayan jetonlar, 15.000+ katalog için sayfalı aktarım (mevcut keyset sorguları), alan bazlı son-kazanan + `revision` çakışma stratejisi, `local` ve `paid` paketlerinde çalışma. Görevler REMOTEEDIT-001..007 olarak sprint'lere bölündü; uygulama onaydan sonra başlayacak.
### IPTV Liste Performans Paketi (Keyset + Program Tick + Prefetch)
* **Keyset sayfalama:** Tam IPTV kütüphanesi ve VOD katalog sayfalama sorguları `LIMIT/OFFSET` yerine `originalIndex >= :fromIndex` anahtar kümesi formuna geçti; yüksek sayfa konumlarında SQLite artık satır atlamak yerine indeks üzerinde yürüyerek okuyor (perf raporundaki 10 ms'lik yüksek-offset kalemi giderildi). Doğrudan numara atlama (`AT_INDEX`) da `originalIndex >= :targetIndex` tabanlı `AtOrAfter` sorgusuna bağlandı; seyrek indeks uyarısıyla davranış aynıdır.
* **Kanal listesinde program ilerlemesi 60 saniyelik tick:** Program ilerleme çubukları artık yalnızca liste açıkken 60 saniyede bir, yalnızca program satırına bağlı görünen satırlar için yeniden bağlanıyor; tam adapter yenilemesi ve saniyelik iş yok — satır titremesi (blink) önleniyor.
* **Yön duyarlı logo prefetch:** Kanal listesinde odak yönüne göre en yakın satırların logoları önce önden yüklenir; bir sonraki odaklenen satırın logosu neredeyse her zaman önbellekten gelir.
### XMLTV Kaynak Satırında Gerçek Adres Gösterimi
* **XMLTV kaynak satırı artık URL/dosya yolunu gösteriyor:** Satırın ikinci satırında yalnızca "URL"/"Dosya" etiketi yerine kaynağın gerçek adresi (http(s) URL'si veya dosya yolu) yazıyor; tür etiketi başlığa taşındı ve tür ikonuyla desteklendi. IPTV kaynak ekranı zaten adresi gösterdiği için orada değişiklik gerekmedi.
### XMLTV Kaynak Ekranı Çökmesi Düzeltmesi
* **XMLTV kaynakları açılışta kapanıyordu:** `item_iptv_source` düzeninin 3 satırlı yapıya geçirilmesinden sonra XMLTV kaynak ekranındaki liste `ArrayAdapter`'ın zorunlu kıldığı `@android:id/text1` kimliğini bulamayıp çöküyordu. Liste artık düzeni kimliklere göre bağlayan özel adapter ile çalışıyor; düzen güncellemeleri satır yeniden kullanımıyla yapılıyor.
### Kanal Değişiminde Siyah Ekran Ayarı
* **Ayarlar > Kanallar > Oynatma’da yeni seçenek:** "Kanal değişiminde siyah ekran göster" açıldığında kanal geçişinde eski kanalın donmuş görüntüsü gizlenir; ekran siyah kalır ve infobar okunabilir durumda kalır. Yeni kanalın görüntüsü geldiğinde (TIF `videoAvailable`, IPTV ilk kare hazır) siyah katman kalkar; yayın açılamazsa hata ekranı gösterilir. Varsayılan olarak kapalıdır; PiP ve Multi-View etkilenmez. Ayar hem TV hem mobil paketlerinde mevcuttur.
### IPTV Veritabanı İndeks İyileştirmesi (DB v24)
* **contentType + displayName ve contentType + groupTitle indeksleri:** Büyük kataloglarda (15.000+ kayıt) VOD kategori/tür ve sayfalı liste sorgularının tam tablo taraması yerine indeks araması yapması sağlandı; tarama sonrası page cache süpürmesi nedeniyle diğer sorguların yavaşlaması da azalır. Room migration 23→24 ile eklenir, kullanıcı verisi korunur.
### IPTV Kayıtlı Liste Düzeni, Zapping Kararlılığı ve Ekran Ortası Hata / Tanılama Gösterimi
* **Kayıtlı IPTV Listelerinde Adres Taşması Düzeltmesi:** Kayıtlı listeler ekranında uzun URL'ye sahip kaynaklarda istatistiklerin ve güncelleme tarihlerinin ekrandan kaybolması önlendi. Liste öğesi 3 satırlı yapılandırılmış düzene (`item_iptv_source.xml`) geçirilerek adres tek satırda sınırlandırıldı (`singleLine`, `ellipsize="end"`), istatistik ve tarihler her zaman net şekilde görünür kılındı.
* **Kanal Gezinirken (Zapping) Spinner Takılması ve Panel Kapanmasının Önlenmesi:** Hızlı kanal geçişlerinde eski kanalın "yükleniyor spinnerı"nın ekranda takılı kalması engellendi; kanal değişimi ve callback doğrulamalarında stale durumlar temizlendi. Yayın toparlandığında veya kontroller tetiklendiğinde açık olan kanal listesi panelinin istemsizce kapanması tamamen önlendi.
* **Ekran Ortasında Kullanıcı Dostu Hata ve Teknik Tanılama Gösterimi:** Bir IPTV kanalı açılamadığında ortadaki dönen spinner gizlenerek yerine yayının neden açılamadığı kullanıcı dostu yerelleştirilmiş mesajlarla (Ağ hatası, zaman aşımı, HTTP 401/403, 404, sunucu hataları vb.) gösterilir. Ayarlar menüsünden "Oynatma istatistikleri (Stats OSD)" etkinleştirilmiş ise mesajın altına teknik hata kodu ve ayrıntısı eklenir.

### IPTV Senkronizasyon Bellek İyileştirmesi ve TV OSD / Infobar Açılış Akıcılığı
* **Infobar Açılışında Gereksiz Layout Ölçümünün (Relayout) Engellenmesi:** `MainActivity` içinde her kanal değişiminde ve infobar açılışında `updateInfoBarHeight` çağrılırken, hesaplanan hedef yükseklik mevcut `layoutParams.height` ile aynıysa yeniden atama yapılması engellendi. Böylece kanal geçişlerinde tüm OSD ağacının baştan ölçülmesi (measure/layout pass) ve kumanda gecikmesi tamamen ortadan kalktı.
* **Teknik Rozet ve Bilgilerde Şartlı Güncelleme:** `updateTechnicalBadges` ve `updateTechnicalBadgesForIptv` fonksiyonlarında slot görünürlükleri ve metin atamaları yalnızca değer değiştiğinde (`text != newText` / `visibility != targetVisibility`) yapılacak şekilde optimize edildi; gereksiz Android arayüz çizimleri elendi.
* **IPTV Toplu Aktarımında Bellek Kopyalamasının Kaldırılması:** `IptvRepository` içinde binlerce kanalın staging tablosuna yazılması esnasında her 500'lük parçada yapılan gereksiz `batch.toList()` bellek kopyalaması kaldırılarak doğrudan liste referansı ile çalışılması sağlandı; çöp toplayıcı (GC) baskısı azaltıldı.
* **Kanal Anahtarı Normalizasyon Koruması:** `selectionName` optimize edilirken `XmlTvMatcher` ve EPG isim normalizasyon kuralları %100 aynen korunarak binlerce kanalın sorunsuz eşleşmesi garanti altına alındı.

### IPTV Oynatıcı Tampon ve Kanal Geçiş (Zapping) Hızlandırması
* **Hızlı Kanal Başlatma (Zapping):** `IptvPlaybackController` içinde ilk oynatma başlangıç tamponu (`BUFFER_FOR_PLAYBACK_MS`) 1.500 ms'den 800 ms'ye düşürülerek kumandadan kanal değiştirildiğinde veya listeden seçildiğinde yayının ekrana gelme süresi yaklaşık 700 ms hızlandırıldı.
* **Hızlı Rebuffer Toparlanması:** Yayın duraksadığında toparlanma süresi (`BUFFER_AFTER_REBUFFER_MS`) 4.000 ms'den 2.500 ms'ye indirilerek ağ dalgalanmalarında bekleme süresi kısaltıldı.
* **Geri Sarma Tamponu (Back Buffer):** Media3 LoadControl için 15 saniyelik `backBuffer` aktif edilerek (`setBackBuffer(15_000, true)`) yayını kısa süreli geri sararken veya atlama yaparken anında yeniden oynatma sağlandı.
* **Canlı Yayın Bellek Yükünün Azaltılması:** Minimum tampon hedefi (`MIN_BUFFER_MS`) 15.000 ms'den 8.000 ms'ye optimize edilerek canlı akışlarda Android TV cihazının RAM ve ağ bant genişliğini gereksiz doldurması engellendi.
* **Adaptif Bant Genişliği Oranı Artırımı:** Adaptif parça seçiminde kullanılan `ADAPTIVE_BANDWIDTH_FRACTION` %75'ten %82'ye yükseltilerek mevcut internet hızında daha yüksek kaliteli video ve ses profillerine daha hızlı geçilmesi sağlandı.

### EPG TV Rehberinde Hücre Yeniden Kullanımı (View Reuse) ile 60 FPS Kaydırma Akıcılığı
* **Sıfırdan View Tahsisinin ve `removeAllViews` Çağrısının Kaldırılması:** EPG tablosunda (`GuideScheduleAdapter`) satır her bağlandığında tüm hücrelerin silinip yüzlerce yeni `TextView` ve `LayoutParams` nesnesinin sıfırdan üretilmesi sonlandırıldı. Satır içindeki mevcut `TextView` ve spacer hücreleri silinmeden yeniden kullanılan (*View Reuse*) hafif bir mekanizmaya geçildi.
* **Garbage Collector Duraklamalarının (Jank) Önlenmesi:** Bellek tahsisi %90 azaltılarak düşük donanımlı Android TV cihazlarında dahi kumanda ile aşağı/yukarı basılı tutulduğunda yaşanan mikro donmalar ve kare düşmeleri tamamen ortadan kaldırıldı; EPG kaydırması akıcı 60 FPS standardına ulaştı.

### Logo Önbelleği (TTL & LRU), Çift Yönlü Prefetch ve HTTP Zaman Aşımı Optimizasyonları
* **Logo İndirme Hatalarında Bellek Sınırı ve 5 Dakika TTL (`FailedLogoTracker`):** İndirilemeyen logolar için tutulan sınırsız küme yerine, LRU mantığıyla çalışan maksimum 200 kapasiteli ve 5 dakika zaman aşımlı (`FailedLogoTracker`) mekanizmasına geçildi. Geçici ağ kesintilerinde inemeyen logolar 5 dakika sonra otomatik affedilir ve tekrar denenir; bellek sızıntısı riski ortadan kaldırıldı.
* **Kanal Listesi ve EPG Kılavuzunda Çift Yönlü Logo Prefetch:** Hem `ChannelAdapter` hem de `GuideScheduleAdapter` içinde kumanda yönü (Yukarı/Aşağı) takip edilerek, kullanıcı yukarı kaydırırken önceki kanalların logoları da önceden yüklenir hale getirildi.
* **IPTV HTTP Bağlantı ve Okuma Zaman Aşımlarının İyileştirilmesi:** `XtreamClient`, `StalkerClient` ve `IptvRepository` içindeki okuma zaman aşımı süreleri 60s/30s değerlerinden TV için optimize edilmiş 20s değerine, bağlantı zaman aşımı süreleri ise 10s değerine çekilerek yanıt vermeyen sunucularda arayüzün askıda kalması önlendi.

### Kanal Düzenleyicide Senkron Tek Kanal Taşıma ve Renk Butonları Tepki Optimizasyonu
* **Senkron `notifyItemMoved` ile Sıfır Gecikmeli Kanal Taşıma:** Kanal düzenleyicisinde (`ChannelEditorAdapter`) asenkron `AsyncListDiffer` kuyruğu yerine senkron liste yönetimi ve Android RecyclerView'ın yerel `notifyItemMoved` mekanizmasına geçildi. Kumandadan Yukarı/Aşağı tuşlarına hızlıca basılsa dahi taşınan kanalın odağı (`itemView`) asla kaybolmaz ve yerine geçen kanalın seçili görünmesi sorunu tamamen çözüldü.
* **Anlık Dinamik Sıra Numarası ve Önizleme Takibi:** Kanal taşınırken liste pozisyonuna göre kanal numarası anında dinamik olarak (`position + 1`) güncellenir (`PAYLOAD_NUMBER`). Sağ taraftaki önizleme panelinde taşınan kanalın adı ve güncel sıra numarası gecikmesiz olarak takip edilir.
* **Sırayı Kaydetme Esnasında Anlık Geri Bildirim (Optimistic UI):** Kullanıcı "Yeşil · Sırayı kaydet" tuşuna bastığı anda ekran beklemeden derhal Normal moda döner ve alttaki butonlar anında güncellenir. Arka planda veritabanı yazımı sessizce tamamlanarak butonların kumandanın gerisinde kalması hissi ortadan kaldırıldı.
* **Dinamik Renk Butonları ve Pasif Buton Yönetimi:** Taşıma modunda işlevsiz olan Mavi buton soluklaştırılarak (`alpha = 0.35f`) pasife alındı. Odaklanan kanala göre buton durumlarının anlık güncellenmesi sağlandı.

### Google TV "Devam Et" (Watch Next) VOD Entegrasyonu & Otomatik Ana Ekran Kanal Satırı
* **Google TV "Devam Et" Satırında VOD İlerlemesi:** TVApp içinde izlenen VOD (Film, Dizi vb.) içerikleri duraklatıldığında veya çıkıldığında Android TV / Google TV'nin ana ekranındaki "Devam Et" (Watch Next) satırına afişi, başlığı, toplam süresi ve kalınan izleme konumu ile birlikte eklenir (`WATCH_NEXT_TYPE_CONTINUE`, `TYPE_MOVIE`). Google TV kartı üzerinde izleme ilerleme çubuğu dinamik olarak gösterilir.
* **Mevcut Yarım Kalan VOD'ların Açılışta Otomatik Senkronizasyonu:** Daha önceden TVApp içinde izlenip yarım bırakılmış tüm VOD içerikleri uygulama açılışında taranır (`syncResumeVods`) ve Google TV "Devam Et" satırına afişleri ve kalınan dakika bilgileriyle otomatik olarak yerleştirilir.
* **Doğrudan Kaldığı Yerden Başlatma (Deep Link):** Kullanıcı Google TV ana ekranındaki filme tıkladığında TVApp açılır; içerik normal canlı kanal listesinde olmasa bile doğrudan IPTV kütüphanesinden yüklenerek tam kaldığı saniyeden oynatılmaya devam eder.
* **İzleme Bittiğinde ve Menüden Kaldırıldığında Otomatik Senkronizasyon:** Film tamamlandığında (veya son 60 saniyesi kaldığında / STOP tuşuna basıldığında) kart Google TV Watch Next satırından otomatik olarak silinir. Kullanıcı Google TV arayüzünden "Listeden kaldır" dediğinde `WatchNextRemovedReceiver` tetiklenerek hem Google TV'den hem de TVApp `IptvResumeStore`'dan kayıt senkronize olarak temizlenir.
* **Google TV Doğrudan Ana Ekran Kanal Satırı (`PreviewChannel` & `PreviewProgram`):** Yeni Google TV arayüzlerinde "Kanalları Özelleştir" menüsü bulunmadığından kanal satırı doğrudan ve otomatik olarak varsayılan kanal statüsünde eklenir (`publishDefaultChannel`). Android TV Launcher'ın içi boş kanalları gizleme kuralı gereğince kanalın içi kullanıcının ilk 10 canlı kanalıyla (`PreviewProgram`, afiş, kanal numarası ve doğrudan izleme linki) otomatik olarak doldurulur.
* **Eski Canlı Yayın (Live Stream) Kayıtlarının Temizlenmesi:** Google TV standartlarına uymayan ve ana ekranı bozan eski canlı kanal kayıtları (`cleanupLegacyLiveChannels`) açılışta taranıp temizlenerek Google TV ana ekranı tertemiz hale getirildi.

### TV Başlangıç Veritabanı Kilitlenmesinin Kökten Çözülmesi (Room WAL Modu & Kilitlenmeyen Kanal Yükleme)
* **Room Veritabanında WAL (Write-Ahead Logging) Modu:** Room SQLite veritabanı `JournalMode.WRITE_AHEAD_LOGGING` yapılandırmasına geçirildi. Rollback Journal modundaki tüm veritabanını donduran kilitlenme ortadan kalktı; arka planda EPG veya IPTV senkronizasyonu yazma yaparken kanal listesi ve arayüz okumaları sıfır gecikmeyle anında döner.
* **Açılıştaki Ağır TvProvider IPC Senkronizasyonunun Kaldırılması:** `TvAppApplication` başlatılırken çağrılan ve Android TV'nin sistem `TvProvider`'ına 1000 IPTV kanalını tek tek IPC ile yazmaya çalışarak CPU/RAM'i tüketen ve 97 saniye kilitlenmeye yol açan `IptvInputSyncScheduler.scheduleImmediate` açılış rotasından kaldırıldı; senkronizasyon yalnızca periyodik arka plan işine devredildi.
* **Kanal Listesi Okumasında Dış Transaction Kilidinin Kaldırılması:** `ChannelRepository` içindeki kanal listeleme okuması `database.withTransaction` kilidinden çıkarıldı. SELECT sorgusu ve liste eşlemesi serbestçe okunur; yalnızca değişen kayıtlar varsa (`changed.isNotEmpty()`) kısa ve dar bir transaction açılır.
* **Hafıza İçi Kanal Önbelleği (`cachedChannels`) ile Anında Panel Açılışı:** Bellekte tutulan kanal listesi sayesinde kullanıcı kumandadan panel açtığında veritabanı okuması beklenmeden kanallar anında ekrana gelir.
* **Açılıştaki Ağır EPG Temizlik (Purge) Tetiklemesinin Kaldırılması:** `MainActivity` kanal yüklemesinde her seferinde tetiklenen ve on binlerce satırlık DELETE çalıştıran `purgeExpiredPrograms` çağrısı kritik yoldan kaldırıldı.

### TV Açılış Hızlandırması, Mobil Filtre Butonu Düzenlemesi ve 7 Günlük Log Rotasyonu
* **TV Açılışında 20-30 Sn Boş Ekran Kilitlenmesinin Giderilmesi:** `MainActivity` açılışında kanal listesinin ekrana gelmesini 20-34 saniye boyunca kitleyen senkron `purgeExpiredPrograms` çağrısı kritik yoldan kaldırıldı. Kanallar artık milisaniyeler içinde anında yüklenir; EPG temizliği ise kanallar ekrana geldikten sonra düşük öncelikli arka plan iş parçacığına aktarıldı. Açılıştaki EPG otomatik kontrol gecikmesi 5 saniyeden 25 saniyeye çıkarılarak ilk açılışta donanım ve oynatıcı kaynaklarının rahatlaması sağlandı.
* **Mobilde Kanal Seçim Filtre Butonunun Kaldırılması:** Mobil ana ekranda (`activity_mobile_main.xml`) arama çubuğunun sağında yer alan ve mobilde tüm canlı kanallar otomatik seçildiği için işlevsiz kalan filtre butonu (`mobile_btn_channel_select`) gizlenerek arayüz sadeleştirildi.
* **Tarih Bazlı Günlük Loglama ve 7 Günlük Otomatik Temizlik:** `CrashReportStore` sistemi gün bazlı isimlendirmeye (`TVApp-debug-yyyyMMdd.log`) geçirildi; gün değiştiğinde otomatik yeni log dosyası açılır. Debug ve exception/çökme logları için 7 günlük saklama süresi tanımlandı; hem harici depolama hem de MediaStore Downloads dizinlerindeki 7 günden eski tüm log dosyaları otomatik taranıp silinerek dosya şişmesi ve disk dolması tamamen engellendi.

### Akıllı ve Donmayan XMLTV EPG Mimarisi: Otomatik Arka Plan Güncellemesi ve Streaming Parse
* **Açılışta Sessiz Otomatik EPG Güncellemesi:** TVApp açılışında kayıtlı XMLTV kaynaklarının son güncellenme zamanı (`shouldAutoRefresh`) kontrol edilir; veriler 12 saatten eskiyse kullanıcıyı hiç bekletmeden ve arayüzü kilitlemeden arka planda (`Dispatchers.IO`) sessizce indirme ve yenileme başlatılır. Güncelleme bittiğinde aktif kanalın infobarı otomatik yeni verilerle beslenir.
* **Akıllı Kanal Filtreleme:** XMLTV dosyasında bulunan binlerce yabancı kanalın programları doğrudan ayıklanır; yalnızca kullanıcının TV ve IPTV listesinde kayıtlı kanallarına ait programlar işlenir (`activeChannelKeys`). Listede olmayan yabancı kanallar XML parse seviyesinde anında atlanır (`skipTag`).
* **Akışkan (Streaming) 500'lük Paketlerle Bellek Tasarrufu:** 150.000 programı tek bir listede toplayıp TV RAM'ini şişirmek yerine, programlar 500'erli paketler halinde veritabanına yazılıp bellek anında boşaltılır. RAM kullanımı 150 MB'tan 5 MB'a düşürülerek Android TV'lerdeki GC donmaları tamamen engellendi.
* **Kilitlenmeyen Mikro İşlemler:** Veritabanı yazımı küçük transaction bloklarıyla yapılarak SQLite yazma kilitleri önlendi; arka planda EPG güncellenirken kullanıcının kanal değiştirme ve gezinme akıcılığı korundu.

### Mobil IPTV Deneyimi: Son Kanalı Hatırlama, Otomatik Canlı Kanallar ve TV Ayarlarını Gizleme
* **Açılışta Son İzlenen IPTV Kanalını Geri Yükleme:** Mobilde IPTV kütüphanesinden veya kanal listesinden izlenen kanalların geçmişe kaydedilmesi sağlandı. Uygulama açılışında (`MainActivity`) son izlenen kanal doğrudan veritabanından sorgulanıp anında oynatılarak TV'deki kesintisiz açılış deneyimi mobilde de IPTV ile sağlandı.
* **Mobilde 'Kanal Seçimi' Menüsünün Kaldırılması ve Otomatik Seçim:** Mobilde `IptvSourcesActivity` üzerinden kaynak tıklandığında çıkan gereksiz "Kanal Seçimi" seçeneği gizlendi. İçe aktarılan kaynakların tüm canlı kanalları mobilde otomatik olarak seçili işaretlenerek kullanıcıyı binlerce kanalı tek tek seçme zahmetinden kurtaran akıcı bir mobil deneyim sunuldu.
* **Mobilde TV Ayarlarının Gizlenmesi:** Ayarlar ekranında (`DisplaySettingsActivity`) cep telefonlarında işlevsiz olan TV Canlı Kanallar Girişi Senkronizasyonu (`iptv_input_sync`), Hibrit TV Çalışma Modu seçimi (`working_mode`), MultiView 4 akış testi (`multiview_force_four_streams`), Cihaz Açılışında Başlat (`launch_tvapp_on_boot`), Kumanda Odakla Kanal Geçişi (`channel_focus_auto_tune`) ve OSD Bilgi Barı Konumu tercihi mobilde gizlenerek sadeleştirildi.

### Tek version.json ile Çoklu Varyant Güncelleme ve Mobil Sürüm Optimizasyonu
* **Merkezi Çoklu Paket Sürüm Yönetimi (`version.json`):** Güncelleme mekanizması tek bir merkezi bildirim dosyası üzerinden hem TV (`com.tvapp.livetv`) hem Mobil (`com.tvapp.mobile`) hem de ileride eklenebilecek varyantları (Paid, Free vb.) yönetecek şekilde güncellendi. `AppUpdateManager` artık JSON içindeki `"packages"` sözlüğünden kendi paket kimliğine (`context.packageName`) ait sürüm ve APK indirme bilgilerini okur; eski TV sürümleriyle geriye dönük tam uyumluluk için kök dizin alanları da korunur.
* **Mobil Optimize Release Derlemesi:** CI/CD iş akışında (`release.yml`) Mobil sürüm `assembleMobileDebug` yerine R8/ProGuard optimizasyonları ve küçültme aktif edilmiş `assembleMobileRelease` olarak imzalı derlenir (dosya boyutu ~23 MB'tan ~4.6 MB'a düşürüldü).
* **CI/CD Sadeleştirmesi:** Ayrı `version-mobile.json` oluşturma ihtiyacı ve gereksiz `.github/workflows/mobile-test.yml` iş akışı kaldırılarak tüm derleme ve paketleme süreci tek bir standart GitHub Actions iş akışına bağlandı.

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

### VOD Ana Sayfa ve Devam Et
* **Ayrı VOD ana sayfası (`VodHomeActivity`):** "Devam Et" yatay şeridi, sayfalı VOD ızgarası ve
  arama alanı içeren özel VOD ekranı eklendi. IPTV kanal seçim ekranının araç çubuğundan açılır.
  15.000+ katalog kuralına uygun olarak kaynak bazlı, sınırlı sayfalama kullanır; tam katalog
  belleğe alınmaz.
* **Devam Et şeridi:** `PlaybackHistoryStore` + `IptvResumeStore` birleşimi
  (`ContinueWatchingRepository`, birim testli) ile en fazla 8 VOD öğesi, kaldığı süre gösterimiyle
  listelenir. Uzun OK ile "Baştan oynat" ve "Listeden kaldır" eylemleri sunar.
* **VOD derin bağlantı:** VOD oynatma, `EXTRA_VOD_SOURCE_KEY` ile `MainActivity`'e devredilir;
  kanal ana listede yoksa IPTV deposundan çözümlenir.
* **Watch Next teşhis düzeltmeleri:** `HomeRecentChannelsPublisher` artık publish/update/delete
  hatalarını `HOME_WATCH_NEXT_FAILURE` olarak debug log'a yazar; poster 16:9 oranı ve etkileşim
  türü açıkça bildirilir; bastırılan kanal listesi 64 kayıtla sınırlanır. Not: Google TV
  launcher'daki "Devam Et" satırı Google sertifikasyonu gerektirir; Android TV launcher
  etkilenmez.

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

- TXT/Teletext remote key now opens the subtitle/teletext track picker on the playback screen and while the channel panel is visible, for both TIF and IPTV sources; a TXT hint was added to the infobar technical row (TR/EN).

- Root-cause fix for stale IPTV playback events: the playback controller now tags every queued retry/watchdog/recovery action and player callback with a tune generation, cancelling pending work and dropping events from a previous channel at dispatch time (the UI-side filters remain as a second line of defence).

- Fixed the IPTV spinner sticking on screen after switching channels: spinner visibility is now tied to the currently tuned channel, stale buffering events from a previous channel are discarded, and a failed stream hides the spinner before showing the failure notice.

- Faster IPTV channel switch: connection-type stream failures (network, HTTP, timeout) now retry once after 300 ms and then surface to the alternative-stream path instead of silently re-preparing the same URI for up to ~7 seconds.
- Repeated OK presses on a channel that is already connecting are ignored, so panicking extra presses no longer restart playback and reset the retry cycle.
- Immediate infobar/selection feedback when selecting an IPTV channel while playing a TIF channel (channel resolution continues asynchronously).

- UIINPUT grubunu tamamladık: remote olay sahipliği için `RemoteActionRouter`, güvenli Back/dialog yönlendirmesi ve Android TV UiAutomator smoke testleri eklendi.
- Ayarlar Görünüm ve Kanal sekmelerine video üstü canlı görünüm önizlemesi eklendi; ortak odak çerçevesi, renk eylemi ve ikon yuvası bileşenleri kullanıma alındı.
