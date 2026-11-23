# Academic OS — Yapılacaklar ve Kararlar

Son güncelleme: 23 Kasım 2026

| ID | İş | Durum | Kapsam / karar |
| --- | --- | --- | --- |
| AOS-001 | Uygulama içinden güncelleme | Windows CI geçti — kullanıcı kabulü bekliyor | 1.6.1 paketinde Windows kurulum/açılış, yardımcılı yükseltme/kaldırma ve veri korunması doğrulandı. Kullanıcının kurulu 1.6.0 sürümünden indirme → onay → kapanış → kurulum → yeniden açılış ve tepsi denemesi bekliyor. [Sürüm notları](release-1.6.1.md). |
| AOS-002 | OneNote entegrasyonu | Fikir — karar verilmedi | Derslerle OneNote bölümlerini eşleştirme, notları listeleme ve ilgili notu açma değerlendirildi. Görevlere not bağlantısı ekleme de öneri aşamasında. |
| AOS-003 | Uygulama içinde not alma | Ertelendi — vize sonrası | OneNote benzeri kalemle yazma, çizim, PDF üzerine not ve otomatik kayıt değerlendirildi. Kapsam kesinleşmedi; acelesi yok. |
| AOS-004 | Ödevler ve sınavlar listesinin görünümü | Yapılacak | İlk ekran görüntüsündeki listeye yalnızca görsel düzenleme uygulanacak; işleyiş değiştirilmeyecek. |
| AOS-005 | Ödev / sınav ekleme formunda saat alanı | Yapılacak | İkinci ekran görüntüsündeki son teslim saati alanı daha kullanışlı yapılacak. Serbest metin nedeniyle saatin tümü silinip rastgele içerik yazılabilmesi düzeltilecek. Saat ve dakika için kontrollü giriş / saat seçici kullanılacak; geçerli saat biçimi ve aralıkları doğrulanacak. Alan isteğe bağlıysa boş bırakılabilme korunacak. Kesin kontrol tasarımı uygulama sırasında belirlenecek. |
| AOS-006 | Haftalık planı Google Takvim'e doğrudan aktarma ve senkron takip | Yapılacak | Haftalık plan / takvim alanına belirgin bir “Google'a aktar” butonu eklenecek. Mevcut plan ve etkinlikler bilgileri tekrar elle girilmeden bağlı Google Takvim'e aktarılacak. Kullanıcı haftalık planlarını Google ile senkron takip etmek istiyor. Tekrar aktarımda aynı etkinliklerin çoğalmaması ve aktarılan etkinliklerin değişikliklerinin eşleştirilmesi tasarımda ele alınacak. Otomatik veya çift yönlü senkron kapsamı henüz kesinleşmedi. |
| AOS-007 | Google etkinliği ekleme formunun düzeni | Yapılacak | Son ekran görüntüsündeki form düzenlenecek. “B...”, “Bi...”, “S...” gibi kesilen alan etiketleri tam ve okunaklı gösterilecek; alan ve etiket genişlikleri iyileştirilecek. Haftalık planı aktarmak için etkinlikleri bu formda tek tek yeniden oluşturmak gerekmeyecek (AOS-006). |

| AOS-008 | Haftalık planda sınavların teslim olarak sınıflandırılması | Hata — düzeltilecek | Ekran görüntüsünde sınavlar saatli sınav blokları olarak görünürken üstteki “Son teslimler” şeridinde de listeleniyor. Sınav türündeki kayıtlar teslimlerden ayrılacak; “Son teslimler” yalnızca teslim niteliğindeki kayıtları gösterecek. Sınavların kendi başlangıç/bitiş saatlerindeki plan blokları korunacak. Başlığında VİZE yazan fakat “Son teslim” görünen kayıtların gerçek türü ayrıca kontrol edilecek; yalnızca başlığa bakılarak tür değiştirilmeyecek. |

## Takip kuralları

- Academic OS hakkında konuşurken yeni istek ve kararlar bu dosyaya işlenecek.
- Fikirler doğrudan geliştirme kararı sayılmayacak.
- Tamamlandığı doğrulanmadan işler tamamlandı işaretlenmeyecek.
- Yeni sohbette devam etmek için bu dosya seçilecek veya adıyla bulunup güncel içeriği okunacak. Dosya her sohbette kendiliğinden yüklenmiş sayılmaz.
- İlk liste hazırlanırken uygulamanın mevcut kodu incelenmedi. 22 Kasım finans incelemesi ve doğrulama durumu aşağıdaki çalışma kaydında belirtilmiştir.

