# Gümüş arayüz: okunabilirlik ve kullanım düzeni

10 Kasım 2026. Kullanıcının seçimi: mevcut gümüş görünümü korumak; okunabilirliği ve kullanım düzenini iyileştirmek.
SVG'ler ilk tasarım önerilerini kaydeder. Kullanıcının sonraki geri bildirimiyle haftalık ekranın
yerleşimi uygulandı; güncel kapsam aşağıdadır. Dar Bugün taslağının tamamı henüz uygulanmadı.

## Kullanıcının sıcak renk paleti — 10 Kasım

Gümüş zemin ve beyaz yüzeyler korunarak şu beş renk native arayüze eklendi:

| Renk | Kullanım |
| --- | --- |
| `#432523` | Altın zeminde koyu yazı; koyu temada seçili menü zemini |
| `#633a30` | Açık temada seçili menü ve ana eylem dolgusu |
| `#8a5a44` | Odak halkası, çalışma blokları, seçim kenarlıkları ve şimdiki zaman vurgusu |
| `#bb895a` | Ders kartı çizgisi, kart/odak kenarlıkları ve koyu tema hover vurgusu |
| `#d4bc6a` | Profil rozeti, hafta özeti çizgisi ve koyu temada ana vurgu |

Kart yüzeylerinde paletten türetilen açık sıcak tonlar kullanılır; ana metin ve zemin nötr kalır.
Öneri bloklarında metni solduran saydamlık kaldırılır; kesikli kenarlık korunur.
Kırmızı teslim/çakışma ve yeşil tamamlanma anlamı sürer. Finansın ayrı HTML stil dosyası bu değişikliğe dahil değil.

Ölçülen metin/zemin kontrastı: beyaz / `#633a30` **9.64:1**, `#432523` / `#d4bc6a`
**7.33:1**, koyu metin / açık çalışma yüzeyi **12.57:1**, açık metin / koyu çalışma yüzeyi **11.58:1**.
Bu ölçüm seçilen eşleşmeler içindir; uygulamanın bütün kontrollerinin erişilebilirlik denetimi değildir.
CSS JavaFX ayrıştırıcısıyla, ekran bağlantısı olmadan kontrol edilir. Kullanıcının masaüstünde pencere açılmaz.

## Kullanıcı geri bildirimiyle uygulanan düzen

- Bugün/Ajanda yapılacakları ve haftalık plan havuzunda, bugünden 7 günden daha eski işler gizlenir.
  Sınır profil saat dilimine göre bugündür; hafta gezinmesi sınırı değiştirmez. Kayıtlar ve CLI havuzu korunur.
- Bütün teslimler, saatleriyle birlikte gün başlıklarının altındaki sabit şeride taşındı. 23:59 teslimleri
  gece yarısı çizgisine sıkışmaz; aynı gün ilk iki teslim ve diğerleri için “+N teslim daha” görünür.
  Tam başlık ipucunda/ayrıntıda, diğer teslimler yan panelde açılır. Saatler kaydırılırken şerit sabit kalır.
- Ayrıntı paneli başlangıçta kapalıdır; iş/blok seçilince açılır, “Ayrıntılar” ile kapanır.
  Kapanınca kullanılabilir genişlik haftaya katılır. Havuz genişliği pencereye göre 228–320 px,
  ayrıntı 248–340 px aralığında hesaplanır; sütunlar takvimin genişliğini doldurur.
- Takvim ve Bugün için dış boşluklar azaltıldı. Hafta gezinmesi ve işlem araçları ayrıldı;
  daha dar pencerede araçlar ikinci satıra geçer. Saat/kart metinleri büyütüldü; bugünün vurgusu başlıkta kaldı.
- Gerçek JavaFX kabuğu görüntüleri: `app/build/reports/planner-layout-full.png`,
  `planner-layout-details.png`, `planner-layout-compact.png`, `planner-layout-dark.png`.
  Bunlar testlerle üretilen build çıktılarıdır; SVG maketleri güncel uygulama görüntüsü değildir.

Kalan tasarım önerileri: dar Bugün görünümünde üstte küçük odak, öneri incelemesinde Türkçe işlem adları
ve dar haftalık ekranda havuz çekmecesi. Tam ekranda yedi sütun kullanılabilir alanı doldurur;
dar pencerede yedi gün yatay kaydırılabilir.

## Görsel taslaklar

- [Haftalık plan — geniş pencere](design/planner-silver.svg)
- [Bugün — dar pencere](design/today-compact-silver.svg)

Taslaklar örnek veri kullanır. Etkileşimli prototip değildir; panel, hiyerarşi ve metin düzenini gösterir.
Ölçüler uygulama aşamasında gerçek JavaFX font ölçümleriyle ayarlanmalı.

## İncelemede görülenler

Yerel JavaFX testlerinin `planner-stage-5.png`, `silver-home-light.png`, `silver-home-compact.png`
ve `silver-home-dark.png` görüntüleri ile WeeklyAgendaView, TodayModuleView ve style.css incelendi.
Görüntüler `app/build/reports/` altında yeniden üretilebilir build çıktılarıdır.

