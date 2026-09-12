# TV App

Android 11 tabanlı Google TV cihazları için özel Live TV uygulaması.

## Özellikler

- Cihazın vendor TIF girişlerini `TvInputManager` ile keşfetmek
- `TvContract` kanal tablosunu okumak
- Preview/öneri kanallarını ayırarak yalnızca donanım tuner kanallarını listelemek
- Room üzerinde kullanıcıya özel kanal sırası, numarası, adı, favori, gizleme ve grup tercihlerini saklamak
- TKGS/vendor taramasından sonra DVB servis kimliğiyle kullanıcı tercihlerini yeniden birleştirmek
- MediaTek tuner girişinin standart TIF setup ekranını uygulama içinden açmak ve dönüşte kanalları yeniden eşitlemek
- Numara tuşlarıyla kanal açmak, CH+/CH- kullanmak ve önceki kanala dönmek
- Son 50 farklı kanal seçimini yerel oynatma geçmişinde saklamak
- OK ile kanal listesini açmak; liste kapalıyken yön tuşlarıyla kanal değiştirmek
- Kanala uzun OK ile favori, taşıma, özel numara ve özel ad işlemleri uygulamak
- Kırmızı tuşla tam ekran kanal editörünü açmak; atlama, tek/çoklu taşıma ve TIF eşitleme yapmak
- Editörde odaktaki TIF kanalını sağdaki küçük canlı pencerede önizlemek
- Seçilen DVB kanalını `TvView` ile oynatmak
- Android TV kumandasıyla kanal listesi ve CH+/CH- kontrolü
- Yetki veya vendor uyumsuzluğunu cihaz üzerinde görünür biçimde teşhis etmek

### Canlı TV ve kanal yönetimi

- Vendor TIF üzerindeki DVB TV/radyo kanallarını özel arayüzle oynatır.
- Room veritabanında özel sıra, kanal numarası, ad, favori, gizleme, atlama ve kanal kilidi tercihlerini saklar.
- TKGS veya vendor taramasından sonra TIF kanallarıyla yeniden eşitlenebilir.
- Kanal listesi, infobar, EPG, ses ve altyazı seçimi kumandayla kullanılabilir.
- Ana yayında Back tuşu son 5 kanal şeridini açar; LAST CHANNEL/RECALL önceki kanala doğrudan döner.
- IPTV oynatma çubuğunda Yukarı/Aşağı zaman çizgisi ile işlem satırı arasında geçer; CH+/CH- kanalı değiştirir. Normal buffer doldurma ilk yükleme ve gerçek yeniden denemeden ayrı gösterilir.
- Program rehberi bütün kanalları ortak saat cetvelinde satır satır gösterir. Yukarı/Aşağı kanal, Sol/Sağ program değiştirir; INFO seçili programın kaydırılabilir tam açıklamasına geçer. Gelecek bir programa uzun OK ile hatırlatıcı kurulabilir.
- Son kanal, önceki kanal ve izleme geçmişi yerel olarak saklanır.

### IPTV

- IPTV oynatma kontrolleri medya tuşuyla infobar içinde açılır. Kontroller açıkken `Yukarı/Aşağı` zaman çizgisi ile işlem satırı arasında geçer; seekbar üzerinde `Sol/Sağ` sarar ve `OK` işlem satırına geçer. Kanal değiştirme `CH+/CH-` ile yapılır.
- Kompakt işlem satırında oynat/duraklat, buffer, VOD hızı ve ses/altyazı/kalite menüsü bulunur. `Sol/Sağ` işlem seçer, `OK` uygular.

- Oynatma OSD'sinde Yukarı/Aşağı ile zaman çizgisi ve işlem satırı seçilir; Sol/Sağ zaman çizgisinde sarar, işlem satırında seçenekler arasında gezer.
- Buffer hedefi `OK` ile uygulanır; değerler arasında gezinirken yayın tekrar tekrar başlatılmaz. OSD açıkken kanal değiştirme yalnız `CH+`/`CH-` ile yapılır.
- Kalıcı buffer hedefi ve varsayılan VOD hızı `Ayarlar > Oynatma` bölümünden de seçilebilir.

