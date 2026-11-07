# Academic OS 1.5.0 — Haftalık plan ve ajan arayüzü

Yeni sürüm haftalık saat ızgarası, plan havuzu, oturuma bağlı odak, JSON CLI, öneri kutusu ve otomatik yerleştirme içerir.
Ajan kullanımı: [AGENTS.md](../AGENTS.md), [komut başvurusu](../docs/agent-cli.md).
Doğrulama ve sınırlar: [teslim raporu](../docs/planner-delivery.md).

Kaynak dağıtımı: `./gradlew :app:test :app:installDist --offline --no-daemon`.
CLI: `app/build/install/app/bin/academic-os --help`.
Linux 1.5.0 paketi için [LINUX.md](LINUX.md) kullanılır.
Windows paketleyici `academic-os.cmd` ve ajan kılavuzlarını içerir; yeni Windows EXE ve gerçek Windows kabulü bu oturumda yapılmadı.
Aşağıdaki bölüm önceki **1.4.1 Windows dağıtımının** tarihli kurulum ve entegrasyon kayıtlarıdır.

---

# Academic OS 1.4.1 — Windows kurulumu

Linux masaüstü sürümünün kurulumu ve paketlenmesi için [LINUX.md](LINUX.md) dosyasına bakın.

Windows 10/11 **64 bit** için Java dahil EXE kurulum paketi. Kullanıcıların Java, Gradle veya Python kurmasına gerek yoktur.

## Çıktı ve ilk kullanım

- Kurulum: `app/build/windows/dist/BerkayAcademicOS-1.4.1-setup.exe` (80,6 MiB; Java/PDF bileşenleri dahil).
- SHA-256: aynı klasörde `BerkayAcademicOS-1.4.1-setup.exe.sha256`.
- Kurulum dosyasını Windows bilgisayara kopyalayıp çalıştır; sihirbaz sonunda Academic OS'u aç.
- Varsayılan program konumu: `%LOCALAPPDATA%\Programs\BerkayAcademicOS`.
- Varsayılan kullanıcı verisi: `%LOCALAPPDATA%\BerkayAcademicOS`. Güncelleme ve kaldırmada bu profil tutulur.

`Görevler` teslim saati, öncelik, tahmini/harcanan süre ve alt görevleri içerir. `Bugün` ekranından çalışma oturumu oluşturup bir göreve/ödeve bağlayabilir, odak sayacında çalışma hedefini seçebilirsin. `Ajanda` günlük ve haftalık birleşik görünümü sunar. `Ayarlar` veri konumu, bildirimler, çalışma tercihleri ve toplu ZIP yedekleme/geri yüklemeyi yönetir.

## Finans ve gelişmiş ders planlayıcı

`Finans → Burs ve başvurular` kurum, burs/staj, tarihler, sonuçlar ve hazır belge listelerini takip eder. Tarihler ajanda/hatırlatmalara katılır. Kaynak CSV içe alınabilir; genişletilmiş CSV kimlikleri ve belge hazırlığını korur. Burs kaydı kendiliğinden gelir kaydı oluşturmaz.

