# Görev: Berkay Academic OS — Dinamik Haftalık Planlayıcı, İşlevsel Odak Modu ve Dış AI Ajanlarına Açık Planlama Arayüzü

Sen bu depoda çalışan kıdemli bir Java/JavaFX geliştiricisisin. Depo: `/home/berkay/projects/berkay-academic-os`. Kullanıcı (Berkay) uygulamayı günlük planlama için kullanmak istiyor, ancak mevcut sürüm onu "form dolduruyormuş gibi" hissettiriyor. Aşağıdaki hedefleri **aşamalar halinde**, her aşamayı test ederek ve çalışır durumda bırakarak gerçekleştir.

Başlamadan önce `berkay-academic-os-road-map.md` dosyasının **0. bölümünü** (özellikle 0.1, 0.14–0.19) ve `packaging/README.md` dosyasını oku. Proje geçmişi ve kesinleşmiş kararlar oradadır.

---

## 1. Kullanıcının isteği (kendi ifadesinden özet)

1. "Her şeyi istediğim gibi dinamik yapamıyorum." Haftalık plan yaparken gerçek bir **haftalık program** görmek istiyor. Görse bile üzerinden çalışma programı oluşturmak karmaşık ve form doldurmak gibi.
2. **Odak modunun bir işlevi olsun.** Şu an yalnızca geri sayan bir sayaç.
3. Classroom ödevlerinin ve yapılacak işlerin **son tarihlerini basitçe görüp** haftalık programda **istediği yere ekleyebilmeli** (sürükle-bırak).
4. Planlamayı onun yerine bir **AI** yapabilmeli. Ancak **uygulamanın içinde LLM çalışmayacak**; kullanıcı yerel model kurmaktan vazgeçti. Bunun yerine kullanıcı, Claude Code (Opus 5.5) veya Codex (GPT Sol 6.1) gibi **dış kodlama/ajan araçlarına** uygulamayı "öğretecek". Bu ajanlar uygulamanın verisini okuyup kullanıcının programını hazırlayacak. Bunun için:
   - Uygulama, ajanların güvenle kullanabileceği **komut satırı arayüzü ve öneri kutusu** sunmalı.
   - Uygulamayla birlikte ajanların okuyacağı bir **kullanım kılavuzu** (`AGENTS.md` + komut başvurusu) gelmeli.
   - Form alanları arka plana alınmalı. Ana ekranda yalnızca kullanıcının düzenleyeceği kısımlar görünmeli. Ayrıntıları ajan doldurabilir.

Tasarım ilkesi: **"Önce doğrudan manipülasyon, form en son çare."** Her işlem fareyle (sürükle, boyutlandır, tıkla-yaz) ve klavyeyle yapılabilmeli. Ayrıntı alanları modal pencerede değil, seçili öğenin yan panelinde (inspector) olmalı ve otomatik kaydedilmeli.

---

## 2. Teknik bağlam

- **Dil/çatı:** Java 21, JavaFX 21.0.2 (`javafx.controls`, `javafx.fxml`, `javafx.web`), Gradle (`./gradlew`), Jackson, Ikonli, Apache PDFBox. Arayüz kodla kuruluyor (FXML yok). Sürüm `app/build.gradle` içinde `1.4.1`.
- **Paket:** Tüm kaynak `app/src/main/java/org/example/` altında, tek paket.
- **Tema:** `app/src/main/resources/ui/style.css` (gümüş tasarım, açık/koyu tema), font Poppins (`ui/fonts`). `UiTheme` dialog ve ikon yardımcıları içerir. Yeni bileşenler mevcut CSS sınıflarını ve token'larını kullanmalı. "Hareketi azalt" tercihine saygı göster.
- **Arayüz dili:** Tüm metinler Türkçe.
- **Modüller:** `app/src/main/resources/config.json` (`today`, `agenda`, `courses`, `classroom`, `tasks`, `calendar`, `finance`, `settings` …). Yönlendirme `ModuleActionHandler.java` içinde. `agenda` modülü `TodayModuleView(agenda=true)` ve `WeeklyAgendaView` kullanır.
- **Veri katmanı (bozulmaması kritik):**
  - `data/tasks.json` → `TaskStorageService`, `TaskItem` (dueTime, priority, estimatedMinutes, loggedMinutes, subtasks)
  - `data/courses/academic.json` → `CourseStorageService`, `CourseData` (şema 2: courses, lessons, work, sections). `CourseData.Work` ödev/sınavdır.
  - `data/study/sessions.json` → `StudyStorageService`, `StudySession` (id, title, date, start, end, courseId, done, taskId, workId, actualMinutes). Şema 1.
  - `data/focus.properties` → `FocusTimerService` (version=1; targetKind yalnızca `task`/`work`)
  - Odak kredileri → `StudyTimeLogService`; ilerleme → `StudyProgressService`
  - Classroom önbelleği → `GoogleClassroomService.loadCached()`, `ClassroomData`; yerele aktarma → `ClassroomImportService` (kararlı `gc-`/`gw-` kimlikleri, üç yönlü birleştirme)
  - Google Calendar önbelleği/kuyruğu → `CalendarCacheStore`, `CalendarWriteQueue`, `CalendarSyncService`
  - Birleşik okuma → `DailyAgendaService` (`load(root, date)`, `loadMonth`, `saveSession`, `deleteSession`, `overlaps`)
  - Tüm depolar **atomik yazım + `.bak` + `.lock` + baseline karşılaştırması** kullanır ("dosya değişti, önce yenile"). Yeni depolar da aynı deseni izlemeli (`AtomicFileWriter`, `StudyStorageService` örnek).
  - Profil yedeği `BackupService` içindedir. Yeni veri dosyası eklersen yedeğe ve doğrulamasına da ekle.
