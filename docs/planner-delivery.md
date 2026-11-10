# Haftalık planlayıcı çalışma raporu

7 Kasım 2026. Görev kaynağı: `prompts/haftalik-planlayici-odak-ai.md`.
Kullanıcının son talimatı: commit ve push kullanıcıda; mevcut çalışma ağacı korunur.

## Aşama 0

Başlangıç: 171 test / 0 başarısız / 0 hata / 0 atlanan (önbellek kullanılmadan).
Mevcut 1.3–1.4.1 değişiklikleri ve kişisel `data/tasks.json` değişikliği korunmuştur.
Testler geçici profiller kullanır; gerçek Google hesabına erişilmez.

## Aşama 1

Komut katmanı, tek snapshot aralık okuması, bağlantı doğrulama, üç çakışma politikası,
50 adımlık kalıcı geri al/yinele ve çok dosyalı kilit/baseline korumalı yazım eklendi.
Bugün ekranının görev ekleme, tamamlama, çalışma kaydetme/taşıma/silme işlemleri bu katmandan geçer.
Yeni tercihler, notlar ve işlem geçmişi profil yedeğinde doğrulanır.

Doğrulama: 176 test / 0 başarısız / 0 hata / 0 atlanan; installDist başarılı.
Geçici profille JavaFX Bugün, haftalık gezinme ve Ayarlar akışları mevcut arayüz testleriyle çalıştırıldı.
Ekran: `app/build/reports/planner-stage-1.png` (Bugün ekranı).
Dosyalar: DailyAgendaService, PlannerService, PlannerCommand, PlanningFiles,
PlanningPreferences, PlanningSnapshot, TodayModuleView, WeeklyAgendaView, BackupService,
ClassroomImportService; PlannerServiceTest.

Sınır: dosya sistemi birden çok dosya için tek atomik rename sağlamaz. Kilitler tutulurken
başarısız yazımlar geri alınır; işlem sırasında elektrik kesintisi için tam bir WAL/kurtarma motoru yoktur.
Eski modüllerin planlama dışındaki yazma yolları kendi mevcut depolarını kullanmaya devam eder.

## Aşama 2–3: Haftalık plan ve havuz

Ajanda'nın varsayılan sekmesi Haftalık Plan oldu. Yedi sütunlu saat ızgarası, 15 dakikalık
seçim, satır içi başlık, günler arası taşıma, alt kenardan süre değiştirme, klavye erişimi,
çakışma önizlemesi, en yakın boş aralık, Alt ile çakışma izni ve geri al/yinele eklendi.
İnceleme paneli başlık, zaman seçicileri, bağlı iş, ders, gerçekleşen süre, alt görevler ve notları
otomatik kaydeder. Seçim/gezinme değişirken bekleyen kayıt önce tamamlanır.

Havuz son tarihe göre Gecikmiş / Bu hafta / Gelecek hafta / Daha sonra / Tarihsiz gruplarını,
arama, kaynak/öncelik, tahmin, planlı/kalan süre, ilerleme ve tamamlama kontrollerini içerir.
Görev/ödevin başlık, son tarih, öncelik, tahmin ve notu yan panelde değiştirilebilir.
Classroom ilk bırakmada yalnızca tek ödevi aktarır; kararlı kimlik ve sonraki üç yönlü birleştirme korunur.
Başvuru planlaması kararlı bir yerel görev bağlantısı oluşturur; finans geliri oluşturmaz.
Tarihsiz görevler yeniden açıldığında tarihsiz kalır.

İlk birleşik doğrulama: 179 test başarılı. Son koşuda boş alandaki fare seçimi + Enter,
klavye taşıma, inspector kaydı, geri al, tek ödev aktarımı ve gerçek pencere akışları da geçti.
Ekranlar: `app/build/reports/planner-stage-2.png`, `planner-stage-3.png`.
Dosyalar: WeeklyAgendaView, QuickTaskParser, PlanningSnapshot, TaskStorageService,
ClassroomImportService, ModuleActionHandler, config.json, style.css; PlannerPoolTest,
WeeklyPlannerInterfaceTest, PlannerWorkflowInterfaceTest.

## Aşama 4: Odak

