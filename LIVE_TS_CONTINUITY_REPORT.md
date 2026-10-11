# Canlı TS süreklilik ölçümü - 2026-10-11

## TV Geri Bildirimi Ve Varsayılanın Düzeltilmesi

`bcc1083` sonrası fiziksel TV geri bildiriminde bazı kanallarda 5–10 saniye
sonra yeniden açılma ve siyah ekran görüldü; bazı kanallar ise iyileşti.
Aşağıdaki çevrimdışı paket ölçümleri decoder ve gerçek bağlantı sürekliliğini
doğrulamıyordu. Sınır doğrulaması hata verdiğinde eklenen temiz MediaSource
kurtarması bu yeniden açılma davranışını tetikleyebilir; güncel cihaz logu
olmadan hangi sınır hatasının gerçekleştiği kesinleştirilemez.

Standart yöntem artık varsayılandır ve `5ba0ea8` veri akışını kullanır.
Tekrar ayıklama yalnız kanalın uzun OK menüsünden açıkça seçilirse çalışır.
Tercih sonraki kanal açılışında uygulanır. Aşağıdaki deneysel sonuçlar tüm
kanallarda daha iyi veya kesintisiz oynatma garantisi değildir.

Infobardaki aynı seçimden Kaydet, kullanıcı isteğiyle izlenen kanala hemen
uygulanır. Canlı IPTV zaman çizgisi kaldırılmıştır; VOD çizgisi korunur.
Buffer değeri `Player.totalBufferedDuration`, hız `playbackParameters.speed`,
konum/süre oynatıcının mevcut değerleridir. CANLI durumu yalnız gerçek live
timeline varsayılan konumuna göre belirlenir (2 saniye tolerans). Bu konumu
bildirmeyen progressive TS kaynakları yanlış şekilde canlı kırmızı gösterilmez.
Anlık değerler görünür infobarda 500 ms aralıkla yenilenir.

İncelenen son beş commit:

| Commit | İlgili değişiklik | Karar |
| --- | --- | --- |
| `bcc1083` | Tam TS paket eşleştirmesi; hata halinde temiz oynatıcı kaynağı | Varsayılan kapalı, kanal bazında deneysel |
| `5ba0ea8` | Sınırlı indirme kuyruğu ve başlangıç rezervi | Standart bağlantı davranışı olarak korunur |
| `e31e56a` | Canlı TS bitiminde bağlantıyı veri katmanında sürdürme | Kaynak ayarına bağlı olarak korunur |
| `a7a960a` | Stalker token cache ve canlı read timeout | Korunur; paket sınırı hatasında yeniden açılma eklemiyor |
| `ed0a8b1` | VOD kategori ve kumanda arayüzü | Korunur; canlı TS okuma katmanına müdahale etmiyor |

## Sorun

Kullanıcının verdiği süreli HTTP MPEG-TS yayını, yeni bağlantıda canlı
noktadan itibaren yalnız yeni veri göndermiyor. Geçmiş yayın penceresini
tekrar gönderiyor. Yanıtları doğrudan eklemek ses/video tekrarına yol açıyor;
daha büyük tampon bu tekrarları da biriktiriyor.

Özel URL, MAC, token ve yönlendirme adresleri bu rapora alınmamıştır.

## Ölçümler

TVApp User-Agent ile yönlendirmeler takip edildi. İki farklı kanalda toplam
16 TS yanıtı incelendi. Yanıtlar yaklaşık 3–5 saniyede indiriliyor, ancak
yaklaşık 24–33 saniyelik ses/video içeriyor. Son seri:

| Önceki indirme sonrası bekleme | Video örtüşmesi | Tekrar video PTS sayısı | Tekrar ses PTS sayısı |
| --- | ---: | ---: | ---: |
| 0 saniye | 24,12 saniye | 1211 | 202 |
| 5 saniye | 19,08 saniye | 957 | 159 |
| 15 saniye | 10,10 saniye | 508 | 85 |

Önceki yanıtın son tam ses/video TS paketleri sonraki yanıtta birebir bulundu.
HTTP yanıtları çoğunlukla 188 baytlık paketin ortasında kesiliyor.

İkinci kanalda aynı 0/5/15 saniye bekleme denemelerinde video örtüşmesi
sırasıyla 22,28 / 17,32 / 6,08 saniye oldu. Tekrar video PTS sayıları
558 / 434 / 153; ses PTS sayıları 133 / 103 / 36 olarak ölçüldü.

## Uygulanan Yaklaşım

- Mevcut kaynak bazlı sürekli bağlantı seçeneği ve sınırlı tampon korunur.
- Her elementary stream için son PES başlangıcı, paket sayısı ve son tam
  paket saklanır. Yeni yanıtta başlangıç ve bitiş sınırı doğrulanıp yalnız
  sonraki paketlerden devam edilir. Ses ve video bağımsız takip edilir.
- HTTP sonunda eksik kalan paket hiç teslim edilmez; sonraki yanıtın tam
  kopyasından alınır. Zaman damgaları ve codec verisi değiştirilmez.
- Sınır bulunamaz veya değişirse kör birleştirme yerine Media3 kurtarmasına
  hata döner. Bu özel hata eski byte konumundan yüklenmez; otomatik kurtarma
  açıksa mevcut retry bütçesi içinde temiz bir MediaSource/Extractor ile başlanır.
  Farklı zaman çizgilerine sahip kaynaklarda kesintisizlik vaat edilmez.

## Doğrulama Ve Sınırlar

Her iki kanaldan dörder gerçek yanıt, derlenmiş üretim sınıfından geçirildi.
İki kanalda da filtrelenmiş ikinci, üçüncü ve dördüncü yanıtta hem ses hem
video için önceden teslim edilmiş PTS tekrar sayısı **0** oldu. İlk kanalda
video DTS tekrarı veya geriye gidişi de görülmedi. Video zaman damgaları
B-frame sıralaması nedeniyle her pakette monoton olmak zorunda değildir;
bu yüzden yalnız küçük/büyük PTS karşılaştırmasıyla veri silinmez.

Birim testleri ses/video bağımsız hizalama, PES ortasından devam, tekrarlanan
dolgu paketleri, yarım HTTP kuyruğu, adaptation alanı, küçük okumalar ve
doğrulanamayan sınırları kapsar. Ham yayın örnekleri yalnız Git tarafından
yok sayılan yerel `.log` alanında tutulur.

Bu testler ağ/veri katmanını doğrular; fiziksel TV'de decoder, ses odağı,
uzun süreli oynatma ve kanal değişimi ayrıca denenmelidir. Tek kaynakla tüm
sağlayıcılar için evrensel bir optimum buffer değeri belirlenemez.