`Dersler → Müfredat / ÇAP ve alternatifler` üzerinden müfredat PDF/TXT dosyasını yükle, önizlemeyi incele ve yarıyılı seç. Metin içeren `1. YARIYIL`, numaralı ders satırları, teori/uygulama/kredi/ECTS sütunları ve `Toplam` satırları olan tablo beklenir; taranmış PDF için OCR yoktur. En fazla 50 MB / 100 sayfa. Eksik satırlar, seçmeli havuzları ve tutarsız toplamlar reddedilir. PDF okuma internetsiz çalışır. [Apache PDFBox 3.0.8](https://pdfbox.apache.org/download.html) pakete dahildir.

Anadal ve ikinci bölümün geçmişi ayrı tutulur. Tamamlanan seçmelinin gerçek kodu seçilir; bir ders aynı bölümde iki gereksinimde sayılamaz. Aynı isim/kod iki bölümde otomatik sayılmaz; `Eşdeğerlikleri onayla` ile açık eşleştirme gerekir. Bu kayıt üniversitenin resmi eşdeğerlik kararının yerine geçmez.

Saatler mevcut CSV şube kataloğundan alınır. Kesin/esnek gün ve saatler, haftalık sabit zaman aralıkları, hoca sırası/koşulu, ders/kredi/AKTS/günlük süre ve yoğunluk tercihleriyle en fazla üç alternatif aranır. Arama 120.000 düğüm veya 5 saniyede durur; sınıra ulaşınca daha iyi sonuç olabileceği gösterilir. Seçilecek derslerin kredi/AKTS yükü sınırlanır; günlük süre hesabına elle girilmiş sabit dersler de katılır. Alternatif önce incelenir, yalnızca `Bu programı uygula` ile kaydedilir. Mevcut ödev/sınav kimlikleri ve elle girilmiş dersler korunur; seçilmeyen CSV dersleri program dışına alınır. Bilgiler değişmişse yeniden arama gerekir.

## Google bağlantıları

Bu paket, kullanıcının onayıyla mevcut uygulama **Desktop OAuth istemcisini** içerir. Her kullanıcı kendi Google hesabına giriş yapar. Geliştiricinin kişisel giriş tokenları, takvim verileri, görevleri, finans kayıtları ve notları pakete alınmaz.

1. `Araçlar → Takvim` ekranındaki Google Calendar bağlantısını aç ve etkinlik düzenleme/takvim okuma izinlerini onayla. Bağlı e-posta ekranda görünür. `Takvim kaynakları` ile ana/ders/paylaşılan/tatil takvimlerinden gösterilecek olanları seç. İlk seçim Google takvim listesindeki işaretli kaynakları kullanır; sonraki seçimler hesaba özel saklanır. Google etkinliği oluşturma, düzenleme ve silme ana takvime yazılır; diğer takvimler bu ekranda okunur. Dış değişiklikler varsayılan olarak 30 saniyede bir alınır. Çevrimdışı oluşturma/düzenleme/silme son doğrulanmış hesaba ait kalıcı kuyruğa yazılır. Bağlantı gelince gönderilir; dışarıda değişmiş kayıtlar `Google senkronizasyonu` ekranında karar bekler.
2. `Araçlar → Classroom` ekranından bağlan ve ders/kendi ödevlerini okuma izinlerini onayla. API uygulamanın Google Cloud projesinde etkin olmalıdır.
3. Classroom'da bir ders seçip `Seçili dersi ve ödevlerini aktar` ile yerel Dersler ekranına al. Ders saatlerini elle tanımla; Classroom API bu entegrasyona haftalık ders saati sağlamaz. Yerel tamamlanma işareti Classroom'a teslim göndermez.
4. `Araçlar → Google senkronizasyonu → Google Drive` üzerinden bağlan. Uygulama kendi `Academic OS` klasörünü oluşturur; varsayılan yerel klasör profil altında `data/documents` olur. Belge klasörü seçilebilir; uygulama profilinin tamamı/kimlik bilgileri seçilemez. `Drive'ı 30 saniyede bir otomatik senkronize et` isteğe göre açılır. Drive API uygulamanın Google Cloud projesinde açık olmalıdır.

Drive dosya eşleştirmesi son ortak içerik ile iki tarafı karşılaştırır. İki taraf değişmişse yerel dosya ve indirilen Drive kopyası korunur; kullanıcı yerel/Drive sürümünü seçer. Yerelde silinme Drive çöp kutusuna; uzakta silinme yerel kurtarma kopyasına taşınır. Yerel değişiklikten önceki kopyalar `data/sync/trash`, çakışma kopyaları `data/sync/conflicts` altında saklanır. Klasör derinliği 50, toplam 10.000 kayıt, tek dosya 200 MB sınırı vardır. Google Dokümanları ve Windows ile uyumsuz/çakışan adlar aktarılmaz, gerekçe gösterilir.

`drive.file` izni uygulamanın oluşturduğu veya uygulamaya tek tek verilmiş dosyaları kapsar; tarayıcıdan klasöre eklenen her yeni dosyayı otomatik görme izni değildir. Uygulamanın yüklediği dosyaların uzaktan düzenlenmesi ve aynı OAuth istemcisiyle başka Academic OS kurulumunun oluşturduğu dosyalar iki yönlü alınabilir. [Google Drive izin kapsamı](https://developers.google.com/workspace/drive/api/guides/api-specific-auth). Koşullu güncelleme/silme için Drive v2 ETag/If-Match kullanılır; v3 dosya listesi/oluşturma korunur. [Drive v2 dosya kaynağı](https://developers.google.com/workspace/drive/api/reference/rest/v2/files).

Takvimde `Uygulama kayıtları` açıkken dersler, ödev/sınavlar, görevler, çalışma oturumları, Classroom teslimleri ve burs tarihleri Google kayıtlarıyla birleşir. Gün kutusu ilk üç kaydın başlığını ve kalan sayıyı gösterir; güne basınca tam liste açılır. Kaynaklar renkli işaret ve isimle ayrılır. Yerel kayıtlar ve diğer Google takvimleri Google düzenleme/silme düğmeleriyle değiştirilemez.

Calendar değişiklikleri sunucusuz kontrol edilir; bu sürümde HTTPS webhook sunucusu yoktur. Her seçili takvim için ayrı artımlı `syncToken` kullanılır; 410 durumunda yalnızca ilgili işaret yenilenir. Takvim seçimi ve katalog değişiklikleri de yerel kopyaları yeniler. Kalıcı kuyruk yalnızca doğrulanmış hesabına gönderilir; yanıtı kaybolan oluşturmalar aynı kimlikle tekrar denenerek çift kayıt önlenir. [Calendar artımlı senkronizasyon](https://developers.google.com/workspace/calendar/api/guides/sync).

1.4.0 ve öncesindeki ana takvim izni, diğer takvimleri okumak için yeterli değildir. 1.4.1 ilk bağlantıda `calendar.events.readonly` ve `calendar.calendarlist.readonly` izinlerini ayrıca ister; mevcut sahip olunan etkinlikleri yazma izni korunur. Yeni kayıt `all-calendars-v2` içinde tutulur; eski token, çevrimdışı kuyruk ve şema 1 takvim kopyaları yükseltmede korunur. [Google Calendar izin kapsamları](https://developers.google.com/workspace/calendar/api/auth).

Calendar, Classroom ve Drive ayrı izin/token kayıtları kullanır. İstemciyi değiştirmek veya yerel bağlantıları kaldırmak için `Ayarlar` kullanılır. ZIP yedeklerinde Google tokenları bulunmaz; yeni bilgisayarda yeniden giriş gerekir.

12 Ekim 2026 canlı doğrulaması: `oltuluoglumehmeteren@gmail.com` hesabında takvim oluşturma/düzenleme/okuma/silme başarılı; Classroom'dan 18 ders ve 36 ödev okundu. Test etkinliği silindi. 13 Ekim 2026’da aynı hesapla Calendar tam/artımlı değişiklik akışı ve Drive gerçek yükleme, sürüm korumalı güncelleme, bağımsız indirme ve çöp kutusuna taşıma doğrulandı. Geçici test dosyaları temizlendi; uygulamanın Academic OS klasörü korundu. Takvim düzeltmesinin canlı kontrolünde aynı hesapta 26 takvim ve Ekim ayında ekran görüntüsüyle eşleşen 7 Google kaydı okundu; tam/artımlı çoklu takvim senkronizasyonu doğrulandı.

Başka insanlara Google girişi sunmadan önce proje sahibinin [OAuth Audience ayarlarını](https://console.cloud.google.com/auth/audience?project=berkay-academic-os) kontrol etmesi gerekir. Google, Testing durumunda test kullanıcıları ve yetkilendirme süresi sınırları uygular; geniş dağıtım için uygulamanın yayın/doğrulama durumunu hesabın kapsamına göre hazırlamak gerekir. [Google'ın resmi uygulama kitlesi belgesi](https://support.google.com/cloud/answer/15549945?hl=en).

## Pencere kapanınca hatırlatmalar

`Ayarlar → Pencere kapanınca arka planda sürdür` açıksa ve sistem tepsisi destekleniyorsa pencerenin çarpısı uygulamayı tepsiye gizler. Teslim, çalışma/Google etkinliği başlangıcı ve odak bitişi bildirimleri işlem açık kaldığı sürece sürer. Tepsi menüsünden tam çıkış, Windows oturumunun kapanması veya bilgisayarın kapanması bunları durdurur. Oturum açılışında otomatik başlama eklenmedi.

Tepsi desteklenmeyen sistemde uygulama açık pencere bildirimini kullanır; bu durumda pencere kapanınca arka planda kalamaz. Bildirimlerin gerçek Windows davranışı kabul testine dahildir.

## Paketi yeniden üretme

Paket üreten bilgisayarda JDK 21, Python 3 ve NSIS 3 gerekir. JavaFX Windows bağımlılıkları ve Windows JRE internetten alınır. JRE arşivinin SHA-256 değeri Adoptium API metadata'sıyla doğrulanır; kullanılan sürüm/kaynak `package-manifest.json` içinde saklanır.

Linux üzerinde NSIS ile:

```bash
python3 packaging/build_windows.py \
  --makensis /path/to/makensis \
  --nsis-dir /path/to/nsis \
  --java-home /usr/lib/jvm/java-21-openjdk-amd64 \
  --google-client config/google-calendar-credentials.json
```

Windows üzerinde PowerShell ile:

```powershell
python packaging/build_windows.py `
  --makensis 'C:/Program Files (x86)/NSIS/makensis.exe' `
  --java-home $env:JAVA_HOME `
  --google-client config/google-calendar-credentials.json
```

`--google-client` yalnızca uygulamaya ait `installed` Desktop istemci JSON'unu kabul eder. Access/refresh token dosyası kullanma. Bu seçenek verilmezse kullanıcıların Ayarlar ekranından kendi Desktop istemci dosyasını seçmesi gerekir. Linux geliştirici ortamındaki Script/UART modülleri paketli Windows varsayılan menüsünde kapalıdır; Windows uyarlaması sonraki kapsamdadır.

## Windows kabul testi

Linux'ta ürün testleri **170 başarılı, 0 başarısız, 0 hata, 0 atlanan**. Son JavaFX kabuğu ayrıca boş profille gerçek pencerede normal boyutta çizildi. Boş profil kabuğu: `app/build/reports/academic-os-release-1.4.1.png`; birleşik takvim arayüz testi: `app/build/reports/unified-calendar.png`.

Bu bilgisayarda gerçek Windows kurulum testi yapılmadı. `.github/workflows/windows-package.yml` Windows 2022 üzerinde tüm testleri çalıştırır, kurulum üretir ve aşağıdaki doğrulama scriptini çalıştırır:

```powershell
./packaging/verify_windows.ps1 -Installer (Resolve-Path './app/build/windows/dist/BerkayAcademicOS-1.4.1-setup.exe')
```

Bu script **kurar, gerçek EXE'yi başlatıp JavaFX ekranını doğrular, günceller ve kaldırır**. Ayrı kullanıcı profiline yazdığı kontrol dosyasının korunduğunu ve eski JAR'ların güncellemede kaldırıldığını denetler. Temiz bir Windows test hesabında çalıştır; kayıtlı mevcut kurulum varsa test onu değiştirmeden durur. Her koşuda yeni test profili kullanılır. Manuel kabulte pencereyi kapatıp tepsiden yeniden açmayı ve bir teslim/odak bildirimini de dene.

Push kullanıcıya bırakıldı. Kaynakları uygun dala gönderdikten sonra GitHub Actions'tan `Windows package verification` akışını başlatabilirsin. `codex/windows-release-*` dallarına push akışı otomatik başlatır; manuel çalıştırma için workflow dosyasının varsayılan dalda bulunması gerekir. CI paketinin mevcut yayıncı istemcisini de içermesi için depoda `GOOGLE_OAUTH_CLIENT_JSON` Actions secret'ı tanımla:

```bash
gh secret set GOOGLE_OAUTH_CLIENT_JSON < config/google-calendar-credentials.json
```

İstemci/token dosyalarını kaynak commit'ine ekleme. `data/tasks.json` halen Git tarafından izlenen mevcut bir dosyadır; kişisel veri değişikliklerini kaynak commit'ine dahil etme. Kurulum çıktıları ve token/profil klasörleri Git dışında tutulur.

Windows testi ve Google OAuth dağıtım ayarları tamamlanınca kurulum insanlara dağıtılabilir. Dijital kod imzası ve otomatik güncelleme bu sürüme eklenmedi.