Çalışma bloğundan, havuzdan, Bugün'ün sıradaki bloğundan ve Ctrl+Shift+F ile odak başlatılır.
Sayaç duvar saatiyle sürer. Tamamlanan ve erken bitirilen süre, kimliğiyle bir kez kaydedilir.
`session` kredisi ile bloğun actualMinutes alanı aynı kilitli yazımda güncellenir; ilerlemede çift sayılmaz.
Değerlendirmede Tamamlandı / Devam edecek / Takıldım, gerçekleşen süre düzeltmesi,
sonraki boş aralık, kalıcı alt görev ve notlar bulunur. Sessiz odak penceresi, süre halkası,
küçük/üstte pencere, çalışma-mola tercihleri ve kalıcı döngü sayısı eklendi.
Uygulama başlığı ve tepsi ipucu süreyi gösterir; bildirimler teslim tercihi dışında susturulur.

Eski focus.properties v1 okunur; yeni yazım v2. Odak günlüğü v1/v2 okunur, v2 yazılır.
Çalışma oturumu şema 1 ve ders şema 2 korunur. Yeni planlama belgeleri şema 1'dir.
İlk odak doğrulaması: 181 test başarılı. Son koşuda gerçek odak penceresi ve değerlendirme de geçti.
Ekran: `app/build/reports/planner-stage-4.png`.
Dosyalar: FocusTimerService, FocusModeView, StudyTimeLogService, StudyProgressService,
ReminderService, TodayModuleView, TrayController, App, SettingsModuleView; SessionFocusTest.

## Aşama 5: CLI, öneri kutusu, otomatik planlama

`academic-os` JavaFX başlatmadan JSON snapshot/pool/free-slots/schema/validate/propose/
proposals/proposal-status/suggest döndürür. Okuma ve kuru doğrulama profil dosyası oluşturmaz.
Doğrudan task/block/undo/redo hem --apply hem kullanıcı ayarı gerektirir; yazımlar geçmişe girer.
Kilit, eski snapshot, geçersiz bağlantı, son tarih, kapalı saat ve günlük limit kontrol edilir.
Bir öneride yeni görev oluşturup ona bağlı blok planlamak mümkündür.

Öneriler kutuda ve hayalet bloklarla görünür; gerekçeleri incelenir. Seçilenleri uygula,
reddet, kullanıcı notu ve konumu değiştirilerek kabul desteklenir. Kabul tek geri al adımıdır.
Sonuç accepted / partially_accepted / rejected ve komut kararlarıyla saklanır.
Eski hash işaretlenir, değişen veriler tekrar doğrulanır; çakışan komut seçilemez.
İşlenmiş kayıtlar 30 gün sonra arşivlenir. Yeniler arka plan hatırlatma akışına da katılır.
Otomatik yerleştir deterministik öneri üretir; son tarih, kalan süre, öncelik, kapalı zaman ve
günlük sınırı kullanır. Sığmayan işleri ve eksik dakikayı döndürür.

Son uçtan uca test: CLI gönderimi → uygulamada hayalet blok → kısmi kabul → CLI geri bildirimi geçti.
Ekran: `app/build/reports/planner-stage-5.png`.
Dosyalar: AgentCli, PlanProposal, ProposalService, AutomaticPlanner, PlanningFiles,
PlannerService, BackupService, SettingsModuleView, WeeklyAgendaView, app/build.gradle,
Linux/Windows başlatıcı ve paketleme kaynakları; AgentCliTest, PlannerWorkflowInterfaceTest.

## Aşama 6: Kılavuz ve temiz ajan denemesi

AGENTS.md, CLAUDE.md, docs/agent-cli.md, JSON örneği ve iki hazır istek metni eklendi.
Aynı kılavuzlar uygulama kaynaklarına ve Linux/Windows paketlerine eklenir; paketli profilde
mevcut kullanıcı kılavuzunun üstüne yazılmaz. Kılavuzdaki gerçek JSON örneği CLI validate testinden geçer.
Temiz bağlamlı ayrı ajan, yalnızca AGENTS.md ve CLI çıktılarını okuyarak iki blokluk öneri
oluşturdu, doğruladı ve gönderdi: valid=true, stale=false, pending.
Ayrıntı: `docs/agent-acceptance.md`. Bu deneme bağımsız bir Claude Code kurulumu değildir.