| Gözlem | Etkisi | Öneri |
| --- | --- | --- |
| Haftalık planın üst satırındaki dokuz düğme benzer ağırlıkta. | Hafta seçimi, kayıt araçları ve plan üretme aynı anda dikkat istiyor. | Tarih gezinmesini bir grupta topla; geri al/yinele/yenilemeyi araç grubuna al. “Otomatik yerleştir” tek belirgin ana eylem olsun. |
| Havuz ve inceleme paneli çoğu yerde 180–235 px; ızgara genişliği en az 900 px. | Tam kabukta yatay alan daralıyor; başlıklar ve hızlı ekleme örneği kesiliyor. | Geniş ekranda havuza yaklaşık 248 px, ayrıntıya 264 px ver; ayrıntı paneli kapatılabilsin. Dar ekranda paneller düğmeyle açılan çekmeceye dönüşsün. |
| Haftalık saat etiketleri ve teslimler 10 px; görev tarihleri 9 px. | Son tarih ve saat gibi karar bilgileri ana başlığa göre fazla silik kalıyor. | Saat/tarih/ikincil metni 12 px, kart başlığını 13–14 px yap; ikincil metni daha koyu griye taşı. |
| Havuz kartı kaynak, öncelik, tarih ve süreyi art arda veriyor. | Uzun başlıklarda kart taramak yavaşlıyor. | En fazla iki satır başlık; altında bir kaynak/öncelik satırı, ayrı son tarih, tek satır kalan/planlı süre. Tam başlık ayrıntıda ve ipucunda bulunsun. |
| Bugün ekranında içerik daralınca odak kartı uzun zaman çizelgesinin altına taşınıyor. | Küçük pencerede sayaca ulaşmak çok kaydırma gerektiriyor. | Dar görünümde odak sayacı başlığın altında yatay ve küçük olsun; ardından teslimler ve gün akışı gelsin. |
| Öneri incelemesinde `schedule` gibi komut adları gösteriliyor. | Kullanıcıya uygulama işlemi yerine CLI ayrıntısı sunuluyor. | “Çalışma ekle”, “Bloğu taşı”, “Süreyi değiştir”; önce/sonra saatleri ve gerekçesiyle göster. |
| Haftalık bugünün sütunu bütünüyle dolgu rengi alıyor. | Seçili blok ve öneri gölgesiyle görsel rekabet oluşuyor. | Bugünü başlık rozeti ve ince sütun vurgusuyla belirt; mavi yalnızca “şimdi” çizgisinde kalsın. |

## Korunacak tasarım dili

Poppins, gümüş zemin, beyaz yüzeyler ve sınırlı renk kullanımı sürer. Fazladan kart ve dekor eklemek
yerine mevcut yüzeyler düzenlenir. Önerilen ışık paleti mevcut CSS değişkenlerinden türetilir:

| İşlev | Açık tema | Koyu tema |
| --- | --- | --- |
| Zemin | `#FAFBFD` | `#191C22` |
| Yüzey | `#FFFFFF` | `#22262E` |
| Ana metin | `#24262B` | `#ECEEF3` |
| İkincil metin | `#626C7B` | `#ACB6C6` |
| Çizgi | `#E1E5EC` | `#383E49` |
| Ana eylem dolgusu | `#633a30` | `#d4bc6a` |

Kırmızı teslim/gecikme/çakışma, yeşil tamamlanma için ayrılır; şimdiki zaman kahverengi/altın çizgi ve saat etiketiyle gösterilir.
Her durumun metin veya simge karşılığı da bulunur. Seçili blok düz koyu kenarlıkla,
öneri kesikli kenarlık ve “Öneri” etiketiyle ayrılır; metnin saydamlığı azaltılmaz.

## Pencere genişliğine göre düzen

- **Geniş:** Havuz + hafta + kapatılabilir ayrıntı paneli. Panel kapatılınca takvim genişler.
- **Orta:** Hafta önceliklidir; ayrıntı paneli seçimle açılır. Yedi gün yatay kaydırılabilir;
  kullanıcı isterse “3 gün” görünümü seçebilir. Görünüm değişince kayıt veya tarih değiştirilmez.
- **Dar:** “Havuz” ve “Ayrıntı” düğmeleri çekmece açar; yedi gün gizlice düşürülmez.
  Bugün'de küçük odak çubuğu üstte kalır. İlk deneme boyutları 1440×900, 1200×800 ve 936×700'dür.

## Önerilen uygulama sırası

1. **Haftalık plan:** Araç çubuğu, havuz kartları, durum özeti, öneri kutusundaki Türkçe işlem adları.
2. **Bugün ve küçük pencere:** Üstte küçük odak, yaklaşan bloğa kaydırma, kapatılabilir yan paneller.
3. **Ortak bileşenler:** Yazı boyutları ve kontrast, buton öncelikleri, boş/yükleniyor/hata durumları;
   aynı kurallar Dersler, Classroom ve Ayarlar'a uygulanır.

Önce ilk iki ekran üzerinde kullanıcı incelemesi yapılmalı; ardından diğer modüllere yayılmalı.
Yeni tema motoru veya ağır görsel bağımlılık gerekmiyor. Finansın WebView ve UART'ın çalışma yapısı bu kapsamda değişmez.

## Tasarımın kabul ölçütleri

- Üç pencere boyutunda ana eylem, tarih ve odak kontrolleri görünür; kaybolan içerik açık bir düğmeyle erişilir.
- Uzun Türkçe ders/ödev başlıkları kontrol düğmelerini itmez; teslim tarihi her zaman okunur.
- Açık ve koyu temada normal metin için en az 4.5:1 kontrast hedeflenir; uygulama aşamasında ölçülür.
- Klavye odağı görünürdür; simgeli düğmelerin erişilebilir adı ve ipucu vardır.
- Odak, teslim, seçili blok, sabit ders ve bekleyen öneri yalnızca renkle ayrılmaz.
- Otomatik kayıt durumu “Kaydediliyor / Kaydedildi / Kaydedilemedi” şeklinde aynı yerde görünür;
  hata halinde taslak kullanıcıdan gizlenmez.
