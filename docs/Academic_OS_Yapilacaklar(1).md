# Academic OS — Yapılacaklar ve Kararlar

Son güncelleme: 22 Kasım 2026

| ID | İş | Durum | Kapsam / karar |
| --- | --- | --- | --- |
| AOS-001 | Uygulama içinden güncelleme | Tamamlanma durumu doğrulanacak | GitHub Releases üzerinden yeni sürüm kontrolü, sağ üstte bildirim ve uygulamadan indirme/yükleme isteniyor. Sürüm sayfasını açmak yerine doğrudan yükleme yapılmalı. |
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

- [ ] AOS-015 — Hızlı yapılacaklar listesi ve günlük faaliyet kaydı. Kullanıcı akışı Claude Design ile tasarlanacak.
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
- Ayrıntılı taksit alışverişi/aylık ödeme çizelgesi, hesaplar arası transfer ve ekstre uzlaştırma sonraki iyileştirme adaylarıdır; henüz geliştirme kararı veya tamamlanmış özellik değildir. Mevcut faiz oranları bu çalışmada değiştirilmedi.
- Doğrulama: 25 finans testi (18 gerçek JavaFX arayüz, 7 depolama) ve genel toplamda 247 test başarılı; başarısız/hatalı/atlanan yok. `:app:test :app:installDist --offline --no-daemon` arayüz testleri açık olarak ayrı sanal ekranda geçti. Profiller geçiciydi. Kaynak kod düzeltmesi kurulu Windows uygulamasının güncellendiği veya Windows kabul testinin tamamlandığı anlamına gelmez.
