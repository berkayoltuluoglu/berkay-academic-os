<div class="cover-page">
    <h2>MARMARA ÜNİVERSİTESİ</h2>
    <h3>Teknoloji Fakültesi</h3>
    <br><br>
    <h3>İHA Marmara Kulübü</h3>
    <br><br><br><br>
    <h1 style="border:none; font-size: 22pt;">Berkay Academic OS — Tam Durum Dokümanı</h1>
    <h3 style="font-weight: normal;"></h3>
    <br><br><br><br><br><br>
    <p><strong>Hazırlayan:</strong> Berkay Kerem Oltuluoğlu</p>
    <p><strong>Tarih:</strong> Ağustos 2026</p>
</div>
<div style="page-break-after: always;"></div>

# Berkay Academic OS — Tam Durum Dokümanı
_Güncel teknik özet, yapılanlar, mevcut mimari, dosya düzeni, entegrasyonlar, hatalar, bekleyen işler ve sonraki adımlar_

**Son durum: 5 Ekim 2026.** Kullanıcı, Apple esintili gümüş arayüz demosunu beğendi ve bu tasarımın daha sonra ana JavaFX uygulamasına aktarılmasını istedi. Font aktarım sırasında daha şık bir fontla değiştirilecek; şu anda arayüz veya font değişikliği yapılmayacak. Tasarım kararı ve demo referansı **0.11 bölümünde**. Son uygulama geliştirmesi 1 Ekim'deki ders planlayıcı aktarımıdır: CSV şube kataloğu, elle şube seçimi, kredi/AKTS ve eski kayıtların korunması. İlk aktarım 0.9, aynı ekranda seçim/program iyileştirmesi **0.10 bölümünde**. Önceki A–G aşamalarına ait ayrıntılar geliştirme geçmişidir. Ortak ajanda, çalışma oturumları, müfredat/ÇAP ve LLM sonraki işlerdir.

**1 Ekim 2026 inceleme güncellemesi:** Kullanıcı ders planlayıcı projesini `/home/berkay/projects/ders-planlayici` konumunda paylaştı. İlk inceleme bulguları 0.8 bölümünde, ardından yapılan ilk aktarım 0.9 bölümünde. LLM entegrasyonu yapılmadı.

**Önceki düzenleme kapsamı (A–E aşamaları):**
- Aşama C — Belge Merkezi Drag & Drop import eklendi.
- Aşama D — UART Konsolu ürünleştirildi.
- Aşama E — Preview polish tamamlandı.
- Yol haritası baştan düzenlendi ve eski “Aşama C sıradaki aktif aşama” bilgisi güncellendi.

---

## 0. Güncel Durum ve Sonraki Oturuma Devir — 5 Ekim 2026

### 0.1 Kullanıcının hedefi ve kesinleşen kararlar

- Finans, ders takibi, kişisel görevler ve iş planlarını aynı masaüstü uygulamasında birleştirmek; günlük kullanıma mümkün olduğunca erken geçmek.
- Java ve JavaFX ile devam etmek. C++ veya başka bir dile geçiş yapılmayacak.
- Önce kullanıcının her şeyi kendisinin planlayabildiği, LLM gerektirmeyen ilk sürümü tamamlamak. Daha sonra bu planlama işlemlerini bir asistana devretmek.
- Uzun vadeli hedef, ders programını ve son tarihleri okuyabilen, çalışma planı önerebilen, değişikliklere göre yeniden planlayabilen bir “Jarvis” asistanı. Bu yetenekler henüz mevcut değil.
- **LLM için son karar:** İlk deneme altyapısını ileride Qwen ile kurmak; şu anda model kurulumu veya LLM entegrasyonu yapmamak. Önceki bulut önerileri kesin sağlayıcı seçimi değildir.
- Kullanıcının bilgisayarı: RTX 4050 Laptop, 16 GB RAM. Yerel model seçimi konuşuldu; gerçek cihazda hız, bellek ve Türkçe görev başarımı testi yapılmadı.
- **Güncel arayüz kararı (5 Ekim):** Kullanıcı Apple esintili gümüş arayüz demosunu beğendi; sonraki arayüz aktarımında bu tasarım esas alınacak. Profesyonel ve sade görünüm, minimum yazı, bugünün akışına öncelik ve yumuşak animasyonlar isteniyor. Font daha şık olacak; henüz font seçilmedi. Şu anda uygulama veya demo değiştirilmeyecek; ayrıntılar 0.11'de.
- Borç Defteri'nin yeni tasarımı şimdilik atlanacak. Burs takibi daha sonraki kapsamda.
- Commit mesajları **Conventional Commits** biçiminde olmalı (`feat:`, `fix:`, `docs:` vb.).
- **Ara verme geçmişi:** 30 Eylül'de yalnızca bağlam kaydedilerek duruldu. 1 Ekim'de kullanıcı ders planlayıcının ilk aktarım aşamasını açıkça istedi; bu aşama tamamlandı. Qwen kurulumu ve API bağlantısını erteleme kararı sürüyor.

### 0.2 Kodda mevcut olanlar

| Alan | Mevcut durum |
| --- | --- |
| Temel kararlılık | Script çalıştırma/iptal işlemleri arka planda; atomik dosya yazma, görev yedekleri ve web notlarında kayıt yaşam döngüsü korumaları var. |
| Tasarım | JavaFX kabuğu, sidebar, modül araması (`Ctrl+K`), açık/koyu tema ve belge/takvim/referans/UART/script/web ekranları yenilendi. AtlantaFX, Ikonli ve paketlenmiş fontlar kullanılıyor. |
| Bugün | Açılış ekranı. Bugünkü dersleri, tamamlanmamış yaklaşan/geciken görev ve ödevleri, sınavları ve ödeme özetlerini toplar. İleriye dönük pencere 7 gün; kaynak hataları ayrı gösterilir. |
| Dersler | Ders ekleme/düzenleme/silme; dönem tarihleri, öğretim elemanı, oturuma özel derslik, haftalık saatler, şube ve kredi/AKTS. CSV kataloğundan elle şube seçimi ve değişimi. |
| Ödev ve sınav | Derse bağlı kayıt oluşturma/düzenleme/silme, tarih ve tamamlanma durumu. |
| Haftalık program | Ders saatlerinin haftalık görünümü ve hafta gezinmesi var. Elle çalışma oturumu planlayıcısı veya çakışma denetimi henüz yok. |
| Görev Planı | Tarih, başlık, açıklama, durum ve dosya kalıcılığı var. Son teslim saati, süre, öncelik ve alt görevler henüz yok. |
| Borç Defteri | Burs bölümü hariç seçilen finans ekranları, Java üzerinden kalıcı kayıt ve içe/dışa aktarma. Arayüz paketlenmiş HTML/CSS/JS olarak WebView içinde; tamamı Java arayüzüne çevrilmedi. |
| Diğer modüller | Belge Merkezi, Referans, UART, scriptler, web/not ekranları ve Google Calendar okuma entegrasyonu mevcut. Bunların eski teknik ayrıntıları sonraki bölümlerde. |

Bugün ekranı bir özet ekranıdır; otomatik çalışma planı oluşturmaz. Ekrandaki ödeme hatırlatmaları işletim sistemi bildirimi değildir. Aboneliklerde ödeme durumu ayrıca takip edilmiyor. Google Calendar, yerel görevler ve dersler henüz tek bir ortak ajandada birleşmiş değil.

### 0.3 Temel dosyalar ve veri modeli

Java kaynakları `app/src/main/java/org/example/` altında:

- `UiTheme.java`: tema ve fontlar; kaynaklar `app/src/main/resources/ui/` altında.
- `TodayModuleView.java`, `TodayService.java`: kayıtları değiştirmeyen günlük özet.
- `CourseData.java`, `CourseModuleView.java`, `CourseStorageService.java`: dersler, haftalık ders oturumları, ödev ve sınav kayıtları.
- `CourseCsvImporter.java`, `CourseCatalogService.java`, `CourseImportDialog.java`: doğrulanan CSV okuma, katalog/şube seçimi ve arka planda önizleme.
- `FinanceModuleView.java`, `FinanceStorageService.java`: yerel WebView ve doğrulanan finans kaydı; arayüz `app/src/main/resources/finance/` altında.
- `TaskItem.java`, `TaskStorageService.java`, `CalendarModuleView.java`: mevcut görev ve takvim yapısı.
- `AtomicFileWriter.java`, `WebNotesSession.java`, `ScriptModuleView.java`: kayıt ve arka plan çalışma iyileştirmeleri.
- `App.java`, `ModuleActionHandler.java`, `SidebarBuilder.java`, `app/src/main/resources/config.json`: başlangıç, yönlendirme ve modüller.

| Veri | Konum / sınır |
| --- | --- |
| Dersler ve ödevler | `data/courses/academic.json`; sürümlü şema, atomik kayıt, yedek, dosya kilidi ve eski veri üzerinden yazmayı önleme. |
| Finans | `data/finance/ledger.json`; doğrulama, atomik kayıt, yedek ve eski veri üzerinden yazmayı önleme. |
| Görevler | `data/tasks.json`; atomik kayıt ve yedek. Mevcut `TaskItem` sabit kimlik, saat ve süre içermiyor. |
| Tema | `data/ui.properties`; açık/koyu tercih kalıcı. |

`CourseData.Work` şu anda `id`, `courseId`, `title`, `kind`, `date`, `done` alanlarını taşır. Ödev/sınav tarihi gün düzeyindedir; saat ve çalışma süresi henüz yok. Ders oturumlarında başlangıç/bitiş saati bulunur.

Yeni planlama alanları eklenirken eski kayıtlar korunmalı ve gerektiğinde şema geçişi yapılmalı. Finans/ders/tema verileri Git dışında tutuluyor; tüm veri dosyalarının aynı politikaya sahip olduğu varsayılmamalı (`data/tasks.json` için mevcut Git istisnası var). Günlük kullanıma geçmeden kişisel veri konumu ve Git kapsamı gözden geçirilmeli.

### 0.4 İlk kullanılabilir sürüm için önerilen sıra

Bu sıra son konuşmada önerildi; uygulamaya geçilmeden kullanıcı geliştirmeyi durdurdu.

