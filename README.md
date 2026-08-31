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
- Son kanal, önceki kanal ve izleme geçmişi yerel olarak saklanır.

### IPTV

- M3U/M3U8 listeleri URL'den veya dosyadan eklenebilir, yenilenebilir ve silinebilir.
- Her IPTV kaynağından ana kanal listesine eklenecek kanallar ayrıca seçilebilir.
- Mavi tuşla tam IPTV görünümüne geçilir; kayıtlı kaynak, Canlı/VOD ve kategori filtresi uygulanır.
- Tam IPTV görünümündeki gezinme ana izleme geçmişine yazılmaz ve kanal yalnızca `OK` ile açılır.
- IPTV kaynak yönetimi, kategori filtresi, kanal önizleme ve Media3 oynatma desteği bulunur.
- Canlı yayınlarda canlı noktaya dönme; VOD içeriklerinde oynat/duraklat ve ileri/geri sarma kontrolleri vardır.

### Ekran ve sistem entegrasyonu

- Bilgi çubuğunun konumu, içeriği, saydamlığı ve ekranda kalma süresi ayarlanabilir.
- Kanal panelinin yönü, saydamlığı ve gösterilecek kanal bilgileri özelleştirilebilir.
- Uygulama dili sistem diline bağlı, Türkçe veya İngilizce olarak seçilebilir.
- Cihaz destekliyorsa sistem PiP, desteklemiyorsa uygulama içi mini pencere kullanılabilir.
- Google TV Home için son izlenen kanal satırı ve isteğe bağlı açılışta TVApp'i başlatma desteği vardır.
- Yapılandırma, kanal düzeni ve IPTV seçimleri sürümlü yedek dosyasına aktarılıp geri alınabilir.

## Kumanda kullanımı

- `OK`: Kanal listesini açar; listedeki kanalı seçer.
- `Yukarı/Aşağı`: Liste kapalıyken kanal değiştirir, liste açıkken satırlar arasında gezer.
- `CH+/CH-`: Kanal değiştirir.
- `INFO`: Program rehberini açar.
- `AUDIO`: Yayındaki ses dilini/parçasını seçer.
- `SUBT/CAPTIONS`: Altyazı parçasını seçer veya kapatır.
- Kanal listesinde `Kırmızı`: Kanal düzenleyiciyi açar.
- Normal kanal listesinde `Mavi`: Son kullanılan filtreyle tam IPTV listesini açar.
- Tam IPTV listesinde `Kırmızı`: IPTV kaynak yönetimini açar.
- Tam IPTV listesinde `Sarı`: Kaynak, Canlı/VOD ve kategori seçimini açar.
- Tam IPTV listesinde `Mavi`: Normal kanal listesine döner.

Kumandaların Android tuş kodları üreticiye göre değişebilir. TVApp tanınmayan tuşları debug
loguna yazar; farklı cihaz eşlemeleri bu kayıtlarla eklenebilir.

## Gereksinimler

- Android Studio JBR 17 veya üzeri
- Android SDK 35
- Android 11 (API 30) Google TV hedef cihaz

Bu proje sisteme yeni bir `TvInputService` ekleyen eski Google örneğinin kopyası değildir.
Var olan vendor tuner input'unu kullanan, DVB ve IPTV kaynaklarını ortak kanal modeli üzerinde
birleştiren özel bir Live TV istemcisidir.

## TVApp Input entegrasyonu

İsteğe bağlı kardeş `TVAppInput` projesi, TVApp'ta ana liste için seçilmiş IPTV kanallarını
Android TIF'e yayımlar. TVApp, `com.tvapp.livetv.iptv` yetkili salt okunur sağlayıcıyı ve
`com.tvapp.livetv.permission.READ_IPTV_CHANNELS` signature iznini sunar. Böylece binlerce ham
IPTV kaydı yerine yalnızca kullanıcının seçtiği ve gizlemediği kanallar sistem girişine aktarılır.

İki APK aynı imzalama anahtarıyla üretilmelidir. Keystore, parola veya `keystore.properties`
gibi özel imza verileri repoya eklenmemelidir.

## Yol haritası

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
- [x] Google TV Home son izlenen kanallar önizleme kanalı
- [x] Kalıcı uyku zamanlayıcısı ve PIN tabanlı kanal kilidi

Multi View'da aynı transponder/polarizasyon eşzamanlılığı vendor tuner sürücüsünün ikinci
TIF oturumunu kabul etmesine bağlıdır. Standart `TvContract` her cihazda frekans ve
polarizasyon alanlarını yayınlamadığı için TVApp bu koşulu yazılım tarafında kesin olarak
doğrulayamaz; desteklenmeyen donanımda ana yayın korunur.

## Derleme

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

Sürüm numarası kökteki `.build` dosyasından okunur. Örneğin `.build` değeri `12` ise
APK'nın `versionCode` değeri `12`, görünen sürümü `0.1.12` olur. Yerel olarak sayacı artırmak
için `./gradlew incrementBuild` kullanılabilir.

## GitHub Actions ve uygulama içi güncelleme

`.github/workflows/release.yml`, `main` dalındaki kod değişikliklerinde veya elle
çalıştırıldığında build sayısını artırır, testleri çalıştırır ve imzalı release APK üretir.
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
