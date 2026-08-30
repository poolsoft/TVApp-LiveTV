# TV App

Android 11 tabanlı Google TV cihazları için özel Live TV uygulaması.

## İlk hedef

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

## Gereksinimler

- Android Studio JBR 17 veya üzeri
- Android SDK 35
- Android 11 (API 30) Google TV hedef cihaz

Bu proje sisteme yeni bir `TvInputService` ekleyen eski Google örneğinin kopyası değildir. Var olan vendor tuner input'unu kullanan özel bir Live TV istemcisidir. IPTV, EPG, PiP ve Home entegrasyonu sonraki katmanlarda aynı kanal modeli üzerine eklenecektir.

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

## Cihaz teşhisi

```powershell
adb shell dumpsys tv_input
adb shell pm list permissions -g | Select-String TV
```

Normal APK kurulumu kanal erişiminde `SecurityException` üretirse uygulamanın cihaz firmware'ine sistem/privileged uygulama olarak eklenmesi ve vendor allowlist izinleri gerekebilir.