1. **Elle çalışma planı:** Bir göreve/derse bağlı başlangıç-bitiş saatli çalışma oturumu ekleme, düzenleme, erteleme ve tamamlama. Son teslim tarihi ile çalışma zamanı ayrı kavramlar olmalı.
2. **Ortak günlük/haftalık ajanda:** Dersleri, ödev/sınav son tarihlerini, kişisel görevleri ve çalışma oturumlarını birlikte gösterme; saat çakışmalarını belirtme.
3. **Görev ayrıntıları:** Sabit kimlik, teslim saati, tahmini/kalan çalışma süresi, öncelik ve alt görevler. LLM gelmeden de kullanıcı bu alanları elle yönetebilmeli.
4. **Hatırlatmalar:** Teslim ve çalışma oturumu bildirimleri; uygulama açıkken/kapalıyken beklenen davranışı netleştirme.
5. **Ayarlar ve toplu yedekleme:** Veri konumu, çalışma tercihleri, tüm kayıtları dışa aktarma/geri yükleme. Mevcut `.bak` dosyaları tam yedekleme arayüzü yerine geçmiyor.
6. **Paketleme ve kullanım doğrulaması:** Terminal gerektirmeyen başlatıcı, proje klasöründen bağımsız sabit veri konumu, güncellemelerde veri koruma ve gerçek günlük akışları deneme.

Referans modülü geliştirmeleri, gelişmiş PDF/Markdown önizleme, UART ek özellikleri, burs takibi, animasyonlar ve LLM ilk sürümün ön koşulu değil. Tema/koyu mod artık tamamlanan işler arasında; içerikte arayan global arama ve ayarlar ekranı bununla karıştırılmamalı.

### 0.5 Kullanıcıdan gelecek tasarım ve ders projesi

- Mevcut Borç Defteri'nin kaynak örneği: `/home/berkay/İndirilenler/takip-paneli.html`.
- Genel tasarım referansı: `/home/berkay/İndirilenler/akademik-os-tasarim` klasörü. Bu referans incelenerek diğer modüllerin tasarımı uygulandı.
- Kullanıcı ders planlayıcı HTML/JS projesini **1 Ekim 2026'da `/home/berkay/projects/ders-planlayici` konumunda paylaştı**. Kaynak incelendi; ilk aşama JavaFX'e aktarıldı. İnceleme 0.8, uygulama kapsamı 0.9 bölümünde.
- Borç Defteri için yeni tasarım bekleniyor; önceki tasarımı yeniden yapmak veya Java'ya taşımak için verilmiş güncel bir uygulama talimatı yok.
- Yukarıdaki dosyalar depo dışındaki referanslardır; başka ortamda mevcut oldukları varsayılmamalı.
- **5 Ekim'de kabul edilen yeni genel arayüz referansı:** Apple esintili gümüş demo; bağlantı, görseller ve sonraki uygulama talebi 0.11'de. Borç Defteri'ne özel tasarımın ertelenmesi ayrı bir karardır.

### 0.6 Sonraki LLM aşaması için tasarım notları — henüz uygulanmadı

- İlk deneme adayı Qwen. Qwen3.5 4B/9B yerel seçenekleri ve çeşitli bulut sağlayıcıları konuşuldu; kesin model/sürüm ve sağlayıcı seçilmedi. Kurulum ve API anahtarı ayarı yapılmadı.
- Kullanıcı başarıyı maliyetten daha önemli görüyor; önceki fiyat ve model önerileri kalıcı teknik karar değil. Entegrasyon tarihinde güncel belgeler yeniden kontrol edilmeli.
- Aynı Java işlemleri hem elle kullanım hem asistan için kullanılmalı: kayıt okuma, görev ekleme, boş zaman bulma, çalışma oturumu önerme/değiştirme.
- Modelin rolü doğal dili anlamak, eksik bilgiyi sormak, işleri parçalamak ve gerekçeli öneri üretmek. Zaman hesabı, çakışma/son tarih kontrolü ve kalıcı kayıt Java tarafında doğrulanmalı.
- Tercihler ve geçmiş uygulama verilerinde tutulmalı; her program değişiminde model eğitimi gerekmez. Modele yalnızca ilgili kayıtlar verilmeli, tüm finans verileri her planlama isteğine eklenmemeli.
- İlk aşamada öneri ve kullanıcı tarafından uygulama akışı; tamamlanmış veya sabitlenmiş oturumları koruma ve yapılamayan planı açıkça belirtme düşünülüyor.
- Qwen ile gerçek Türkçe senaryolar, araç çağrılarının doğruluğu, geçerli plan üretimi ve gecikme ölçülmeden başarı/hız garantisi verilmemeli. Sesli giriş/çıkış ayrı bir sonraki özellik.

### 0.7 Doğrulama ve çalışma ağacı notları

- 1 Ekim ilk aktarım ve gerçek CSV uyumluluğu düzeltmesi sonunda `./gradlew :app:test :app:installDist --no-daemon` başarılı. Son raporlar: **54 test, 0 başarısızlık, 0 hata, 0 atlanan**. JavaFX CSV önizleme/şube değiştirme akışı gerçek pencerelerde test edildi.
- Görsel kontrol çıktıları `app/build/reports/` altında: `courses-week.png`, `today-preview.png` ve `design-*.png`. Bunlar build çıktısıdır; kalıcı kaynak değildir.
- Çalıştırma: proje kökünde `./gradlew :app:run`.
- Borç Defteri, tasarım, Bugün ve Dersler değişiklikleri çalışma ağacında; bu devir kaydında commit/push yapılmadı. Sonraki oturumda önce `git status` kontrol edilmeli, mevcut kullanıcı değişiklikleri korunmalı.
- Bu Markdown dosyası oturum başında zaten değiştirilmişti; önceki teknik içerik korunarak güncellendi. Aynı adlı PDF otomatik güncellenmedi ve bu yeni kararların kaynağı sayılmamalı.

### 0.8 Paylaşılan ders planlayıcının incelenmesi — 1 Ekim 2026

- Kaynak bağımsız HTML/CSS/JavaScript uygulaması: `index.html`, `style.css`, `script.js`, `planner.js`, `study-plan.js`, `curriculum.js`, `curriculum-ui.js`, `workflow.js`. Kullanım ve kapsam notları `to-do.txt`, testler `tests/` altında. Kaynak proje değiştirilmedi.
- CSV'den ders/şube/hoca/saat/kredi/AKTS okuma; müfredat PDF'sinden yarıyıl ve seçmeli havuzu çıkarma; iki bölüm/ÇAP, bağımsız ders geçmişi ve kullanıcı onaylı eşdeğerlikler var. PDF okuyucu belirli metin/tablo biçimini bekliyor; OCR veya genel amaçlı her PDF'yi okuma desteği değil.
- Elle şube seçimi, sabit haftalık etkinlikler, çakışma gösterimi, bölüm ilerlemesi, Excel çıktısı ve tarayıcı üzerinden yazdır/PDF kaydet akışları mevcut. Manuel ders seçimi akışı bile CSV ve kontrol edilmiş müfredat bilgisi istiyor; ana uygulamadaki dosyasız elle ders girişini korumak gerekir.
- Otomatik düzenleme LLM kullanmıyor. `StudyPlan.alternatives` kesin/esnek saat ve gün koşulları, hoca sırası, ders/kredi/AKTS/günlük yük ve yoğunluk tercihlerinden en fazla üç alternatif arıyor. Arama sınırı 120.000 düğüm veya 5 saniye; sınırda en iyi sonucu bulduğu garanti edilmiyor. Alternatifler kullanıcı uygulayana kadar takvimi değiştirmiyor.
- Bu çözücü **ders şubesi/dönem programı seçiyor**; ödev son tarihine göre kişisel çalışma saatlerini dağıtan bir planlayıcı değil. Manuel etkinliklerde haftanın günü ve saat var; gerçek tarih, ödev bağlantısı, kalan süre ve tamamlanma takibi yok.
- Kayıtlar `localStorage` içindeki `ders-planlayici:v1` anahtarında, veri sürümü 2 olarak saklanıyor. Java uygulamasına aktarımda merkezi, sürümlü dosya kaydı ve yedekleme; dönem tarihleri ve sabit kimlikler gerekli. Seçim kimliği şu anda kaynak/kod/hoca/saat içeriğinden üretiliyor.
- CSV, Excel ve PDF bileşenleri CDN'den geliyor. Masaüstü aktarımında bunlar paketlenmeli veya Java karşılıkları kullanılmalı; WebView'e gömme seçilirse PDF, dosya seçimi/indirme, yazdırma ve tarayıcı API uyumluluğu ayrıca sınanmalı. JavaFX'e taşıma henüz uygulanmış veya kesinleştirilmiş değil.
- **Doğrulanan kusur:** `script.js:131` içindeki `parseSchedule`, hücrenin yalnızca okunabilen bölümünü kabul edebiliyor. `Pazartesi 09:00-10:00 [D1] | Salı 13.00-14.00 [D2]` girdisi hata vermeden yalnızca pazartesiyi döndürüyor. Okunamayan ikinci oturum aktarımda kaybolabilir; kısmi ayrıştırma açık hata/inceleme gerektirmeli. Bu incelemede düzeltilmedi.
- **Test kapsamı:** Mevcut testlerin DOM gerektirmeyen ilk 18 senaryosu kaynak dosyalarıyla V8 üzerinde çalıştırıldı; 18/18 başarılı. Tarayıcı arayüzü, gerçek PDF/CSV dosya yükleme, CDN, Excel indirme ve yazdırma testleri bu incelemede çalıştırılmadı. Kısmi saat ayrıştırma kusuru ayrıca örnek girdiyle yeniden üretildi.
- Önerilen aktarım: önce ders/şube ve müfredat modelini mevcut Java ders kaydına bağlamak, elle seçim ve ortak ajandayı tamamlamak; ardından LLM'siz alternatif program çözücüsünü taşımak. Mevcut ödev/sınav kayıtları korunmalı. Kullanıcının güncel isteği incelemeydi; kapsamlı aktarım henüz başlatılmadı.

### 0.9 İlk aktarım aşaması tamamlandı — 1 Ekim 2026

Bu bölüm 0.8'deki incelemeden sonra kullanıcının verdiği uygulama talimatının sonucudur.

