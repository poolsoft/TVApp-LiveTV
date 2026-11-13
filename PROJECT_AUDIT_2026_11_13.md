# PROJE DENETİM RAPORU — 13 Kasım 2026

Kapsam: performans, UI/UX, VOD modu, eksik/yarım işler ve saha çökmeleri.
Yöntem: 4 paralel salt-okunur kod incelemesi + saha çökme logları (`/.log/TVApp/`) +
çalışma ağacı doğrulaması. **Bu rapor bir bulgular envanteridir; hiçbir kod değişikliği
içermez** (istisna: §0'daki regresyon düzeltmesi).

Görev ID'leri `TASKS.md` düzeniyle uyumludur; sonraki aşamada doğrudan referans verilebilir.

Dal durumu: inceleme `refactor/main-activity-split` üzerinde yapıldı.

---

## 0. DIELENEN TEK DEĞİŞİKLİK — kendi kendine soktuğumuz regresyon

**AUDIT-000 (KAPATILDI)** — `f1fb402` (1 Kasım, sniffer çift-fetch temizliği) sırasında
`s/VodVideoResolver.extractStreamFromPage` içindeki 4 regex çağrısı **null olabilir `html`
parametresinde bırakılmıştı**; doğru hedef null-olmayan `targetHtml` yerel değişkeniydi.

- `WebVideoResolver.kt:610` `JSON_LD_REGEX.matcher(html)`
- `WebVideoResolver.kt:632` `M3U8_REGEX.matcher(html)`
- `WebVideoResolver.kt:641` `MP4_REGEX.matcher(html)`
- `WebVideoResolver.kt:652` `IFRAME_SRC_REGEX.matcher(html)`

Kotlin bunu **derler**, çünkü `java.util.regex.Pattern.matcher(CharSequence)` bir platform
tipidir (`CharSequence!`) ve null kabul eder. `html = null` geldiğinde çalışma zamanında NPE.

- Etki: `resolveStream` hızlı yolu (ölü kod) sessizce bozulur; `html` null olmayan
  yollarda (canlı akış `resolveCandidates` → `collectStaticStreams`) etkisizdi. Bu yüzden
  12 gün boyunca sahada görünmedi. **Test boşluğu sayesinde fark edilmedi** (§5, MISS-002).
- Düzeltme: `ca6dcee` (`refactor/main-activity-split`) + `f48fc5b` (`main`, cherry-pick).
- Doğrulama: `testLocalDebugUnitTest testPaidDebugUnitTest assembleLocalDebug assemblePaidDebug`
  yeşil, `git diff --check` temiz.

**Ders:** platform tipi alanlarındaki null geçişleri sessiz kalıyor. VOD regex/sınıflandırma
katmanına birim test yazılmadan bu sınıf hata tekrar eder.

---

## 1. SAHA ÇÖKMELERİ — en yüksek öncelik

`.log/TVApp/` içinde 6 yakalanmamış istisna logu (toplam 32 MB, tümü gitignore'da ✓).
Cihaz: **Expressluck Apollo Premium4K TV, Android 11 (API 30)** — minimum desteklenen platform.
Log tarihleri 25–28 Ekim; 13 Kasım'a kadar yenisi yok (iyi işaret ama kesin değil).

### CRASH-001 — WebView thread ihlali (Kök neden BULUNDU, `main`'de kısmen düzelmış)
```
java.lang.RuntimeException: A WebView method was called on thread 'ThreadPoolForeg'.
  at android.webkit.WebView.getSettings(WebView.java:1983)
  at <obfuscated>.shouldInterceptRequest(...)
```
- Zincir: `SniffingWebViewClient.shouldInterceptRequest` (**arka plan thread'i**) →
  `StreamSniffer.onRequest` → `announce()` → `bestRequest()` → `userAgent()` lambda'sı →
  `webView.settings.userAgentString` → `WebView.getSettings()` → **checkThread() çökmesi**.
- Kaynak: `browser/TvBrowserActivity.kt:206` (refactor dalı).
- Durum: `origin/main` bunu **düzeltmiş** — `userAgent = { cachedUserAgent }` (önbellekten okuyor).
  **`refactor/main-activity-split` hâlâ bozuk.** Dalı main'e almak bu çökmeyi de getirir.
- Kalıntı risk: `StreamSniffer.kt:62` ve `:75` — `CookieManager.getInstance().getCookie()`
  **hâlâ arka plan thread'inde**. `CookieManager` UI-thread-only olarak belgelenmiştir;
  pratikte çoğu sürümde sessiz geçer ama aynı sınıfın ikinci mayınıdır.
  Aynı desen VOD sniffer'ında da var: `WebVideoResolver.kt:309` (`shouldInterceptRequest`
  içinde `onSniffedUrl` → `CookieManager`).

### CRASH-002 — StackOverflowError (Kök neden TAM pinpoint edilmedi)
```
java.lang.StackOverflowError: stack size 8192KB
  at android.view.View.setVisibility(View.java:11719)
  at com.tvapp.livetv.MainActivity.n1(Unknown Source:31)   ← visibility ayarlıyor
  at com.tvapp.livetv.MainActivity.u1(Unknown Source:18)
  at com.tvapp.livetv.MainActivity.n1(Unknown Source:57)   ← u1'i çağırıyor
  at com.tvapp.livetv.MainActivity.u1(Unknown Source:18)   ← n1'i çağırıyor (sonsuz)
```
- **Senkron (aynı stack üzerinde) karşılıklı çağrı döngüsü**: iki MainActivity metodu
  birbirini çağırıyor, biri `setVisibility` yapıyor. StackOverflowError'un 8 MB stack'i
  100.000+ frame doldurmuş.
- Metot adları R8 ile `n1`/`u1`'e kırpılmış → **kesin eşleme için `mapping.txt` gerekli**
  (`MISS-005`). Release mapping arşivi yoksa bu çökme bir daha da teşhis edilemez.
- Aday döngüler (MainActivity üzerinde 2-döngü analizi, 217 fonksiyon / 614 kenar;
  visibility içerenler **kalın**):
  - **`hideChannelPanel` ↔ `scheduleChannelPanelClose`** (ikisi de visibility) — ancak
    `scheduleChannelPanelClose` bir `lifecycleScope.launch` ile gecikiyor; `lifecycleScope`
    `Dispatchers.Main.immediate` kullandığı için **ilk çalıştırma senkron** olabilir, sonra
    `delay`'de askıya alır → doğrudan StackOverflow yerine **asenkron ping-pong** (OSD titreme)
    üretir. Ayrı bir hata sınıfı olarak izlenmeli.
  - **`hideChromeForPictureInPicture` ↔ `restoreChromeAfterPictureInPicture`**
  - **`updateInfoColorActions` ↔ `action`**
  - **`loadIptvLibraryWindow` ↔ `openChannelAlphabet`**
  - **`buildNormalPanelChannels` ↔ `applyChannelFilter`**
  - **`updateDiagnosticsOverlay` ↔ `startDiagnosticsObservation`**
  - **`hideRecentChannels` ↔ `createRemoteInputEnv`** (yanlış pozitif olasılığı yüksek)
- Hipotez (doğrulanamadı, eski sürümde farklıydı): `setInfoBarVisible` ↔ `showVodPlayerControls`.
  `showVodPlayerControls` **şu an** ince bir delege (`MainActivity.kt:2397`), yani bu spesifik
  döngü `02acdd0` ile sönmüş olabilir. **Cihazda yeniden üretmeye çalışılmalı.**

### CRASH-003 — OutOfMemoryError, 127 MB tek ayrma
```
java.lang.OutOfMemoryError: Failed to allocate a 133103624 byte allocation
  with 25165824 free bytes and 124MB until OOM, growth limit 201326592
  at java.util.Arrays.copyOf → AbstractStringBuilder.append → StringWriter.write
  Suppressed: [D{Cancelling}@..., Dispatchers.Main.immediate
```
- Devasa bir metin `String`/`StringWriter` içinde biriktirilmiş. Growth limit 192 MB iken
  tek istek 127 MB → doubling ile çökme.
- **Güçlü aday: `diagnostics/CrashReportStore.kt:98-99`** — çökme raporunu
  `StringWriter().also { it.printStackTrace(PrintWriter(it)) }` ile **tamamen bellekte**
  kuruyor. CRASH-002'deki 100k+ frame'lik StackOverflow trace'i burada String'e yazılırken
  127 MB'a ulaşmış olabilir. Yani **CRASH-003, CRASH-002'nin ikincil hasarı olabilir.**
- İkincil adaylar (tüm gövdeyi String'e okuyan network çağrıları — 15k+ kanallı M3U /
  XMLTV için gerçek risk):
  - `data/IptvRepository.kt:479` `inputStream.bufferedReader().readText()` — tam playlist
  - `data/XtreamClient.kt:330` ve `data/StalkerClient.kt:160` — tam katalog JSON
  - `data/XmlTvRepository.kt:747` `legacyCacheFile.readText()` → sonra `JSONArray(...)`
    (metin + parse edilmiş ağaç **aynı anda** bellekte → 2× tepe)
- Eylem: bu okumalar akışa/parçalı parse'a çevrilmeli; `CrashReportStore` trace'i
  boyut sınırıyla (örn. 200 KB) ve dosyaya stream edilerek yazmalı.

---

## 2. PERFORMANS

| ID | Konu | Yer | Etki |
|---|---|---|---|
| PERF-001 | VOD kataloğu her sayfa/kategori/sayaç sorgusunda **tam malzemeleştirilip** dış seviyede sıralanıyor; `name LIKE '%q%'` indeks kullanamıyor | `data/VodCatalogQuery.kt:18-101`, `data/VodRepository.kt:17-21` | **YÜKSEK** |
| PERF-002 | Kanal arama filtresi her tuşta **tüm kanalları ana thread'de** `Normalizer(NFD)` + Regex + `copy()` ile dönüştürüyor | `main/ChannelListRules.kt:23-33`, `:60-70` | **YÜKSEK** |
| PERF-003 | `DiffUtil.calculateDiff` **senkron** (background differ / `AsyncListDiffer` proje genelinde hiç yok) + listeyi tam kopyalıyor | `ui/ChannelAdapter.kt:49-65` | **YÜKSEK** |
| PERF-004 | IPTV→TIF eşitleme: indekslenemeyen `ORDER BY COALESCE(...)` ile **OFFSET paging** (kanal arttıkça karesel) + kanal başına ayrı ContentResolver yazması (`bulkInsert` yok) | `data/local/IptvDao.kt:51-65`, `tifinput/IptvInputChannelSyncRepository.kt:38-75` | ORTA-YÜKSEK |
| PERF-005 | 500 ms oynatım ticker'ı saniyede 2 tam kontrol/infobar güncellemesi | `MainActivity.kt:2216-2223`, `:2445-2509` | ORTA |
| PERF-006 | `updateInfoColorActions` her seferinde `removeAllViews()` + yeni `TextView` üretimi | `MainActivity.kt:5031+` | ORTA |
| PERF-007 | Headless sniffer: 1920×1080 WebView Main thread'de DecorView'a ekleniyor, içinde 500 ms'de bir `querySelectorAll` taraması | `vod/WebVideoResolver.kt:266-517`, `:441-445` | ORTA |
| PERF-008 | Multi-View 4 ExoPlayer örneği grid kapanınca `release()` edilmiyor (kodek + buffer belleği tutuluyor) | `playback/MultiViewController.kt:100-148`, `IptvPlaybackController.kt:585-598` | ORTA |
| PERF-009 | Her tune'da yeni `DefaultRenderersFactory`/`LoadControl`/`AdaptiveTrackSelection` ayrılıyor; `EXTENSION_RENDERER_MODE_PREFER` kurulu ama **hiç decoder-extension bağımlılığı yok** → mod işlevsiz, sadece maliyet | `IptvPlaybackController.kt:263-341` (`:281-286`, `:308`), `build.gradle.kts:153-158` | ORTA |
| PERF-010 | Harf filtresi non-sargable: `SUBSTR(TRIM(displayName),1,1) IN (...)` → indeks kullanılamaz, 15k satırda tam tarama | `data/local/IptvDao.kt:143`, `:166`, `:273` | ORTA |
| PERF-011 | EPG liste penceresi kanal başına ayrı ContentResolver sorgusu (N+1, önbekle korumalı ama ilk doldurma yavaş) | `data/ProgramRepository.kt:253-263` | ORTA |
| PERF-012 | Tek kanalın listeye üyeliği **tüm birleşik listeyi** yeniden yüklüyor | `MainActivity.kt:4708-4713` (çağrı `:4207`) | ORTA |
| PERF-013 | TMDB zenginleştirme sayfa başına 15 **ardışık** HTTP (paralel yok) | `VodHomeActivity.kt:478-523` | ORTA-DÜŞÜK |
| PERF-014 | `notifyDataSetChanged()` — 11 dosyada 14 nokta (15k seçim listesi, EPG, editör, kilit toggle) | `IptvChannelSelectionActivity.kt:375,466,651`; `MainActivity.kt:4737,4746`; `MultiViewChannelAdapter.kt:29`; `GuideProgramAdapter.kt:26`; `GuideScheduleAdapter.kt:56`; `ChannelEditorAdapter.kt:34`; `XmlTvSourceAdapter.kt:28`; `GithubManifestAdapter.kt:67,73`; `VodRightPanelAdapters.kt:21,81` | ORTA-DÜŞÜK |
| PERF-015 | Canlı akış yolunda her chunk/byte için yeni dizi ayrması (süregiden GC baskısı) | `playback/BufferedLiveInputStream.kt:26`, `:48` | DÜŞÜK |
| PERF-016 | XMLTV sıcak yolunda `File.exists()` syscall'ı, kaynak başına ayrı sayaç sorgusu, IO içinde gereksiz `runBlocking` | `data/XmlTvRepository.kt:740-741`, `:53-59`, `:87` | DÜŞÜK |
| PERF-017 | Regex'ler fonksiyon içinde derleniyor (her çağrıda `Pattern.compile`) — liste doldurmada öğe başına maliyet | `TifProviderDataInspector.kt:45,59`; `XtreamClient.kt:194`; `StreamCandidates.kt:76`; `WebVideoResolver.kt:566`; `VodTitleCleaner.kt:46-47`; `CrashReportStore.kt:317` | DÜŞÜK |
| PERF-018 | Boşta OSD ticker'ları: her saniye yeni `SimpleDateFormat`, 1 sn tanılama döngüsü | `MainActivity.kt:5330-5341`, `:5476-5483`; `VodPlayerOverlayController.kt:452-456` | DÜŞÜK |
| PERF-019 | DB her açılışında 3 tetikleyici DROP+CREATE; logo loader'da `@Synchronized` her bind'de + cache boyutu için tam dizin taraması | `data/local/TVAppDatabase.kt:603+`; `image/ChannelLogoLoader.kt:36,93,106` | DÜŞÜK |

**En yüksek getirili üçlü: PERF-001 + PERF-002 + PERF-003** — aynı kullanıcı yolunda
(VOD'da kaydırma, kanal listesinde arama) zincirleniyor ve doğrudan hissedilir.

---

## 3. UI / UX

| ID | Konu | Yer | Etki |
|---|---|---|---|
| UI-001 | **IPTV oynatma kontrolleri ve VOD butonlarında odak göstergesi HİÇ YOK** — `state_focused` dalı yok. AGENTS.md "Focused state must remain clearly visible on the dark OSD" ihlali | `drawable/bg_iptv_control.xml`, `drawable/bg_vod_button.xml` (kullanım `activity_main.xml:752,767,782,798,814`) | **YÜKSEK** |
| UI-002 | VOD ana ekranında yükleme göstergesi yok, hatalar yalnız debug log'a gidiyor, retry yolu yok | `VodHomeActivity.kt:412-456`, `:442` | **YÜKSEK** |
| UI-003 | Ana kanal listesinde empty-state yok (filtre/arama 0 sonuç → bomboş panel); IPTV/XMLTV kaynak listelerinde de yok | `activity_main.xml`, `activity_iptv_sources.xml`, `activity_xmltv_sources.xml` | **YÜKSEK** |
| UI-004 | VOD ekranında **hard-coded Türkçe metin + emoji** (🌐⚡📂🎬⏱️🔴), EN dilde de Türkçe kalıyor | `activity_vod_home.xml:31,99,137,153,162,192,209,247,274,342-368`; `VodHomeActivity.kt:545,578-583,636-639,763,770`; `VodRightPanelAdapters.kt:47`; `TmdbApiClient.kt` | **YÜKSEK** |
| UI-005 | 10-foot için okunmaz punto: **7–9sp** (renk aksiyon ipuçları 9f, kalite rozeti 8sp, kaynak rozeti 9sp, tarih 7sp) | `MainActivity.kt:5037,5079,5123` (9f), `:5103` (11f); `item_channel.xml:71,126`; `activity_main.xml:685`; `activity_vod_home.xml:139` | **YÜKSEK** |
| UI-006 | VOD ana ekranında sabit yükseklikler (38dp bar, 175dp ray, 185dp panel) — 720p'de taşma riski; `TvOverlayLayoutHelper` bu ekranı kapsamıyor | `activity_vod_home.xml` | ORTA |
| UI-007 | Quick Hub diyaloğu sabit 760dp (35 adet sabit dp) | `dialog_quick_hub.xml` | ORTA |
| UI-008 | OSD geçişlerinde animasyon yok — anlık GONE/VISIBLE "kırpma" | `MainActivity.kt:5140+` (`renderPlaybackUiState`) | ORTA |
| UI-009 | 40+ Toast çağrısı — TV'de süresi kısa, odaklanamayan geri bildirim | `TvBrowserActivity.kt` (10), `DisplaySettingsActivity.kt` (6), `MainActivity.kt` (6+), `MultiViewController.kt:169,185,189` | ORTA |
| UI-010 | Ham `error.message` kullanıcıya gösteriliyor (teknik/İngilizce, hassas URL parçası riski) | `IptvChannelSelectionActivity.kt:322,357,456` | ORTA |
| UI-011 | Tarayıcıda `onReceivedError` yok → Chromium'un varsayılan hata sayfası (remota uygun değil) | `browser/TvBrowserActivity.kt` | ORTA |
| UI-012 | VOD alt ipucu barı **gerçek tuş atamalarıyla çelişiyor**; sarı tuş kaynak yokken sessiz no-op | `activity_vod_home.xml:332-383` vs `VodHomeActivity.kt:789-811`, `:252` | ORTA |
| UI-013 | Bölüm seçici düz `setItems` AlertDialog — sezon gruplaması, bölüm no ve izleme durumu yok | `VodHomeActivity.kt:636-639`, `:578-583` | ORTA |
| UI-014 | Ölü layout/adapter: `dialog_vod_detail.xml`, `dialog_vod_filters.xml` hiçbir yerden inflate edilmiyor; `VodSourceAdapter` kullanılmıyor | `res/layout/`, `vod/VodRightPanelAdapters.kt` — **dikkat:** `androidTest/VodHomeLayoutTest.kt:135,139` `vod_detail_scroll` ID'sine bağımlı | DÜŞÜK |
| UI-015 | Teknik rozet metinleri (RADIO/DOLBY/TXT) layout'a gömülü, string kaynağında değil | `activity_main.xml:903,922,966` | DÜŞÜK |

**Uyumlu bulunanlar (bulgu değil):** PIN girişi sayısal tuşlar + yazılım klavye bastırma ✓;
renk ipuçları `technical_row` içinde spacer ile sağda ✓; `ChannelAdapter` DiffUtil+payload
mimarisi ✓; `values`/`values-en` **905 = 905** anahtar paritesi ✓.

---

## 4. VOD MODU

### VOD-001 — Sniffer 0 aday dönerse ham sayfa URL'si oynatılıyor (KRİTİK)
`main/VodHost.kt:82` → `candidates.isEmpty() → PlayBase(baseChannel)`.
Yani çözülemeyen içerik **başlıksız/cookiesiz HTML sayfası olarak** oynatıcıya verilir →
`PARSING_CONTAINER_UNSUPPORTED` / 403. `VodPageResolver.kt:16` "fallback theater/browser
akışı"nı belgeler ama **böyle bir yönlendirme hiçbir yerde yok**; `ResolvedStream.isPlayable=false`
üretiliyor (`WebVideoResolver.kt:160`) ama **tek bir tüketici bile yok**.
Ek olarak bu yolda `statusRetryAction` **ayarlanmaz** (`MainActivity.kt:1639-1657` yalnız
`showPlaybackError` kurar ve o yol VOD'da `currentChannel == null` iken devreye girer) →
**kullanıcıya Retry sunulmaz**, tek çıkış Back.

### VOD-002 — Header/cookie taşımada üç kayıp
- `MainVodExtras.EXTRA_VOD_REFERRER/ORIGIN/USER_AGENT/COOKIE` tanımlı ve payload'a okunuyor
  (`VodHost.kt:379-382`, `:418-421`, `:269-274`) ama **`VodHomeActivity` bu dört extra'yı
  hiç doldurmuyor** (`:619-627`, `:643-651`, `:687-694`) → ana ekran yolunda daima null.
- `VodLibraryResolver.buildChannel` (`:98-114`) header/cookie **taşımaz** → aday bulunamazsa
  başlıksız oynatma, tipik 403.
- `VodPageResolver.applyTo` (`:34-38`) `base.userAgent ?: chosen.userAgent` — **base öncelikli**.
  DB kanalının/kaynağın `defaultUserAgent`'ı varsa **sniffer'ın ürettiği UA/referer/cookie
  yok sayılır.** Tam da token korumalı CDN için gereken cookie düşer.

### VOD-003 — Global zaman bütçesi yok (en kötü durum: dakikalarca)
- `fetchHtml`: connect 6 sn + read 8 sn = istek başına ~14 sn (`WebVideoResolver.kt:673-674`).
- `extractStreamFromPage` adım 5'teki iframe listesi **sınırsız** (`:652-659`); aynı embed
  sayfaları hem bu rekürsiyon hem `collectStaticStreams` döngüsü üzerinden **iki kez** iniyor
  (`:231` vs `:242-249`).
- Aday hiç yoksa ve tüm istekler timeout'a düşüyorsa: ~1 + 8 + 64 istek × iki kat ≈ **140+
  ardışık istek** → teorik ~30+ dk. Ekranda yalnız "hazırlanıyor" blackout'u. **Üst süre limiti yok.**
- `HEADLESS_TIMEOUT_MS = 10_000` (`:24`) — mp4-only sitelerde her seferinde tam 10 sn.
- DB/kütüphane yolunda `context = null` geçtiği için sniffer **hiç çalışmaz**
  (`VodHost.kt:318`, `:363`) → aynı içerik deep-link yolunda oynayıp DB yolunda oynamayabilir.
- Sonra oynatıcı katmanı: OkHttp 15/20 sn + ilk kare watchdog 15 sn + buffering 25 sn + 2
  re-prepare → gerçekçi kötü senaryoda **~30-90 sn + 10 sn + ~35 sn** bekleme.

### VOD-004 — Güvenlik (kurallı, kabul edilmiş risk mi, karar verilmeli)
- Trust-all SSL **statik çözümlemede**: `WebVideoResolver.kt:682-685`, `701-712`
- Trust-all SSL **oynatma veri yolunda** (tüm IPTV/VOD trafiği): `IptvDataSourceFactory.kt:75-96`
- `onReceivedSslError → handler.proceed()`: `WebVideoResolver.kt:450-456`
- `MIXED_CONTENT_ALWAYS_ALLOW` + `allowFileAccess` + `allowContentAccess` + JS/DOM/DB + `mediaPlaybackRequiresUserGesture=false`: `:293-301`
- Global cleartext: `AndroidManifest.xml:31`
- Hard-coded fallback host `https://play2.pilavyerplay.top` — HTML'de işaret yok ve `core.js`
  bulunamazsa `s.php?s=<slug>` **bu host'a** gider (`:589-596`)
- **Hard-coded TMDB API anahtarı** kaynak kodunda ve URL query'de: `TmdbApiClient.kt:26,66,78`
- **Aday enjeksiyonu**: sniffing kanalı `console.log('net_stream_sniff:'+url)` (`:336-339`,
  `:493-501`). Sayfadaki script bu önekle istediği URL'i loglayabilir → aday olur → sonra
  **UA + cookie ile** biz istek atarız. Cookie/UA sızıntısı mümkün.

### VOD-005 — Bölüm/seri modelinde üç ayrı parser kopyası ve iki veri yolu
- Room yolu (`vod_metadata` + `parentKey`) ve JSON yolu (bölüm listesi `SERIES.streamUrl`
  içinde gömülü JSON string; `JsonVodImporter.kt:53-73` **yalnız parent yazar, satır yok**).
- Üç kopya: `VodUiSupport.parseEpisodesJson` (`:45-85`), `VodHost.showEpisodePicker` JSON dalı
  (`:154-183`), `VodHomeActivity.showEpisodesDialog` inline parser (`:564-610`).
  Başlık kuralları farklı: `"Bolum"` (ASCII, `VodUiSupport.kt:77-81`) vs `"Bölüm"`
  (`VodHomeActivity.kt:577-584`).
- `currentIndex` uyuşmazlığı: `VodHost.kt:158-160` aktif bölümü `it.title == activeEpisodeTitle`
  ile arıyor; VodHomeActivity `"N. Sezon M. Bölüm - ad"` gönderiyor (`:599-600`), parseEpisodesJson
  başlığı ham `title` → **eşleşme yok, vurgulama hep 0. bölüme düşüyor.**
- **Kırılgan `:ep:<index>_<hash>`**: index JSON dizi sırasına mutlak bağlı, **hash doğrulanmıyor**
  (`VodLibraryResolver.kt:36-39`). Kaynak güncellenip sıra değişirse aynı anahtar **farklı bölümü**
  oynatır; `LAST_VOD_KEY` ve resume kaydı da yanlış bölüme gider.
- `buildLibraryChannel` kanal-listesi dalında `setVodHasEpisodes` **hiç çağrılmıyor**
  (`VodHost.kt:306-321`) → `currentVodHasEpisodes` önceki içerikten bayat kalabiliyor, LIST
  butonu yanlış görünür (`VodPlayerOverlayController.kt:201-202`).

### VOD-006 — Yarış boşlukları (durum makinesi büyük ölçüde sağlam)
Generation/`cancelResolve`/`tuneGeneration` korumaları doğru çalışıyor; eski istek yeni sonucu
genel olarak ezemiyor. Kalan boşluklar:
- **`episodeJob` hiç iptal edilmiyor** — `cancelResolve` yalnız `resolveJob`'ı iptal eder
  (`VodHost.kt:37-40`). Bölüm seçici işi mod/kanal değişse de koşar ve callback'i
  `playVodKey` tetikleyebilir (`VodHost.kt:107-186`).
- Resolve sonrası `selectChannel` sayacı ikinci kez artırıyor (`MainActivity.kt:1151`) —
  sayaç paylaşımı kırılgan.
- `handlePayload` aynı-anahtar-ready dalında da `beginGeneration` artırıyor (`:207` → `:219-224`).

### VOD-007 — Resume (kaldığı yerden devam) — oynatma doğru, "Devam Et" rayında delik
- Motor doğru: 30 sn altı kaydedilmez, okumada 5 sn geri sarılır, son 60 sn içindeyse "bitti"
  sayılıp silinir (`IptvResumeStore.kt:15,24,26-27,90`). Her bölüm kendi anahtarıyla tutar.
- **Delik:** Devam Et rayı `VodCatalogQuery.resumeItems` içinde `sourceKey IN (...)` ile
  `iptv_channels ∪ vod_metadata` arıyor (`VodHomeActivity.kt:289-315`; `VodCatalogQuery.kt:41-45`).
  JSON dizilerinin dinamik `...:ep:3_hash` anahtarlarının **vod_metadata'da satırı yok** →
  **JSON dizilerinde yarım kalan bölümler rayda hiç görünmez.**
- `currentIptvContentKind` yalnız READY'de set ediliyor → hazırlıkta ölen oynatmalarda kayıt yok.

### VOD-008 — Özellik boşlukları
- ❌ **VOD overlay'de altyazı seçimi yok.** `AUDIO_SUB` düğmesi yalnız sesi açar
  (`MainActivity.kt:2374`); `showSubtitleTracks` yalnız **canlı IPTV** kontrol çubuğunda
  (`:5901-5903`) ve o çubuk VOD'da gizli (`:5150-5152`). VOD'da altyazı sadece otomatik tercihe
  göre seçiliyor (`:3218-3238`).
- ❌ **VOD overlay'de kalite/video seçimi yok** (`VodPlayerOverlayController.kt:236-246`);
  `showIptvVideoOptions` yalnız canlı IPTV'de (`MainActivity.kt:4601`).
- ❌ **Sonraki bölüm otomatik geçişi yok.** `onPlaybackEnded` yalnız resume temizler
  (`MainActivity.kt:729-737`). Manuel "sonraki bölüm" `VodDao.nextEpisode` SQL'i `kind='EPISODE'`
  aradığı için **JSON dizilerinde hiç çalışmaz** (`VodDao.kt:16-21`).
- ❌ **İzleme ilerleme yüzdesi gösterimi yok** — `position/duration` tutuluyor ama
  `VodPosterAdapter.bind` (`:80-106`) hiçbir ilerleme çizmiyor.
- ❌ Timeline üzerinde **drag/scrub yok**; timeline'a dokunma play/pause yapıyor, ilerleme yalnız
  ±10 sn / ±30 sn buton adımlarıyla (`VodPlayerOverlayController.kt:79-85`, `:271-274`, `:314-323`;
  `MainActivity.kt:2168-2171`). Chapter/segment atlama kavramı yok.
- ⚠️ VOD arama girişi ham `EditText` + yazılım klavye (`VodHomeActivity.kt:761-783`) —
  remote-first kuralıyla gerilimli. `VodHomeActivity.history` ölü alan (`:53`, `:99`).
- **Var ve çalışanlar:** ses parçası seçimi, 0.5–2.0x hız, ±10/±30 sn seek, play/pause, ses/mute,
  tam ekran, bölüm seçici (Room + JSON fallback), Devam Et rayı (+ uzun bas "sonra ekle"),
  manuel sonraki bölüm (yalnız Room), harici altyazı (SAF), kaynak/sıralama/kategori/arama.

### VOD-009 — Ölü kod
`WebVideoResolver.resolveStream` / `resolve` (`:118-165`) üretimde hiç çağrılmıyor;
`VodHost.classifyDeepLink` (`:52-63`) üretimde kullanılmıyor;
`VodHomeActivity.history` (`:53`, `:99`) ölü.

---

## 5. EKSİK / YARIM İŞLER

### MISS-001 — Play Billing HİÇ YOK, paid flavor'da IPTV tamamen açık ⚠️ EN BÜYÜK İŞ RİSKİ
- `app/build.gradle.kts` bağımlılıklarında `com.android.billingclient:billing` **yok**
  (`:141-158`). `BillingClient`, `PurchasesUpdatedListener`, `queryPurchases`,
  `acknowledgePurchase`, purchase-token doğrulaması **hiçbir yerde yok**.
- **`IPTV_PRO_REQUIRED = false` her flavor'da, `paid` dahil** (`:66`, `:76`, `:87`) →
  `IptvEntitlementPolicy.evaluate()` `FREE_BUILD / accessGranted = true` dönüyor
  (`billing/IptvEntitlementManager.kt:90-92`). Yani **production paid release'de ücretli
  içerik tamamen ücretsiz**, deneme/satın alma yolu hiç çalışmıyor.
- `billing/IptvAccessDialogs.kt:35` — "Satın al" butonu sadece `billing_not_connected` Toast'u
  gösteriyor ("sonraki aşamada etkinleştirilecek"). **Yer tutucu, kullanıcıya ölü akış.**
- Doğru olan kısımlar: `isPaid` bayrağı koda hiç girmemiş (0 eşleşme) → AGENTS.md yasağı
  fiilen ihlal edilmiyor ✓. `grantsAccess` `PURCHASE_PENDING`'i dışlıyor ve bu testli ✓
  (`IptvEntitlementManager.kt:106-111`). Debug simülatör `BuildConfig.DEBUG` kapılı ✓.
- İz: `TASKS.md:367` `STORE-001 [ ]`, `TASKS.md:371` `STORE-002 [ ]`.

### MISS-002 — VOD test boşluğu (AUDIT-000'ın doğrudan nedeni)
Testi **olmayan** kritik sınıflar:
- `vod/VodUrlClassifier.kt` (68 satır, saf JVM — son iki commit bunu değiştirdi ama **regresyon
  testi yok**) ← **en kritik açık, en kolay kapatılır**
- `vod/WebVideoResolver.kt` (713 satır), `vod/VodMetadataExtractor.kt` (216, saf JVM),
  `vod/VodPageResolver.kt`, `vod/VodPosterAdapter.kt`, `vod/VodRightPanelAdapters.kt`
- `playback/IptvPlaybackController.kt` (**1276 satır, projenin en büyük test-sınıfı**),
  `MultiViewController.kt` (827), `TifPlaybackController.kt`, `ChannelZapper.kt`,
  `IptvResumeStore.kt`, `PlaybackHistoryStore.kt`, `IptvDataSourceFactory.kt`,
  `ReconnectingHttpDataSource.kt`, `BufferedLiveDataSource.kt`, `ExternalPlayerLauncher.kt`,
  `VodPlayerOverlayController.kt`
- `main/` paketinde `MainDialogs`, `VodDialogs`, `RemoteInputRouter` test-siz (diğer 8 sınıfın var)
- Mevcut durum: 70 unit + 15 instrumented test. `main/` paket refactor sınıflarının **tamamı
  kullanımda, ölü sınıf yok** ✓.

### MISS-003 — Room migration testleri
`TVAppDatabase.kt:24` versiyon **32** (refactor dalı). Zincir eksiksiz, `fallbackToDestructiveMigration`
**0 eşleşme** ✓, `schemas/` 31 dosya (25 bilinçli yok, `24_26` köprüsü var).
Ama migration testinin **tek** tane: `Migration25To24ContractTest.kt`. `MIGRATION_31_32` (`:84`),
`30_31` (`:105`), `24_26` (`:143`) test-siz.
⚠️ **`main` dalında v33 var** (`21c6d50 Add alternativeStreamsJson support with Room DB v33
migration`) — refactor dalı v32'de kaldı. **Dal birleştirilirken migration sırası kontrol edilmeli.**

### MISS-004 — `.gitignore` boşlukları (ikili/playlist sızma riski)
`.log/`, `*.log.txt`, `*.jks`, `*.keystore`, `keystore.properties`, `.env` doğru ignore'lu ✓
(`git ls-files .log` boş ✓, imzalama materyali repoda yok ✓).
Ama:
- **`.doc/` untracked VE ignore edilmemiş** — içinde `Güneş TV 5.2.apk+`,
  `Güneş Dns Koruma 1.0.16.apk+`, `demo_vod.m3u`, `playlist.m3u`, `test.m3u` var.
  `*.m3u` satır bazlı örtüyor ama **APK'ları ve dizini örtmüyor** → `git add .` ile sızar.
- `.clinerules/` untracked ve ignore edilmemiş.
- `.freebuff/` dizini ignore edilmemiş (yalnız `.freebuff/project-id` satırı listeli,
  `worktrees/` gibi alt içerik korumasız).

### MISS-005 — Release R8 mapping arşivi yok
`isMinifyEnabled = true` (`build.gradle.kts:122`) ama mapping dosyası hiçbir yere
kopyalanmıyor/upload edilmiyor. **Sonuç: CRASH-002 ve CRASH-003 loglarındaki `n1`/`u1`/`q2.m`
gibi adlar çözülemiyor, saha çökmeleri kalıcı olarak teşhis edilemez durumda.**

### MISS-006 — Küçük temizlik
- `MainActivity.kt:75` kullanılmayan import (`main.LongPressKeyGate` — yalnız
  `RemoteInputRouter.kt:21` kullanıyor).
- `build.gradle.kts:65,75,87` `STORE_BILLING_ENABLED` BuildConfig alanı **hiçbir Kotlin
  kodunda okunmuyor** → ölü bayrak.
- Refaz faz etiketleri koda gömülü ("Faz 1/2/3/4/5"): `EpgHost.kt:13`, `ChannelListRules.kt:12`,
  `RemoteInputRouter.kt:9`, `VodHost.kt:17`, `MainActivity.kt:2415`, `:6121`.
- `TASKS.md:375` `TXT-001 [ ]` — TXT/Teletext tuşu geçici olarak gerçek teletext yerine
  **VOD Ana Sayfa'yı açıyor** (`MainActivity.kt:3081-3083`).
- Klasik TODO/FIXME/XXX/HACK işareti proje genelinde **0** ✓ (yarım işler yorum yerine
  TASKS.md ve "geçici davranış" notlarında yaşıyor).

---

## 6. YAPISEL RİSK — DAL UYUŞMAZLIĞI

`refactor/main-activity-split` **main'in 15+ commit gerisinde** ve artık farklı davranıyor:

| | refactor dalı | main |
|---|---|---|
| WebView thread fix (CRASH-001) | ❌ bozuk | ✅ düzelmish (`cachedUserAgent`) |
| Room versiyonu | 32 | **33** (`21c6d50`) |
| EPG UNION/debounce perf | ❌ yok | ✅ `7056a66` |
| Touch/motion dispatch perf | ❌ yok | ✅ `da9c00b` |
| Fare/uzun-tık etkileşimleri | ❌ yok | ✅ 4 commit |
| TIF blackout mute + EPG kanal arama | ❌ yok | ✅ `8a147cc` |

Ayrıca **MainActivity hâlâ ~5856 satır** (refactor dalı) — Faz 6 (kabuk küçültme) yapılmadı;
`main`'de bu sayı daha da büyük. Kalan refactor kuyruğu: Faz 6 + plan dosyası güncellemesi
(`C:\Users\pools\.opencode\plan\main-activity-split.md` hâlâ Faz 4/5'i tamamlanmış işlemedıyor).

**Öneri:** refactor dalını `main` üzerine rebase/merge etmeden yeni iş yapmayın — aksi hâlde
main'deki çökme ve performans düzeltmeleri geri gelir.

---

## 7. ÖNERİLEN İŞ SIRASI

**Sıcak (çökme/güvenlik):**
1. `CRASH-003` — `CrashReportStore` trace'ini boyut sınırlı + stream'e yaz; tam-gövde
   `readText()` okumalarını sınırla (`IptvRepository.kt:479`, `XtreamClient.kt:330`,
   `StalkerClient.kt:160`, `XmlTvRepository.kt:747`)
2. `MISS-005` — release mapping arşivi (CI artifact) olmadan diğer çökmeler teşhis edilemez
3. `CRASH-001` kalıntısı — `StreamSniffer.kt:62,75` ve `WebVideoResolver.kt:309`'daki
   `CookieManager` çağrılarını UI thread'ine taşı
4. `VOD-004` — TMDB anahtarını BuildConfig/secret'e taşı; SSL toleransını hedef host'la sınırla;
   `console.log` sniffing kanalını kapat veya origin doğrulaması ekle

**Yüksek getiri / dar değişiklik:**
5. `MISS-002` — `VodUrlClassifier` + `VodMetadataExtractor` + `isNetworkMediaCandidate` /
   `isSegmentLike` / `rankHeadlessCandidates` için saf JVM birim testler (AUDIT-000 tipi
   regresyonları kalıcı olarak kapatır)
6. `VOD-001` — 0 adayda açık hata + Retry; `VOD-002` — `applyTo` önceliğini tersine çevir
7. `PERF-002` + `PERF-003` — filtreleme/diff'i IO'ya al, `AsyncListDiffer` kullan
8. `UI-001` — `bg_iptv_control` / `bg_vod_button`'a `state_focused` ekle (tek dosya,
   kullanılamaz ekranları kullanılır yapar)
9. `MISS-004` — `.gitignore`'a `.doc/`, `.clinerules/`, `.freebuff/`

**Orta:**
10. `PERF-001` — `VodCatalogQuery` CATALOG malzemeleştirme + `LIKE '%q%'` (cihazda ölç, sonra
    indeks/FTS/coverting kararı ver)
11. `VOD-005` — üç parser kopyasını tek noktaya indir, `:ep:` hash doğrulaması ekle
12. `VOD-008` — VOD overlay'e altyazı + sonraki bölüm otomatik geçişi
13. `UI-002` / `UI-003` / `UI-004` / `UI-005` — empty-state, yükleme/hata, i18n, punto
14. `MISS-001` — Play Billing (kapsamlı ayrı iş)
15. Faz 6 refactor + dal birleştirme

---

## 8. CİHAZDOĞRULAMASI BEKLEYENLER (Android 11 Google TV)

AGENTS.md gereği hiçbir aşağıdaki kalem "tamamlandı" sayılmaz:
- VOD deep-link, sniffer, bölüm seçici, seek/resume — **tüm VOD akışları**
- `VOD_DIRECT` / `VOD_RESOLVED` log satırlarıyla hangi URL'in seçildiğinin doğrulanması
  (hâlâ `PARSING_CONTAINER_UNSUPPORTED` varsa logdaki URL'yi paylaş)
- 1 Kasım `isStrongCandidate` gevşetmesinin 10 sn bekleme hissini azaltıp azaltmadığı
- Sniffer sırasında **odak kaybı** olup olmadığı (DecorView'a eklenen görünmez WebView —
  gerekirse `isFocusable = false`)
- Arama/ liste kaydırma jank'i (PERF-001/002/003 için 15k+ katalogla Before/After)
- CRASH-002 (StackOverflow) ve CRASH-003 (OOM) yeniden üretilebiliyor mu
- Renk/odak tuş sözleşmeleri ve `Back` davranışı (tüm refactor'lar boyunca hiç cihazda test edilmedi)

---

*Oluşturma: 13 Kasım 2026. Kaynaklar: 4 paralel salt-okunur kod denetimi,
`.log/TVApp/` çökme logları, `git` geçmişi ve AGENTS.md sabitleri.*