## Son doğrulama ve sınırlar

`./gradlew :app:test :app:installDist --offline --no-daemon`: **195 test / 0 başarısız / 0 hata / 0 atlanan**.
Linux kurulumun 5 testi ve paketleme Python sözdizimi kontrolleri geçti.
JavaFX testleri ve ekranlar geçici profillerle çalıştı; gerçek kullanıcı verisi ve Google hesabı kullanılmadı.
Başlangıçta değiştirilmiş `data/tasks.json` dosyası değiştirilmedi ve commit komutuna dahil edilmedi.

- Gerçek Windows kurulum, sürükle-bırak, tepsi ve PATH kabulü yapılmadı. Windows başlatıcı/paketleme kaynağı güncellendi;
  bu oturumda yeni Windows EXE üretilmedi. Windows PATH'i otomatik değiştirilmez.
- Yerel şema gece yarısını geçen blokları ve 24:00 bitişini desteklemiyor; son bitiş 23:59.
- Türkçe hızlı ekleme: bugün, yarın, gün adları, ISO tarih, HH:mm ve ayrı yazılmış tam sayı s/saat/dk/dakika.
  “Gelecek cuma”, “üç gün sonra”, kesirli ve bitişik “2s30dk” için doğal dil çözümleyicisi yoktur.
- Pomodoro çalışma/mola geçişleri düğmeyle ilerletilir. Tek bir odak dönemi 180 dakika ile sınırlıdır.
- Ekran testlerinde JavaFX fare/klavye olayları ve taşıma/süre komut yolları çalıştırıldı;
  fiziksel fareyle işletim sisteminin bütün sürükle-bırak varyasyonları elle denenmedi.
- Otomatik yerleştirme bir sezgiseldir; küresel en iyi plan garantisi vermez.
- Çok dosyalı yazım kilit ve hata geri alması sağlar; elektrik kesintisinde tam WAL kurtarma garantisi yoktur.
  Geri al, araya başka modülden düzenleme veya odak kredisi girdiyse veriyi korumak için reddedilebilir.
- Eski modüllerin planlama dışındaki yazma yolları kendi depolarında kalır. Google etkinlikleri kaynak Takvim ekranında düzenlenir.
- Kaynaklar genelinde modül/veri/yedek/paketleme yolları incelendi ve regresyon takımı çalıştırıldı;
  bu çalışma bağımsız bir güvenlik denetimi veya her harici entegrasyonun canlı kabulü değildir.

Varsayımlar: agenda dönüştürüldü; Pazartesi; 08:00–24:00 görünüm; çakışmada en yakın aralık;
odakta blok süresi; ajan varsayılanı yalnızca öneri; CLI adı academic-os.
Kullanıcıdan açık karar beklenmiyor. Commit ve push talimat gereği kullanıcıda; aşama arası commit atılmadı.
Ayrı sonraki kapsam olarak aynı komut katmanı bir MCP sunucusuyla sunulabilir; bu çalışmada uygulanmadı.

Linux 1.5.0 paket kabulü: profil aktarımı, kullanıcının düzenlemesini koruyan yeniden kurulum,
Java yedek okuyucusu, boşluklu yoldan CLI, profil kılavuzları ve gerçek JavaFX kabuğu doğrulandı.
Paket: `app/build/linux/dist/BerkayAcademicOS-1.5.0-linux-x64.tar.gz` ve yanındaki `.sha256`.
Paket görüntüsü: `app/build/reports/linux-package-1.5.0.png`.
Kaynak kontrolü: `git diff --check` temiz. Gerçek kullanıcı kurulumu değiştirilmedi.

## Bilinen küçük sorunlar — 8 Kasım 2026 incelemesi

**10 Kasım güncellemesi:** Aşağıdaki liste tarihsel bulguları korur. 1–3 düzeltildi; 4'te otomatik
planlayıcının geçmiş teslim gerekçesi düzeltildi, havuz görünürlüğü korundu. 5'teki boş `suggest`
uyumsuzluğu giderildi; genel şema hata ayrıntıları, kalıcı kilit dosyaları ve elle planlama politikası açık.
Güncel kapsam aşağıdaki "10 Kasım doğrulama düzeltmeleri" bölümündedir.