- **Tamamen Java/JavaFX:** Dersler modülüne “CSV'den şube aktar” ve “Şube kataloğu” eklendi. Kaynak HTML gömülmedi, dış projedeki dosyalar değiştirilmedi; bu akış CDN veya internet gerektirmiyor.
- CSV dosyası arka planda okunur ve önizlenir. Kullanıcı “Kataloğa aktar” demeden veri kaydedilmez. Katalog aktarımı mevcut programı değiştirmez; kullanıcı bir şubeyi seçip dönem tarihleriyle “Şubeyi programa al” düğmesine basar.
- UTF-8/BOM, virgül/noktalı virgül/sekme ayırıcıları, tırnaklı çok satırlı alanlar, Türkçe ve temel İngilizce başlıklar desteklenir. Dosya sınırı 10 MB, katalog sınırı 5000 şube; kalıcı ders dosyası sınırı 5 MB olarak sürer.
- **Zorunlu CSV başlıkları:** `Ders Kodu` ve `Gün Saat`. Önerilen tam başlık: `Ders Kodu;Ders Adı;Öğretim Elemanı;Gün Saat;Kredi;AKTS`. Kredi/AKTS boşsa bilinmiyor olarak tutulur; sıfır varsayılmaz. Saat hücresi boş olabilir ve önizlemede belirtilir.
- Kaynak planlayıcıdaki gibi `EE201.1` kodunda `EE201` ders kodu, tam kod ise şubeyi belirtir. Oturumlar `Pazartesi 09:00-10:50 [D101] | Çarşamba 13:00-14:50 [Lab]` biçimindedir. Gece yarısını geçen oturum bu aşamada desteklenmez.
- **Gerçek sunulan dersler CSV uyumluluğu:** `Gün Saat Derslik` başlığı ve `|` olmadan boşlukla art arda yazılan oturumlar da desteklenir. Aynı gün/saat/derslik tekrarları tekilleştirilir; okunamayan parçalar ve gerçek oturum çakışmaları reddedilir. Fazladan kontenjan/öğrenci sayısı sütunları aktarımı engellemez. Örnek CSV bu saat başlığına göre güncellendi; örnekteki AKTS sütunu isteğe bağlıdır.
- `/home/berkay/İndirilenler/` altındaki dört gerçek dosya güncel Java okuyucusuyla, kalıcı verilere yazmadan doğrulandı: `SunulanDersListesi.csv` (351 şube / 1267 oturum), `SunulanDersListesi_20260621_153216.csv` (339 / 1273), `SunulanDersListesi_20260917_180359.csv` (353 / 1267), `SunulanDersListesi_YeniMufredat.csv` (63 / 235). Dördü de okunuyor; hiçbirinde AKTS sütunu olmadığından bu bilgi boş kalıyor. Bu kullanıcı dosyaları depoya kopyalanmadı.
- Eksik/bozuk saat parçası, hatalı satır, geçersiz kredi ve bozuk UTF-8 bütün aktarımı durdurur; satır numarasıyla hata gösterilir. İncelemede bulunan kısmi saat kaybı yeni Java okuyucusunda engellendi; dış HTML projesindeki okuyucu değiştirilmedi.
- Aynı içeriğin yeniden aktarımı aynı şube kimliğini üretir ve katalogda çoğalmaz. İçeriği değişmiş şubeler yeni seçenek olarak eklenir; önceki seçimler sessizce güncellenmez.
- Aynı ders kodu ve çakışan dönem için şube değişiminde ders kimliği korunur; ödev/sınav ilişkileri kaybolmaz. Farklı, çakışmayan dönem için ayrı ders kaydı oluşturulur. Aynı dönemde birden fazla eşleşen ders varsa belirsiz seçim reddedilir.
- Elle ders ekleme/düzenleme korunarak şube, kredi/AKTS ve oturuma özel derslik alanları eklendi. Dersler ekranından elle düzenlenen kayıt katalog seçimine bağlı otomatik güncelleme olarak değerlendirilmez.
- Veri şeması **2**: `CourseData.sections` katalog, derslerde `section`, `credit`, `ects`, `sectionId`; oturumlarda `room`. Şema 1 okunduğunda bellekte yükseltilir, dosya hemen yeniden yazılmaz. İlk başarılı kayıtta eski dosyanın tam içeriği `.bak` olarak korunur. Bütün ders/ödev işlemleri kataloğu da korur.
- Seçilen şubenin saatleri ve derslikleri hem Haftalık program hem Bugün ekranında gösterilir.
- Yeni testler: `CourseCsvImporterTest`, `CourseCatalogServiceTest`; `CourseModuleViewTest` genişletildi. Eski verinin göçü/yedeği, tekrar aktarım, şube değişiminde ödev koruma, dönem ayrımı ve hatalı CSV'de dosyanın değişmemesi doğrulandı. Şube liste görünümü `app/build/reports/courses-catalog.png` ile kontrol edildi.

Kullanım:

1. Proje kökünde `./gradlew :app:run`.
2. Dersler → CSV'den şube aktar → Dosya seç / Önizle → Kataloğa aktar.
3. Şube kataloğu → şubeyi seç → dönem tarihlerini kontrol et → Şubeyi programa al.
4. Haftalık programdan sonucu kontrol et. Deneme verisi: `examples/dersler.csv`.

**Sonraki kapsam:** Müfredat PDF'si/ÇAP aktarımı, otomatik alternatif çözücüsü, kişisel çalışma oturumları ve ortak ajanda henüz uygulanmadı. Mevcut dersler arasında genel çakışma gösterimi de bu aşamaya dahil değil. Borç Defteri tasarımı ve LLM ertelenmeye devam ediyor. Commit/push yapılmadı.

### 0.10 Ders seçimi ve program aynı ekranda — 1 Ekim 2026

Kullanıcı ilk aktarımı hızlı fakat kaynak projeye göre yeterince pratik bulmadı; temel sorununu **ders seçerken programı görememek** olarak belirtti. Bu bölüm 0.9'daki ayrı katalog/program sekmelerinin yerini alan güncel kullanımı açıklar.

- Dersler açılınca **Planlayıcı** görünür: solda ders koduna göre gruplanmış şubeler, sağda saatlere orantılı haftalık çizelge. Bölümler arasındaki ayırıcı sürüklenebilir. Ders kayıtları ve ödev/sınavlar ayrı sekmelerde korunur.
- **CSV yükle → Önizle → Kataloğa aktar → ders ara → şubeye tıkla.** Sonucu görmek için sekme değiştirilmez. Dönem tarihleri soldaki açılır bölümden belirlenir. Örnek: `examples/dersler.csv`.
- Şube değiştirmek tek tıklamadır; başarılı kayıttan sonra çizelge yenilenir. Arama, filtreler, açılmış ders grupları ve görüntülenen hafta korunur. Kayıt hatasında eski seçim ve program geri gösterilir.
- Ders kodu/adı/hocasıyla arama, “Seçtiklerim” ve “Çakışmayanlar” filtreleri; seçilen ders/kredi/AKTS toplamı eklendi. Eksik kredi/AKTS bilinmiyor olarak ayrıca sayılır.
- Çakışmalar uyarıyla gösterilir ve takvimde yan yana yerleştirilir; kullanıcının çakışan ders seçmesi engellenmez. Bitiş ile başlangıcın aynı olduğu oturumlar çakışmaz. Çakışma kontrolü dönem kesişiminde ilgili günün gerçekten bulunmasını dikkate alır.
- Haftalık görünüm önceki/sonraki hafta ve “Bu hafta” ile gezilir. Dönem hafta ortasında başlıyorsa ilk görünüm, mümkünse ilk tam haftadır; böylece Pazartesi seçimi boş görünmez. Hafta sonu dersi olduğunda ilgili günler otomatik açılır. Takvim bloklarına tıklayarak ders düzenlenir. Dar pencerelerde kaydırma kullanılabilir.
- Seçili şubeye tekrar tıklamak dersi **programdan çıkarır**, ders kaydını veya bağlı ödev/sınavı silmez. Yeniden seçilince aynı ders kimliği kullanılır. Elle eklenen dersin formunda da “Haftalık programda göster” seçeneği vardır.
- Şema 2'ye isteğe bağlı `Course.planned` eklendi; eski dosyalarda eksikse `true` kabul edilir. `false` olan ders Bugün ekranına ve haftalık çizelgeye girmez; ödevleri görünmeye devam eder. İlk okuma dosyayı yeniden yazmaz.
- Yeni görünüm `CoursePlannerView` ve `CourseWeekView` içinde, JavaFX/CSS ile uygulanır. Kaynak HTML projesi ve Borç Defteri tasarımı değiştirilmedi. Müfredat/ÇAP, alternatif çözücü, çalışma planı ve LLM kapsamı genişletilmedi.
- `./gradlew :app:test :app:installDist --no-daemon` başarılı: **58 test, 0 hata, 0 atlanan**. Commit/push yapılmadı.
- Doğrulama: form/CSV/şube değiştirme, seçimi kaldırıp geri alma, kayıt hatasında görsel geri dönüş, çakışma filtresi, yan yana bloklar, hafta sonu görünümü ve eski veri uyumluluğu otomatik testlerle kontrol edilir. Görsel çıktılar: `app/build/reports/courses-planner-light.png`, `courses-planner-dark.png`, `courses-planner-conflicts.png`.

### 0.11 Kabul edilen gümüş arayüz tasarımı — 5 Ekim 2026

Kullanıcı önce örnek görsel, ardından sohbetten açılabilen küçük etkileşimli demo istedi. Demo beğenildi. Son talep: **“fontu daha şık bir font yaparsın şu an yapmana gerek yok. bunu hafızanda tut sonrasında işleyeceksin ana uygulamaya bu tasarımı.”**

