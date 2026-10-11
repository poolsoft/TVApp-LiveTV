# Canlı TS süreklilik ölçümü - 2026-10-11

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