Kopyalanmış gerçek veriyle CLI denemesinde bulundu. Hiçbiri öneri kutusunun kullanıcı onayı korumasını delmiyor;
düzeltilmeleri acil değil, ertelendi.

1. **Hafta dışı tarih kabul ediliyor.** `week: 2026-11-09` önerisinde 2026-11-20'ye `schedule` → `valid: true`.
   `ProposalService` komut tarihinin `snapshot.week`–`until` aralığında olduğunu denetlemiyor.
2. **Geçmiş tarihe plan kabul ediliyor.** Bugünden önceki bir güne (ör. 2026-11-02) blok önerisi geçerli sayılıyor.
3. **15 dakikalık ızgara uygulanmıyor.** `start: 10:07`, `minutes: 50` geçiyor; AGENTS.md 15 dk aralık istiyor
   (`ProposalService.checkLimits` yalnızca tercih sınırlarına bakıyor).
4. **Havuzda süresi geçmiş işler.** `PlanningSnapshot.assemble` teslimi geçmiş işleri ayırmıyor; gerçek veride havuzun
   18 öğesinin tamamı geçmiş tarihli (çoğu 2025 Classroom ödevi). `suggest` bunları "yeterli boş aralık yok" gerekçesiyle
   `unscheduled` listesine koyuyor; doğru gerekçe "teslim tarihi geçmiş" olmalı. Sonuçta gerçek veride öneri üretilmiyor.
5. **Şema hatası ayrıntısız.** `INVALID_PROPOSAL: Öneri şeması veya alanları geçersiz` hangi alanın bozuk olduğunu
   söylemiyor (`ProposalService.java:28`). Ör. `suggest` boş `commands` döndürünce çıktı `minItems: 1` yüzünden
   `validate`'ten geçmiyor ama sebep görünmüyor.
6. **Kilit dosyaları kalıyor.** `data/agent/proposals/` içinde her önerinin yanında `.json.lock` dosyası birikiyor.

7. **Arayüz teslimden sonraya plan yapabiliyor.** Haftalık ızgarada 2025 teslimli Classroom ödevleri 2026-11-02/03'e
   bırakılabildi; `AFTER_DEADLINE` yalnızca ajan önerisi doğrulamasında uygulanıyor. Gecikmiş işe çalışma koymak
   bilinçli bir tercih olabilir; karar verilmedi.

### Düzeltilen — 8 Kasım 2026

- **Birkaç işlemden sonra plan kaydedilemiyordu.** Geçmiş (`history.json` v1) her adımda beş alan dosyasının
  tamamını önce+sonra olarak base64 saklıyordu; `academic.json` içindeki ~160 KB ders kataloğu yüzünden adım başına
  ~590 KB tutuyor, 8 adımda 5 MB yazma sınırına takılıyordu. Geçmiş artık **alan düzeyinde değişiklik** saklıyor
  (`PlanEdits`, `history.json` v2): ör. `sessions → [blok-id] → start: "19:00" → "20:00"`. Geri al/yinele her alanın
  hâlâ beklenen değerde olduğunu denetler; başka yerden değiştiyse reddeder. Kimlikli dizilerde silinen öğe eski
  sırasına döner. Eski v1 geçmişi bir kez sıfırlanır. Kopya gerçek profilde 15 adım: 4,7 MB → 6,7 KB; 50 adım sığar.
  `historyBytes` 3 MB bütçesi yalnızca toplu içe aktarma gibi büyük adımlar için güvenlik ağı olarak kaldı.
- **Haftalık ızgarada sürüklerken kaydırma yoktu.** Havuz kartı, blok taşıma/boyutlandırma ve aralık seçimi sırasında
  imleç görünüm kenarına (48 px) gelince ızgara dikey/yatay otomatik kayıyor (`WeeklyAgendaView.installAutoScroll`).
- **Classroom listeleri** yuvarlak kenarlıklı kartlar ve aralarında boşlukla gösteriliyor; uzun başlıklar satır kırıyor.

