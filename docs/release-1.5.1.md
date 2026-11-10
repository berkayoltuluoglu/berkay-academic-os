# Academic OS 1.5.1 — Windows hazırlık incelemesi

10 Kasım 2026. Hedef: kullanıcının Windows 10/11 x64 bilgisayarına kurup dış ajanla haftalık plan üretmesi.
Kaynak ve Linux üzerindeki kontroller bu kullanım için uygun; geniş dağıtımın Windows kabulü henüz tamamlanmış değildir.
Gerçek Windows kurulum/açılış, tepsi ve bildirim davranışı Windows bilgisayarda ayrıca denenmelidir.

## Düzeltilen dağıtım sorunları

- Windows paketleyici yeni çıktı dizininde payload oluşturmadan dosya kopyalıyordu; temiz derleme düzeltildi.
- Sürüm 1.5.1. Eski/farklı sürüm uygulama JAR'ı veya Linux/macOS JavaFX bağımlılığı içeren girdi reddedilir.
- Sürüm başına payload üretilir; eski yayınların çıktıları korunur. Yükseltmede eski uygulama kütüphaneleri temizlenir.
- `academic-os.cmd`, yerleşik Java çalışma ortamı ve ajan belgeleri kurulumdadır. Self-check belgeleri de denetler.
- Profil başka bilgisayardan taşındığında Windows'ta desteklenmeyen UART/script araçları görünümde kapalı kalır;
  kullanıcının saklanan ayar dosyası bu uyarlama için yeniden yazılmaz.
- CLI stdout'u Windows konsol kodlamasından bağımsız UTF-8 JSON üretir; Türkçe hata metni alt süreç testiyle doğrulanır.
- Manifest payload dosyalarının SHA-256 değerlerini içerir. Kurulum ayrıca dış SHA-256 dosyasıyla teslim edilir.
- Windows doğrulama scripti kurulumdaki dosya hash'lerini, Unicode/geçici profilde CLI öneri döngüsünü,
  gerçek EXE açılışını, yükseltmeyi ve kaldırmada profil korunmasını denetleyecek şekilde genişletildi.

## Doğrulama

- İzole Xvfb ekranında `:app:test :app:installDist -PguiTests=true --offline --no-daemon`:
  **210 test başarılı; başarısızlık, hata ve atlanan test yok.** Kullanıcının masaüstünde pencere açılmadı.
- `python3 -m unittest discover -s packaging -p 'test_*.py'`: **11 paketleme testi başarılı**.
  Windows paketleyici testlerinde sahte derleyici kullanılır; bunlar gerçek Windows kurulumu değildir.
- `:app:stageWindows -PjavafxPlatform=win --offline --no-daemon`: başarılı.
- Son Windows payload'ı üzerinde manifestteki **366 dosyanın hash'i** ve sahnelenen son uygulama JAR'ıyla
  birebir eşitlik doğrulandı. Java/launcher PE dosyaları ve kişisel profil/istemci içermeyen paket yapısı kontrol edildi.
- Payload kütüphaneleri Linux JDK 21 ile çalıştırılarak self-check; snapshot/pool/free-slots/schema;
  bağlantılı create-task/schedule için validate → propose → pending; kabul öncesi gerçek planın değişmemesi;
  APPLY_REQUIRED/DIRECT_WRITES_DISABLED engelleri ve UTF-8 çıktı doğrulandı.
  Bu kontrol Windows JavaFX native kütüphanelerini veya Windows JRE'yi çalıştırmaz.
  Kayıt: `app/build/reports/windows-release-cli.json`.

## Kullanım ve kalan sınırlar

[Windows kılavuzu](../packaging/WINDOWS.md) kurulum, ZIP ile Linux verisi taşıma, Google'a yeniden giriş,
CLI başlangıcı ve diğer AI'ya verilecek hazır planlama isteğini içerir.
Öneriler uygulamada kullanıcı kabulüne sunulur; profil dosyalarına elle plan yazılmaz.
Bu paket kişisel veri, Google tokenı veya Google Desktop istemcisi içermez. Google için kendi istemcisi Ayarlar'dan seçilir.

Kod imzası ve otomatik güncelleme yoktur. UART/scriptlerin Windows uyarlaması kapsam dışıdır.
Dar ekran düzeni, uygulama içi bildirim geçmişi ve elle planlamada gecikmiş iş politikası
[kalan işler](remaining-work.md) içinde takip edilir. Ajan önerileri teslim sonrası/geçmiş gün/çakışma denetimlerini uygular.
Kaynakta bir Windows kabul scriptinin bulunması, bu incelemede Windows üzerinde çalıştırıldığı anlamına gelmez.

## Çıktı kaydı

- Setup: `app/build/windows/dist/BerkayAcademicOS-1.5.1-setup.exe`.
- Boyut: **84.749.583 bayt / 80,8 MiB**.
- SHA-256: `0bb875133be93d879cda8cb46e3f9efc639e0edc5350dca7e78bfcb82abee5fc`.
- Dahil edilen Windows x64 JRE: **Temurin 21.0.13+9 LTS**; indirme hash'i Adoptium metadata'sıyla doğrulandı.
- Son EXE için `7z t` başarılı. EXE geçici dizine açıldı; içindeki manifestin **366 dosyası** hash'leriyle
  doğrulandı ve nihai payload manifestiyle aynı olduğu kontrol edildi.
- Paket bütünlüğü kaydı: `app/build/reports/windows-release-installer.json`.
- Yan çıktılar: `.exe.sha256` ve `BerkayAcademicOS-1.5.1-Windows-Kullanim.md`.
- Gerçek Windows işletim sistemi üzerinde çalıştırma: **yapılmadı**. Sonraki kabul kullanıcının Windows bilgisayarındadır.