- M3U/M3U8 listeleri URL'den veya dosyadan eklenebilir, yenilenebilir ve silinebilir.
- Xtream Codes hesaplarından canlı kanal ve film/VOD katalogları alınabilir.
- Stalker Portal kaynakları MAC adresiyle eklenebilir; canlı bağlantı oynatma anında yenilenir.
- Her IPTV kaynağından ana kanal listesine eklenecek kanallar ayrıca seçilebilir.
- Sarı kaynak seçimiyle tam IPTV görünümüne geçilir; kayıtlı kaynak, Canlı/VOD ve kategori filtresi uygulanır.
- Tam IPTV görünümündeki gezinme ana izleme geçmişine yazılmaz ve kanal yalnızca `OK` ile açılır.
- IPTV kaynak yönetimi, kategori filtresi, kanal önizleme ve Media3 oynatma desteği bulunur.
- Büyük IPTV kaynakları ana kütüphanede 250, seçim yöneticisinde 200 öğelik Room sayfalarıyla yüklenir; binlerce kayıt aynı anda belleğe alınmaz.
- Kanal panelinin başlığındaki arama simgesi, kumandanın `SEARCH` tuşu veya normal kanal listesindeki `Mavi`; kanal adı, numarası ve kategori içinde arama yapar. Tam IPTV kütüphanesinde arama Room FTS ve sayfalama ile çalışır.
- Seçim yöneticisinde kategori, arama, yalnız seçilenler filtresi, 1-4 haneli doğrudan sıra erişimi ve ilk/son arasında dolaşım bulunur.
- M3U içe aktarma 500 kayıtta bir veritabanına yazılır; Canlı/VOD türü içe aktarım sırasında sınıflandırılıp indekslenir.
- Canlı yayınlarda canlı noktaya dönme; VOD içeriklerinde oynat/duraklat, ileri/geri sarma, kaldığın yerden devam ve “İzlemeye devam et” görünümü vardır.
- M3U `catchup-source`/`catchup-days` ve Xtream arşiv bilgisi olan kanallarda geçmiş EPG programları saat simgesiyle gösterilir; `OK` arşivi oynatır, kırmızı eylem canlı yayına döner.
- Mavi oynatıcı eylemi üzerinden kalite, ekrana sığdırma/doldurma/yakınlaştırma ve harici oynatıcı seçilebilir.
- Geçici IPTV oynatma hataları 1, 2 ve 4 saniyelik aralıklarla otomatik yeniden denenir; aynı kanala ait alternatif akış bulunursa sıradaki URL denenir.
- IPTV watchdog'u ilk kare gelmemesi, uzun buffer, görüntünün donması ve canlı akışın beklenmedik bitmesini ayırır. Kurtarma denemeleri sınırlıdır; kanal değiştirildiğinde eski denemeler iptal edilir ve radyo yayınları video hatası sayılmaz.

### Ekran ve sistem entegrasyonu

- Bilgi çubuğunun konumu, içeriği, saydamlığı ve ekranda kalma süresi ayarlanabilir.
- Kanal panelinin yönü, saydamlığı ve gösterilecek kanal bilgileri özelleştirilebilir.
- Uygulama dili sistem diline bağlı, Türkçe veya İngilizce olarak seçilebilir.
- Cihaz destekliyorsa sistem PiP, desteklemiyorsa uygulama içi mini pencere kullanılabilir.
- Google TV Home için son izlenen kanal satırı ve isteğe bağlı açılışta TVApp'i başlatma desteği vardır.
- Yapılandırma, kanal düzeni ve IPTV seçimleri sürümlü yedek dosyasına aktarılıp geri alınabilir.
- Birden fazla XMLTV URL/dosya kaynağı ayrı ayrı eklenebilir, güncellenebilir ve silinebilir. Programlar kaynak kimliği ile Room'da saklanır; bir kaynağı yenilemek diğer EPG verilerini silmez ve kayıtlı URL kaynakları ağ varken 12 saatte bir yenilenir. EPG eşleştirme editörü otomatik `tvg-id`/kanal adı sonucunu gösterir ve yanlış eşleşmelerin belirli bir XMLTV kanalına elle bağlanmasını sağlar.

## Kumanda kullanımı

