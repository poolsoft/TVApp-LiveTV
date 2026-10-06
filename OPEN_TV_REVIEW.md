# Open-TV inceleme ve uygulama raporu

Tarih: 2026-10-07. İncelenen kaynak: Fredolx/open-tv, commit
`1bfa78a181166025a6b977d783f895e819ff720f` (Fred TV 1.9.1).
Kaynak kodu yerel olarak incelendi; uygulama derlenmedi ve TVApp ile performans karşılaştırması yapılmadı.
Kod kopyalanmadı; bağımsız uygulama mevcut TVApp API'lerini kullanır.

## TVApp'e uygulananlar

- Kanal Origin header'ı, kaynağa özel varsayılan User-Agent/Referer/Origin.
- Kaynağa özel eşzamanlı yayın sınırı; sunucunun bildirdiği pozitif Xtream limiti ile
  kullanıcı limitinin küçüğü kullanılır. Bilinmeyen veya sıfır limitten sayı üretilmez.
  Cihazın decoder/RAM sınırı ayrıca korunur. PiP/Multi-View seçim, başlatma ve değiştirme
  yollarında kontrol edilir; mevcut yayın sessizce kapatılmaz. Diğer cihazlar ve harici
  oynatıcıların oturumları yerel sayımda değildir; sağlayıcı son kararı verir.
- Xtream hesap bilgileri isteğe bağlı HTTP sorgusu ile gösterilir. Parola/token loglanmaz.
- Küçük canlı/VOD kategori yanıtları iki worker ile paralel alınır. Büyük kataloglar
  JsonReader üzerinden sırayla staging'e yazılır. Altı büyük yanıtı belleğe toplama yoktur.
  Toplam güncelleme hızında belirli bir kazanç garantisi verilmez; gerçek sağlayıcı testi gerekir.

## Veritabanı kararı

Open-TV `sql.rs` içinde 36 kayıtlık LIMIT/OFFSET, LIKE araması ve kaynak/isim bazlı
tercih geri yüklemesi kullanır. Aynı isimli farklı kanallar ve isim değişiklikleri bu yaklaşımı
TVApp'e taşımamak için önemli nedenlerdir. TVApp'in FTS, bounded/keyset sayfaları,
resolvedSourceKey ve atomik staging aktarımı korunur. Yeni migration sadece nullable
header/limit sütunları ekler; eşleştirme SQL'ini, indeksleri veya kullanıcı tablolarını değiştirmez.