Doğru çalıştığı doğrulananlar: `AFTER_DEADLINE`, `NOT_FOUND`, `CONFLICT` (Google tatili dahil), `DAILY_LIMIT`,
`PREFERENCES`, 23:59 sınırı, boş `reason`, `--apply`/Ayarlar yazım koruması, hata çıkış kodu 2.

## 10 Kasım doğrulama düzeltmeleri

- `ProposalService`: yeni blok için snapshot hafta sınırı, profil saat diliminde geçmiş gün ve
  15 dakikalık ızgara denetimi. Hatalar indeksli `OUTSIDE_WEEK`, `PAST_DATE`, `TIME_GRID` döner.
  `move` kaynak bloğun haftasına ait öneriyle ileri haftaya taşınabilir; `resize` mevcut blok tarihini kullanır.
  Beklerken tarihi geçen öneri ve kabul sırasında değiştirilen komutlar yeniden denetlenir.
- `AutomaticPlanner`: yerleştirme yoksa `proposal: null`, özet `message` ve sığmayan işlerin nedenleri.
  Süresi geçmiş teslimin nedeni artık "Teslim tarihi geçmiş". Boş `commands` kaydedilmez.
- `WeeklyAgendaView`: null öneriyi göndermez; yerleşmeyen işler kullanıcıya başlık/neden olarak gösterilir.
- CLI ve kılavuzlar yeni sonucu ve hata kodlarını açıklar; paketli kılavuzlar kaynakla aynı tutulur.
- Testler sabit saat ve geçici profiller kullanır. Geçmiş gün, hafta sınırı, saniyeli/ızgara dışı başlangıç,
  süre, ileri haftaya taşıma, kabulde yeniden denetim ve boş/yerleşemeyen/karışık havuz kapsanır.
- Tasarım incelemesi: `docs/design-review.md`, `docs/design/planner-silver.svg`,
  `docs/design/today-compact-silver.svg`. Kullanıcının seçtiği gümüş görünüm korunur;
  bu görseller öneridir, üretim ekranlarının düzeni değiştirilmedi.

Son doğrulama: `./gradlew :app:test :app:installDist --offline --no-daemon` başarılı;
**206 test, 0 başarısızlık, 0 hata, 0 atlanan**. Linux kurulumun **5 testi başarılı**.
Gerçek CLI'da `OUTSIDE_WEEK`, `PAST_DATE`, `TIME_GRID`, geçerli öneri ve boş öneri sonucu denendi;
kuru komutlar geçici profilde dosya oluşturmadı. `git diff --check` temiz.
Yeni kurulum paketi üretilmedi; gerçek kullanıcı verisi ve Google hesabına yazılmadı.

## 10 Kasım arayüz geri bildirimi

- Bugün/Ajanda yapılacaklarında ve haftalık havuzda yalnızca son 7 günlük geçmiş gösterilir.
  Bugün profil saat diliminden hesaplanır; hafta gezinmesi sınırı değiştirmez. Eski kayıtlar silinmez;
  CLI havuzunun kapsamı değişmez.
- Son teslimler gün başlıklarının altında sabit şeride alındı. İlk iki kart gerçek saatini gösterir;
  “+N teslim daha” bütün listeyi ayrıntıda açar. Birden çok 23:59 teslimi üst üste binmez.
- Ayrıntı paneli başlangıçta kapalıdır, seçimle açılır ve kapatılabilir; kalan alanı yedi sütun kullanır.
  Pencereye göre panel genişliği, daha az dış boşluk, gruplanmış araçlar ve dar ekranda ikinci araç satırı vardır.
- `PlannerLayoutInterfaceTest`: son 7/8 gün sınırı, depolama/CLI korunması, sabit teslim şeridi,
  23:59 saati, çoklu teslim, genişliğin geri kazanılması, açık/koyu ve 1920×1040 / 1200×800 düzen kontrolü.
  Güncel uygulama görüntüleri `app/build/reports/planner-layout-*.png`.

Arayüz sonrası son doğrulama: `./gradlew :app:test :app:installDist --offline --no-daemon` başarılı;
**208 test, 0 başarısızlık, 0 hata, 0 atlanan**. `git diff --check` temiz.