## Karar geçmişi

- 13 Kasım 2026: Haftalık planda sınavların “Son teslimler” şeridinde de görünmesi hata olarak kaydedildi. Kod değişikliği yapılmadı.

- 13 Kasım 2026: Haftalık planları Google ile senkron takip etme, doğrudan “Google'a aktar” butonu ve Google etkinlik formunun düzenlenmesi istendi. Yalnızca not alındı; uygulamada değişiklik yapılmadı.

- 13 Kasım 2026: Kullanıcının iki ekran görüntüsüne dayanarak ödev/sınav listesine yalnızca görünüm güncellemesi ve ekleme formundaki saat alanına kontrollü giriş iyileştirmesi yapılacaklar listesine eklendi. Bu istek kapsamında kod değişikliği yapılmadı.

- 13 Kasım 2026: Uygulama içi not alma vize sonrasına ertelendi.
- 13 Kasım 2026: OneNote entegrasyonunun faydaları konuşuldu; geliştirme kararı verilmedi.
- 13 Kasım 2026: Listenin sohbet dışında erişilebilir tek bir dosyada tutulması istendi.

## Windows yayını — 23 Kasım 2026

- [Academic OS 1.6.1](https://github.com/berkayoltuluoglu/berkay-academic-os/releases/tag/v1.6.1) kararlı yayın olarak açıldı; setup, SHA-256 ve Windows kılavuzu birlikte yüklendi. Herkese açık güncelleme API'si 1.6.1'i döndürüyor.
- Windows CI'da 271 uygulama testi ve 7 paketleyici testi geçti; kurulum, JavaFX açılışı, CLI, bakım yardımcısıyla yükseltme/kaldırma ve kullanıcı verisinin korunması doğrulandı. [Doğrulama kaydı](release-1.6.1.md).
- Kullanıcının Windows sürümü 1.6.0. Ayarlar'dan 1.6.1'i bulma, indirme, onaylı kapanış/kurulum/yeniden açılış ve tepsi denemesi henüz kullanıcı tarafından doğrulanmadı; AOS-001 bu kabul tamamlanana kadar açık kalır.

## Finans — 13 Kasım 2026

- [x] AOS-009: Dönem borcu ve gelecek taksitler ayrı izleniyor. Yapı Kredi WORLD örneği: limit 15.000 TL, toplam kalan borç 12.440,52 TL, kalan dönem borcu 0 TL. Bu durumda asgari ödeme 0 TL ve gecikme uyarısı yok. Toplam borç kart limitinden düşülüyor; ekstre dışı tutar bu ayın ödeme ve faiz hesabına katılmıyor. 22 Kasım 2026: geçici profille doğrulandı. Ay bazında taksit çizelgesi bu düzeltmenin kapsamında değil.
- [x] AOS-010: Kart 0 TL dönem borcuyla kaydedilebiliyor; boş alan ile sıfır ayrılıyor. 22 Kasım 2026: kayıt ve yeniden açılış geçici profille doğrulandı.
- [ ] AOS-011: Kullanıcı 0,01 TL girince “script error” gördüğünü ve uygulamadan atıldığını bildirdi. Kesin hata metni, kaydın oluşup oluşmadığı ve kök neden doğrulanmadı. Ondalık giriş, küçük tutar hesapları ve kayıt/hata yönetimi incelenmeli; neden henüz bilinmiyor. Düzeltme sonrası 0 ve 0,01 TL senaryoları kontrol edilmeli.
- Öneri: Taksitli alışverişte toplam tutar, taksit sayısı, kalan taksit sayısı ve ilk ödeme dönemi alanlarıyla gelecek ayların ödemeleri planlanabilir. Ayrıntılı tasarım kesinleşmedi.
- 13 Kasım'da yalnızca not alınmıştı; 22 Kasım'da AOS-009/010 uygulandı. AOS-011 için aşağıdaki doğrulama sınırı geçerlidir.


## Nakit akışı — 14 Kasım 2026

- [x] AOS-012: Nakit akışına hesap seçimi eklendi. Gider seçilen hesabı bir kez azaltıyor; gelir artırıyor. Düzenleme, yön veya hesap değiştirme ve silme önceki etkiyi geri alıyor. Eksi gider girişi destekleniyor. Harçlık kullanımı geri alınabiliyor; yetersiz bakiye ve kayıt hatası mevcut kaydı koruyor. 22 Kasım 2026: geçici profille doğrulandı.

## Ödev süresi tahmini — 15 Kasım 2026

- [ ] AOS-013: Yaklaşan ödevlerin açıklamalarını ve PDF/DOCX eklerini okuyup tahmini çalışma süresi öneren yapı. Durum: fikir — geliştirme kararı verilmedi.
- Ödevi aşamalara bölme; her aşama ve toplam için süre aralığı, kısa gerekçe ve belirsizlikler gösterme. Konuya hâkimiyet dikkate alınmalı; kullanıcı tahmini düzeltebilmeli.
- İlk kapsam önerisi: içerik okuma ve aşamalı süre tahmini. Gerçek çalışma süresi kaydedilerek ileride kişiselleştirme yapılabilir.
- İleride teslim tarihi ve boş saatlere göre çalışma oturumları önerme, haftalık plan ve Google Takvim ile bütünleşme değerlendirilebilir.
- Ekleri okuyan ve aşamalı tahmin veren sürüm için konuşulan 3–5 günlük süre, kod incelenmeden verilen kaba ön tahmindir.
- Kullanıcı yalnızca fikrin not alınmasını istedi; uygulama kodunda değişiklik yapılmadı.

## Mobil senkronizasyon · 21 Kasım 2026

- [ ] AOS-014 — Windows ve telefon arasında çift yönlü senkronizasyon. Durum: fikir.
- İlk kapsam: dersler, sınavlar, ödevler ve haftalık plan. Telefon için ana ekrana eklenebilen PWA değerlendirilecek. Telefonda tamamlanan ödev Windows'ta da güncellenmeli.
- Önerilen başlangıç: Supabase Free (PostgreSQL, kullanıcı girişi, hazır API, 500 MB veritabanı, bir hafta kullanılmazsa duraklatma).
- Alternatifler: Firebase Firestore (1 GiB, günde 50.000 okuma ve 20.000 yazma), Cloudflare Workers + D1 (özel API, veritabanı başına 500 MB), Render (deneme için uygun, ücretsiz PostgreSQL 30 gün sonra sona eriyor).
- Önce mevcut veri saklama biçimini incele. Yerel kayıtlar varsa koruyarak aktar ve eşzamanlı düzenlemeleri ele al. PDF ve not ekleri için depolama kotasını ayrıca değerlendir.

## Yapılacaklar ve Bugün Ne Yaptım? · 21 Kasım 2026

- [x] AOS-015 — Hızlı yapılacaklar listesi ve günlük faaliyet kaydı. Claude Design çıktısı 23 Kasım 2026 tarihinde native JavaFX ekranlarına aktarıldı; aşağıdaki teslim notları geçerlidir.
- Mevcut Görev Planlaması sekmesi form dolduruyormuş hissi veriyor. Görev eklemek için tek satıra başlığı yazıp Enter'a basmak yeterli olmalı. Kullanıcı kendi listelerini oluşturabilmeli ve görevleri tamamlandı olarak işaretleyebilmeli. Son tarih ve ders bağlantısı isteğe bağlı.
- Kullanıcı gününü zaten günlük olarak planlıyor. Bu akışta takvime çalışma bloğu ekleme ve Google Takvim'e aktarım yerine gün içinde gerçekleşen işleri kaydetme tercih edildi.
- Bugün Ne Yaptım? ekranında tek satırda saat aralığıyla (ör. 10:30–11:15 Sinyaller çıkmış soru çözdüm) veya yalnızca süreyle (ör. 30 dk kontrol çalıştım) kayıt eklenebilmeli.
- Yapılacaklar listesindeki bir görevden Süre kaydet seçeneğiyle açıklama hazır gelmeli. Listede bulunmayan faaliyetler de doğrudan kaydedilebilmeli.
- Günlük görünüm saatli kayıtları kronolojik göstermeli. Saat belirtilmeden girilen süreler de günlük toplama dahil edilmeli. İlk sürümün ölçütü, gün sonunda kayıtların yaklaşık bir dakikada girilebilmesi.

## Finans düzeltmeleri ve kullanım · 22 Kasım 2026

- AOS-009/010/012 kaynak kodda uygulandı. Kart formundaki **Dönem borcu**, bu ekstre için ödenmemiş tutardır. **Toplam kalan kart borcu**, dönem borcu ve ekstre dışı borçların toplamıdır; boş bırakılırsa dönem borcu kullanılır. Toplam, dönem borcundan küçük olamaz.
- Önceki kayıtlardaki tek tutarın ne kadarının gelecek taksit olduğunu uygulama tahmin etmez. Eski kayıtlar okunmaya devam eder; kartı Düzenle ile açarak iki tutarı ayırabilirsin. Bu geliştirmede gerçek kullanıcı profili düzenlenmedi.
- Ayrı toplam borç girilmiş kartta asgari ödeme tamamlandığında yeni ekstre kendiliğinden oluşturulmaz. Dönem borcu ve toplam borç gerçek ödeme kadar azalır; kalan asgari aynı dönem için izlenir. Yeni ekstre geldiğinde dönem borcu ve son ödeme tarihi güncellenir. Sadece notu değiştirmek ödeme takibini sıfırlamaz.
- Nakit akışında hesap seçilirse bakiye işlemle birlikte değişir. Tek hesap varsa başlangıçta seçilir; **Yalnızca kayıt** seçeneği bakiyeyi değiştirmez. Eski hesapsız hareketler kendiliğinden bir hesaba bağlanmaz. Düzenleme/silme önceki etkiyi geri alır; dosyayı açmak veya CSV/JSON içe aktarmak hareketleri ikinci kez uygulamaz.
- Bir hesaba bağlı hareket varsa hesap silinemez. Borç ödemelerinin oluşturduğu hareketler ayrıca düzenlenemez/silinemez; bu işlemler borç kaydı ve hesapla birlikte ele alınmalıdır.
- **AOS-011 kısmen ele alındı:** 0,01 TL kaydetme, detay açma ve yeniden yükleme mevcut JavaFX ortamında başarılı. Özgün kapanma birebir üretilemedi; bu yüzden madde tamamen kapatılmadı. Kayıt hatalarının işlenmeden JavaScript'e taşındığı yol kapatıldı; doğrulama ve disk/yedek hatasında son kayıt korunuyor, açıklayıcı hata gösteriliyor ve formdaki değerler yeniden denemek için kalıyor. Küçük tutarlı borçların projeksiyonda sıfır kabul edilmesi de düzeltildi.
- Ayrıntılı taksit alışverişi/aylık ödeme çizelgesi, hesaplar arası transfer ve ekstre karşılaştırması ilk düzeltmelerden sonra kullanıcı tarafından onaylandı; aşağıdaki AOS-016/017/018 kapsamında uygulandı. Mevcut faiz oranları değiştirilmedi.
- Doğrulama: 25 finans testi (18 gerçek JavaFX arayüz, 7 depolama) ve genel toplamda 247 test başarılı; başarısız/hatalı/atlanan yok. `:app:test :app:installDist --offline --no-daemon` arayüz testleri açık olarak ayrı sanal ekranda geçti. Profiller geçiciydi. Kaynak kod düzeltmesi kurulu Windows uygulamasının güncellendiği veya Windows kabul testinin tamamlandığı anlamına gelmez.

## Finans kapsamının tamamlanması · 22 Kasım 2026

İlk düzeltmeler kullanıcı tarafından `1be5871` commit'ine alındı. Sonrasında aşağıdaki üç geliştirme istendi ve kaynak kodda uygulandı:

- [x] **AOS-016 — Aylık taksit çizelgesi.** Taksitler sekmesinde kart, alışverişin toplam tutarı, toplam/ödenen taksit adedi ve ilk taksit tarihiyle plan oluşturma, düzenleme ve silme var. Kalan adetten 12 aylık görünüm ve tüm çizelgenin CSV çıktısı üretilebilir. Tutar kuruşlara bölünür; bölünmeyen fark son taksite eklenir. Ocak 31 gibi tarihlerde ay sonu doğru hesaplanır. Bu çizelge mevcut kart borcunun dökümüdür; kart borcu veya hesap bakiyesi ikinci kez artırılmaz. Gerçek ödemeden sonra ödenen taksit adedi kullanıcı tarafından güncellenir. Faiz ve diğer alışverişler çizelgeye otomatik eklenmez.
- [x] **AOS-017 — Hesaplar arası transfer.** Bakiyeler sekmesinde tek işlemle kaynak ve hedef hesap, tutar, tarih, açıklama ve aktarılan harçlık girilir. İki hesap birlikte güncellenir; toplam para korunur. Transfer gelir/gider toplamına katılmaz. Düzenleme ve silme iki hesaptaki önceki etkiyi birlikte geri alır. Aynı hesaba aktarım, yetersiz bakiye/harçlık, harcanmış parayı geri almaya çalışan silme ve kayıt hatası değişikliği engeller. Banka masrafı ayrı giderdir.
- [x] **AOS-018 — Banka hesap ekstresi karşılaştırması.** Ekstre karşılaştır sekmesinde UTF-8 CSV/TSV dosyası veya yapıştırılmış içerik okunur. Ayraç ve yaygın sütun başlıkları algılanır; tarih, açıklama, tutar veya ayrı giriş/çıkış sütunları kullanıcı tarafından seçilebilir. Tek tutar sütununda pozitif tutarın yönü seçilir. ISO, GG.AA.YYYY ve GG/AA/YYYY tarihleri desteklenir. Aynı tarih/yön/tutar/açıklama önce tam eşleşir; açıklama veya tutar farkı, yalnızca ekstrede ve yalnızca yerelde kalan hareketler ayrı gösterilir. Yinelenen satırlar yerel kaydı ikinci kez kullanmaz. Transferin seçilen hesaba ait yönü karşılaştırmaya dahil edilir. İsteğe bağlı açılış/kapanış bakiyesiyle dönem hesabı kontrol edilebilir; bugünkü bakiye geçmiş ekstre kapanışı olarak varsayılmaz. Ekstre saklanıp güncel kayıtlarla tekrar karşılaştırılabilir. Hiçbir karşılaştırma hareket veya bakiye oluşturmaz/değiştirmez. PDF okuma ve bankaya otomatik bağlanma yoktur.

Veri uyumluluğu:

- Finans defteri şeması **2** oldu. Eski şema 1 okunur; okumak dosyayı değiştirmez. İlk başarılı kayıt önceki tam içeriği `.bak` olarak saklar. Yeni şema eski uygulamada açılmaz; eski sürüme dönüş için o sürüme uygun yedek gerekir.
- Taksit planları, transferler ve saklanan ekstreler JSON/CSV yedeklerine ve uygulamanın genel profil yedeğine dahildir. İçe aktarma ve yeniden açılış hesap hareketlerini yeniden uygulamaz. Bağlı planı olan kart ve bağlı hareket/ekstresi olan hesap silinemez.
- Kullanıcı profiline bu geliştirmede yazılmadı; kurulu Windows paketi yayımlanmadı. Kod/yerel dağıtım tamamlanması Windows kurulum kabulü değildir.
- AOS-009/010/012/016/017/018 ile kararlaştırılan finans geliştirme kapsamı tamamlandı. AOS-011'in özgün kapanması birebir üretilemediğinden önceki doğrulama sınırı korunur; 0 ve 0,01 TL senaryoları ile kayıt hatası yönetimi test edilir.
- Son doğrulama: **255 test başarılı**, başarısız/hatalı/atlanan yok; bunların 33'ü finans testi (23 JavaFX arayüz, 10 depolama). `:app:test :app:installDist --offline --no-daemon` arayüz testleri açık olarak ayrı sanal ekranda geçti. Yerel dağıtımın kaynak kontrolü ayrıca Java 21 ile başarılı. JSON/CSV turu, şema 1 → 2 geçişi ve önceki dosyanın yedeği, ay sonu/kuruş bölüşümü, transfer düzenleme/silme/yetersiz bakiye ve ekstrede yinelenen satır/tutar/açıklama farkları doğrulandı.


## Yapılacaklar ve faaliyet günlüğü — teslim · 23 Kasım 2026

- Referans: İndirilenler’deki `Shared link(1).zip`, içindeki `Yapilacaklar ve Gunluk.dc.html`. Gümüş–sıcak palete uyarlanmış sürüm esas alındı; Modernist varyantın kırmızı/sıfır köşe tasarımı uygulanmadı. Kaynak referans `notes/design-references/aos-015-claude-reference.html` olarak korundu. Prototipin tarayıcı betikleri veya harici font bağlantıları uygulamada çalıştırılmaz; ekranlar mevcut Java 21 / JavaFX, Poppins ve yerel tema ile oluşturulur.
- Kenar çubuğunda **Yapılacaklar** ve **Bugün Ne Yaptım?** bulunur. Eski kullanıcı yapılandırmasına eksik gezinme bağlantıları yalnızca bellekte eklenir; kullanıcı config dosyası elle veya bu uyarlama sırasında değiştirilmez.
- Tek satır + Enter ile tarihsiz görev eklenir. Kişisel listeler eklenebilir, yeniden adlandırılabilir ve silinebilir. Liste silinince görevler Genel’e taşınır. Görev tamamlama/yeniden açma, taşıma, silme ve geri al/yinele desteklenir.
- Sağ ayrıntı panelinde başlık, not, isteğe bağlı tarih ve ders bağlantısı bulunur. Saat, öncelik, tahmin, ek çalışma ve alt görevler açılabilir gelişmiş bölümde korunur. Dar ekranda panel sağdan içerik üzerine açılır; kapatma/vazgeçme eylemi vardır. Eski görev kimlikleri ve mevcut görev deposu kullanılmaya devam eder.
- Günlüğe `10:30–11:15 Sinyaller çıkmış soru çözdüm`, `30 dk kontrol çalıştım`, `1,5 saat rapor yazdım` biçimlerinde kayıt girilir. Önizleme vardır; hatalı giriş korunur. Saatli kayıtlar sıralanır; saatsiz süreler ayrı grupta ve günlük toplamda gösterilir. Geçmiş günlere geçiş, düzenleme, silme ve geri alma desteklenir. Gerçekleşen faaliyet gelecekteki güne kaydedilemez; çalışma bloğu oluşturulmaz ve Google’a etkinlik gönderilmez.
- **Süre kaydet** görevin açıklamasını hazır getirir; görevi tamamlamaz. Göreve bağlı elle kayıtlar planlayıcının gerçekleşen/kalan süre hesabına katılır. Gerçek sayaç kayıtları yerel odak günlüğünden okunur; gece yarısını geçen süre ilgili yerel günlere bölünür. Aynı çalışmada Enter ile açıklama sayaçla birleştirilir ve süre bir kez sayılır; ayrı faaliyet açıkça Alt+Enter ile eklenebilir. Sayaç süresi bu ekrandan değiştirilmez veya silinmez; açıklama notu eklenip kaldırılabilir.
- Yeni listeler, görev yerleşimleri ve faaliyetler `data/planning/journal.json` şema 1 içinde tutulur. Görev dosyasının şeması değiştirilmedi. Bütün kullanıcı işlemleri mevcut planlayıcının kilitli/stale korumalı işlem ve geri al/yinele katmanından geçer. Genel yedekleme ve geri yükleme günlük dosyasını da kapsar. Görev silmek geçmiş faaliyetleri, sayaç süresini veya çalışma bloklarını silmez; görev bağlantıları kaldırılır.
- Kullanıcı profilinde bu geliştirme sırasında değişiklik yapılmadı. Test profilleri geçicidir. Commit/push yapılmadı; kurulu Windows paketi yayımlanmadı.

- AOS-015 son doğrulaması: **271 test başarılı**, başarısız/hatalı/atlanan yok. Arayüz testleri ayrı sanal ekranda, geçici profillerle çalıştı; `:app:test :app:installDist --offline --no-daemon` ve Java 21 paket kaynak kontrolü başarılı. Hızlı görev/listeler, geniş/dar yerleşim, günlük ekleme/düzenleme/silme, bozuk girişin korunması, sayaç birleştirme/gece yarısı ayrımı, silinen görevin tarihçesi, stale form koruması ve genel yedekleme/geri yükleme doğrulandı.