- **Testler:** `app/src/test/java/org/example/`. JavaFX arayüz testleri `FxTestSupport` ile yapılır. Son durumda **171 test başarılı**. Komut:
  `./gradlew :app:test :app:installDist --offline --no-daemon`
- **Commit:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`). **Push yapma**, push kullanıcıdadır.

---

## 3. Mevcut uygulamanın eksikleri (kod incelemesine dayalı; senin düzelteceğin noktalar)

### 3.1 Haftalık görünüm gerçek bir program değil
- `WeeklyAgendaView.render()` her gün için alt alta **buton listesi** üretir: "09:00–10:30\nBaşlık\nTür". Zaman ekseni, saat ızgarası, blok yüksekliği/süre orantısı yok.
- Bir öğeye tıklamak yalnızca o günü açar (`openDay`) veya modüle gider. Haftalık ekrandan **oluşturma, taşıma, boyutlandırma yok**.
- `load()` her gün için ayrı `new DailyAgendaService().load(...)` çağırır. Bu, **tüm veri dosyalarını 7 kez okur**. `loadMonth` benzeri tek snapshot'lı bir `loadRange(start, end)` gerekli.
- "Saat çakışması" yalnızca metin ve kırmızı stil olarak görünür.

### 3.2 Çalışma oturumu oluşturmak form doldurmak gibi
- `TodayModuleView.sessionDialog()`: modal dialogda başlık, DatePicker, **elle yazılan `HH:mm` metin alanları**, ders ComboBox, hedef ComboBox, tamamlandı, gerçekleşen dakika Spinner'ı vardır. Kısacası bir blok için ~9 alan.
- `DailyTimelineView` yalnızca **o günün içinde, var olan** çalışma bloklarını sürüklemeye izin verir. Gün değiştirme, süre değiştirme veya bir işi sürükleyip blok oluşturma yok.
- Görev ekleme (`addTask` + `TaskMetadataEditor`) da uzun bir form: saat metni, öncelik, iki Spinner, alt görevler.
- `DailyAgendaService.saveSession()` çakışmada kaydı **tamamen reddeder**. Kullanıcıya alternatif (en yakın boş aralık) sunulmaz.

### 3.3 Son tarihler planlanabilir bir havuz değil
- Classroom ödevleri yerele aktarılmadıysa `DailyAgendaService` içinde `external=true` bir `Todo` olur. Bunlar **planlanamaz, tamamlanamaz** ("Bu kayıt kaynak uygulamada yönetilir").
- `include()` teslimleri yalnızca **7 gün** ileriye kadar gösterir. Haftalık planlamada daha uzak teslimler görünmez.
- Classroom aktarımı ders bazında (`importCourse`). **Tek bir ödevi** planlarken aktarma yolu yok.
- "Bu iş için ne kadar süre planladım / ne kadar kaldı" bilgisi (`StudyProgressService`) haftalık bağlamda gösterilmiyor.

### 3.4 Odak modu işlevsiz
- `FocusTimerService` yalnızca sabit süreli (varsayılan 25 dk, 1–180) geri sayım yapar. Hedef `task`/`work` ile sınırlıdır. **Planlanmış çalışma bloğuna (`StudySession`) bağlanamaz.**
- Hedef seçimi `TodayModuleView` içindeki bir ComboBox'tır. Takvimdeki "şimdiki blok" otomatik önerilmez.
- Mola döngüsü, oturum sonu değerlendirmesi ("bitti mi, ne kadar kaldı?"), kalan işi yeniden planlama, alt görev işaretleme, not alma ve dikkat dağıtıcıları azaltma yok.
- Tamamlanan süre `StudyTimeLogService`'e yazılır. Ancak planlanan oturumun `actualMinutes`/`done` alanları odaktan güncellenmez.

### 3.5 Dış ajanlar uygulamayı kullanamıyor
- İş kuralları ve kayıt işlemleri **görünümlerin içinde** (`mutate(() -> store.saveTasks(...))`, dialog sonuç dönüştürücüleri). Uygulama dışından çağrılabilecek tek bir **komut/eylem katmanı** yok.
- Bir ajanın planlama işini yapabilmesi için JSON dosyalarını doğrudan elle düzenlemesi gerekir. Bu tehlikelidir: kilit/baseline korumasını, doğrulamayı ve ilişkili kayıtları (taskId/workId/courseId) atlar, açık uygulamayla çakışır.
- Uygulamanın verisini, kurallarını ve kimlik biçimlerini anlatan bir **ajan kılavuzu** yok.
- Geri al (undo), işlem geçmişi ve "öneri olarak göster, onaylayınca uygula" akışı yok.
- **Road map'teki eski karar geçersiz:** Kullanıcı Qwen/yerel LLM entegrasyonundan vazgeçti. Uygulamaya LLM istemcisi, model ayarı veya API anahtarı **eklenmeyecek**. Road map'i güncellerken bu kararı da yaz.

---

## 4. Hedef davranış

### 4.1 Haftalık Planlayıcı (yeni ana planlama ekranı)

`agenda` modülünü (veya yeni bir `planner` modülünü, sidebar'da üstte) aşağıdaki yerleşimle yeniden kur:

```
┌──────────────┬───────────────────────────────────────────────┬──────────────┐
│ PLAN HAVUZU  │  ‹ 9–15 Kas 2026 ›  [Bu hafta]  [Öneriler ●2]  │  İNCELEME    │
│ (son tarih   │  Pzt   Sal   Çar   Per   Cum   Cmt   Paz      │  (seçili     │
│  sırasıyla)  │ 08 ─────────────────────────────────────────  │   öğe)       │
│ ▸ Bu hafta   │ 09 [Ders: VTYS]      [Ders]                   │  Başlık      │
│   • Fizik    │ 10 [Çalışma: Fizik ödev]                       │  Saat ⟷      │
│     Ödev 3   │ 11 ▲teslim 23:59                               │  Bağlı iş    │
│     Pzt 23:59│ …                                              │  Alt görevler│
│     kalan 2s │                                                │  Not         │
│ ▸ Gelecek    │                                                │  [Odakla ▶]  │
│ ▸ Tarihsiz   │                                                │              │
└──────────────┴───────────────────────────────────────────────┴──────────────┘
```

**Zaman ızgarası**
- 7 sütun (Pzt–Paz), zaman orantılı satırlar. Varsayılan görünür aralık 08:00–24:00, kaydırılabilir. 15 dakikalık yapışma (snap). Bugün sütunu ve "şimdi" çizgisi vurgulu.
- Blok türleri ve davranışları:
  - **Ders saatleri:** Sabit, sürüklenemez, soluk/çizgili. `CourseData` lesson'larından gelir.
  - **Google etkinlikleri:** Yalnızca ana takvimdekiler mevcut düzenleme kurallarına göre düzenlenebilir. Diğerleri salt okunur.
  - **Çalışma blokları (`StudySession`):** Tamamen düzenlenebilir.
  - **Teslim işaretçileri:** Ödev/görev/burs son tarihinin saatinde ince bir çizgi ve etiket. Saatsizse gün başlığında rozet.
- Haftalık veri **tek snapshot** ile yüklenmeli (`DailyAgendaService`'e `loadRange` ekle). Arka plan iş parçacığında yükle, mevcut iptal/`disposed` desenini koru.

**Doğrudan manipülasyon**
- **Havuzdan sürükle → ızgaraya bırak:** Bağlı bir çalışma bloğu oluşturur. Başlık işin adı olur. Süre şu sırayla belirlenir: kalan tahmini süre (`StudyProgressService` remaining), yoksa 60 dk, en fazla 120 dk. Bağlantı `taskId`/`workId` ile, `courseId` otomatik.
- **Boş alanda sürükleyerek aralık seç → satır içi başlık yaz → Enter:** Bağsız çalışma bloğu oluşturur. Esc iptal eder.
- **Blok gövdesini sürükle:** Gün ve saat değiştirir (günler arası taşıma dahil). **Alt kenarı sürükle:** Süreyi değiştirir.
- **Çift tık:** Başlığı satır içi düzenler. **Tek tık:** İnceleme panelinde açar. **Sağ tık menüsü:** Odakla, tamamlandı, çoğalt, sil, sonraki boş aralığa taşı.
- **Çakışma:** Kaydı reddetme. Bırakma sırasında çakışan alanı önizlemede göster. Bırakılınca aynı gündeki **en yakın boş aralığa** yerleştir ve bunu bildir ("Ders ile çakıştığı için 14:00'e kaydırıldı · Geri al"). Kullanıcı Alt tuşuyla bırakırsa çakışmaya izin ver ve uyarı rozeti göster. `DailyAgendaService.saveSession` içindeki kuralı bu politika ile uyumlu hale getir; eski çağıranları kırma.
- **Geri al / yinele:** Ctrl+Z / Ctrl+Shift+Z, en az son 50 planlama işlemi. Her değişiklikten sonra kısa "Geri al" bildirimi.
- **Klavye erişimi:** Ok tuşlarıyla blok seçimi; Alt+ok ile 15 dk/1 gün taşıma; Shift+Alt+↑/↓ ile süre; Delete; Enter ile inceleme. Erişilebilir metinler (`setAccessibleText`).

**Plan havuzu (sol panel)**
- Tamamlanmamış tüm işler tek listede, son tarihe göre gruplu: **Gecikmiş · Bu hafta · Gelecek hafta · Daha sonra · Tarihsiz**. Görüntülenen haftaya göre hesaplanır. 7 günlük sınır burada uygulanmaz.
- Kaynaklar: Görevler (`TaskItem`), ödev/sınavlar (`CourseData.Work`), **aktarılmamış Classroom ödevleri**, burs/başvuru son tarihleri.
- Her kartta: başlık, ders/kaynak rengi, son tarih ve saat, kalan süre ("2s 30dk kaldı · 1s planlı"), öncelik noktası, ilerleme çubuğu. **Tamamen planlanmış** işler soluk görünür veya filtrelenir.
- Hızlı ekleme alanı: "Fizik raporu cuma 17:00 2s" gibi bir metin yazıp Enter → görev oluşturur. Basit ve deterministik bir Türkçe ayrıştırıcı yeterli; anlamazsa yalnızca başlıkla oluşturur. Tahmini süre ve tarih kartta tıklanarak değiştirilebilir.
- Arama/filtre: ders, kaynak, öncelik.
- Karttaki onay kutusu işi tamamlar. Classroom kaynaklıysa yerel kayıt üzerinden yapılır (aşağı bak).
- **Classroom ödevini planlamak:** Kart ilk kez ızgaraya bırakıldığında, `ClassroomImportService`'e **tek ödev aktaran** bir yöntem ekle (`importWork(data, courseId, workId)`). Bu yöntem gerekirse ders kabuğunu oluşturur ve mevcut kararlı kimlik ile üç yönlü birleştirme mantığını korur. Ardından oturum `workId` ile bağlanır. Aynı ödev ikinci kez aktarılmaz. Classroom'a hiçbir şey yazılmaz.

**İnceleme paneli (sağ)**
- Seçili blok veya kart için alanlar: başlık, gün/saat (tıklanabilir zaman seçici, metin değil), bağlı iş, ders, alt görevler (onay kutuları), not, tahmini/gerçekleşen süre, öncelik.
- Değişiklikler **otomatik kaydedilir** (debounce ~400 ms). Doğrulama hatası alanın altında görünür. Kayıt butonu yok.
- Gelişmiş/nadir alanlar ("Ek çalışma kaydı" vb.) katlanabilir "Ayrıntılar" bölümünde durur.
- "Odakla ▶" butonu odak modunu bu blokla başlatır.
- Mevcut modal dialoglar diğer modüllerde kalabilir. Ancak planlayıcıdan hiçbir akış modal form açmamalı (silme onayı hariç; o da geri al ile gereksizleşebilir).

### 4.2 İşlevsel Odak Modu

- **Hedef olarak çalışma bloğu:** `FocusTimerService` hedef türlerine `session` ekle. `focus.properties` sürüm 1 dosyalarını okumaya devam et. Gerekirse sürüm 2 yaz ve eski hedefleri koru (geriye uyumluluk testi şart).
- **Başlatma yolları:** (1) blok üzerinde "Odakla", (2) Bugün ekranında şu anki veya sıradaki blok otomatik önerilir ("15:00 Fizik ödev — Başla"), (3) havuzdaki karttan (anlık bir oturum oluşturur), (4) global kısayol (örn. Ctrl+Shift+F).
- **Süre:** Varsayılan olarak bloğun kalan süresi. Seçenek olarak pomodoro döngüsü (çalışma/kısa mola/uzun mola; varsayılan 25/5/15, 4 döngü). Ayarlar'dan değiştirilebilir. Mevcut duvar saati mantığını koru: navigasyon, uyku ve yeniden başlatmada sayaç doğru kalmalı.
- **Odak ekranı:** Sade, tam pencere veya küçük "her zaman üstte" mini pencere seçeneği. Gösterilecekler: hedefin başlığı ve dersi, son tarihe kalan zaman, alt görev listesi (işaretlenebilir, kalıcı), hızlı not alanı (ilgili göreve/oturuma kaydedilir), kalan süre halkası, duraklat/bitir/mola butonları.
- **Dikkat:** Odak sırasında uygulama içi hatırlatmaları ve bildirimleri sustur. Yalnızca teslim uyarıları kalsın, ayarlanabilir olsun. Tepsi ikonu ve başlıkta kalan süreyi göster.
- **Oturum sonu değerlendirmesi** (form değil, 2–3 tıklık bir kart):
  - "Bitti mi?" → **Tamamlandı** / **Devam edecek** / **Takıldım**.
  - Gerçekleşen süre otomatik doldurulur ve düzeltilebilir.
  - Sonuç şunları günceller: oturumun `actualMinutes` ve `done` alanları, işin ilerlemesi, alt görevler. Mevcut `StudyTimeLogService` kredisiyle **çift sayım olmamalı**; mevcut kuralı koru ve test et.
  - "Devam edecek" seçilirse kalan süre için **sonraki uygun boş aralık önerilir**. Tek tıkla planlanır veya havuza geri döner.
- **İstatistik:** Haftalık planlayıcının üstünde ince bir şerit: "Bu hafta planlanan 12s · gerçekleşen 8s 40dk · ders bazında dağılım". Ayrı grafik ekranı bu aşamada gerekmez.

### 4.3 Dış AI ajanlarına açık planlama arayüzü ("formlar arka plana")

**Kullanım senaryosu:** Kullanıcı terminalde Claude Code veya Codex açar ve "Bu haftayı finallere göre planla, cuma akşamı boş kalsın" der. Ajan önce `AGENTS.md` dosyasını okur, sonra uygulamanın CLI'ı ile haftayı ve havuzu okur, bir öneri hazırlayıp gönderir. Kullanıcı öneriyi uygulamanın haftalık ekranında görür ve onaylar. Uygulamanın içinde hiçbir model çalışmaz; uygulama yalnızca **güvenli bir arayüz ve iyi bir kılavuz** sağlar.

**a) Komut katmanı (UI ve ajanlar aynı yolu kullanır)**
- `PlannerService` (veya `planning` alt paketi) oluştur. Planlayıcının yaptığı her değişiklik **tipli komutlarla** yapılır. Önerilen asgari küme:
  - `CreateTask`, `UpdateTask`, `CompleteItem`
  - `ScheduleBlock(itemRef, date, start, minutes)`, `MoveBlock`, `ResizeBlock`, `DeleteBlock`, `CompleteBlock(actualMinutes)`
  - `ImportClassroomWork`, `SetEstimate`, `SetPreferences`
- Her komut doğrulanır, mevcut depolar üzerinden atomik uygulanır ve bir `PlanChange` (önce/sonra) döndürür. Geri al/yinele bu kayıtlar üzerinden çalışır.
- Görünümler depolara doğrudan yazmaz, yalnızca komut gönderir. Mevcut `TodayModuleView`/`DailyTimelineView` yazma yolları da bu katmana taşınmalı (davranışı değiştirmeden; testlerle doğrula).
- Okuma tarafı: `PlanningSnapshot`. İçeriği: hafta aralığı, sabit bloklar (ders/Google), mevcut çalışma blokları, boş aralıklar, havuz öğeleri ve kalan süreleri, kullanıcı tercihleri (çalışma saat aralığı, günlük azami süre, en kısa/en uzun blok, kapalı zamanlar). JSON'a serileştirilebilir olmalı.

**b) Ajan CLI'ı (`academic-os` komutu)**
- Aynı JAR'a ikinci bir giriş noktası ekle (örn. `org.example.cli.AgentCli`). `installDist` ve Linux/Windows paketlerinde başlatıcı betik olarak gelsin. JavaFX başlatmadan çalışmalı ve hızlı açılmalı.
- Profil konumu uygulamanınkiyle aynı çözülür (`AppPaths`). `--profile <dizin>` ile geçici profil verilebilir (testler ve ajan denemeleri için).
- Tüm çıktılar **makine okunur JSON** (stdout). Hatalar JSON'dur, çıkış kodu sıfır değildir. Tarih/saat ISO biçiminde, saat dilimi açık. `--help` her komutta örnek verir. Önerilen komutlar:
  - `academic-os snapshot --week 2026-11-09`: haftanın tamamı (yukarıdaki `PlanningSnapshot`)
  - `academic-os pool [--until 2026-12-31]`: planlanmamış/kısmen planlanmış işler, son tarih ve kalan süreyle
  - `academic-os free-slots --week … [--min 30]`: tercihleri dikkate alan boş aralıklar
  - `academic-os schema`: tüm komutların ve öneri dosyasının JSON şeması
  - `academic-os validate <proposal.json>`: uygulamadan kuru çalıştırma; çakışma, geçersiz bağlantı, teslimden sonra planlama gibi sorunları listeler
  - `academic-os propose <proposal.json>`: öneriyi öneri kutusuna koyar (aşağı bak)
  - `academic-os proposals [--status pending]` / `academic-os proposal-status <id>`: önerinin kabul/ret durumunu ve kullanıcının notunu okuma
  - Doğrudan yazım komutları (`task add`, `block add|move|delete` …) yalnızca `--apply` ile çalışır ve varsayılan olarak **kapalıdır**. Ayarlar'da "Ajanların doğrudan değişiklik yapmasına izin ver" seçeneği açıksa çalışırlar. Her doğrudan yazım geri al geçmişine ve ajan işlem günlüğüne girer.
- Açık uygulamayla eşzamanlılık: CLI da mevcut kilit/baseline mekanizmasını kullanır. Uygulama açıkken dosya değişirse ekran otomatik yenilenir (dosya izleme veya mevcut yenileme akışı). "Önce yenile" çakışmasında CLI anlaşılır bir hata döndürür.
- Güvenlik: CLI Google/Classroom'a ağ isteği yapmaz, yalnızca yerel önbelleği okur (Classroom yenilemesi kullanıcı işidir). OAuth token dosyalarını ve istemci sırlarını **hiçbir çıktıya koymaz**. Profil dışına yazmaz.

**c) Öneri kutusu: ajan önerir, kullanıcı karar verir**
- `data/agent/proposals/<id>.json`, şema 1. İçeriği: ajan adı/notu, oluşturulma zamanı, dayandığı snapshot'ın parmak izi (hash), gerekçeli komut listesi (her komutta kısa `reason`).
- Uygulama yeni öneriyi algılar ve bildirim gösterir. Haftalık ekranda öneri **hayalet bloklar** (kesikli kenar, yarı saydam, ajan rozeti) olarak görünür, gerekçe fareyle üzerine gelince veya inceleme panelinde okunur.
- Kullanıcının seçenekleri: **Tümünü uygula**, tek tek ✓/✗, hayalet bloğu sürükleyip konumunu değiştirerek kabul etme, **Reddet** (isteğe bağlı not: "cuma akşamı yerine cumartesi sabahı"). Uygulanan öneri tek bir geri al adımıdır.
- Sonuç dosyaya yazılır (`accepted`, `partially_accepted`, `rejected` + kullanıcı notu + uygulanan/değiştirilen komutlar). Böylece ajan `proposal-status` ile geri bildirimi okuyup yeni öneri hazırlayabilir.
- Snapshot parmak izi değişmişse uygulama engellenmez ama etkilenen komutlar işaretlenir. Çakışanlar uygulanamaz, kullanıcı yeniden öneri isteyebilir.
- Eski/işlenmiş öneriler 30 gün sonra arşivlenir. Profil yedeği öneri klasörünü içerir ve doğrular.

**d) Ajan kılavuzu (uygulamayı ajana "öğreten" belge)**
- Depo köküne `AGENTS.md` ve aynı içerikle `CLAUDE.md` (veya `CLAUDE.md` içinden `AGENTS.md`'ye yönlendirme) yaz. Paketli kurulumun profil klasörüne de bir kopya koy. Böylece ajan, kullanıcının profil klasöründe açıldığında da kılavuzu bulur.
- İçerik (kısa, örnekli, ajanın işini yapmasına yetecek kadar):
  1. Uygulamanın amacı, kullanıcının planlama tercihleri ve **altın kurallar**: JSON dosyalarını asla elle düzenleme, yalnızca CLI kullan; varsayılan yol öneridir; derslerin ve Google etkinliklerinin üzerine plan koyma; teslimden sonra çalışma planlama; tokenlara dokunma.
  2. Veri modeli sözlüğü: görev, ödev/sınav, ders saati, çalışma bloğu, havuz öğesi, kalan süre, kimlik biçimleri (`gc-`, `gw-`, UUID), bağlantı kuralları (bir blok ya göreve ya ödeve bağlanır).
  3. Tipik iş akışları ve örnekler: "haftayı planla", "yeni gelen Classroom ödevlerini yerleştir", "kaçırılan blokları yeniden dağıt", "sınav haftasına göre tekrar planı". Her biri snapshot → pool → free-slots → proposal → validate → propose sırasıyla, gerçek JSON örnekleriyle.
  4. Öneri dosyası şemasının açıklaması ve tam bir örneği.
  5. Hata kodları ve ne yapılacağı.
- Kılavuzdaki örnekler test edilmeli: bir test, kılavuzdaki örnek öneri dosyasını `validate` ile doğrulasın. Böylece belge kodla birlikte eskimez.
- Ajan için isteğe bağlı bir planlama şablonu: `prompts/` altında kullanıcının ajana verebileceği hazır istek metinleri ("Bu haftayı planla", "Kalan işleri sınavlara göre dağıt").

**e) Deterministik otomatik planlayıcı (AI'sız yardımcı)**
- Ajan yokken de işe yarar ve ajanın başlangıç noktası olur: `academic-os suggest --week …` ve ekrandaki **"Otomatik yerleştir"** butonu. Boş aralıkları, son tarihleri, kalan süreleri, önceliği ve tercihleri kullanarak işleri teslimden önce dağıtır, son güne yığmamaya çalışır. Sığmayanları açıkça bildirir ("Fizik Ödev 3 için 1s30dk yer bulunamadı").
- Sonucu doğrudan yazmaz, bir öneri üretir (aynı öneri akışı).
- Birim testleri: çakışmasızlık, teslimden sonra plan yapmama, günlük sınır, deterministik çıktı.

**Bilinçli olarak yapılmayacaklar:** Uygulama içi LLM istemcisi, model indirme, API anahtarı ayarı, uygulamanın kendisinin bir ajan başlatması. İsteğe bağlı olarak (son aşamadan sonra, ayrı onayla) aynı komut katmanının bir **MCP sunucusu** olarak sunulması değerlendirilebilir; bunu yalnızca raporda öner, uygulama.

---

## 5. Aşamalar ve kabul kriterleri

Her aşama sonunda: tüm testler geçer, uygulama gerçek profille değil **geçici bir profille** açılıp ilgili akış denenir, ekran görüntüsü `app/build/reports/` altına alınır, ardından tek bir Conventional Commit atılır.

**Aşama 0: Hazırlık**
- `git status`'u incele. Çalışma ağacında 1.3.0–1.4.1 dönemine ait **commit edilmemiş çok sayıda değişiklik** var. Kullanıcı bunları senden önce commit'lemediyse **hiçbir şeyi silme/geri alma**. Önce kullanıcıya sor veya mevcut hali ayrı bir commit olarak kaydetmeyi öner.
- Mevcut test sayısını ve sonucunu kaydet (beklenen 171 başarılı).

**Aşama 1: Komut katmanı + tek snapshot okuma**
- `DailyAgendaService.loadRange`, `PlanningSnapshot`, `PlannerService` + komutlar + geri al/yinele, mevcut yazma yollarının taşınması.
- Kabul: Davranış değişmeden tüm eski testler geçer. Komutlar için yeni birim testleri yazılır (çakışma politikası, bağlantı doğrulama, geri al, eşzamanlı dosya değişikliği → "önce yenile").

**Aşama 2: Haftalık ızgara + inceleme paneli**
- Kabul (JavaFX testleri dahil): boş alanda sürükleyip başlık yazarak blok oluşturma; günler arası taşıma; boyutlandırma; çakışmada en yakın boş aralık ve geri al; ders bloklarının taşınamaması; inceleme panelinde otomatik kayıt; klavye ile taşıma. Haftalık yükleme veri dosyalarını bir kez okur (test ile doğrula).

**Aşama 3: Plan havuzu + Classroom tek ödev aktarımı**
- Kabul: aktarılmamış Classroom ödevini sürükleyip bırakınca tek ödev aktarılır, oturum ona bağlanır, ikinci sürüklemede tekrar aktarılmaz; sonraki Classroom yenilemesi elle değiştirilen alanları korur; havuz gruplaması ve kalan süre doğru; hızlı ekleme ayrıştırıcısı testleri geçer.

**Aşama 4: Odak modu**
- Kabul: bloktan odak başlatma; uygulama yeniden açıldığında sayacın doğru devam etmesi; oturum sonu değerlendirmesinin `actualMinutes`/`done`/alt görevleri güncellemesi; çift sayım olmaması; "Devam edecek" seçeneğinin sonraki boş aralığı önermesi; eski `focus.properties` dosyasının okunması; odak sırasında bildirimlerin susturulması.

**Aşama 5: Ajan CLI'ı + öneri kutusu + otomatik yerleştir**
- Kabul: `snapshot`/`pool`/`free-slots`/`schema`/`validate`/`propose`/`proposal-status` komutlarının JSON çıktıları için testler; geçici profille uçtan uca senaryo (CLI ile öneri gönder → uygulamada hayalet bloklar görünür → kısmi kabul → `proposal-status` sonucu doğru); uygulama açıkken CLI yazımında kilit/yenileme davranışı; `--apply` ayarı kapalıyken doğrudan yazımın reddedilmesi; token/sır dosyalarının hiçbir çıktıda bulunmaması; otomatik yerleştirme kuralları için birim testleri.

**Aşama 6: Ajan kılavuzu ve gerçek ajan denemesi**
- `AGENTS.md`/`CLAUDE.md`, öneri şeması açıklaması, örnekler, hazır istek metinleri.
- Kabul: kılavuzdaki örnek öneri `validate` testinden geçer. Kılavuzu hiç görmemiş bir ajanın (mümkünse kendi oturumunun dışında temiz bir oturumda) yalnızca `AGENTS.md`'yi okuyarak geçici profilde bir haftalık öneri üretip gönderebildiği gösterilir. Bunu yapamadıysan raporda nedenini yaz. Ajanın takıldığı noktalar kılavuza geri eklenir.

---

## 6. Kısıtlar ve yapılmayacaklar

- Kullanıcı verisini (`~/.local/share/berkay-academic-os`, depodaki `data/`) testlerde **değiştirme**. Geçici profil kullan.
- Şema değiştirirsen sürüm numarasını artır. Eski dosyaları okuyabilmeli, okuyamadığın dosyayı **asla üzerine yazmamalısın** (mevcut "dosya korunuyor" davranışı). Yedek/geri yükleme yeni alanları da doğrulamalı.
- Gerçek Google hesabına yazma (Calendar etkinliği oluşturma vb.) yapma. Classroom'a hiçbir şey yazma.
- Uygulamaya LLM istemcisi, model indirme/kurulumu, Ollama, bulut AI anahtarı veya ayarı ekleme yok. AI işi uygulama dışındaki ajanlar tarafından CLI ve öneri kutusu üzerinden yapılır.
- Ajanlara açılan arayüz varsayılan olarak yalnızca okuma + öneri içindir; doğrudan yazım ayarla açılır.
- Java/JavaFX dışına geçme. Yeni ağır bağımlılık ekleme; gerekiyorsa gerekçesini yaz (JSON için mevcut Jackson yeterli; CLI argümanları için küçük bir el yazımı ayrıştırıcı veya picocli gibi tek, hafif bir kütüphane kabul edilebilir).
- UI iş parçacığında dosya/ağ işlemi yapma.
- Var olan modülleri (finans, belgeler, UART, referans) bozma. Kapsam dışı yeniden düzenleme yapma.
- **Push yapma.**

---

## 7. Teslim raporu (her aşama sonunda ve en sonda)

Türkçe ve kısa:
1. Ne yapıldı (kullanıcının göreceği davranış olarak; ajan arayüzü değiştiyse `AGENTS.md`'nin de güncellendiğini belirt).
2. Değişen/eklenen dosyalar.
3. Test sonucu (toplam / başarısız / atlanan) ve elle denenen akışlar + ekran görüntüsü yolları.
4. **Kendi eksiklerin ve sınırların:** Yapamadığın, test edemediğin, emin olmadığın veya varsayım yaptığın her şey. Örnek: "gerçek bir ajanla uçtan uca denenmedi", "Windows'ta sürükle-bırak denenmedi", "Türkçe tarih ayrıştırıcısı şu kalıpları anlamıyor". Bunları gizleme. Başarısız bir test varsa çıktısıyla birlikte bildir.
5. Kullanıcıdan karar bekleyen sorular.
6. `berkay-academic-os-road-map.md` dosyasının 0. bölümüne yeni bir alt bölüm (0.20 …) ekle ve en üstteki "Son durum" satırını güncelle.

---

## 8. Belirsiz kalan ve kullanıcıya sorulacak kararlar

Bunlar için makul varsayılanı uygula, raporda açıkça belirt:
- Planlayıcı `agenda` modülünün yerini mi alacak, yoksa ayrı bir `planner` modülü mü olacak? (Varsayılan: `agenda` modülünü dönüştür, sidebar adı "Haftalık Plan".)
- Haftanın başlangıcı Pazartesi, görünür saatler 08:00–24:00 mü?
- Çakışmada varsayılan davranış "en yakın boş aralık" mı, "izin ver + uyar" mı?
- Pomodoro mu, blok süresi kadar kesintisiz odak mı varsayılan olacak?
- Ajanlar varsayılan olarak yalnızca öneri mi gönderecek, yoksa kullanıcı doğrudan yazımı baştan açmak mı istiyor? (Varsayılan: yalnızca öneri.)
- CLI komutunun adı `academic-os` mu olacak, PATH'e kurulum sırasında mı eklenecek?
