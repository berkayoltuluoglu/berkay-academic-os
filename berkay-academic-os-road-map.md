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

**Son durum: 7 Kasım 2026 — 1.5.0 haftalık planlayıcı, oturuma bağlı odak ve dış ajan CLI/öneri akışı eklendi.** Başlangıçtaki kaynak değişiklikleri ve kullanıcı verisi korundu. Son doğrulama **195 uygulama testi + 5 Linux kurulum testi başarılı**; Linux 1.5.0 paketi geçici profilde CLI, yedek/yeniden kurulum ve gerçek JavaFX pencere kontrolünden geçti. Kılavuzla temiz bağlamlı ajan denemesi başarılı. Ayrıntılar **0.20**, `docs/planner-delivery.md`, `AGENTS.md` ve `packaging/LINUX.md` içinde. Windows kabulü ve rapordaki sınırlar açık; commit/push kullanıcıdadır.

**1 Ekim 2026 inceleme güncellemesi:** Kullanıcı ders planlayıcı projesini `/home/berkay/projects/ders-planlayici` konumunda paylaştı. İlk inceleme bulguları 0.8 bölümünde, ardından yapılan ilk aktarım 0.9 bölümünde. LLM entegrasyonu yapılmadı.

**Önceki düzenleme kapsamı (A–E aşamaları):**
- Aşama C — Belge Merkezi Drag & Drop import eklendi.
- Aşama D — UART Konsolu ürünleştirildi.
- Aşama E — Preview polish tamamlandı.
- Yol haritası baştan düzenlendi ve eski “Aşama C sıradaki aktif aşama” bilgisi güncellendi.

---

## 0. Güncel Durum ve Sonraki Oturuma Devir — 12 Ekim 2026

### 0.1 Kullanıcının hedefi ve kesinleşen kararlar

- Finans, ders takibi, kişisel görevler ve iş planlarını aynı masaüstü uygulamasında birleştirmek; günlük kullanıma mümkün olduğunca erken geçmek.
- Java ve JavaFX ile devam etmek. C++ veya başka bir dile geçiş yapılmayacak.
- Önce kullanıcının her şeyi kendisinin planlayabildiği, LLM gerektirmeyen ilk sürümü tamamlamak. Daha sonra bu planlama işlemlerini bir asistana devretmek.
- Uzun vadeli hedef, ders programını ve son tarihleri okuyabilen, çalışma planı önerebilen, değişikliklere göre yeniden planlayabilen bir “Jarvis” asistanı. Bu yetenekler henüz mevcut değil.
- **LLM için son karar (7 Kasım 2026):** Uygulamanın içinde model, LLM istemcisi, model indirme veya AI API anahtarı olmayacak. Planlama dışarıdaki Codex/Claude Code benzeri ajanlara CLI ve kullanıcı onaylı öneri kutusuyla açılır. Önceki Qwen/yerel model kararı geçersizdir.
- Kullanıcının bilgisayarı: RTX 4050 Laptop, 16 GB RAM. Yerel model seçimi konuşuldu; gerçek cihazda hız, bellek ve Türkçe görev başarımı testi yapılmadı.
- **Güncel arayüz (9 Ekim):** 5 Ekim’de kabul edilen Apple esintili gümüş tasarım JavaFX’e aktarıldı. Font Poppins; bugünün akışı, sınırlı renk, sade gezinme ve hareketi azaltma tercihi esas alındı. Kabul geçmişi 0.11, uygulama ayrıntıları 0.12’de.
- Borç Defteri'nin yeni tasarımı şimdilik atlanacak. Burs takibi daha sonraki kapsamda.
- Commit mesajları **Conventional Commits** biçiminde olmalı (`feat:`, `fix:`, `docs:` vb.).
- **12 Ekim yeni özellik öncelikleri:** Kullanıcı son sıralamadaki ilk ve üçüncü başlıkları değiştirip finansı ilk, gelişmiş ders planlayıcıyı üçüncü istedi. İlk dokuz maddelik listenin numaralarıyla güncel sıra **7 → 6 → 3 → 8 → 9 → 5 → 4 → 2 → 1**. Güncel sıra ve kapsam 0.15 bölümünde; son öncelik yapay zekâ/Jarvis asistanıdır.
- **Ara verme geçmişi:** 30 Eylül'de yalnızca bağlam kaydedilerek duruldu. 1 Ekim'de kullanıcı ders planlayıcının ilk aktarım aşamasını açıkça istedi; bu aşama tamamlandı. Qwen kurulumu ve API bağlantısını erteleme kararı sürüyor.

### 0.2 Kodda mevcut olanlar

| Alan | Mevcut durum |
| --- | --- |
| Temel kararlılık | Script çalıştırma/iptal işlemleri arka planda; atomik dosya yazma, görev yedekleri ve web notlarında kayıt yaşam döngüsü korumaları var. |
| Tasarım | JavaFX kabuğu, sidebar, modül araması (`Ctrl+K`), açık/koyu tema ve belge/takvim/referans/UART/script/web ekranları yenilendi. AtlantaFX, Ikonli ve paketlenmiş fontlar kullanılıyor. |
| Bugün | Gümüş açılış ekranı; gerçek ders/çalışma zaman çizelgesi, sıradaki etkinlik, görev/ödev işaretleme, hızlı ekleme ve kalıcı odak sayacı. Yaklaşan teslim/ödeme ayrıntıları açılır bölümde; kaynak hataları görünür. |
| Dersler | Ders ekleme/düzenleme/silme; dönem tarihleri, öğretim elemanı, oturuma özel derslik, haftalık saatler, şube ve kredi/AKTS. CSV kataloğundan elle şube seçimi ve değişimi. |
| Ödev ve sınav | Derse bağlı kayıt oluşturma/düzenleme/silme, isteğe bağlı teslim tarihi ve saati, öncelik, tahmini/elle kaydedilen süre, çalışma oturumu bağlantısı ve tamamlanma durumu. |
| Haftalık program | Derslerin haftalık programına ek olarak Ajanda içinde ortak günlük/haftalık ders, çalışma, görev, Google etkinlikleri ve Classroom teslimleri görünümü var. Saat çakışmaları gösterilir. |
| Görev Planı | Sabit kimlik, teslim tarihi/saati, başlık/açıklama, durum, öncelik, tahmini/harcanan/kalan süre ve alt görevler. Atomik kayıt, yedek ve eski veri üzerinden yazmayı önleme korunur. |
| Hatırlatma ve odak | Süresi ayarlanabilen, göreve/ödeve bağlanabilen odak sayacı; teslim/oturum/odak bitiş bildirimleri. Desteklenen sistem tepsisinde pencere kapansa da işlem sürer. |
| Ayarlar ve yedek | Veri konumu, çalışma/bildirim/arka plan tercihleri, Google istemci seçimi ve bağlantı kaldırma; doğrulanan toplu ZIP dışa aktarma/geri yükleme. |
| Google entegrasyonları | Ana takvim hesabı görünür; etkinlik okuma/oluşturma/düzenleme/silme var. Classroom öğrenci dersleri, kendi ödevleri ve teslim durumu okunur; seçilen ders yerel Dersler modülüne aktarılır. |
| Borç Defteri | Burs bölümü hariç seçilen finans ekranları, Java üzerinden kalıcı kayıt ve içe/dışa aktarma. Arayüz paketlenmiş HTML/CSS/JS olarak WebView içinde; tamamı Java arayüzüne çevrilmedi. |
| Diğer modüller | Belge Merkezi, Referans, UART, scriptler ve web/not ekranları mevcut. Google Calendar okuma/yazma entegrasyonu 0.13 bölümünde. |