- `OK`: Kanal listesini açar; listedeki kanalı seçer.
- `Yukarı/Aşağı`: Liste kapalıyken kanal değiştirir, liste açıkken satırlar arasında gezer.
- `CH+/CH-`: Kanal değiştirir.
- `INFO`: Bilgi çubuğunu açar; bilgi çubuğu açıkken tekrar basılırsa program rehberini açar.
- `GUIDE/EPG`: Program rehberini doğrudan açar.
- `AUDIO`: Yayındaki ses dilini/parçasını seçer.
- `SUBT/CAPTIONS`: Altyazı parçasını seçer veya kapatır.
- `INPUT/SOURCE`: DTV/ATV ve HDMI/AV gibi fiziksel TV girişlerini açar.
- Ana yayın ekranında `Kırmızı`: Kumanda `INPUT/SOURCE` tuşunu uygulamaya iletmiyorsa TV girişlerini açar.
- Kanal listesinde `Kırmızı`: Kanal düzenleyiciyi açar.
- Yayın ekranında ve kanal listesinde `Yeşil` kısa basış: IPTV PiP seçimini açar; uzun basış: IPTV Grid seçimini açar.
- Normal kanal listesinde `Mavi`: Kanal aramasını açar.
- Tam IPTV listesinde `Kırmızı`: IPTV kaynak yönetimini açar.
- Tam IPTV listesinde `Sarı`: Kaynak, Canlı/VOD ve kategori seçimini açar.
- Tam IPTV listesinde `Mavi`: Kaynak, içerik türü ve kategori filtresini açar.

Kumandaların Android tuş kodları üreticiye göre değişebilir. TVApp tanınmayan tuşları debug
loguna yazar; farklı cihaz eşlemeleri bu kayıtlarla eklenebilir.

## Gereksinimler

- Android Studio JBR 17 veya üzeri
- Android SDK 35
- Android 11 (API 30) Google TV hedef cihaz

Bu proje sisteme yeni bir `TvInputService` ekleyen eski Google örneğinin kopyası değildir.
Var olan vendor tuner input'unu kullanan, DVB ve IPTV kaynaklarını ortak kanal modeli üzerinde
birleştiren özel bir Live TV istemcisidir.

## TVApp IPTV sistem girişi

TVApp, ana liste için seçilmiş IPTV kanallarını aynı APK içindeki `TVApp IPTV`
`TvInputService` kaynağıyla Android TIF'e yayımlar. Binlerce ham IPTV kaydı yerine yalnızca
kullanıcının seçtiği ve gizlemediği kanallar sistem kanal tablosuna aktarılır.

IPTV seçimi, kaynak yenileme veya silme sonrasında yerel eşitleme işi otomatik başlatılır;
12 saatlik periyodik eşitleme yedek olarak kalır. Sistem girişinin kurulum ekranından elle
eşitleme de yapılabilir.

TVApp kendi TIF kaynağını vendor tuner olarak yeniden içeri almaz. Kaynak, servis component
kimliğiyle filtrelenir; yayımlanan kanallar ayrıca `tvapp-iptv:` provider kimliği ve
`owner=com.tvapp.livetv` metadata işareti taşır. Bu nedenle ortak listede yinelenen IPTV
kanalları oluşmaz.

## Yol haritası

Ayrıntılı performans ve cihaz uyumluluğu planı [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md),
uygulanabilir görev listesi ise [TASKS.md](TASKS.md) dosyasındadır.

- [x] Vendor TIF keşfi, kanal okuma ve canlı oynatma
- [x] Room tabanlı özel kanal sırası ve kanal editörü
- [x] Kumanda odaklı kanal listesi, infobar ve görünüm ayarları
- [x] M3U/M3U8 URL/dosya içe aktarma, kaynak yenileme/silme ve IPTV oynatma
- [x] IPTV kaynağından ana listeye kanal seçme ve kalıcı Tümü/Uydu/IPTV filtreleri
- [x] TIF EPG, şimdi/sonraki bilgisi ve program rehberi
- [x] Android sistem Picture-in-Picture desteği (cihaz özelliği varsa)
- [x] Kanal düzeni, IPTV seçimleri ve uygulama ayarları için sürümlü yedek export/import
- [x] Sistem PiP olmayan cihazlar için uygulama içi mini pencere fallback
- [x] İkinci TIF oturumunu cihaz yeteneğine göre deneyen ve IPTV'yi destekleyen Multi View temeli
- [x] URL/dosya kaynaklı XMLTV alternatif EPG ve `tvg-id`/kanal adı eşleştirme
- [x] XMLTV olmayan Xtream kaynaklarında `get_short_epg` tabanlı şimdi/sonraki program bilgisi
- [x] Google TV Home son izlenen kanallar önizleme kanalı
- [x] Kalıcı uyku zamanlayıcısı ve PIN tabanlı kanal kilidi