Kaynak: [sql.rs](https://github.com/Fredolx/open-tv/blob/1bfa78a181166025a6b977d783f895e819ff720f/src-tauri/src/sql.rs).

## MPV canlı ön yükleme ve döngü

Open-TV `mpv.rs` canlı yayına `--prefetch-playlist=yes` ve `--loop-playlist=inf` gönderir.
`loop-playlist` EOF sonrası URL'yi tekrar açar; `loop-file` ile aynı şey değildir.
MPV belgesine göre ön yükleme mevcut URL tamamen okunduğunda sonraki playlist URL'sini
açar; HLS segment ön yüklemesi bu seçenekten ayrı çalışır. Dolayısıyla sürekli bir canlı
HTTP yanıtında veya HLS'de bu iki seçenek her donmayı çözmez. Open-TV kodundan tek
öğeli döngü sınırındaki kusursuz geçişi kanıtlayamayız; gerçek MPV/sağlayıcı testi gerekir.

TVApp, sonlu ve tamamen indirilmiş canlı parça için açıkça bir sonraki MediaSource'u
ekleyip Media3'ün kendi ön yüklemesini kullanır. Kuyruk iki öğeyle sınırlıdır, eski öğe bırakılır.
Süre, liste bazında 250–5000 ms'dir. Yeni indirme başarısızsa, başlangıç keyframe'i geçse veya
sağlayıcı zaman damgaları/medya içeriği uyumsuzsa geçiş boşluğu, tekrar veya atlama olabilir.
Ön yükleme ağ/codec/sağlayıcı sorununu yok etmez. Bu incelemede mevcut strateji değiştirilmedi.

Kaynaklar: [mpv.rs](https://github.com/Fredolx/open-tv/blob/1bfa78a181166025a6b977d783f895e819ff720f/src-tauri/src/mpv.rs),
[MPV ön yükleme](https://mpv.io/manual/stable/#options-prefetch-playlist),
[MPV playlist döngüsü](https://mpv.io/manual/stable/#options-loop-playlist).

## Kayıt ve yeniden yayınlama

Kayıt: Open-TV MPV'ye `--stream-record=<dosya>` verir. MPV demuxer'dan okunan veriyi
çıktı kapsayıcısına yazar; ekran görüntüsü kaydı değildir. MPV belgesi kanal değiştirme veya
seek sırasında bozuk/eksik kayıt ihtimalini belirtir. Sağlam kayıt için ayrıca depolama sınırı,
dosya finalizasyonu, uygulama kapanışı ve bağlantı hatası yönetimi gerekir.

Restream: `restream.rs` harici FFmpeg çalıştırır: `-c copy` ile video/sesi yeniden kodlamadan
HLS kapsayıcısına aktarır, hedef 5 saniyelik segment ve 6 segmentlik hareketli liste oluşturur.
Yerel HTTP sunucusu bu dosyaları LAN'a açar; yaklaşık 30 saniyelik playlist penceresi, tam
30 saniye uçtan uca gecikme garantisi değildir. Keyframe aralığı segment süresini etkiler.
Yeniden kodlama olmadığından istemcinin desteklemediği codec desteklenir hale gelmez.

İncelenen FFmpeg komutunda reconnect seçenekleri `-i` sonrasında bulunuyor. FFmpeg
seçeneklerin sonraki giriş/çıkışa uygulanacağını belirttiği için girişe ait reconnect
seçeneklerinin gerçekten uygulanmasını varsaymak doğru olmaz; bu sırayı kopyalamamalıyız.
Sunucu `0.0.0.0` üzerinde açılıyor; görünen başlatma kodunda erişim token'ı yok. TVApp için
LAN erişimi, kimlik doğrulama ve yayın bağlantısı sınırları ayrıca tasarlanmalı.

TVApp'e bu aşamada MPV/FFmpeg eklenmedi. Kayıt kesin olarak FFmpeg gerektirir demek de
doğru değil: sınırlı şifresiz doğrudan TS kaydı ayrı bir ağ/dosya akışıyla mümkün olabilir;
genel HLS/DASH kayıt/remux ve kesintili akışlarda sağlam dosya üretmek daha kapsamlıdır.
TIF kaydı ise vendor `TvRecordingClient`/input kayıt desteğine bağlıdır; `TvView` yüzeyinden
ham DVB yayını alınabildiği varsayılmamalı. Kayıt V2 kapsamı olarak ayrı planlanabilir.

Kaynaklar: [restream.rs](https://github.com/Fredolx/open-tv/blob/1bfa78a181166025a6b977d783f895e819ff720f/src-tauri/src/restream.rs),
[MPV stream-record](https://mpv.io/manual/stable/#options-stream-record),
[FFmpeg seçenek sırası](https://ffmpeg.org/ffmpeg.html#Description),
[FFmpeg HTTP reconnect](https://ffmpeg.org/ffmpeg-protocols.html#http).

## Xtream deneme ve sınırlar

Rastgele internet demo hesaplarına ihtiyaç olmadan yerel HTTP fixture testleri hesap yanıtı,
eksik alanlar, hatalı yetkilendirme, kategori paralelliği ve canlı/VOD URL üretimini sınar.
Fixture sağlayıcı ve içerikler açıkça test verisidir; gerçek yayın erişimi veya abonelik değildir.
Bu testler gerçek sağlayıcının bağlantı kısıtı, DRM'i, hatalı JSON'u ve canlı yayınını doğrulamaz.
Gerçek hesap olduğunda küçük bir kaynakla başlayıp hesap bilgisi, güncelleme, EPG ve
PiP/Multi-View sınırını doğrulamak gerekir. Emülatör kullanıcı verileri testlerde silinmez.

## Doğrulama

- Local/Paid debug birim testleri ve APK derlemeleri başarılı.
- Emülatörde 9 Android testi: gerçek export edilmiş Room 29 şemasından 30'a migration ve
  Room şema validasyonu, 15.000 staging kaydında üyelik/sıra/favori/motor koruma,
  kaynak yedek roundtrip, bağlantı ayarı kalıcılığı, Origin/Referer/User-Agent HTTP aktarımı,
  gerçek yerel HTTP yanıtıyla Xtream hesap/kategori/canlı/VOD ve sezon/bölüm ayrıştırma.
- Testlerde yalnız in-memory veya özel fixture veritabanı kullanılır. Kurulum `install -r`
  ile yapılır; kullanıcı listeleri, EPG verileri veya uygulama verileri temizlenmez.
- Fiziksel Android 11 Google TV kumandası, gerçek Xtream hesabı ve sağlayıcı bağlantı
  sınırı ayrıca doğrulanmalı. İzole emülatör testinden fiziksel cihaz hız sonucu çıkarılmaz.