Bugün ekranı günlük akış ve elle planlama ekranıdır; otomatik çalışma planı oluşturmaz. Ekrandaki finans ödeme özeti mevcut davranışını korur; yeni işletim sistemi bildirimleri çalışma/teslim/odak kapsamındadır. Aboneliklerde ödeme durumu ayrıca takip edilmiyor. Google etkinlikleri son başarılı yerel kopyadan ortak ajandaya alınır; dış değişiklikler bağlı takvimde yenilemeyle güncellenir.

### 0.3 Temel dosyalar ve veri modeli

Java kaynakları `app/src/main/java/org/example/` altında:

- `UiTheme.java`: tema ve fontlar; kaynaklar `app/src/main/resources/ui/` altında.
- `TodayModuleView.java`, `DailyTimelineView.java`, `DailyAgendaService.java`: günlük akış ve kullanıcı işlemleri. `TodayService.java`: kayıtları değiştirmeyen teslim/ödeme özeti.
- `StudySession.java`, `StudyStorageService.java`: tarihli çalışma oturumları. `FocusTimerService.java`: kalıcı odak sayacı.
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
| Görevler | `data/tasks.json`; sabit kimlik, teslim saati/öncelik/süre/alt görevler, atomik kayıt, yedek, dosya kilidi ve eski veri üzerinden yazmayı önleme. |
| Çalışma oturumları | `data/study/sessions.json`; şema 1, atomik kayıt, yedek ve eski veri üzerinden yazmayı önleme. |
| Odak sayacı | `data/focus.properties`; kalan süre ve gerçek bitiş zamanı. |
| Tema | `data/ui.properties`; açık/koyu tercih kalıcı. |
| Tercihler | `data/preferences.json`; odak süresi, bildirim ve arka plan davranışı. |
| Odak çalışma geçmişi | `data/time/focus-log.json`; tamamlanan odak dönemleri hedef kimliğiyle, tekrar kaydı önlenerek tutulur. |
| Google yerel kopyaları | `data/calendar/` ve `data/classroom/catalog.json`; hesap bilgisi ve son başarılı okuma. |
| Hatırlatma geçmişi | `data/reminders/delivered.json`; aynı bildirimin yeniden başlatmada tekrarlanmasını önler. |

`CourseData.Work`, önceki alanlarına ek olarak `dueTime`, `estimatedMinutes`, `loggedMinutes`, `priority` taşır. Teslim tarihi olmayan Classroom işleri tarih uydurulmadan saklanır. `StudySession`, isteğe bağlı `taskId`/`workId` bağlantısı ve `actualMinutes` içerir. Eski kayıtlar varsayılanlarla okunur; sırf okuma nedeniyle yeniden yazılmaz.

Yeni planlama alanları eklenirken eski kayıtlar korunmalı ve gerektiğinde şema geçişi yapılmalı. Finans/ders/tema verileri Git dışında tutuluyor; tüm veri dosyalarının aynı politikaya sahip olduğu varsayılmamalı (`data/tasks.json` için mevcut Git istisnası var). Günlük kullanıma geçmeden kişisel veri konumu ve Git kapsamı gözden geçirilmeli.

### 0.4 İlk kullanılabilir sürüm için önerilen sıra

Kullanıcı ilk Windows paketinden önce bu ürün özelliklerinin tamamlanmasını istedi. 12 Ekim'deki uygulama durumu:

1. **Elle çalışma planını genişletme — kod tamamlandı:** Görev/ödev bağlantısı, gerçek çalışma süresi ve kalan süre hesabı eklendi. Teslim tarihi ile çalışma zamanı ayrı tutulur.
2. **Ortak günlük/haftalık ajanda — kod tamamlandı:** Dersler, çalışma oturumları, görev/ödev teslimleri, Google etkinlikleri ve Classroom teslimleri birlikte gösterilir; çakışmalar belirtilir.
3. **Görev ayrıntıları — kod tamamlandı:** Teslim saati, tahmini/harcanan/kalan süre, öncelik ve alt görevler eklendi.
4. **Hatırlatmalar — kod tamamlandı:** Kullanıcı pencere kapanınca arka planda sürmesini seçti. Sistem tepsisi destekleniyorsa süreç açık kalır; tamamen çıkış veya bilgisayarın kapanması bildirimi durdurur. Windows üzerinde canlı doğrulama bekler.
5. **Ayarlar ve toplu yedekleme — kod tamamlandı:** Veri konumu, çalışma/bildirim tercihleri ve doğrulanan ZIP yedekleme/geri yükleme eklendi.
6. **Paketleme — EXE üretimi mevcut:** Java dahil, terminal gerektirmeyen Windows x64 kurulum ve sabit kullanıcı profili hazırlandı. Gerçek Windows kurulum/açılış/güncelleme/kaldırma test akışı depoda; bu makine Linux. Push işlemleri kullanıcıya bırakıldı.

Kullanıcının ek kararıyla Classroom ilk sürüme dahil edildi. Dağıtımda mevcut Google Desktop OAuth istemcisi kullanılır; her kullanıcı kendi hesabıyla izin verir. Kişisel token ve veriler paketlenmez.

Referans modülü geliştirmeleri, gelişmiş PDF/Markdown önizleme, UART ek özellikleri, burs takibi, animasyonlar ve LLM ilk sürümün ön koşulu değil. Tema/koyu mod artık tamamlanan işler arasında; içerikte arayan global arama ve ayarlar ekranı bununla karıştırılmamalı.

### 0.5 Kullanıcıdan gelecek tasarım ve ders projesi

- Mevcut Borç Defteri'nin kaynak örneği: `/home/berkay/İndirilenler/takip-paneli.html`.
- Genel tasarım referansı: `/home/berkay/İndirilenler/akademik-os-tasarim` klasörü. Bu referans incelenerek diğer modüllerin tasarımı uygulandı.
- Kullanıcı ders planlayıcı HTML/JS projesini **1 Ekim 2026'da `/home/berkay/projects/ders-planlayici` konumunda paylaştı**. Kaynak incelendi; ilk aşama JavaFX'e aktarıldı. İnceleme 0.8, uygulama kapsamı 0.9 bölümünde.
- Borç Defteri için yeni tasarım bekleniyor; önceki tasarımı yeniden yapmak veya Java'ya taşımak için verilmiş güncel bir uygulama talimatı yok.
- Yukarıdaki dosyalar depo dışındaki referanslardır; başka ortamda mevcut oldukları varsayılmamalı.
- **5 Ekim'de kabul edilen yeni genel arayüz referansı:** Apple esintili gümüş demo; bağlantı, görseller ve sonraki uygulama talebi 0.11'de. Borç Defteri'ne özel tasarımın ertelenmesi ayrı bir karardır.

### 0.6 Eski LLM tasarım notları — 7 Kasım 2026 kararıyla geçersiz

Bu alt bölüm tarihsel bağlamdır. Güncel dış ajan kararı 0.1 ve 0.20 içindedir; aşağıdaki Qwen entegrasyonu uygulanmayacak.

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
- Önceki modüller ve tasarım referansları `a49137b` / `0b28e92` commit’lerine alınmış. 9 Ekim gümüş arayüz aktarımı çalışma ağacında; bu oturumda commit/push yapılmadı. Sonraki oturumda önce `git status` kontrol edilmeli.
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