Multi View'da aynı transponder/polarizasyon eşzamanlılığı vendor tuner sürücüsünün ikinci
TIF oturumunu kabul etmesine bağlıdır. Standart `TvContract` her cihazda frekans ve
polarizasyon alanlarını yayınlamadığı için TVApp bu koşulu yazılım tarafında kesin olarak
doğrulayamaz; desteklenmeyen donanımda ana yayın korunur.

## Derleme

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleLocalDebug
```

### Performans ve cihaz modu

TVApp cihaz adını değil gerçek yetenekleri kullanır. Vendor tuner bulunan cihazlarda Hibrit TV,
tuner bulunmayan TV stick/box cihazlarında IPTV-only, telefon test varyantında Mobil Test modu
seçilir. Algılanan mod ve bellek/decoder özeti `Ayarlar > Sistem` altında görülebilir; kullanıcı
aynı yerden Otomatik, Hibrit veya IPTV-only tercihinde bulunabilir. Donanımın desteklemediği
Hibrit seçimi IPTV-only moda güvenli biçimde döner.

Macrobenchmark modülü soğuk başlangıcı ve sıcak başlangıçtan sonra kanal panelinde hızlı D-pad
gezinimini ölçer. Emülatör sonuçları yalnız regresyon karşılaştırmasıdır; fiziksel TV hedefi
olarak kabul edilmez:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest
```

Baseline profile; ana açılış, kanal repository'si, IPTV liste ve oynatma rotalarını kapsar ve
hem local APK hem paid AAB içine paketlenir. Düşük RAM cihazlarda logo cache/prefetch bütçesi,
Grid kanal sayısı ve Multi View kullanılabilirliği bellek ile donanım decoder kapasitesine göre
otomatik azaltılır.

Telefon üzerinde IPTV/VOD ve yönetim ekranlarını sınamak için TV paketlerinden ayrı mobil test
varyantı kullanılabilir:

```powershell
.\gradlew.bat assembleMobileDebug
```

Bu varyant `com.tvapp.livetv.mobile` paket kimliğiyle kurulur. Telefonlarda DVB/ATV ve fiziksel
TV girişleri bulunmadığından bu kaynaklar gösterilmez; gerçek tuner davranışı Android/Google TV
cihazında doğrulanmalıdır. Oluşan kolay adlı çıktı `app/build/TVApp-Mobile-Test.apk` dosyasıdır.
Mobil video yüzeyinde tek dokunma bilgi çubuğunu açıp kapatır, yukarı/aşağı kaydırma kanal
değiştirir ve yatay kaydırma kanal listesini açar. Mobil workflow bu APK'yı Releases sayfasında
ayrı bir prerelease olarak yayımlar; `version.json` üretmez, `latest` TV release'ine veya uygulama
içi güncelleme manifestine eklemez. Mobil varyantta kanal listesi ve infobardaki renk eylemleri
dokunulabilir; ana kanal paneli yalnız tam IPTV kütüphanesini gösterir. Kanal editörü ve IPTV
seçim ekranındaki renk eylemleri de dokunma hedefidir. TV varyantlarında bu alanlar kumanda ipucu
ve renk tuşu davranışını korur.

Yerel GitHub güncelleme sürümü `local`, mağaza sürümü `paid` varyantıdır. Play için imzalı
Android App Bundle şu komutla hazırlanır:

```powershell
.\gradlew.bat bundlePaidRelease
```

`local`, mevcut `com.tvapp.livetv` paket kimliğini ve uygulama içi GitHub güncelleyicisini
korur. `paid`, `com.tvapp.livetv.play` paket kimliğiyle ayrı kurulur; harici APK güncelleme
izni içermez ve Ayarlar ekranında yalnızca kurulu sürümü gösterir. Paid varyantında
`STORE_BILLING_ENABLED=true` tanımlıdır. Geliştirme tamamlanana kadar
`IPTV_PRO_REQUIRED=false` olduğu için IPTV özellikleri kısıtlanmaz; satın alma entegrasyonu
tamamlandığında yalnız mağaza varyantında etkinleştirilecektir.

Debug derlemelerinde `Ayarlar > IPTV ödeme test senaryoları` ekranı; ilk kurulum,
aktif/dolmuş 7 günlük deneme, bekleyen ödeme, tamamlanan ödeme ve geri yüklenen
satın alma durumlarını simüle eder. Bu Activity ve manifest kaydı release
paketlerine dahil edilmez. Gerçek ödeme devreye alınırken yetki, Play purchase
token'ının sunucuda doğrulanmasıyla verilecek; debug durumu üretimde okunmaz.

