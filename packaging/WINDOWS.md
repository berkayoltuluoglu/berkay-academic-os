# Academic OS 1.5.1 — Windows kurulumu ve dış ajanla planlama

Windows 10/11 x64 içindir. Java 21, JavaFX ve planlama CLI'ı pakettedir; Java, Python veya Gradle kurman gerekmez.
UART ve script modülleri Windows menüsünde kapalıdır. Uygulamanın içinde model veya AI API anahtarı bulunmaz.

## Kurulum

1. `BerkayAcademicOS-1.5.1-setup.exe` dosyasını Windows'a kopyalayıp çalıştır.
2. Kurulum sonunda Academic OS'u aç. Varsayılan program klasörü `%LOCALAPPDATA%\Programs\BerkayAcademicOS`.
3. Kayıtlar `%LOCALAPPDATA%\BerkayAcademicOS` altında tutulur; Ayarlar'da veri konumunu görebilirsin.
   Güncelleme ve kaldırma bu ayrı profili silmez. Program klasörünü veri klasörü olarak seçme.
4. Mevcut Linux verilerini taşımak için uygulamanın Ayarlar ekranından ZIP yedeği dışa aktar;
   Windows'ta Ayarlar'dan geri yükle. Google girişleri yedeğe dahil değildir; Windows'ta yeniden bağlan.
   Drive/Belge Merkezi için saklanmış Linux klasör yolları varsa Windows'taki klasörlerini yeniden seç.

Paket dijital imzasızdır; Windows yayıncıyı doğrulanmamış gösterebilir. Yanındaki `.sha256` dosyasıyla
dosyanın aynı olduğunu PowerShell'de kontrol edebilirsin:

```powershell
Get-FileHash .\BerkayAcademicOS-1.5.1-setup.exe -Algorithm SHA256
```

Bu pakete kişisel veri, Google tokenı veya Google istemci dosyası eklenmez. Google kullanacaksan
Ayarlar'dan kendi Desktop OAuth istemci dosyanı seç, ardından Calendar/Classroom/Drive bağlantılarını aç.
Classroom ödevlerini ve Google etkinliklerini uygulamada yenile; ajan CLI'ı Google ağına bağlanmaz.

## Kurulum sonrası kısa kontrol

Bugün, Haftalık Plan, Dersler ve Ayarlar ekranlarını aç. Haftalık planın teslim şeridini kontrol et.
Yeni bir görev/blok ekle, geri al/yinele işlemini dene. Pencereyi kapatıp tepsiden geri açma tercihini
Ayarlar'dan yönetebilirsin; tam çıkış veya bilgisayarın kapanması hatırlatmaları durdurur.

PowerShell'de CLI kontrolü:

```powershell
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$academicCli = "$env:LOCALAPPDATA\Programs\BerkayAcademicOS\academic-os.cmd"
& $academicCli --help
$planDate = (Get-Date).Date
$planWeek = $planDate.AddDays(-(([int]$planDate.DayOfWeek + 6) % 7)).ToString('yyyy-MM-dd')
& $academicCli snapshot --week $planWeek
```

Programı farklı klasöre kurduysan ilk satırdaki yolu değiştir. CLI uygulamanın seçtiği veri konumunu
otomatik çözer; varsayılan profili ayrıca elle değiştirme. Snapshot `errors` içeriyorsa ilgili kaynağı
uygulamadan düzeltmeden planlama başlatma. İlk normal açılış profil klasörüne ajan kılavuzlarını koyar;
kurulumdaki `agent-guide` klasöründe de kopyaları bulunur. Windows PATH'i değiştirilmez.

## Diğer AI'ya verilecek istek

Terminal ve yerel dosya erişimi olan ajana şu metni ver; tarih/boş zaman tercihlerini kendine göre ekle:

> Academic OS üzerinden bu haftaki çalışma planımı düzenle. Önce kurulu programın `agent-guide/AGENTS.md`
> ve `agent-guide/CLI.md` dosyalarını oku. CLI yolu varsayılan kurulumda
> `%LOCALAPPDATA%\Programs\BerkayAcademicOS\academic-os.cmd`.
> Snapshot, pool, free-slots ve schema çıktılarını sırayla al. Snapshot'taki saat dilimini ve tercihleri kullan.
> Kaynak hatası varsa durup bana bildir. Ders/Google etkinliklerini koru; teslimden sonraya çalışma koyma.
> Güncel fingerprint ile gerekçeli öneri hazırla, UTF-8 JSON kaydet, validate geçerse propose ile gönder.
> Sonucu proposals ve proposal-status ile kontrol et. Öneriyi uygulamada ben kabul edeceğim.
> Profil JSON/properties dosyalarını elle düzenleme, doğrudan yazım ayarını açma ve --apply kullanma.
> Sığmayan işleri ve nedenlerini bildir. suggest sonucu proposal:null ise boş öneri göndermeye çalışma.

Yalnızca sohbet eden ve yerel terminal erişimi olmayan bir AI, CLI'ı kendi başına çalıştıramaz;
ona CLI çıktılarını senin aktarman gerekir. Öneriler Haftalık Plan → Öneriler içinde görünür;
komutları inceleyip seçerek kabul edebilirsin. Google'a ödev teslim edilmez.

## Bu sürümün sınırları

- Kişisel Windows kullanımına hazırlanmıştır; gerçek Windows kurulum/açılış kabulü bu makinede yapılmamıştır.
- Dijital kod imzası ve otomatik güncelleme yoktur.
- Gece yarısını geçen çalışma bloğu yoktur; yerel şemada son bitiş 23:59.
- Dar ekranda yedi gün yatay kaydırılır; ayrıntı paneli kapatılabilir.
- Teslim sonrası çalışma, ajan önerisinde engellenir; elle planlamadaki gecikmiş iş politikası ayrı takip konusudur.
- Genel şema hatalarının ayrıntıları ve bildirimlerin uygulama içinde toplanması sonraki iyileştirmelerdir.