### 0.12 Gümüş tasarım ana uygulamaya aktarıldı — 9 Ekim 2026

Kullanıcı gümüş arayüzü ana koda entegre etmeyi istedi. Çalışma başlangıcında Git ağacı temizdi; mevcut modüller ve 5 Ekim tasarım referansları commit edilmişti.

- **Native JavaFX:** Açık gümüş/grafit kabuk, sade üst çubuk, Bugün/Ajanda/Dersler/Görevler/Borç Defteri gezinmesi. Diğer araçlar ve web modülleri açılabilir gruplarda; modül araması bunları da bulur. Ders planlayıcı ve diğer native ekranlar ortak gümüş renklerden beslenir. Borç Defteri’nin kendi HTML tasarımı korunur.
- **Font:** Poppins Regular/Medium/SemiBold/Bold uygulama kaynaklarına paketlendi. Çalışma sırasında font indirme veya sistem kurulumu gerekmez. Monospace alanlar IBM Plex Mono kullanmaya devam eder. [Poppins lisans kaynağı](https://github.com/google/fonts/blob/main/ofl/poppins/OFL.txt); dağıtılan lisans `app/src/main/resources/ui/fonts/Poppins-OFL.txt` içinde.
- **Bugün:** Gerçek dersler ve çalışma oturumları saatlere orantılı çizelgede; sıradaki/halen süren etkinlik, gerçek saat çizgisi, kısa görev listesi ve açılır teslim/ödeme ayrıntıları. Örnek kayıtlar kullanıcı verisine eklenmez. Dar pencerede sağ sütun akışın altına geçer.
- **Etkileşim:** Görev ve ödev/sınav doğrudan işaretlenebilir; artı menüsünden görev veya çalışma oturumu eklenir. Etkinlik ve görev ayrıntıları açılır; dersler kendi modülünde düzenlenir. Kayıt başarısız olursa arayüz son başarılı duruma döner.
- **Çalışma oturumları:** Başlık, tarih, başlangıç/bitiş, isteğe bağlı ders bağlantısı ve tamamlanma durumu. Düzenleme, silme ve 15 dakikaya yuvarlanan sürükleyerek taşıma var. Tamamlanmamış oturumlar ders veya başka bir açık çalışma oturumuyla çakışıyorsa işlem reddedilir; mevcut ders çakışmaları çizelgede gösterilir. Gece yarısını geçen oturum desteklenmez.
- **Ajanda:** Tarih seçimi ve önceki/sonraki gün gezinmesiyle günlük ders/çalışma görünümü. Google Calendar eski Takvim aracında durur; bu günlük çizelgeye veya ortak haftalık görünüme birleştirilmedi.
- **Odak:** 25 dakikalık başlat/duraklat/sıfırla sayacı. Gerçek bitiş zamanı dosyada tutulduğundan modül değiştirmek, sistemin uyuması veya uygulamanın kapanıp açılması süreyi dondurmaz. Duraklatılan sayaç duraklatılmış açılır. İşletim sistemi bildirimi veya otomatik görev tamamlama yok.
- **Görünüm tercihleri:** Koyu tema ve hareketi azaltma aynı `data/ui.properties` dosyasında korunur. Modül geçişlerinde kısa fade; hareketi azaltma seçilince geçiş kaldırılır. Ders/CSV pencereleri de ortak temayı kullanır.
- **Veri koruma:** `TaskItem.id` eklendi. Kimliksiz eski görevler ilk okumada dosya değiştirilmeden deterministik kimlik alır; ilk başarılı kayıtta eski içerik `.bak` olarak korunur. Takvimde düzenleme/tamamlama kimliği değiştirmez. Görev yazımlarına dosya kilidi ve başka yerde değişen veriyi ezmeme kontrolü eklendi. Çalışma oturumları `data/study/sessions.json` şema 1’de, sayaç `data/focus.properties` içinde; ikisi Git dışında. Ders ve finans şemaları değişmedi.
- **Yeni kaynaklar:** `DailyAgendaService`, `DailyTimelineView`, `StudySession`, `StudyStorageService`, `FocusTimerService`; `TodayModuleView` yenilendi. Başlangıç, yönlendirme, sidebar, tema, görev depolama ve kaynak CSS/config güncellendi. `App` testlerinde geçici veri kökü kullanılarak gerçek kişisel kayıtlar ayrıldı.
- **Doğrulama:** İlk `./gradlew :app:test :app:installDist --offline --no-daemon` başarılı: **69 test, 0 başarısızlık, 0 hata, 0 atlanan**. Eski görev kimliklerinin/ham yedeğinin korunması, yinelenen başlıklardan doğru görevin seçilmesi, ödev ilişkisinin korunması, çalışma çakışmaları ve bitişik saatler, başarısız yedek/eski veri koruması, sayaç yeniden açma/duraklatma ve gerçek JavaFX hızlı ekleme/işaretleme/tarih gezinmesi test edildi.
- **Doğrulama sınırı:** Bu başarılı koşudan sonra yan menü grupları katlanabilir hale getirildi, güncel saat çizelge aralığına alındı, ders/CSV pencere teması ortak yardımcıya geçirildi ve ekran görüntüsü bekleme koşulu düzeltildi. İkinci Gradle çalıştırması kullanıcı tarafından reddedildi; bu son düzenlemeler yeniden derlenip test edilmedi. Sürükleme hesapları/çakışma modeli test edildi, fiziksel sürükleme hareketi otomatik GUI testinde yapılmadı. `app/build/reports/silver-home-light.png`, `silver-home-dark.png`, `silver-home-compact.png` ilk koşuya ait ara görüntülerdir; en son katlanabilir menüyü göstermez. Dağıtım çıktısı da ilk başarılı koşuya aittir.
- **Sonraki kontrol:** İzin verildiğinde son kaynakla test/dağıtım komutu yeniden çalıştırılmalı, katlanabilir gruplar ve ders/CSV pencereleri açık-koyu modda kontrol edilmeli. Ortak haftalık ajanda, doğrudan görev/ödev çalışma bağlantısı, süre takibi, hatırlatmalar, toplu yedekleme ve paketleme kalan kapsamdır. Qwen/LLM entegrasyonu yapılmadı. Commit/push yapılmadı.

---

### 0.13 Google Calendar iki yönlü etkinlik işlemleri — 11 Ekim 2026

- **Ekran:** Takvim aracında bağlı ana takvim hesabı görünür. `Etkinlik ekle`, seçili etkinliği `Düzenle` ve onayla `Sil` işlemleri Google'a doğrudan gönderilir. Başlık, açıklama, konum, başlangıç/bitiş tarihleri, saatler ve tüm gün seçeneği vardır. Çok günlük ve gece yarısını geçen Google etkinlikleri kapsadıkları günlerde gösterilir.
- **Senkronizasyon:** Google'daki değişiklikler `Yenile` veya ay gezinmesiyle alınır. Yazım başarılı olunca Google yanıtı gösterilir ve ay yeniden okunur. Okuma hatasında son doğrulanmış etkinlikler korunur. Yerel görevler otomatik olarak Google'a aktarılmaz; çevrimdışı yazım kuyruğu veya anlık Google push senkronizasyonu yoktur.
- **Veri koruma:** Düzenleme yalnızca form alanlarını PATCH ile değiştirir; davetliler, hatırlatmalar, tekrar kuralı ve Meet verileri korunur. Düzenleme/silmede `If-Match`/ETag kontrolü başka yerde değişmiş kaydı ezmeyi engeller. Tekrarlı etkinlikte seçili tekrar değişir. Özel/kilitli Google etkinlikleri yalnızca görüntülenir. Kayıt hatasında form ve girilen bilgiler korunur; silme hatasında etkinlik listeden kaldırılmaz. Oluşturma tekrarlarında sabit etkinlik kimliği kullanılır.
- **İzin geçişi:** `calendar.events.owned` ve `calendar.calendars.readonly` istenir. Eski okuma tokenları silinmez; yeni düzenleme tokenları `config/google-calendar-tokens/events-write-v1/` altına yazılır. İlk bağlantıda mevcut Google hesabıyla iki izin de onaylanmalıdır. OAuth yerel, boş bir portta callback ve PKCE kullanır; eksik izin kaydedilmez, `invalid_grant` sonrasında yeni yetkilendirme başlar.
- **Kaynaklar:** `CalendarEventDraft`, `GoogleCalendarEventDialog`; mevcut Calendar servis, OAuth, etkinlik modeli ve modül görünümü genişletildi. Google tokenları/credentials Git dışında tutulur.
- **Doğrulama:** `./gradlew :app:test :app:installDist --offline --no-daemon` ile 91 test geçti. Mock Google HTTP istekleriyle oluşturma/PATCH/silme, ETag uyuşmazlığı, tarih/saat dönüşümleri, yeniden denemede kimlik koruma ve OAuth izin geçişi doğrulandı. Gerçek JavaFX formunda hata sonrası tekrar kayıt, düzenleme, silme hatasında kaydı koruma ve yenileme sınandı. Bu bölüm, 0.12'deki son kaynakların tekrar test edilmemiş olması notunu günceller.
- **Canlı hesap sınırı:** Önceki okuma bağlantısı mevcut hesapla doğrulandı. Yeni yazma izni kullanıcının Google onayını gerektirir; yeni izinle gerçek hesaba etkinlik yazımı henüz doğrulanmadı. Classroom entegrasyonu yapılmadı.

---

### 0.14 İlk sürüm özellikleri ve Windows kurulumu — 12 Ekim 2026

Kullanıcı notlardaki ilk sürüm özelliklerini Windows paketinden önce tamamlamayı, hatırlatmaların pencere kapanınca arka planda sürmesini ve Classroom ders/ödev okumasının ilk sürüme alınmasını seçti. Dağıtım için mevcut Google Desktop OAuth istemcisinin kullanılması onaylandı. Son karar: **push işlemleri kullanıcıda; bu oturumda commit/push yapılmadı.**

- **Planlama ve görevler:** Görev teslim saati, öncelik, tahmini/elle kaydedilen çalışma süresi ve alt görevler; ödev/sınavda teslim saati/öncelik/süre. Çalışma oturumları göreve veya ödeve bağlanabilir; gerçek süre kaydedilir. Odak sayacı süre ve hedef seçimine izin verir; tamamlanan süre bir kez kaydedilir. Aynı tamamlanan çalışma oturumunun içindeki odak kaydı süre hesabında iki kez sayılmaz.
- **Ortak ajanda:** Günlük ve haftalık görünüm dersleri, çalışma oturumlarını, görev/ödev teslimlerini, Google etkinliklerini ve Classroom teslimlerini birleştirir. Google'nın son başarılı ay okumaları hesapla ilişkilendirilmiş yerel kopyada tutulur; yenileme dış değişiklikleri alır. Çevrimdışı Google yazımı veya Google push bildirimi yoktur. İki aya yayılan haftada bir ayın okuma hatası diğer ayın başarısıyla gizlenmez.
- **Classroom:** Ayrı ve salt okuma OAuth izni; aktif öğrenci dersleri, kendi ödevleri ve teslim durumu, tüm sayfalar, UTC teslim saatini yerel saate çevirme, tarihsiz ödevi tarih uydurmadan koruma. Seçilen ders ve ödevler yerel Dersler modülüne tekrar oluşturmadan aktarılır; sonraki senkronizasyon elle değiştirilen başlık/tarih/süre/önceliği korur. Aktarım gerçek ders saatlerini kendiliğinden üretmez, Classroom'a teslim veya not yazmaz. Google'nın eşdeğer `classroom.student-submissions.me.readonly` izin yanıtı desteklenir.
- **Hatırlatmalar:** Teslim, çalışma/Google etkinliği başlangıcı ve odak bitişi; önceden bildirim süresi ve saatsiz teslimlerin saati ayarlanır. Kalıcı bildirim geçmişi yeniden açmada tekrarı önler. Sistem tepsisi destekleniyorsa pencerenin çarpısı yalnızca gizler; tepsi menüsündeki tam çıkış uygulamayı kapatır. Tepsi desteklenmiyorsa açık pencere bildirimi kullanılır; bu durumda pencere kapandıktan sonra sürmez. Windows oturum açılışında otomatik başlama veya tamamen kapalı uygulamayı uyandırma eklenmedi.
- **Ayarlar ve veri:** Odak/bildirim/arka plan tercihleri, profil konumu değiştirme, Google istemci dosyası seçme ve iki Google bağlantısını yerelden kaldırma. Profil taşıma ve geri yükleme yeniden başlatma gerektirir. Windows kurulumunda veri `%LOCALAPPDATA%\\BerkayAcademicOS` altında; program klasörünün dışında kalır. Paket yapılandırması geliştiricinin Linux belge yollarını kullanıcı profiline uyarlar. Linux'a bağlı Script/UART modülleri Windows varsayılan menüsünde kapalıdır; geliştirici sürümünde korunur.
- **Toplu yedek:** Veri/notlar ve uygulama yapılandırması SHA-256 doğrulamalı ZIP'e alınır. Google OAuth giriş tokenları ve istemci dosyaları yedek ZIP'ine dahil edilmez; taşınan profilde korunur. Arşiv yolları/boyutları/şemaları/kilitleri doğrulanır; başarılı geri yüklemeden önce mevcut kayıtların ayrıca yedeği alınır. Yazım hatasında geri alma ve korunan önceki yedek vardır.
- **Kurulum:** `packaging/build_windows.py` Windows x64 JavaFX kütüphaneleri ve sağlama toplamı doğrulanan Windows Java 21 JRE ile NSIS EXE üretir. Başlatıcı konsol açmaz; Java'yı ayrıca kurmak gerekmez. Türkçe sihirbaz, kullanıcıya özel kurulum, masaüstü/Başlat kısayolları, kaldırıcı ve sürüm bilgisi vardır. Güncellemede eski sürüm JAR/runtime dosyaları temizlenir; kullanıcı profili programdan ayrı korunur. Mevcut Desktop OAuth istemcisi pakettedir; her kullanıcı kendi hesabına giriş yapar. Kişisel erişim/yenileme tokenları, dersler, finans, görevler ve notlar pakete alınmaz.
- **Çıktı:** `app/build/windows/dist/BerkayAcademicOS-1.3.0-setup.exe` (yaklaşık 77 MB), yanında `.sha256`. Kullanım/yeniden üretim ve Windows doğrulama komutları `packaging/README.md` içinde.
- **Doğrulama:** Ürün koduyla `./gradlew :app:test :app:installDist --offline --no-daemon`: **124 test, 0 başarısızlık, 0 hata, 0 atlanan**. Eski veri uyumluluğu, süre/odak tekrar kaydı, ZIP doğrulama/geri alma, hatırlatma saati/gece yarısı/tekrar önleme, Classroom sayfalama/aktarım/elle değişiklikleri koruma ve gerçek JavaFX görev/haftalık ajanda/ayarlar akışları kapsandı. Son test başlatıcısı ayrıca derlenip boş profille normal boyutta gerçek JavaFX penceresi çizilerek doğrulandı. Görüntü `app/build/reports/academic-os-release-1.3.0.png`. Uygulama mevcut kişisel veriyle de açıldı.
- **Canlı Google:** `oltuluoglumehmeteren@gmail.com` hesabında geçici etkinlik oluşturma, düzenleme, Google'dan yeniden okuma ve silme başarılı; test etkinliği kaldırıldı. Classroom izin adlandırması düzeltildikten ve kullanıcı `berkay-academic-os` projesinde API'yi etkinleştirdikten sonra aynı hesaptan **18 ders, 36 ödev** başarıyla okundu. Gerçek dersler otomatik olarak yerel programa topluca aktarılmadı; seçim kullanıcıda.
- **Windows doğrulama sınırı:** Bu bilgisayarda Linux kullanılıyor. EXE üretildi, ancak gerçek Windows üzerinde kurulum/çalıştırma/güncelleme/kaldırma ve sistem tepsisi bildirimleri henüz denenmedi. `.github/workflows/windows-package.yml` ve `packaging/verify_windows.ps1` bu akışı otomatik yapmaya hazır. Push kullanıcıya bırakıldığı için Windows CI çalıştırılmadı. Dijital kod imzalama ve otomatik güncelleme henüz eklenmedi.
- **Sonraki kapsam:** Windows kabul testi; dağıtılacak hesaplar için Google OAuth uygulama kitlesi/yayın durumunu doğrulama. Qwen/LLM, ses, müfredat/ÇAP ve alternatif program çözücüsü, ileri PDF/Markdown önizleme, içerik araması, referans filtreleri, ileri UART/Script Windows uyarlaması ve burs takip ekranı önceki erteleme kararlarına göre sonraki sürümlerde.

---

### 0.15 Kullanıcının belirlediği sonraki özellik sırası — 12 Ekim 2026

Kullanıcı kalan dokuz özellik grubunu önce **3, 6, 7, 8, 9, 5, 4, 2, 1** olarak sıraladı. Ardından ilk ve üçüncü öncelikleri değiştirerek **"Finans ilk, gelişmiş ders planlayıcı üçüncü olsun"** diye netleştirdi. İlk listenin numaralarıyla güncel sıra **7, 6, 3, 8, 9, 5, 4, 2, 1**. Önceki numaralar konuşmadaki ilk listeyi belirtir; bundan sonraki yeni özellik geliştirme sırası aşağıdaki tablodur. 13 Ekim 2026’da kullanıcının ilk üç grubu sırasıyla uygulama isteği tamamlandı; diğer altı grup sonraki kapsamdadır.

| Öncelik | Önceki liste no. | Özellik | Planlanan kapsam |
| --- | --- | --- | --- |
| 1 | 7 | Finans | Burs takip ekranı ve ertelenen Borç Defteri tasarım yenilemesi. Tasarım uygulama aşamasında mevcut kullanıcı referansı/tercihi esas alınacak. |
| 2 | 6 | Google senkronizasyonu | Drive ile gerçek dosya senkronizasyonu; Calendar'da anlık değişiklik alma ve çevrimdışı yazımları bağlantı gelince gönderme. |
| 3 | 3 | Gelişmiş ders planlayıcı | Müfredat PDF'sini içe aktarma, ÇAP/iki bölüm, ders geçmişi ve eşdeğerlikler, tercihlere göre alternatif ders programları. |
| 4 | 8 | UART ve scriptler | UART loglarını dosyaya kaydetme, gelişmiş hex araçları ve Windows uyarlaması. |
| 5 | 9 | Bakım araçları | Uygulama içi hata raporu paneli. |
| 6 | 5 | Belge araçları ve içerik araması | Uygulama içinde PDF görüntüleme, Markdown önizleme, dosya içe aktarma geçmişi ve tüm içeriklerde arama. |
| 7 | 4 | Referans kütüphanesi | Konu/içerik araması, etiket filtreleri, kategori yönetimi, Markdown önizleme ve kaynak bağlantıları. |
| 8 | 2 | Sesli kullanım | Konuşarak komut verme ve sesli cevap alma. |
| 9 | 1 | Yapay zekâ/Jarvis asistanı | Doğal dille görev işlemleri, son tarihlere göre çalışma planı önerme ve değişikliklerde yeniden planlama. İlk deneme adayı Qwen; kesin model/sağlayıcı seçilmedi. |

**İlk üç öncelik 1.4.0’da tamamlandı:** Finans/burs, Google senkronizasyonu ve gelişmiş ders planlayıcı. Sıradaki özellik UART/scriptlerdir. Ders planlayıcı kaynağı ve önceki aktarım notları 0.8–0.10 bölümlerinde; uygulanan kapsam ve sınırlar 0.16 bölümündedir.

Dağıtımın bekleyen Windows kabul testi ve Google OAuth uygulama kitlesi doğrulaması 0.14'teki durumunu korur. Kod imzalama ve otomatik güncelleme dağıtım kapsamındaki açık işlerdir. Push işlemleri kullanıcıya bırakılmaya devam eder.

---

### 0.16 İlk üç önceliğin uygulanması — 13 Ekim 2026

Kullanıcı **finans → Google senkronizasyonu → gelişmiş ders planlayıcı** sırasıyla üç aşamanın gerçekleştirilmesini istedi. Push işlemleri kullanıcıda kalır.

- **1. Finans tamamlandı:** Finans ekranı gümüş/Poppins tasarıma ve açık/koyu temaya uyarlandı. Burs, staj ve diğer başvurular için ayrı native sekme eklendi; kurum, kapsam, son başvuru/başvurulan/sonuç tarihleri, durum, sonuç, not, bağlantı ve hazır belge kontrol listesi var. Kayıtlar `data/finance/applications.json` şema 1'de atomik kayıt, yedek, dosya kilidi ve eski veri üzerinden yazmayı önleme ile korunur. Başvuru/sonuç tarihleri ortak ajandaya ve mevcut hatırlatma akışına katılır. Kaynak dokuz sütunlu CSV içe alınabilir; genişletilmiş CSV kimlikleri ve belge hazırlığını korur. Toplu profil yedeği yeni kayıtları doğrulayarak geri yükler. Başvuru kaydı kendiliğinden gelir kaydı oluşturmaz. **131 test başarılı, 0 başarısızlık, 0 hata, 0 atlanan.**
- **2. Google senkronizasyonu tamamlandı:** Kullanıcının seçimiyle uygulamaya özel **Academic OS Drive klasörü** ve **sunucusuz 30 saniyelik Calendar kontrolü** eklendi. Drive yerel/uzak dosyaları son ortak MD5 ile karşılaştırır; iki taraf değişmişse yerel dosya ve uzaktan indirilen çakışma kopyası korunur. Değişiklikler sürüm/ETag koşuluyla yazılır, silmeler Drive çöp kutusuna veya yerel kurtarma kopyasına gider. Belirsiz oluşturma aynı üretilmiş kimlikle tekrar denenir. Yerel klasör seçimi uygulama profilini/kimlik bilgilerini reddeder. Varsayılan yerel klasör `data/documents`; Drive otomasyonu ekrandan açılabilir. `drive.file` kapsamı uygulamanın oluşturduğu/tek tek izin verilmiş dosyalarla sınırlıdır; tarayıcıdan klasöre eklenen her yeni dosya otomatik görülemez. Google Dokümanları otomatik ikili dosya aktarımına dahil değil. Dosya sınırı 200 MB, toplam 10.000 kayıt, derinlik 50. Drive v3 liste/oluşturma; koşullu yazım için v2 ETag/If-Match kullanılır. Calendar artımlı `syncToken`, 410 durumunda yeniden tam okuma, kalıcı çevrimdışı işlem kuyruğu ve yalnızca aynı hesabın kuyruğunu gönderme destekler. Kuyruk ortak ajandada da görünür. Çakışmış işlemler Google senkronizasyonu ekranında kullanıcı kararı bekler. Canlı doğrulama: **oltuluoglumehmeteren@gmail.com**, Calendar tam/artımlı okuma; Drive yükleme, sürüm korumalı güncelleme, bağımsız indirme ve çöp kutusuna taşıma başarılı. Geçici test dosyaları temizlendi, Academic OS klasörü korundu.
- **3. Gelişmiş ders planlayıcı tamamlandı:** `Dersler → Müfredat / ÇAP ve alternatifler` native JavaFX sekmesi eklendi. Apache PDFBox 3.0.8 ile metin içeren PDF/TXT müfredat aktarımı çevrimdışı çalışır; yarıyıl ders sırası, kredi/AKTS toplamları, seçmeli havuzları ve varsa genel ders sayısı doğrulanır. 50 MB / 100 sayfa sınırı; OCR ve her biçimde PDF okuma desteği değil. İçe aktarmadan önce tüm tablo/havuzlar incelenir. Anadal ve ikinci bölümün yarıyılı, ders geçmişi, başarı notu/notları ayrı kaydedilir; tamamlanan seçmelinin gerçek kodu ve tek kullanımı kontrol edilir. Aynı isim/kod otomatik eşdeğer sayılmaz; iki bölümden birer ders kullanıcı tarafından onaylanır. Eşdeğerlik uygulamadaki plan içindir, üniversitenin resmi kararının yerine geçmez. Tercihler: kesin/esnek gün/saat aralığı, haftalık sabit bloklar, hoca sırası/kesin hoca koşulu, bölüm/ders önceliği, ders/kredi/AKTS/günlük süre ve gün/ara/yük/erken bitiş amacı. CSV şube kataloğuyla en fazla üç alternatif aranır; 120.000 düğüm / 5 saniye sınırında daha iyi sonuç olabileceği belirtilir. Eksik gereksinimler ve reddedilen şubelerin nedenleri gösterilir. Alternatif önce incelenir; kullanıcı uygulayana kadar program değişmez. Bilgiler arama sonrasında değişmişse uygulama engellenir. Eski ders/ödev/sınav kimlikleri ve elle girilmiş sabit dersler korunur; seçilmeyen CSV dersleri program dışına alınır. `data/courses/curricula.json` şema 1 atomik/yedekli/eski veri korumalı kaydedilir; profil yedeği içerir ve doğrular.

**1.4.0 son doğrulama:** Tüm **161 test başarılı, 0 başarısızlık, 0 hata, 0 atlanan**; gerçek PDF metin okuma, kalıcı kuyruk/hesap değişimi/çakışmalar, Drive içerik koruma, ÇAP tek kullanımı, çözücü sınırları, yedek geri yükleme ve gerçek JavaFX geçmiş/alternatif uygulama akışları dahil. Boş profille gerçek 1400×900 JavaFX kabuğu da çizildi (`app/build/reports/academic-os-release-1.4.0.png`). Güncel Windows setup `app/build/windows/dist/BerkayAcademicOS-1.4.0-setup.exe`; gerçek Windows kurulum/güncelleme/tepsi kontrolü henüz yapılmadı. Push kullanıcıda. Kullanım ve Google izin kapsamları `packaging/README.md` içinde.

### 0.17 Birleşik takvim ve diğer Google takvimleri — 13 Ekim 2026

Kullanıcının Google web takvimindeki ödev/tatilleri uygulamada görememesi üzerine ana takvimle sınırlı sorgu genişletildi.

- Google `CalendarList` tüm sayfalarıyla okunur; ana/ders/paylaşılan/tatil takvimlerinin ad, renk, erişim ve seçili bilgileri alınır. `Takvim kaynakları` seçimleri hesaba özel saklanır; başka hesabın tercihi/verisi kullanılmaz. İlk seçim Google’daki işaretli takvimleri izler.
- Her takvimin kendi artımlı işareti vardır. 410 yalnızca o takvimin işaretini sıfırlar; katalog/seçim değişikliği de aylık kopyaları yeniler. Başarısız okumada son tam kopya korunur; işaretler başarılı kopya yazılmadan ilerletilmez.
- Etkinlik kimliği takvim kimliğiyle birlikte değerlendirilir; farklı takvimlerde aynı etkinlik kimliği birbirini ezmez. Kalıcı kuyruk yalnızca ana takvime yazmaya devam eder. Diğer Google kaynakları ve yerel kayıtlar Google düzenleme/silme işlemlerine kapalıdır.
- Ajanda bir ayın dosyalarını tek seferde okuyan ortak snapshot ile ders, ödev/sınav, görev, çalışma, Classroom ve burs kayıtlarını üretir. Ay takvimi teslimleri yalnızca gerçek tarihine koyar; Ajanda’nın yaklaşan/gecikmiş teslim mantığı korunur. `Uygulama kayıtları` seçimi kaydedilir.
- Gün hücrelerinde ilk üç başlık, kaynak renkleri ve kalan sayısı; seçili gün listesinde tam başlık/saat/kaynak görünür. Hücre klavyeyle de seçilebilir. Ay/gün panellerinin boyutu bölücüyle ayarlanabilir.
- OAuth kapsamına tüm etkinlikleri ve takvim listesini okuma izinleri eklendi; sahip olunan etkinlikleri yazma kapsamı korunur. Kullanıcı mevcut hesapla ek izinleri onayladı. Yeni token namespace `all-calendars-v2`; eski token ve kuyruk korunur. Takvim kopyası şema 2 kaynak bilgilerini saklar, eski şema 1 okunur. Kaynak seçimi profil yedeğinde doğrulanır. Google Tasks ayrı API olduğundan bu düzeltme Google Tasks entegrasyonu eklemez.
- **Canlı sonuç:** `oltuluoglumehmeteren@gmail.com` hesabında 26 takvim, Ekim ayında 7 Google kaydı: 1 Ekim VTYS ödevi, 11 Ekim iki ödev, 13 Ekim iki ana etkinlik, 28–29 Ekim tatiller. Google ekran görüntüsüyle eşleşti. Tam/artımlı çoklu takvim okuması doğrulandı. Test doğrulaması önce geçici profilde yapıldı.
- **Doğrulama:** Tüm 170 test başarılı, 0 başarısızlık/hata/atlanan; kaynak seçimi, aynı kimlik ayrımı, hesap geçişi, 410, hata halinde kopya koruma, tüm yerel kaynakların doğru tarihi ve gerçek JavaFX kaynak seçimi dahil. Kullanıcı eski uygulamadan çıktı; 1.4.1 mevcut profille yeniden açıldı. Setup `app/build/windows/dist/BerkayAcademicOS-1.4.1-setup.exe`; gerçek Windows kabul testi hâlâ bekler. Push yapılmadı.

---

### 0.18 Linux günlük kullanım sürümü — 16 Ekim 2026

Kullanıcı mevcut sürümü Linux masaüstü uygulaması olarak kullanmak istedi. Yeni ürün özelliği eklemek yerine Java dahil dağıtım ve mevcut verilerin korunması tamamlandı.

- **Paket:** `app/build/linux/dist/BerkayAcademicOS-1.4.1-linux-x64.tar.gz` (108,1 MiB) ve SHA-256 dosyası. `packaging/build_linux.py` yerel JDK 21 ile `jlink` runtime üretir. Linux JavaFX/PDF/Google kütüphaneleri dahildir. Arşivde kişisel profil, Google istemci dosyası veya token bulunmadığı kontrol edildi.
- **Kurulum:** `packaging/install_linux.py`, paket içindeki `install.sh` ile kullanıcıya özel kurar. Yönetici yetkisi gerekmez. Program `~/.local/opt/berkay-academic-os/releases/1.4.1` altında; `current` bağlantısı etkin sürümü gösterir. Menü girdisi `~/.local/share/applications/berkay-academic-os.desktop`, masaüstü kısayolu `~/Masaüstü/berkay-academic-os.desktop`. İkisi de oluşturuldu; uygulama menü girdisinden açıldı ve gerçek `WM_CLASS=org.example.App` doğrulandı.
- **Profil:** Mevcut `data`, `notes`, `config` ve `scripts` kullanıcı profilinin `~/.local/share/berkay-academic-os` altına kopyalandı. Kaynak görev, ders, referans ve not dosyalarının değişmediği ve kopyalarıyla eşleştiği kontrol edildi. Tokenlar yalnızca bu bilgisayardaki yerel aktarımda tutuldu. Profil izni 0700, aktarılan dosyalar 0600. Önbellekler/kilitler ve eski profil konumu işaretçisi kopyalanmaz. Mevcut hedefin üzerine aktarım reddedilir; açık uygulama POSIX kayıt kilidiyle tespit edilir.
- **Yedek ve kayıtlar:** Yeni profilde `backups/before-linux-install-20261016T191611Z.zip` oluşturuldu; 24 veri/not dosyası hash ile doğrulandı, Google tokenları dahil değil. ZIP biçimi gerçek Java `BackupService` okuyucusuyla ayrıca test edildi. Güncellemeler kullanıcı kayıtlarına dokunmaz; mevcut sürümün tekrar kurulumunda kullanıcı değişikliklerinin korunduğu test edildi. Hata kaydı `~/.local/state/berkay-academic-os/application.log`; açılış sonrası exception yok.
- **Linux yapılandırması:** Paketli sürümde Windows için kapatılan UART/script modülleri Linux'ta açık tutulur. Bu ayrım yeni testle doğrulandı. Belge Merkezi varsayılanı kişisel Drive mount yolundan bağımsız `data/documents`. Paketli başlatıcı çalışma klasöründen ve sistemde seçili Java sürümünden bağımsızdır; Ayarlar'dan profil konumu değiştirilebilir.
- **Doğrulama:** Tüm uygulama testleri yeniden çalıştırıldı: **171 başarılı, 0 başarısız/hata/atlanan**. Kurulumun 5 testi, gerçek runtime öz denetimi, yalıtılmış profil aktarımı/yeniden kurulumu ve `.desktop` doğrulaması geçti. `packaging/verify_linux.py` paketli runtime ile boş profilde gerçek pencereyi çizdi; görüntü `app/build/reports/linux-package-1.4.1.png`. Regolith/i3 pencereyi döşediği için yalnızca geçici doğrulama penceresi 1400×900 olarak yüzdürülür; genel pencere yöneticisi ayarları değiştirilmez.

Kullanım ve yeniden üretme: `packaging/LINUX.md`. Oturum açılışında otomatik başlatma eklenmedi; pencere kapanınca çalışma mevcut tepsi desteğine bağlıdır. Gerçek Windows kabul testi beklemeye devam eder. Bu oturumda commit/push yapılmadı.

---

### 0.19 Kullanım denemesinden sonra kaldırma — 16 Ekim 2026

Kullanıcı uygulamanın hâlâ eksik olduğunu, kurulu sürümü kaldırıp ileride yeni sürüm hazırlamak istediğini belirtti. Eksiklerin ayrıntılarını şimdi paylaşmak istemedi; kapsam daha sonra kullanıcıdan gelecek.

Uygulamanın kapalı olduğu profil kilidiyle doğrulandı. Yalnızca `~/.local/opt/berkay-academic-os` program klasörü, uygulama menüsü girdisi ve `~/Masaüstü/berkay-academic-os.desktop` kaldırıldı. Menü indeksi yenilendi. `~/.local/share/berkay-academic-os` profilinin 36 dosyasının kaldırma öncesi ve sonrası SHA-256 değerleri eşleşti; görevler, dersler, notlar, yerel Google bağlantıları ve yedekler korundu. Kaynak proje, derlenmiş dağıtım arşivi ve hata kayıtları duruyor. Yeni özellik veya yeni sürüm üretilmedi; kullanıcı eksikleri paylaştığında devam edilecek.

---

### 0.20 Dinamik haftalık plan, işlevsel odak ve dış ajanlar — 7 Kasım 2026

Kullanıcı `prompts/haftalik-planlayici-odak-ai.md` kapsamını verdi. Sonradan commit komutlarını
kendisinin çalıştıracağını belirtti; aşama arası commit/push yapılmadı. Başlangıçtaki çok sayıdaki
kaynak değişikliği ve kişisel `data/tasks.json` farkı korundu.

- **Komut katmanı:** PlannerService / PlannerCommand, tek snapshot aralık okuması, kararlı bağlantılar,
  çakışma politikaları, 50 adımlık kalıcı geri al/yinele. İşlemler geçici kopyada doğrulanır; dosya kilidi,
  baseline karşılaştırması, atomik dosya yazımı ve başarısız çok dosyalı yazımı geri alma uygulanır.
  Snapshot mevcut kayıt kilitlerine salt okuma erişimiyle uyar; profil dosyası oluşturmaz.
- **Haftalık Plan:** agenda'nın varsayılanı haftalık saat ızgarası; boş alanda seçim/başlık, havuzdan bırakma,
  günler arası taşıma, süre değiştirme, klavye, çakışma önizlemesi/en yakın aralık/Alt istisnası,
  otomatik kayıtlı inceleme paneli. Dersler ve Google kayıtları sabittir; Google düzenleme kaynak Takvim ekranında kalır.
- **Havuz:** bütün tarihlerdeki açık görev, ödev/sınav, Classroom ve başvuru işleri; son tarih grupları,
  tahmin/kalan/planlı süre, öncelik, arama, ilerleme, hızlı Türkçe ekleme. Classroom ilk planlamada
  yalnızca seçili ödevi ve ders kabuğunu aktarır. Tarihsiz görevler tarihsiz korunur.
- **Odak:** session hedefi, blok/havuz/Bugün/kısayoldan başlatma, kalıcı gerçek süre ve tekrar kredisi koruması,
  sessiz pencere/halkalı sayaç/mini pencere, notlar ve alt görevler, bitiş değerlendirmesi ve devam aralığı.
  focus.properties v1 okunur/v2 yazılır; odak günlüğü v1/v2 okunur/v2 yazılır. Ders şema 2 ve oturum şema 1 korunur.
- **Dış ajan:** JavaFX başlatmayan academic-os CLI, JSON çıktılar, ağsız yerel önbellek okuması,
  snapshot/pool/free-slots/schema/validate/propose/status/suggest. Doğrudan yazım varsayılan kapalı;
  kullanıcı ayarı ve --apply gerekir. Öneri kutusu, hayalet bloklar, kısmi kabul/ret, not, değiştirilen komutlar,
  eski snapshot uyarısı, son tarih/günlük limit/kapalı aralık denetimi, 30 günlük arşiv ve profil yedeği vardır.
  Otomatik yerleştir doğrudan kayıt yerine aynı öneri akışını üretir, sığmayan dakikayı bildirir.
- **Kılavuz:** kök AGENTS.md / CLAUDE.md, docs/agent-cli.md, test edilen JSON örneği ve hazır istek metinleri.
  Paketli profile kopyalanır, mevcut kullanıcı kılavuzu ezilmez. Temiz bağlamlı ayrı ajan yalnızca AGENTS.md ve
  CLI çıktılarıyla iki blokluk öneri gönderdi; valid=true, stale=false, pending. Kısmi kabul ayrıca JavaFX testinde geçti.
- **Doğrulama:** başlangıç 171; aşama 1 176; haftalık/havuz 179; odak 181; son kaynakta **195 test,
  0 başarısız/hata/atlanan**. Linux kurulumun 5 testi başarılı. Gerçek profil/Google hesabı testlerde kullanılmadı.
  Ekranlar `app/build/reports/planner-stage-1.png` … `planner-stage-5.png`, paket kabuğu `linux-package-1.5.0.png`.
- **Dağıtım:** `app/build/linux/dist/BerkayAcademicOS-1.5.0-linux-x64.tar.gz` ve SHA-256.
  Paketli Java 21 ile boş/geçici profilde CLI, profil aktarımı, Java yedek doğrulaması, yeniden kurulum ve JavaFX kontrolü geçti.
  Linux CLI `~/.local/bin/academic-os`; Windows kaynağı academic-os.cmd içerir, PATH otomatik değiştirilmez.
  Yeni Windows EXE/gerçek Windows kabulü bu oturumda yapılmadı. Kullanıcının kurulumuna dokunulmadı.

**Varsayımlar ve sınırlar:** Pazartesi, 08:00–24:00 görünüm; yerel şema son bitişi 23:59 ile sınırlar,
gece yarısını geçen blok yoktur. Varsayılan çakışma en yakın boş aralık; odak blok süresi; ajan yalnızca öneri.
Pomodoro faz geçişleri düğmeyle ilerler; Türkçe ayrıştırıcı sınırlı ve deterministiktir. Fiziksel fareyle
bütün işletim sistemi sürükleme varyasyonları ve Windows davranışları denenmedi. Çok dosyalı yazımda elektrik
kesintisi için tam WAL kurtarma garantisi yoktur. Kapsam, dosyalar, testler ve diğer sınırlar
`docs/planner-delivery.md` içindedir. İsteğe bağlı MCP sunucusu ayrı sonraki kapsamdır; uygulanmadı.
Kullanıcıdan açık karar beklenmiyor; commit/push komutları teslim edilir.

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
- Google etkinliği oluşturma/düzenleme/silme ve çok günlük gösterim

### `app/src/main/java/org/example/TaskItem.java`
- Görev veri modeli.

### `app/src/main/java/org/example/TaskStorageService.java`
- Görevlerin diskten okunması/yazılması.

### `app/src/main/java/org/example/CalendarEventItem.java`
- Google Calendar’dan gelen etkinliklerin UI modeli.

### `app/src/main/java/org/example/GoogleCalendarService.java`
- Google Calendar verilerini okuyan, etkinlik oluşturan/düzenleyen/silen servis.
- Bağlanma durumu.
- Hata mesajı.
- Event yükleme.
- ETag ile koşullu güncelleme/silme; ay cache'i `CalendarModuleView` içinde yönetilir.
- Bağlantı yokken event yükleme denemesini güvenli şekilde boş döndürme.

### `app/src/main/java/org/example/GoogleCalendarOAuthService.java`
- OAuth yetkilendirme katmanı.
- `LocalServerReceiver` tabanlı masaüstü callback akışı.
- Düzenleme tokenlarını `config/google-calendar-tokens/events-write-v1/` altında saklama; eski okuma tokenlarını koruma.
- Dar kapsamlı etkinlik yazma ve takvim metadata okuma izni; PKCE ve eksik izin denetimi.

### `app/src/main/java/org/example/CalendarEventDraft.java` / `GoogleCalendarEventDialog.java`
- Form doğrulaması, tüm gün bitiş tarihi dönüşümü ve arka planda Google'a kayıt.
- Hatalı kayıt sırasında kullanıcı formunu koruma.

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
config/google-calendar-tokens/events-write-v1/
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
- **11 Ekim 2026:** Etkinlik oluşturma/düzenleme/silme ve yeni OAuth izin akışı uygulandı; 0.13 bölümüne bak.

Kalan geliştirme notları:
- Disconnect/reconnect butonu ileride eklenebilir.
- Anlık Google push veya çevrimdışı yazım kuyruğu eklenmedi; dış değişiklikler yenilemeyle okunur.

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
- ilk sürümün elle çalışma planı, ortak ajanda, görev ayrıntıları, süre takibi, hatırlatma, ayarlar ve yedekleme özellikleri eklendi; Windows EXE üretildi (güncel doğrulama: 0.14)

Önceki yol haritasından kalan ek geliştirmeler (ilk sürüm öncelikleri için 0.4 bölümüne bakın):
1. Referans modülü zenginleştirme
2. UART gelişmiş log/hex özellikleri
3. PDF için gerçek dahili renderer
4. Markdown preview
5. Google Drive gerçek senkronizasyonu ve Google push/çevrimdışı yazım (Calendar yazma ve yerel disconnect/reconnect tamamlandı)
6. Global içerik araması (tema ve ayarlar tamamlandı; mevcut modül araması içerik araması değildir)

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

30 Eylül'deki ara verme sonrasında geliştirme yeniden başladı. 12 Ekim'de 1.3.0 ilk sürüm kapsamı, 13 Ekim'de kullanıcının ilk üç önceliği 1.4.0 olarak tamamlandı. 16 Ekim'de Java dahil 1.4.1 Linux masaüstü sürümü kurulup mevcut verilerle açıldı (0.18), ardından kullanıcı eksikleri daha sonra paylaşmak üzere kurulumu kaldırttı; veriler korundu (0.19). Takvim düzeltmesi 0.17'de, ilk üç özellik 0.16'da, öncelik sırası 0.15'tedir. Gerçek Windows kabul testi bekler; push kullanıcıdadır.

Tamamlanan son ana aşamalar:
- **Aşama A:** Takvim + Google Calendar stabilizasyonu
- **Aşama B:** Belge Merkezi readOnly/yetki kontrolü
- **Aşama C:** Belge Merkezi Drag & Drop import
- **Aşama D:** UART ürünleştirme
- **Aşama E:** Preview polish

Bundan sonraki özellik geliştirme sırası:

**Finans → Google senkronizasyonu → Gelişmiş ders planlayıcı → UART/scriptler → Hata raporu paneli → Belge araçları/arama → Referans kütüphanesi → Ses → Yapay zekâ/Jarvis.** İlk üç grup tamamlandı; sıradaki UART/scriptlerdir. Ayrıntılar 0.15–0.16'da. Windows kabul testi ve Google OAuth dağıtım ayarları bekleyen dağıtım kontrolleridir. Qwen ile deneme son önceliktedir; şu anda başlatılmayacak.
