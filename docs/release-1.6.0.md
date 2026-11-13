# Academic OS 1.6.0 — Windows güncellemeleri ve kaldırma

13 Kasım 2026. Bu sürüm uygulama içinden yeni kararlı GitHub Release kontrolü, isteğe bağlı otomatik indirme,
SHA-256 doğrulama, onaylı kurulum/yeniden başlatma ve kaldırma ekler.
Kullanıcının onayladığı kahverengi komut simgeli logo uygulama başlığına, ana/odak penceresine,
tepsiye, Windows EXE/kısayollarına, setup ve kaldırıcıya eklendi.
İlk 1.6.0 geçişi elle yapılır. Kullanım ve yayınlama: [updates.md](updates.md), [Windows kılavuzu](../packaging/WINDOWS.md).

Saat aralıklı sınav kaydı ve tarih bazlı ders iptalleri bu pakete eklendi. Kullanım ve veri geçişi:
[Sınavlar ve ders iptalleri](exams-and-cancellations.md). Sınavlar sabit blok olarak planlayıcıya
aktarılır; ders iptali yalnızca seçilen tarihleri etkiler. Gerçek kullanıcı kayıtlarına örnek eklenmez.

## Uygulanan davranış

- Güncelleme yalnızca kurulu Windows paketinde açılır. Açılış kontrolü varsayılan açık; otomatik indirme kapalıdır.
- Sürüm kaynağı JAR'ın kendi config'idir; eski kullanıcı ayarı güncelleme karşılaştırmasını değiştirmez.
- GitHub deposu herkese açıktır; ürüne giriş tokenı eklenmez. Kaynak/CLI kendiliğinden güncelleme ağına bağlanmaz.
- Sürüm notları ve indirme ilerlemesi Ayarlar'dadır. İndirme, sayfa değiştirilse de sürer; kullanıcı iptal edebilir.
- Yeni kararlı sürüm, doğru repo/tag dosyaları, boyut, Windows dosya başlığı ve SHA-256 zorunludur.
  Hatalı/yarım indirme kurulum için hazır sayılmaz; değiştirilmiş önbellek tekrar indirilir.
- Metadata yanıtları 30 saniye, setup gövdesi yavaş bağlantılar için en çok iki saat bekler; indirme iptali desteklenir.
- Kurulum ve kaldırma kullanıcı onayından sonra hazırlanır; aktif odak önce duraklatılır.
  Bekleyen düzenlemeler tamamlanır. Yardımcı süreç eski Java ve başlatıcının kapanışını bekler.
- Güncelleme başarıyla kurulursa uygulama bir kez açılır. Kaldırmada kullanıcı profili korunur.
  `Uninstall.exe` setup tarafından program klasöründe üretilir; Ayarlar, Başlat menüsü ve Windows uygulama listesi erişimi vardır.
- PowerShell yardımcısı UTF-8 BOM içerir; Windows PowerShell 5.1 Türkçe hata metinlerini doğru okur.

## Doğrulama

- Logolu arayüzle izole Xvfb ekranında tüm **235 uygulama testi** başarılı: başarısızlık/hata/atlanan yok.
  HTTP yanıtları ve profiller geçicidir; kullanıcının masaüstünde pencere veya gerçek plan kaydı açılmadı.
- Son PowerShell kodlama ve indirme süresi düzeltmeleri hedefli **13 güncelleme testi** ile ayrıca doğrulandı.
- Python paketleme testleri: **12 başarılı**; özel ikonun EXE/setup derleyicilerine verilmesi dahil.
- Windows kabul scripti UTF-8/boşluklu kurulum yollarını, kurulumdaki `Uninstall.exe` varlığını,
  gerçek JavaFX smoke ve CLI akışını, paketteki yardımcı süreçle yükseltme/kaldırmayı denetleyecek şekilde güncellendi.
- Gerçek Windows işletim sistemi, PowerShell ve installer yürütmesi bu Linux makinede yapılmadı.
  Uygulama içi indirme → onay → kapanış → kurulum → yeniden açılış Windows'ta ayrıca denenmelidir.

## Çıktı ve logo

Setup: `app/build/windows/dist/BerkayAcademicOS-1.6.0-setup.exe`.
Yanında SHA-256 ve Windows kullanım kılavuzu bulunur. Paket kişisel veri, Google tokenı veya istemcisi içermez.
Son arşiv ve JAR eşleşme kaydı: `app/build/reports/windows-release-1.6.0.json`.

- Sınav ve ders iptali eklenmiş son setup: **85.624.896 bayt / 81,7 MiB**.
- SHA-256: `e96b0aff02f6a6466c272ec57a5aaed79be6e8c4911786b3405a0cf582540ff9`.
- `7z t` başarılı; setup geçici dizine açılarak manifestteki **366 dosyanın hash'i** doğrulandı.
- İçindeki uygulama JAR'ı son sahnelenmiş/test edilmiş JAR ile aynı; sürüm ve PowerShell kaynağı eşleşti.
- PNG/ICO kaynaklarının JAR'a girdiği ve özel ikonun **dokuz DIB çerçevesinin** uygulama EXE'si ile setup'ın
  yerel ikon kaynaklarında bulunduğu doğrulandı. Manifest `customIconBundled: true` içerir.
- Windows payload sınıfları Linux JDK 21 ile headless self-check ve geçici profilli CLI snapshot kontrolünden geçti.
  Bu kontrol Windows JRE/native JavaFX veya gerçek Windows kurulumu çalıştırmaz.

Logo master'ı `notes/design-references/academic-os-logo-master.png` içindedir. Kullanıcının görseli referans alınarak
built-in imagegen ile bitmap ikon uyarlaması üretildi; kullanılan istem `branding/README.md` içinde kayıtlıdır.
512 piksel PNG, 32/64/128/256 pencere ikonları ve 16/20/24/32/40/48/64/128/256 boyutlu Windows ICO kaynağa eklendi.
`packaging/build_branding.py` yeniden üretim aracıdır; normal build'de hazır ikonlar kullanılır.
Yayınlama öncesinde gerçek Windows kabulü tamamlanmalıdır.
Kod imzası, fark paketleri, kaldığı yerden indirme ve kurulum hatasında otomatik rollback kapsam dışındadır.