- **Görsel yön:** Tamamen Apple esintili, profesyonel ve sade; ağırlıklı gümüş, inci beyazı, açık gri ve grafit. İnce çizgiler, ölçülü gölgeler, ferah boşluklar ve yuvarlatılmış yüzeyler. Renk vurgusu sınırlı.
- **Bilgi yoğunluğu:** Minimum yazıyla neyin nerede ve nasıl kullanılacağı anlaşılmalı. Uzun açıklamalar ve yoğun istatistik kartları ana ekrana eklenmemeli; ayrıntılar etkileşimle açılmalı.
- **Ana odak:** Bugünün akışı. Solda sade gezinme, ortada sıradaki etkinlik ve günlük zaman çizelgesi, sağda odak sayacı ve kısa görev listesi.
- **Etkileşim referansı:** Görevleri işaretleme, odak sayacını başlatma/duraklatma/sıfırlama, çalışma oturumlarını sürükleyerek taşıma, artı düğmesinden ekleme, ayrıntı pencereleri ve arama. Yumuşak geçişler, küçük hover/tıklama tepkileri ve animasyonları azaltma seçeneği.
- **Font:** Demo fontu nihai kabul edilmedi. Ana uygulamaya aktarımda daha şık, okunaklı ve Türkçe karakterleri destekleyen bir font seçilecek. Belirli font ailesi kararlaştırılmadı; şimdi font kurulumu veya değişikliği yapılmayacak.
- **Demo:** [Academic OS · Gümüş Tasarım Demosu](https://academic-os-silver-demo.oltuluoglumehmeteren.chatgpt.site). Sites ile ayrı, kullanıcıya özel bir demo olarak oluşturuldu; gerçek uygulama verileri kullanılmadı. Örnek kayıtlar tarayıcıda tutulur. Ana uygulamaya bağlanmadı.
- **Görsel referanslar:** [Masaüstü görünümü](notes/design-references/academic-os-silver-demo-desktop.png), [dar ekran görünümü](notes/design-references/academic-os-silver-demo-mobile.png). Kabul edilen tasarımı sonraki oturumlar için korur.
- **Demo kaynağı:** Sites proje kimliği `appgprj_6ac3dd314d04819199fd214307d02012`; yayınlanan kaynak commit'i `26c161a1d11a35b5cbac44bbf8ada6f3361e17dc`. Gerekirse Sites kaynak deposundan yeniden açılabilir. Geçici `/tmp/academic-os-silver-demo` klasörünün kalıcı olduğu varsayılmamalı.
- **Doğrulama:** Demo üzerinde görev, sayaç, oturum ekleme, yeniden zamanlama/çakışma, sürükleme, gezinme, arama, animasyon ayarı, sıfırlama ve dar ekranda taşma kontrolleri tarayıcıda geçti. Bunlar JavaFX uygulamasına yönelik testler değildir.
- **Sonraki oturum:** Kullanıcı ana uygulamaya aktarma işini başlattığında bu tasarım ve daha şık font tercihi esas alınacak. Mevcut dersler, ödevler, sınavlar ve kalıcılık korunmalı. Demo özellikleri ana uygulamada tamamlanmış sayılmamalı; özellikle çalışma oturumları ve odak sayacı için gerçek veri/yaşam döngüsü bağlantısı gerekir.
- **Bu oturumun sınırı:** Ana uygulama kodu ve mevcut demo değiştirilmedi. Yalnızca bu karar ve tasarım referansları kaydedildi. Java/JavaFX ile devam etme, LLM'yi erteleme ve commit/push yapmama kararları sürüyor.


---

## 1. Proje Özeti

**Berkay Academic OS**, JavaFX tabanlı, modüler, config odaklı bir masaüstü uygulaması olarak ilerliyor.

Ana amaç:
- tek bir masaüstü uygulaması içinde farklı modülleri çalıştırmak
- modülleri `config.json` üzerinden tanımlamak
- sidebar üzerinden modül açmak
- merkez alanda modül bazlı içerik göstermek
- dosya yönetimi, not alma, görev planlama, referans saklama, UART, takvim ve preview araçlarını aynı çatıya toplamak

Bu doküman:
- şu ana kadar yapılan işleri
- projedeki dosya mantığını
- hangi dosyada ne olduğunu
- hangi dosyaların hangi amaçla değiştirildiğini
- tamamlanan aşamaları
- kalan teknik borçları
- bundan sonra yapılacak işleri  
ayrıntılı şekilde toparlar.

---

# 2. Genel Mimari

Uygulama genel olarak şu akışta çalışıyor:

```text
App.java
 ├─ ConfigLoader.java → config.json yükler
 ├─ SidebarBuilder.java → soldaki modül butonlarını üretir
 ├─ ModuleActionHandler.java → modül tipine/id’sine göre yönlendirme yapar
 ├─ WebModuleView.java → dahili web + not ekranı
 ├─ FileExplorerView.java → Belge Merkezi
 │   ├─ FileOperationService.java → dosya işlemleri + drag-drop import
 │   ├─ PreviewService.java → preview karar mekanizması
 │   └─ PreviewOptions.java → preview config seçenekleri
 ├─ CalendarModuleView.java → Takvim + görev planı
 │   ├─ GoogleCalendarService.java → Google Calendar event servis katmanı
 │   └─ GoogleCalendarOAuthService.java → OAuth bağlantı katmanı
 ├─ ReferenceLibraryView.java → Referans kütüphanesi
 ├─ UartConsoleView.java → UART paneli
 │   └─ SerialPortService.java → seri port servis katmanı
 └─ ScriptModuleRunner.java → script çalıştırma
```

Temel prensip:
- `App.java` her şeyi tek başına yapmasın.
- Her modül kendi view/service sınıfına ayrılsın.
- Config üzerinden büyüyebilen yapı olsun.
- Veri gereken modüller dosya tabanlı kalıcılığa sahip olsun.
- UI işlemleri ile uzun süren disk/IO işlemleri ayrıştırılsın.
- Kullanıcı hatalarında uygulama çökmesin, durum mesajı üretsin.

---

# 3. Proje Klasör Yapısı (Mantıksal Şema)

```text
berkay-academic-os/
├─ app/
│  ├─ build.gradle
│  └─ src/
│     └─ main/
│        ├─ java/
│        │  └─ org/example/
│        │     ├─ App.java
│        │     ├─ AppConfig.java
│        │     ├─ ConfigLoader.java
│        │     ├─ SidebarBuilder.java
│        │     ├─ ModuleActionHandler.java
│        │     ├─ ModuleItem.java
│        │     ├─ AppSettings.java
│        │     ├─ UiSettings.java
│        │     ├─ StorageSettings.java
│        │     ├─ IntegrationSettings.java
│        │     ├─ ModuleFeatures.java
│        │     ├─ ModuleDataSources.java
│        │     ├─ WebModuleView.java
│        │     ├─ ScriptModuleRunner.java
│        │     ├─ FileExplorerView.java
│        │     ├─ FileOperationService.java
│        │     ├─ PreviewService.java
│        │     ├─ PreviewOptions.java
│        │     ├─ ExplorerItem.java
│        │     ├─ FavoriteLocation.java
│        │     ├─ FileExplorerOptions.java
│        │     ├─ UartConsoleView.java
│        │     ├─ SerialPortService.java
│        │     ├─ CalendarModuleView.java
│        │     ├─ TaskItem.java
│        │     ├─ TaskStorageService.java
│        │     ├─ CalendarEventItem.java
│        │     ├─ GoogleCalendarService.java
│        │     ├─ GoogleCalendarOAuthService.java
│        │     ├─ ReferenceLibraryView.java
│        │     └─ ReferenceLibrary ile ilgili iç yardımcı sınıflar
│        └─ resources/
│           └─ config.json
│
├─ data/
│  ├─ notes/
│  ├─ cache/
│  ├─ downloads/
│  ├─ tasks.json
│  └─ reference-library/
│     ├─ index.json
│     └─ topics/
│        ├─ elektronik/
│        ├─ matematik/
│        └─ embedded/
│
├─ config/
│  ├─ google-calendar-credentials.json
│  ├─ google-calendar-tokens/
│  ├─ google-drive-credentials.json
│  └─ google-drive-tokens/
│
└─ scripts/
   └─ test_script.py
```

Notlar:
- `config/` klasörü sistem tarafından hazır gelmez; proje kökünde manuel oluşturulabilir.
- `data/` içindeki dosyalar runtime sırasında oluşabilir.
- `reference-library/topics/` altına markdown içerikler yazılır.
- `tasks.json` görev planı kalıcılığı için kullanılır.
- Google Calendar tokenları `config/google-calendar-tokens/` altında tutulur.

---

# 4. Şu Ana Kadar Yapılanlar — Ayrıntılı

## 4.1 Uygulama iskeleti kuruldu

Yapılanlar:
- JavaFX ana pencere oluşturuldu.
- Üst başlık barı eklendi.
- Sol sidebar eklendi.
- Alt durum çubuğu eklendi.
- Merkez alan modül gösterme mantığı kuruldu.

Ana dosya:
- `app/src/main/java/org/example/App.java`

---

## 4.2 Config tabanlı mimari kuruldu

Yapılanlar:
- `config.json` dosyası resource olarak okunuyor.
- Uygulama ayarları config üzerinden geliyor.
- Modüller config içinden geliyor.

İlgili dosyalar:
- `AppConfig.java`
- `ConfigLoader.java`
- `App.java`

Önerilen teknik yaklaşım:
- Config büyüdükçe `FAIL_ON_UNKNOWN_PROPERTIES = false` yaklaşımı korunmalı.
- Yeni modül seçenekleri eklense bile eski config dosyaları uygulamayı düşürmemeli.

---

## 4.3 Sidebar üretimi ayrıştırıldı

Yapılanlar:
- Sidebar üretimi `App.java` içinden çıkarıldı.
- `SidebarBuilder` sınıfına taşındı.

Faydası:
- `App.java` sadeleşti.
- Modül butonlarıyla ilgili mantık ayrıldı.

İlgili dosya:
- `SidebarBuilder.java`

---

## 4.4 Modül yönlendirme ayrıştırıldı

Yapılanlar:
- Modül açma kararı `App.java` içinden çıkarılıp `ModuleActionHandler` içine taşındı.
- `internal`, `external_web`, `script`, `file_explorer`, `web` ayrımları netleşti.

İlgili dosya:
- `ModuleActionHandler.java`

Faydası:
- Modül açma mantığı merkezileşti.
- Yeni modül eklemek daha kolay hale geldi.

---

## 4.5 Web modülü + not alma sistemi çalışır hale geldi

Yapılanlar:
- Dahili web ekranı kuruldu.
- Not alanı ile beraber çalışacak şekilde bölünmüş ekran yapısı oluşturuldu.
- Modül bazlı not kaydetme eklendi.

İlgili dosyalar:
- `WebModuleView.java`
- `App.java`
- `FileOperationService.java`

Notların yolu:

```text
data/notes/<module_adi>.txt
```

---

## 4.6 Script modülü ayrıldı

Yapılanlar:
- Script çalıştırma mantığı `ScriptModuleRunner` içine taşındı.
- Config içinden interpreter ve hedef script çalıştırılabiliyor.

İlgili dosya:
- `ScriptModuleRunner.java`

Örnek kullanım:
- `scripts/test_script.py`

---

## 4.7 Belge Merkezi oluşturuldu

Yapılanlar:
- Klasör listeleme
- Klasör açma
- Metin dosyası açma
- Dosya kaydetme
- Klasör oluşturma
- Dosya oluşturma
- Yeniden adlandırma
- Silme
- Harici uygulamada açma
- Favoriler
- Preview davranışı

İlgili dosyalar:
- `FileExplorerView.java`
- `FileOperationService.java`
- `PreviewService.java`
- `PreviewOptions.java`
- `ExplorerItem.java`
- `FavoriteLocation.java`
- `FileExplorerOptions.java`

---

## 4.8 Belge Merkezi performans iyileştirildi

Yapılanlar:
- Klasör yükleme async mantığa taşındı.
- UI donmaları azaltıldı.
- Eski yükleme isteğinin yeni sonucu bozması engellendi.
- Seçim davranışları temizlendi.
- Çift tıkla açma mantığına geçildi.

Sonuç:
- Klasör gezme daha stabil oldu.
- Seçim listener kaynaklı çökme riski azaldı.

---

## 4.9 Recursive delete eklendi

Yapılanlar:
- Dolu klasörler de silinebilir hale getirildi.
- `Files.walk(...)` ile klasör içeriği tersten silinecek şekilde düzenlendi.

İlgili dosya:
- `FileOperationService.java`

---

## 4.10 Takvim modülü placeholder’dan çıkarıldı

Yapılanlar:
- Görev listesi
- Filtreleme
- Detay gösterimi
- Görev ekleme
- Görev silme
- Tamamlandı yapma

İlgili dosyalar:
- `CalendarModuleView.java`
- `TaskItem.java`

---

## 4.11 Takvim modülüne veri kalıcılığı eklendi

Yapılanlar:
- Görevler JSON’a yazılıyor.
- Açılışta görevler JSON’dan okunuyor.

İlgili dosya:
- `TaskStorageService.java`

Veri yolu:

```text
data/tasks.json
```

---

## 4.12 Referans modülü gerçek panele dönüştürüldü

Yapılanlar:
- Kategori listesi
- Konu listesi
- Detay/edit alanı
- Yeni konu ekleme
- Kaydetme
- Silme
- Diskten yeniden yükleme
- Veri dosyası + markdown içerik mantığı

İlgili dosya:
- `ReferenceLibraryView.java`

Mevcut veri şeması:

```text
data/reference-library/index.json
data/reference-library/topics/<kategori>/<konu>.md
```

---

## 4.13 UART modülü ilk iskelet olarak oluşturuldu

İlk durumda:
- Basit port tarama
- Port seçme
- Baud seçme
- Bağlan/kes butonları
- Log alanı
- Metin gönderme alanı vardı.

Bu modül Aşama D’de ürünleşmiş hale getirildi.

İlgili dosyalar:
- `UartConsoleView.java`
- `SerialPortService.java`

---

## 4.14 Preview config entegrasyonu başlatıldı

İlk durumda:
- `PreviewService` dosya uzantısına göre metin/görsel/harici/desteklenmeyen kararını veriyordu.
- `PreviewOptions` allowed/openExternally/maxSize gibi temel config alanlarını taşıyordu.

Bu modül Aşama E’de metadata, PDF davranışı ve daha net karar sistemiyle cilalandı.

İlgili dosyalar:
- `PreviewService.java`
- `PreviewOptions.java`
- `FileExplorerView.java`

---

## 4.15 Takvim modülünde sekmeli yapı kurgulandı

Yapılanlar:
- Üstte ay görünümü
- Altta görev planı yerine sekmeli yapı
- Takvim ve görev planı ayrı sekmeler
- Görev planı taşma sorununun azaltılması

Yeni mantık:
- **Takvim** sekmesi
- **Görev Planı** sekmesi

---

## 4.16 Google Calendar entegrasyonu denendi ve stabilize edildi

Aşamalar:
1. Önce ICS tabanlı kolay entegrasyon düşünüldü.
2. Sonra gerçek OAuth 2.0 masaüstü akışına geçildi.
3. Başlangıçta auth çalışmasının çökme riski yarattığı görüldü.
4. OAuth sadece butonla tetiklenecek yapıya çekildi.
5. `GoogleCalendarService` ile `GoogleCalendarOAuthService` ayrıştırıldı.

İlgili dosyalar:
- `CalendarEventItem.java`
- `GoogleCalendarService.java`
- `GoogleCalendarOAuthService.java`
- `CalendarModuleView.java`

Config / dosya yolu:

```text
config/google-calendar-credentials.json
config/google-calendar-tokens/
```

---

## 4.17 Takvimde çift tık davranışı düzeltildi

İstek:
- Takvimde güne tıklayınca anında görev planına geçmesin.
- **Çift tıklanınca** görev planı sekmesine geçsin.

Yapılan çözüm:
- Tek tıkta sadece seçili gün güncelleniyor.
- Çift tıkta görev planı sekmesine geçiliyor.
- Seçili gün filtresi uygulanıyor.
- Takvim rebuild’i ilk tıkta yapılmıyor.

---

## 4.18 Aşama A tamamlandı — Google Calendar stabilizasyonu

Aşama A kapsamında Google Calendar entegrasyonundaki çökme riski ve UI donma riski azaltıldı.

Tamamlananlar:
- Eski `urn:ietf:wg:oauth:2.0:oob` tabanlı manuel kod yapıştırma akışı kaldırıldı.
- `TextInputDialog` ile authorization code alma yaklaşımı terk edildi.
- OAuth akışı `LocalServerReceiver` tabanlı lokal callback yapısına taşındı.
- `GoogleCalendarService.connect()` işlemi JavaFX UI thread üzerinden çıkarılıp arka plan `Task` yapısına alındı.
- Google Calendar event çekme işlemi de arka planda çalışacak hale getirildi.
- Ay değiştirirken bağlantı yoksa yeniden OAuth tetiklenmemesi garanti altına alındı.
- Google Calendar event sonuçları ay bazlı cache ile saklanmaya başlandı.
- Hızlı ay geçişlerinde eski yükleme sonucunun yeni ay görünümünü bozmasını engellemek için sıra kontrolü eklendi.
- Bağlantı, yükleme ve hata durumları UI bilgi etiketinde daha okunur hale getirildi.

İlgili dosyalar:
- `CalendarModuleView.java`
- `GoogleCalendarService.java`
- `GoogleCalendarOAuthService.java`
- `CalendarEventItem.java`

Ek dependency ihtiyacı:

```gradle
implementation 'com.google.oauth-client:google-oauth-client-jetty:1.36.0'
```

---

## 4.19 Aşama B tamamlandı — Belge Merkezi yetki kontrolü

Aşama B kapsamında Belge Merkezi için `readOnly` ve işlem izinleri config/options katmanına bağlandı.

Tamamlananlar:
- `FileExplorerOptions` içine izin kararlarını merkezi veren yardımcı metotlar eklendi.
- `readOnly` aktifken dosya/klasör oluşturma, yeniden adlandırma, silme ve kaydetme işlemleri kapatıldı.
- Create/rename/delete butonlarının görünürlüğü ve kullanılabilirliği config izinlerine bağlandı.
- Metin editörü `readOnly` modda düzenlenemez hale getirildi.
- Kaydetme butonu `readOnly` modda gizlendi ve ayrıca guard kontrolü eklendi.
- `showPreviewPane: false` ise sağ önizleme/editor panelinin gizlenmesi sağlandı.
- `FileOperationService` içine izin kontrollü overload metotlar eklendi.
- Dosya/klasör adlarında `/`, `\`, `.`, `..` gibi riskli/geçersiz isimler engellendi.
- Kullanıcıya Belge Merkezi üstünde izin özeti gösterilecek hale getirildi.

İlgili dosyalar:
- `FileExplorerOptions.java`
- `FileExplorerView.java`
- `FileOperationService.java`
- `config.json`

Eklenen ana yardımcı metotlar:

```java
canCreateFolder()
canCreateFile()
canRename()
canDelete()
canEditFiles()
canDragDropImport()
permissionSummary()
```

---

## 4.20 Aşama C tamamlandı — Belge Merkezi Drag & Drop import

Aşama C kapsamında Belge Merkezi’ne dışarıdan dosya/klasör sürükleyip bırakma desteği eklendi.

Tamamlananlar:
- `FileExplorerView` içine JavaFX `drag-over`, `drag-entered`, `drag-exited` ve `drag-dropped` event akışları eklendi.
- Kullanıcıya görünür import alanı/izin etiketi eklendi.
- Dosya veya klasörler mevcut açık klasöre import edilecek hale getirildi.
- Import işlemi UI thread dışında `Task` ile arka planda çalışacak şekilde bağlandı.
- Import sırasında liste geçici olarak disable edilir ve durum çubuğunda “Import ediliyor...” mesajı gösterilir.
- Import tamamlandığında klasör listesi otomatik yenilenir.
- `readOnly` ve `allowDragDropImport` izinlerine tam uyum sağlandı.
- `FileOperationService` içine izin kontrollü `importPaths(...)` overload metodu eklendi.
- Klasör importu için recursive copy desteği eklendi.
- Aynı isimli dosya/klasör varsa otomatik çakışma çözümü eklendi: `dosya.txt`, `dosya (1).txt`, `dosya (2).txt`.
- Bir klasörün kendi içine veya alt klasörüne import edilmesi güvenlik nedeniyle engellendi.
- Import sonucu kullanıcıya dosya/klasör sayısı olarak gösterilir.

İlgili dosyalar:
- `FileExplorerView.java`
- `FileOperationService.java`
- `FileExplorerOptions.java`
- `config.json`

Test beklentisi:
- `allowDragDropImport: true` ve `readOnly: false` olduğunda dosya/klasör sürükle bırak çalışır.
- `readOnly: true` olduğunda import reddedilir.
- `allowDragDropImport: false` olduğunda import reddedilir.
- Aynı isimli dosyalar eski dosyanın üzerine yazılmaz, yeni adla kopyalanır.
- Büyük klasör importunda UI donmaz.
- Import sonrası liste otomatik yenilenir.

---

## 4.21 Aşama D tamamlandı — UART ürünleştirme

Aşama D kapsamında UART Konsolu iskelet seviyesinden günlük kullanıma daha uygun, daha stabil bir araca dönüştürüldü.

### `UartConsoleView.java` tarafında yapılanlar

- Port seçim alanı düzenlenebilir hale getirildi.
- Baud rate seçim alanı düzenlenebilir hale getirildi.
- Yaygın baud rate değerleri eklendi: `9600`, `19200`, `38400`, `57600`, `115200`, `230400`, `460800`, `921600`.
- Port tarama işlemi JavaFX UI thread dışına alındı.
- Bağlanma işlemi JavaFX UI thread dışına alındı.
- Bağlantı durumuna göre butonlar otomatik açılıp kapanır hale getirildi.
- Port bağlıyken port ve baud alanlarının değiştirilmesi engellendi.
- Gönderme alanı Enter tuşu ile de veri gönderebilir hale getirildi.
- Satır sonu seçimi eklendi: `Yok`, `LF`, `CR`, `CRLF`.
- Timestamp seçeneği eklendi.
- Auto-scroll seçeneği eklendi.
- Log temizleme butonu korundu.
- Log boyutu sınırlandı, çok uzun kullanımda `TextArea` aşırı büyümesi engellendi.
- TX / UART / SİSTEM / HATA ayrımı eklendi.

### `SerialPortService.java` tarafında yapılanlar

- Port keşfi `ProcessBuilder + ls` bağımlılığından çıkarıldı.
- `DirectoryStream` tabanlı glob port tarama yapısı kuruldu.
- Linux için şu port yolları desteklendi:
  - `/dev/ttyUSB*`
  - `/dev/ttyACM*`
  - `/dev/ttyS*`
  - `/dev/rfcomm*`
  - `/dev/serial/by-id/*`
- macOS benzeri cihazlar için şu yollar da taramaya dahil edildi:
  - `/dev/cu.*`
  - `/dev/tty.*`
- Port bağlanmadan önce varlık kontrolü yapıldı.
- Port bağlanmadan önce okuma/yazma izin kontrolü yapıldı.
- İzin hatasında Linux için `dialout` grubu uyarısı verildi.
- `stty` yapılandırması shell string yerine argüman listesiyle çalıştırıldı.
- Shell injection riski azaltıldı.
- `raw`, `cs8`, `-cstopb`, `-parenb`, `-ixon`, `-ixoff`, `-echo`, `min 0`, `time 1` ayarları eklendi.
- Veri okuma işlemi ayrı daemon reader thread üzerinde çalışır hale getirildi.
- Veri callback ve hata callback ayrıştırıldı.
- `sendBytes(byte[])` metodu eklendi.
- `getConnectedBaudRate()` metodu eklendi.
- `disconnect()` akışı daha kontrollü hale getirildi.

İlgili dosyalar:
- `UartConsoleView.java`
- `SerialPortService.java`

Test beklentisi:
- ESP32 / Arduino / STM32 USB bağlantısı takılıyken `Portları Tara` portları göstermeli.
- Doğru port ve baud seçilip `Bağlan` denince durum etiketi bağlı portu göstermeli.
- Mikrodenetleyiciden gelen UART verisi log paneline düşmeli.
- Metin gönderirken seçilen satır sonu tipi uygulanmalı.
- Linux izin hatasında `dialout` uyarısı anlamlı şekilde görünmeli.

Linux izin notu:

```bash
sudo usermod -aG dialout $USER
```

Bu komuttan sonra oturumu kapatıp yeniden açmak gerekir.

---

## 4.22 Aşama E tamamlandı — Preview polish

Aşama E kapsamında Belge Merkezi içindeki önizleme sistemi daha kontrollü, okunur ve config ile uyumlu hale getirildi.

### `PreviewOptions.java` tarafında yapılanlar

Yeni / iyileştirilen alanlar:
- `allowedExtensions`
- `openExternallyExtensions`
- `maxPreviewSizeBytes`
- `maxImagePreviewSizeBytes`
- `showFileMetadata`
- `previewTextFiles`
- `previewImageFiles`
- `openPdfExternally`

Önemli davranışlar:
- Uzantılar otomatik normalize edilir.
- `.txt` ve `txt` aynı kabul edilir.
- Boş `allowedExtensions` listesi, varsayılan desteklenen türlerin serbest olduğu anlamına gelir.
- `openExternallyExtensions` içine yazılan uzantılar dahili preview yerine harici uygulamaya yönlendirilir.

### `PreviewService.java` tarafında yapılanlar

Eklenenler:
- Merkezi `decide(...)` metodu.
- `PreviewDecision` sonucu.
- `PDF` preview modu.
- Metin ve görsel için ayrı boyut kontrolü.
- Metadata metni üreten `buildMetadataText(...)` metodu.
- Daha geniş metin uzantısı desteği.
- Daha açıklayıcı hata / karar mesajları.

Desteklenen ana modlar:
- `EDITABLE_TEXT`
- `IMAGE`
- `PDF`
- `EXTERNAL`
- `UNSUPPORTED`

### `FileExplorerView.java` tarafında yapılanlar

Eklenenler:
- Preview açılırken artık doğrudan `resolvePreviewMode(...)` yerine `decide(...)` kullanılır.
- Metin dosyalarında üstte metadata, altta editor gösterilir.
- Görsel dosyalarında metadata + scroll destekli görsel alanı gösterilir.
- PDF dosyalarında bilgi kartı ve harici açma butonu gösterilir.
- Desteklenmeyen dosyalarda dosya bilgisi ve neden önizlenemediği gösterilir.
- Harici açılacak dosyalarda bilgi kartı + harici açma butonu gösterilir.

İlgili dosyalar:
- `FileExplorerView.java`
- `PreviewOptions.java`
- `PreviewService.java`

Örnek config:

```json
"previewOptions": {
  "allowedExtensions": [".txt", ".md", ".json", ".csv", ".java", ".png", ".jpg", ".pdf"],
  "openExternallyExtensions": [".docx", ".xlsx", ".pptx"],
  "maxPreviewSizeBytes": 2097152,
  "maxImagePreviewSizeBytes": 10485760,
  "showFileMetadata": true,
  "previewTextFiles": true,
  "previewImageFiles": true,
  "openPdfExternally": true
}
```

Test beklentisi:
- `.txt`, `.md`, `.json`, `.java` gibi bir dosyaya çift tıklanınca sağ panelde metadata ve içerik görünmeli.
- `readOnly` açıksa editör düzenlenemez olmalı.
- `.png`, `.jpg`, `.jpeg`, `.webp` gibi görseller metadata + scroll alanıyla görünmeli.
- `.pdf` dosyasında bilgi kartı ve harici açma butonu çıkmalı.
- Büyük metin dosyası editöre yüklenmemeli; bilgi kartı gösterilmeli.
- `openExternallyExtensions` içine eklenen uzantılar dahili preview yerine harici açma akışına gitmeli.

---

# 5. Mevcut Önemli Dosyalar ve Ne İşe Yaradıkları

## 5.1 Ana uygulama

### `app/src/main/java/org/example/App.java`

Görevleri:
- Uygulamayı başlatır.
- Config yükler.
- Root layout kurar.
- Sidebar oluşturur.
- Modül açma akışını başlatır.
- Web/not, belge merkezi, takvim gibi ana view geçişlerini yönetir.

---

## 5.2 Config tarafı

### `app/src/main/java/org/example/AppConfig.java`
- Config’in kök modelidir.
- `app`, `ui`, `storage`, `integrations`, `modules` alanlarını taşır.

### `app/src/main/java/org/example/ConfigLoader.java`
- `resources/config.json` dosyasını yükler.
- JSON → Java model dönüşümünü yapar.
- Bilinmeyen alanları tolere edecek şekilde ayarlanması önerilir.

---

## 5.3 Modül yönlendirme

### `app/src/main/java/org/example/ModuleActionHandler.java`
- Internal modülleri açar.
- External web modüllerini dış tarayıcıda açar.
- Script modülünü çalıştırır.
- `file_explorer`, `calendar`, `reference`, `uart` gibi modülleri ilgili view sınıflarına yönlendirir.

---

## 5.4 Web modülü

### `app/src/main/java/org/example/WebModuleView.java`
- Dahili tarayıcı ve not panelini oluşturur.
- Modül bazlı not alanı ile web içeriğini birlikte gösterir.

---

## 5.5 Belge Merkezi

### `app/src/main/java/org/example/FileExplorerView.java`
Görevleri:
- Dosya/kapsam görünümü
- Klasör gezinme
- Preview paneli
- Dosya açma/kaydetme/işlem arayüzü
- `readOnly` ve işlem izinlerine göre buton/editor davranışı
- `showPreviewPane` ayarına göre sağ panel görünürlüğü
- Drag & Drop import UI akışı
- Preview metadata / bilgi kartı gösterimi

### `app/src/main/java/org/example/FileOperationService.java`
Görevleri:
- Dosya sistemi işlemleri
- İzin kontrollü dosya/klasör oluşturma, kaydetme, silme ve yeniden adlandırma overload metotları
- Riskli dosya/klasör adlarını engelleyen doğrulama
- Recursive delete
- Drag & Drop import için dosya/klasör kopyalama
- Aynı isim çakışmalarında otomatik yeni ad üretme

### `app/src/main/java/org/example/PreviewService.java`
Görevleri:
- Dosya preview tür kararları
- Metin/görsel/PDF/harici/desteklenmeyen kararları
- Boyut ve uzantı kontrolleri
- Metadata metni üretme
- `PreviewDecision` ile UI’a açıklamalı karar döndürme

### `app/src/main/java/org/example/PreviewOptions.java`
Görevleri:
- Preview ayarları
- İzin verilen uzantılar
- Harici açılacak uzantılar
- Metin ve görsel preview boyut limitleri
- Metadata gösterme ayarı
- PDF davranışı

### `app/src/main/java/org/example/FavoriteLocation.java`
- Favori yol yapısı.

### `app/src/main/java/org/example/ExplorerItem.java`
- Listede gösterilen dosya/klasör öğe modeli.

### `app/src/main/java/org/example/FileExplorerOptions.java`
Görevleri:
- Belge Merkezi varsayılan kök klasörü
- `readOnly` ve işlem izinleri
- create/rename/delete/edit/drag-drop için merkezi izin kararları
- Kullanıcıya gösterilecek izin özeti

---

## 5.6 Takvim / görev planı

### `app/src/main/java/org/example/CalendarModuleView.java`
- Aylık takvim görünümü
- Seçili gün etkinlik listesi
- Görev planı sekmesi
- Çift tık ile görev planına geçiş
- Görev CRUD akışları
- Google Calendar bağlantı ve event yükleme UI akışı

### `app/src/main/java/org/example/TaskItem.java`
- Görev veri modeli.

### `app/src/main/java/org/example/TaskStorageService.java`
- Görevlerin diskten okunması/yazılması.

### `app/src/main/java/org/example/CalendarEventItem.java`
- Google Calendar’dan gelen etkinliklerin UI modeli.

### `app/src/main/java/org/example/GoogleCalendarService.java`
- Google Calendar verilerini çeken servis.
- Bağlanma durumu.
- Hata mesajı.
- Event yükleme.
- Ay bazlı event cache yönetimi.
- Bağlantı yokken event yükleme denemesini güvenli şekilde boş döndürme.

### `app/src/main/java/org/example/GoogleCalendarOAuthService.java`
- OAuth yetkilendirme katmanı.
- `LocalServerReceiver` tabanlı masaüstü callback akışı.
- Tokenları `config/google-calendar-tokens/` altında saklama.

---

## 5.7 Referans modülü

### `app/src/main/java/org/example/ReferenceLibraryView.java`
- Kategori listesi
- Konu listesi
- İçerik editörü
- Markdown tabanlı veri saklama
- Metadata + markdown içerik ayrımı

Aşama F’de zenginleştirilecek ana dosya budur.

---

## 5.8 UART

### `app/src/main/java/org/example/UartConsoleView.java`
- UART panel UI’si
- Port tarama butonu
- Port/baud seçimi
- Bağlan/kes akışı
- Log paneli
- TX gönderme alanı
- Satır sonu seçimi
- Timestamp ve auto-scroll seçenekleri

### `app/src/main/java/org/example/SerialPortService.java`
- Port tarama
- Bağlanma
- `stty` yapılandırması
- Seri veri okuma thread’i
- Veri gönderme
- Bağlantı kapatma
- Hata callback yönetimi

---

# 6. Örnek `config.json` Yapısı

```json
{
  "app": {
    "name": "Berkay Academic OS",
    "version": "1.5.0",
    "theme": "light"
  },
  "ui": {
    "sidebarWidth": 220,
    "defaultStatus": "Hazır",
    "webNotesEnabledByDefault": true
  },
  "storage": {
    "notesDir": "data/notes",
    "cacheDir": "data/cache",
    "downloadsDir": "data/downloads"
  },
  "integrations": {
    "googleDrive": {
      "enabled": true,
      "credentialsPath": "config/google-drive-credentials.json",
      "tokensPath": "config/google-drive-tokens",
      "rootFolderName": "BerkayAcademicNotes"
    }
  },
  "modules": [
    {
      "id": "documents",
      "name": "Belge Merkezi",
      "category": "Verimlilik",
      "type": "file_explorer",
      "enabled": true,
      "icon": "folder",
      "message": "Belge merkezi açıldı",
      "explorerOptions": {
        "defaultRoot": "/home/berkay/GoogleDriveMount",
        "readOnly": false,
        "allowCreateFolder": true,
        "allowCreateFile": true,
        "allowRename": true,
        "allowDelete": true,
        "allowDragDropImport": true,
        "showPreviewPane": true,
        "showHiddenFiles": false
      },
      "previewOptions": {
        "allowedExtensions": [".txt", ".md", ".json", ".csv", ".java", ".png", ".jpg", ".jpeg", ".webp", ".pdf"],
        "openExternallyExtensions": [".docx", ".xlsx", ".pptx"],
        "maxPreviewSizeBytes": 2097152,
        "maxImagePreviewSizeBytes": 10485760,
        "showFileMetadata": true,
        "previewTextFiles": true,
        "previewImageFiles": true,
        "openPdfExternally": true
      }
    },
    {
      "id": "wikipedia",
      "name": "Wikipedia",
      "category": "Akademik",
      "type": "web",
      "enabled": true,
      "icon": "globe",
      "message": "Wikipedia açıldı",
      "target": "https://www.wikipedia.org"
    },
    {
      "id": "reference",
      "name": "Referans",
      "category": "Akademik",
      "type": "internal",
      "enabled": true,
      "icon": "book",
      "message": "Referans modülü açıldı"
    },
    {
      "id": "uart",
      "name": "UART Konsolu",
      "category": "Embedded",
      "type": "internal",
      "enabled": true,
      "icon": "serial",
      "message": "UART monitör modülü açıldı"
    },
    {
      "id": "calendar",
      "name": "Takvim",
      "category": "Planlama",
      "type": "internal",
      "enabled": true,
      "icon": "calendar",
      "message": "Takvim modülü açıldı"
    }
  ]
}
```

---

# 7. Runtime Veri Dosyaları

## 7.1 Notlar

```text
data/notes/
```

## 7.2 Görevler

```text
data/tasks.json
```

## 7.3 Referans kütüphanesi

```text
data/reference-library/index.json
data/reference-library/topics/<kategori>/<dosya>.md
```

## 7.4 Google Calendar tokenları

```text
config/google-calendar-tokens/
```

## 7.5 Google Calendar credential dosyası

```text
config/google-calendar-credentials.json
```

## 7.6 Google Drive credential/token dosyaları

```text
config/google-drive-credentials.json
config/google-drive-tokens/
```

---

# 8. Karşılaşılan Büyük Sorunlar ve Ne Yapıldı

## 8.1 Config yüklenemedi sorunu

Sebep:
- Config alanları ile Java model sınıfları birebir uyuşmayabiliyordu.

Çözüm:
- `ConfigLoader` tarafında bilinmeyen alanları tolere edecek yapı önerildi.
- Hata mesajını status ve center panelde görünür yapma fikri eklendi.

---

## 8.2 TaskStorageService sorunu

Sebep:
- `JavaTimeModule` bağımlılığı veya `LocalDate` serileştirme uyumsuzluğu.

Çözüm:
- Daha dayanıklı okuma/yazma yaklaşımı.
- Null parent kontrolleri.
- Bağımlılık hassasiyetini azaltan tasarım.

---

## 8.3 ReferenceLibrary derleme hataları

Sebep:
- `ReferenceTopic`, `ReferenceStorageService` gibi ayrı sınıflar referans veriliyordu ama projede bulunmuyordu.

Çözüm:
- Referans modülünü tek başına çalışacak gömülü yardımcı sınıflarla yazmak.
- Metadata + markdown mantığını tek view içinde toparlamak.

---

## 8.4 PreviewService uyumsuzluğu

Sebep:
- `FileExplorerView`, `PreviewService` içinde belirli metotları bekliyordu.
- Preview API’si zamanla değiştiği için view-service uyumu bozulabiliyordu.

Çözüm:
- Aşama E’de karar mekanizması `decide(...)` ve `PreviewDecision` ile netleştirildi.
- `EDITABLE_TEXT`, `IMAGE`, `PDF`, `EXTERNAL`, `UNSUPPORTED` modları standartlaştırıldı.

---

## 8.5 Google OAuth girişte çökme

Sebep olası:
- OAuth akışının başlangıçta/yanlış anda tetiklenmesi.
- Local receiver / browser açma katmanının stabil olmaması.
- Auth akışının UI ile çok sıkı bağlı olması.

Çözüm:
- OAuth başlangıçtan çıkarıldı.
- Buton ile tetiklenen bağlanma akışına geçildi.
- OAuth `LocalServerReceiver` tabanlı callback yapısına alındı.
- Bağlanma ve event çekme arka plan `Task` yapısına taşındı.

---

## 8.6 Takvimde çift tık algısının bozulması

Sebep:
- İlk tıkta `refreshCalendarView()` çağrılıp butonlar yeniden oluşturuluyordu.

Çözüm:
- Seçili gün güncellemesi ayrı tutuldu.
- Header update ayrı tutuldu.
- Çift tıkta görev planı sekmesine geçiş düzeltildi.

---

## 8.7 Drag & Drop import izin ve çakışma riski

Sebep:
- Drag-drop import doğrudan dosya sistemi yazma işlemi yaptığı için `readOnly`, izin ve isim çakışması riskleri vardı.

Çözüm:
- UI ve servis katmanında `canDragDropImport()` kontrolü yapıldı.
- Aynı isimlerde overwrite yerine otomatik yeni ad üretildi.
- Klasör kopyalama recursive hale getirildi.
- Klasörün kendi içine kopyalanması engellendi.

---

## 8.8 UART port erişim ve UI donma riski

Sebep:
- Port tarama/bağlanma IO ağırlıklı işlemlerdi.
- Linux’ta `/dev/ttyUSB*` erişimi için kullanıcı grubu izni gerekebiliyordu.

Çözüm:
- Port tarama ve bağlanma UI thread dışına taşındı.
- `SerialPortService` daha sağlam hata mesajları üretir hale getirildi.
- `dialout` grubu uyarısı eklendi.

---

# 9. Şu Anda Tamamlanmış Başlıklar

Tamamlanan veya işlevsel olanlar:
- Config tabanlı modül yapısı
- Sidebar
- Dahili web + not sistemi
- Script modülü
- Belge merkezi temel işlevleri
- Klasör gezinme ve async yükleme
- Recursive delete
- Takvim görev planı
- Görev kalıcılığı
- Referans modülü veri dosyası mantığı
- Takvim sekmeli yapı
- Çift tıkla görev planına geçiş mantığı
- Google Calendar için OAuth tabanlı iskelet
- Google Calendar OAuth/Event yükleme stabilizasyonu
- Belge Merkezi `readOnly` ve işlem izinleri
- Belge Merkezi izin kontrollü servis metotları
- Belge Merkezi Drag & Drop import
- UART port tarama / bağlanma / veri gönderme-alma ürünleşmesi
- Preview metadata / görsel / PDF / büyük dosya davranışları

---

# 10. Bilerek Ertelenen / Tam Bitmeyen Alanlar

## 10.1 Belge Merkezi readOnly / yetki kontrolü

Durum:
- **Aşama B kapsamında tamamlandı.**

Kalan küçük takip notu:
- İleride global kaydetme kısayolu eklenirse aynı izin kontrolü o akışa da bağlanmalı.

---

## 10.2 Drag & Drop Import

Durum:
- **Aşama C kapsamında tamamlandı.**

Kalan küçük takip notları:
- Çok büyük klasör importlarında progress bar eklenebilir.
- Import geçmişi/log dosyası tutulabilir.
- Import sırasında iptal butonu eklenebilir.

---

## 10.3 UART gerçek seri haberleşme ürünleşmesi

Durum:
- **Aşama D kapsamında tamamlandı.**

Kalan küçük takip notları:
- Log’u `.txt` dosyasına kaydetme
- Hex görüntüleme modu
- Hex veri gönderme modu
- Gelen veride filtreleme / arama
- Otomatik reconnect
- Windows desteği için ileride `jSerialComm` benzeri kütüphaneye geçme

---

## 10.4 Preview polish

Durum:
- **Aşama E kapsamında tamamlandı.**

Kalan küçük takip notları:
- PDF için gerçek dahili sayfa renderer
- Markdown için render edilmiş preview modu
- Metin dosyalarında arama
- Syntax highlighting
- Image zoom in / zoom out butonları
- Dosya metadata panelini ayrı katlanabilir bölüm yapmak

---

## 10.5 Google Calendar entegrasyonu olgunlaştırma

Durum:
- **Aşama A kapsamında stabilizasyon tamamlandı.**

Kalan geliştirme notları:
- Disconnect/reconnect butonu ileride eklenebilir.
- İleride readonly dışı event oluşturma/güncelleme desteği ayrı aşama olarak ele alınabilir.

---

## 10.6 Referans modülü zenginleştirme

Durum:
- **Ertelendi.** Güncel öncelik 0. bölümdeki elle planlama ve ortak ajanda; Aşama F ilk sürümün ön koşulu değil.

Yapılacaklar:
- Arama
- Etiket filtresi
- Kategori yönetimi
- İçerik içi arama
- Markdown preview
- Dış kaynak link alanı

---

# 11. Bundan Sonra Yapacağım İşler (Öncelik Sırasıyla)

> Aşağıdaki A–G sırası önceki yol haritasını belgeler. 30 Eylül 2026 itibarıyla güncel geliştirme sırası 0.4 bölümündedir; çalışmaya ara verilmiştir.

## Aşama A — Takvim / Google Calendar stabilizasyonu

Durum:
- **Tamamlandı.**

İlgili dosyalar:
- `CalendarModuleView.java`
- `GoogleCalendarService.java`
- `GoogleCalendarOAuthService.java`
- `CalendarEventItem.java`

---

## Aşama B — Belge Merkezi yetki kontrolü

Durum:
- **Tamamlandı.**

İlgili dosyalar:
- `FileExplorerView.java`
- `FileOperationService.java`
- `FileExplorerOptions.java`
- `PreviewService.java`
- `config.json`

---

## Aşama C — Drag & Drop import

Durum:
- **Tamamlandı.**

Tamamlananlar:
1. Dışarıdan dosya sürükle bırak
2. Dışarıdan klasör sürükle bırak
3. Hedef klasöre kopyalama
4. Klasör için recursive copy
5. Aynı isimli dosya/klasör için çakışma yönetimi
6. Hata uyarıları
7. `allowDragDropImport` ve `readOnly` izinlerine tam uyum
8. Import sonrası klasör listesini yenileme

İlgili dosyalar:
- `FileExplorerView.java`
- `FileOperationService.java`
- `FileExplorerOptions.java`

---

## Aşama D — UART ürünleştirme

Durum:
- **Tamamlandı.**

Tamamlananlar:
1. Gerçek port yönetimi
2. Baud rate ayarı
3. Bağlanma durumu
4. Veri log paneli
5. Gönderme/alma stabilizasyonu
6. Hata yönetimi
7. Async port tarama ve bağlanma
8. Satır sonu seçimi
9. Timestamp ve auto-scroll

İlgili dosyalar:
- `UartConsoleView.java`
- `SerialPortService.java`

---

## Aşama E — Preview polish

Durum:
- **Tamamlandı.**

Tamamlananlar:
1. Görsel preview iyileştirmesi
2. PDF davranışı netleştirme
3. Büyük dosya davranışları
4. Config’ten gelen preview kurallarına uyum
5. Metadata / bilgi paneli görünümü
6. Desteklenmeyen dosya türleri için açıklayıcı kart
7. Harici açma akışının iyileştirilmesi

İlgili dosyalar:
- `FileExplorerView.java`
- `PreviewService.java`
- `PreviewOptions.java`

---

## Aşama F — Referans modülü zenginleştirme

Durum:
- **Ertelendi; önceki yol haritasının Aşama F başlığı.**

Yapılacaklar:
1. Referans konularında arama
2. Etiket filtresi
3. Kategori oluşturma / yeniden adlandırma / silme
4. İçerik içi arama
5. Markdown preview
6. Dış kaynak link alanı
7. Konu metadata alanlarını zenginleştirme
8. Daha okunur split-pane arayüz

İlgili dosya:
- `ReferenceLibraryView.java`

---

## Aşama G — Ek ürünleştirme hattı

Aşama F sonrası düşünülebilecek işler:
- Global arama
- Ayarlar ekranı
- Tema / koyu mod — 30 Eylül 2026 itibarıyla tamamlandı
- Veritabanı veya index tabanlı arama
- Google Drive ile gerçek senkronizasyon
- Belge Merkezi import geçmişi
- UART log kaydetme
- Uygulama içi hata raporu paneli

---

# 12. En Kısa Haliyle Projenin Şu Anki Durumu

Şu anda proje:
- basit JavaFX denemesi olmaktan çıktı
- modüler çalışan bir masaüstü uygulama iskeleti haline geldi
- belge merkezi, takvim/görev planı, referans modülü, UART konsolu ve preview sistemi gerçek iş yapan araçlara dönüştü
- Google Calendar entegrasyonunda Aşama A stabilizasyonu tamamlandı
- Belge Merkezi readOnly/yetki kontrolünde Aşama B tamamlandı
- Belge Merkezi Drag & Drop import için Aşama C tamamlandı
- UART ürünleşmesi için Aşama D tamamlandı
- Preview polish için Aşama E tamamlandı
- sonraki geliştirme önerisi elle çalışma planı ve ortak ajandadır; kullanıcı şu anda geliştirmeye ara verdi

Önceki yol haritasından kalan ek geliştirmeler (ilk sürüm öncelikleri için 0.4 bölümüne bakın):
1. Referans modülü zenginleştirme
2. UART gelişmiş log/hex özellikleri
3. PDF için gerçek dahili renderer
4. Markdown preview
5. Google Calendar disconnect/reconnect ve ileride yazma desteği
6. Global ayarlar ve içerik araması (tema tamamlandı; mevcut modül araması içerik araması değildir)

---

# 13. Dosya Bazlı Hızlı Özet

## Çekirdek
- `App.java` → uygulama başlangıcı
- `ConfigLoader.java` → config yükleme
- `AppConfig.java` → config kök modeli

## Modül yönlendirme
- `SidebarBuilder.java`
- `ModuleActionHandler.java`

## Web
- `WebModuleView.java`

## Script
- `ScriptModuleRunner.java`

## Belge Merkezi
- `FileExplorerView.java`
- `FileOperationService.java`
- `PreviewService.java`
- `PreviewOptions.java`
- `ExplorerItem.java`
- `FavoriteLocation.java`
- `FileExplorerOptions.java`

## Takvim/Görev
- `CalendarModuleView.java`
- `TaskItem.java`
- `TaskStorageService.java`

## Google Calendar
- `CalendarEventItem.java`
- `GoogleCalendarService.java`
- `GoogleCalendarOAuthService.java`

## Referans
- `ReferenceLibraryView.java`

## UART
- `UartConsoleView.java`
- `SerialPortService.java`

---

# 14. Aşama C/D/E Dosya Yerleştirme Notları

Aşama C sonrası kullanılan dosyalar:
- `FileExplorerView_AşamaC.java` → daha sonra Aşama E ile güncellendiği için nihai dosya olarak kullanılmamalı.
- `FileOperationService_AşamaC.java` → `FileOperationService.java` olarak kullanılmalı.

Aşama D sonrası kullanılan dosyalar:
- `UartConsoleView_AsamaD.java` → `UartConsoleView.java` olarak kullanılmalı.
- `SerialPortService_AsamaD.java` → `SerialPortService.java` olarak kullanılmalı.

Aşama E sonrası kullanılan dosyalar:
- `FileExplorerView_AsamaE.java` → `FileExplorerView.java` olarak kullanılmalı.
- `PreviewOptions_AsamaE.java` → `PreviewOptions.java` olarak kullanılmalı.
- `PreviewService_AsamaE.java` → `PreviewService.java` olarak kullanılmalı.

Önemli not:
- Aşama E’de `FileExplorerView.java` yeniden güncellendiği için Belge Merkezi tarafında nihai view dosyası Aşama E sürümüdür.
- Aşama C’de eklenen drag-drop import davranışı Aşama E sürümünde korunmalıdır.
- `FileOperationService.java` için Aşama C sürümündeki import metotları korunmalıdır.
- UART için Aşama D sürümleri nihai UART dosyalarıdır.

---

# 15. Son Not

Bu doküman, şu ana kadar yapılan ve bundan sonra yapılacak işleri topluca teknik referans olarak tutmak için baştan düzenlendi.

30 Eylül 2026 itibarıyla geliştirmeye ara verildi. Devam edildiğinde önerilen ilk odak **elle çalışma planı ve ortak ajanda**; güncel kapsam ve kullanıcı kararları 0. bölümde tutulur.

Tamamlanan son ana aşamalar:
- **Aşama A:** Takvim + Google Calendar stabilizasyonu
- **Aşama B:** Belge Merkezi readOnly/yetki kontrolü
- **Aşama C:** Belge Merkezi Drag & Drop import
- **Aşama D:** UART ürünleştirme
- **Aşama E:** Preview polish

Bundan sonraki ana ürünleştirme önerisi:

**Elle planlama + ortak ajanda + görev ayrıntıları + hatırlatma + yedekleme/ayarlar + paketleme.** Referans/önizleme geliştirmeleri ve LLM sonraki aşamalardır. Qwen ile deneme şu anda başlatılmayacak.
