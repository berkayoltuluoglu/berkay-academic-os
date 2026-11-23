# Academic OS 1.6.1

23 Kasım 2026. Finans geliştirmeleri, hızlı yapılacaklar listeleri ve günlük faaliyet kaydı Windows paketinde bir araya gelir.

[v1.6.1 kararlı yayını](https://github.com/berkayoltuluoglu/berkay-academic-os/releases/tag/v1.6.1) açıldı.
Herkese açık latest API ve indirilebilir SHA-256 dosyası GitHub girişi olmadan doğrulandı.

## Değişiklikler

- Yapılacaklar ekranında hızlı görev girişi, listeler, ders bağlantısı ve ayrıntı düzenleme.
- Bugün Ne Yaptım? ekranında saat aralığı veya süreyle faaliyet kaydı, günlük toplam ve odak kayıtlarının tek sayılması.
- Görev ve faaliyet değişikliklerinde geri al/yinele; mevcut odak ve çalışma bağlantılarının korunması.
- Finans bölümünde dönem borcu ile toplam kart borcunun ayrılması ve 0 TL kaydı.
- Hesaba bağlı gelir/gider, düzenleme ve silmede bakiye etkisinin geri alınması.
- Aylık taksit çizelgesi ve CSV çıktısı; hesaplar arası transfer.
- CSV/TSV banka ekstresi karşılaştırması; karşılaştırma bakiye veya hareket oluşturmaz.
- Önceki 1.6.0 geliştirmeleri: uygulama içi Windows güncellemesi, logo, saatli sınavlar ve ders iptalleri.
- Haftalık planın ilk açılışı, Bu hafta düğmesi ve hızlı görev tarihi profilin saat dilimini kullanır.
- Windows bakım yardımcısı SHA-256 hesabını doğrudan .NET ile yapar; PowerShell modül yolu nedeniyle
  `Get-FileHash` bulunamadığında güncellemenin durması giderildi.

## Geçiş ve Windows kontrolü

1.6.0 kullanıcıları Ayarlar → Uygulama ve güncellemeler üzerinden 1.6.1'i indirebilir.
1.5.1 ve daha eski sürümlerde güncelleme sistemi yoktur; setup bir kez elle çalıştırılır.
Güncellemeden önce Ayarlar'dan ZIP yedeği alın. Kullanıcı profili kurulumdan ayrı tutulur.
Finans şema 1 okunur; ilk başarılı kayıtta şema 2 ve önceki içeriğin `.bak` yedeği oluşur.
Eski uygulama şema 2'yi okuyamaz; geri dönüş için eski sürüme uygun yedek gerekir.

Kurulumdan sonra Ayarlar'da 1.6.1 sürümünü, eski kayıtları ve yeni ekranları kontrol edin.
İndirme → onay → kapanış → kurulum → yeniden açılış ve tepsi davranışı kullanıcının Windows bilgisayarında ayrıca denenecektir.
0,01 TL ile daha önce bildirilen özgün kapanma tekrar üretilemedi; güncel kayıt/yükleme kontrolleri başarılıdır ve olay takiptedir.
Dijital kod imzası yoktur.

Paketin Google istemcisi içerip içermediği `package-manifest.json` içindeki `googleClientBundled` alanında belirtilir.
Kişisel profil ve Google tokenları dağıtıma dahil edilmez.

## Doğrulama

- Linux'ta ayrı Xvfb ekranında `:app:test :app:installDist --offline --no-daemon -PguiTests=true` başarılı:
  **271 test**, başarısızlık/hata/atlanan yok.
- Windows paketleyici birim testleri: **7 başarılı**.
- Java 21 ile yerel dağıtımın `--self-check` kontrolü başarılı.
- UTC ortamında da 271 uygulama testi başarılı; testlerin Windows dosya yolu/satır sonu uyumu ve kayıt sonunu beklemesi düzeltildi.
- [Windows CI](https://github.com/berkayoltuluoglu/berkay-academic-os/actions/runs/45163789347) başarılı:
  **271 test**, başarısızlık/hata/atlanan yok; 7 paketleyici testi de geçti.
- Windows'ta kurulum, payload hash'leri, paketli Java ve CLI, gerçek JavaFX açılışı,
  aynı bakım yardımcısıyla yükseltme/kaldırma ve kullanıcı verisinin korunması doğrulandı.
- Kaynak commit: `bbab1aebcd433bc5319fa9a391d0968c4c770389`.
- Setup: `BerkayAcademicOS-1.6.1-setup.exe`, **85.608.786 bayt**.
- SHA-256: `3f5615bcacaaa69e089a4c577b1f2c3dd5baedaa23d8705c642c43a4095f9247`.
- İndirilen CI paketinin hash'i yan dosyayla eşleşti; `7z t` arşiv kontrolü başarılı.
- Kullanıcının kurulu 1.6.0 uygulamasından gerçek indirme → onay → kapanış → kurulum → yeniden açılış
  ve tepsi kabulü ayrıca takip edilir. CI bakım kontrolü `restart: false` kullanır; bu kullanıcı akışının yerine geçmez.

Kullanım: [Windows kılavuzu](../packaging/WINDOWS.md).