Sürüm numarası kökteki `.build` dosyasından okunur. Geliştirme döneminde bu değer sabittir;
yerel APK ile Action APK'sı aynı `versionCode` ve aynı imzayı kullanır. `.build` üretim sürüm
politikası belirlenene kadar elle veya Action tarafından artırılmaz.

Yerel makinede `../.signing/TVApp-release.properties` bulunursa debug APK da release APK ile
aynı anahtarla imzalanır. Böylece yerel derleme ve Action çıktısı aynı uygulamanın üzerine
kurulabilir. Her push değişmeyen Android `versionCode` ile yeni bir `dev-r<run>` GitHub
release'i üretir ve bu release'i `latest` olarak işaretler.

Uygulama içi denetim, build numarası aynı fakat kurulu APK'nın SHA-256 özeti release APK'dan
farklıysa son development release'ini aynı sürüm koduyla yeniden kurmayı önerir. Güncelleme
revizyonu `version.json` içinde ayrıca tutulur; Android sürüm koduna dönüştürülmez.

### Google TV emülatörü

`TVApp_GoogleTV_API31` AVD'si 1920x1080 Google TV API 31, D-pad ve isteğe bağlı fare girişiyle
arayüz/fokus testlerinde kullanılabilir. Emülatör çalışırken `tools/TVAppRemote.cmd` dosyası
masaüstü kumanda panelini açar. Panel; renk, medya, TV, kanal, sayı ve gezinme tuşlarını ADB
üzerinden gönderir. `Sonraki tusa uzun bas` seçeneği bağlamsal menüleri sınamak içindir.

Uygulama veya panel odaktayken yön tuşları, Enter ve Esc gezinme kumandasıdır. F1-F4 sırasıyla
Info, Rehber, Menü ve Ayarlar; F5-F8 kırmızı, yeşil, sarı ve mavi; F9 Giriş, F10 Son Kanal,
F11/F12 ise CH-/CH+ gönderir. PageUp/PageDown da CH+/CH- olarak kullanılabilir. Emülatör vendor
MediaTek tuner sağlamadığı için TIF/DVB sinyal ve track callback testleri gerçek Google TV
cihazında yapılır.

## GitHub Actions ve uygulama içi güncelleme

`.github/workflows/release.yml`, `main` dalındaki kod değişikliklerinde veya elle
çalıştırıldığında testleri çalıştırır ve imzalı development APK üretir. Her başarılı çalışma
benzersiz `dev-r<run>` etiketi oluşturur; uygulama her zaman GitHub'ın son release'indeki
dosyaları denetler.
Release'e şu dosyalar eklenir:

- `TVApp.apk`
- `version.json`

`version.json` sürüm kodunu, APK adresini ve SHA-256 özetini içerir. TVApp içindeki
`Ayarlar > Uygulama > Güncellemeleri denetle` komutu bu dosyayı okuyup yeni APK'yı indirir,
özetini doğrular ve Android paket kurulum ekranını açar. Normal bir Android uygulaması
güncellemeyi kullanıcı onayı olmadan sessiz kuramaz.

Repository'nin GitHub Actions Secrets bölümünde aşağıdaki değerler tanımlanmalıdır:

- `TVAPP_KEYSTORE_BASE64`: Release keystore dosyasının Base64 içeriği
- `TVAPP_KEY_ALIAS`: Anahtar alias değeri
- `TVAPP_KEY_PASSWORD`: Anahtar parolası
- `TVAPP_STORE_PASSWORD`: Keystore parolası

Keystore dosyası ve parolalar repoya eklenmez. İlk release APK kurulduktan sonraki tüm
sürümler aynı anahtarla imzalanmalıdır. Debug imzalı mevcut APK, farklı release imzalı APK ile
yerinde güncellenemez; release kanalına ilk geçişte debug sürümünün kaldırılması gerekir.

## Cihaz teşhisi

```powershell
adb shell dumpsys tv_input
adb shell pm list permissions -g | Select-String TV
```

Normal APK kurulumu kanal erişiminde `SecurityException` üretirse uygulamanın cihaz firmware'ine sistem/privileged uygulama olarak eklenmesi ve vendor allowlist izinleri gerekebilir.
