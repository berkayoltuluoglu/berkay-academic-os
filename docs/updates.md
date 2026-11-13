# 1.6.0 güncelleme sistemi ve yayınlama

Güncelleme kaynağı herkese açık `berkayoltuluoglu/berkay-academic-os` GitHub deposudur.
Java 21 HttpClient kullanılır; GitHub/OAuth anahtarı ürüne eklenmez. Kontrol ve indirme ayrı sanal iş parçacıklarında yapılır.
Sürüm numarası eski kullanıcı config'inden değil, uygulama JAR'ına gömülü sürümden okunur.

## Kullanıcı akışı

Ayarlar → Uygulama ve güncellemeler: açılış kontrolü (varsayılan açık), otomatik indirme (varsayılan kapalı),
elle kontrol, sürüm notları, indirme/iptal, onaylı yeniden başlatma ve kaldırma.
Yeni sürüm ve hazır indirme ana pencerenin durum alanında da duyurulur.
Güncelleme yalnızca kurulu Windows paketinde etkindir; kaynak geliştirme ve CLI kendiliğinden ağ kontrolü başlatmaz.

Güncelleme paketi bütünüyle indirilir; fark paketi veya kaldığı yerden devam yoktur.
Dosya adı, repo/tag bağlantısı, boyutu, Windows başlığı ve SHA-256 doğrulanır.
Eksik `.sha256`, hatalı hash, yarım indirme, aşırı boyut veya beklenmeyen bağlantı kurulumu engeller.
Önbellek tekrar kullanılmadan ve kurulum hazırlanırken yeniden doğrulanır.
İndirilen setup ile bakım yardımcısı program klasörü dışında tutulur; eski JRE değiştirilebilir.

Kullanıcı onayından sonra kayıtlar tamamlanır; çalışan odak oturumu varsa önce duraklatılması istenir.
PowerShell yardımcısı hazır olduğunu bildirir, Java ve varsa NSIS başlatıcı sürecinin bitmesini bekler,
dosya hash'ini tekrar kontrol eder ve setup'ı `/S` ile aynı klasöre kurar.
NSIS'in `/D` yolunun son ve tırnaksız argüman olması koşulu uygulanır
([NSIS komut satırı](https://nsis.sourceforge.io/Docs/Chapter3.html#installerusage)).
Yardımcı script Windows PowerShell 5.1'in Türkçe karakterleri doğru okuması için UTF-8 BOM içerir
([Microsoft kodlama belgesi](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_character_encoding?view=powershell-5.1)).
Başarılı kurulumdan sonra EXE bir kez açılır. Hata sonuç dosyasına yazılır ve Windows mesajıyla gösterilir.
Tam kapanış veya kurulum hatasında yardımcı süreç uygulamaları zorla sonlandırmaz.
Kurulum kısmen başarısız olursa elle setup tekrar çalıştırılır; otomatik rollback yoktur.

Kaldırıcı da aynı kapanış akışını kullanır. `Uninstall.exe`, Başlat menüsü ve Windows uygulama listesi ayrıca kullanılabilir.
Kullanıcı profili kaldırmada silinmez. Güncelleme önbelleği `%LOCALAPPDATA%\BerkayAcademicOS-updates` altındadır.
Tercihler uygulamanın `data/updates.properties` dosyasında tutulur; ajan profil dosyalarını elle değiştirmez.

## İlk 1.6.0 geçişi

1. Onaylanan logo `branding/logo.png`, pencere PNG'leri ve çok boyutlu `branding/app-icon.ico` olarak kaynaklardadır.
   Pencere başlığı, ana/odak penceresi, tepsi, setup, kaldırıcı ve EXE/kısayollar bu varlıkları kullanır.
   Logo değişirse `packaging/build_branding.py` ile ikonları yeniden üretip uygulama JAR'ını ve setup'ı tekrar derle.
2. Testleri ve `:app:stageWindows -PjavafxPlatform=win` çalıştır; son kaynaklardan setup üret.
3. Windows'ta temiz/geçici hesapla `packaging/verify_windows.ps1` çalıştır. Bu script kurulum, gerçek JavaFX smoke,
   Unicode/boşluklu yollar, CLI önerisi, yardımcı süreçle güncelleme/kaldırma ve profil korunmasını denetler.
   Uygulama içi indirme → onay → gerçek kapanış → yeniden açılış ayrıca Windows'ta elle denenir.
4. Kaynakları commit/push et. `data/tasks.json`, giriş tokenları ve istemci sırlarını commit'e ekleme.
5. Son setup, SHA-256 ve kılavuzu **aynı kararlı release** içinde yayımla.
6. 1.5.1 kullanıcıları 1.6.0 setup'ını bir kez elle kurar. Sonraki sürümler uygulamadan indirilir.

Bu sürümün kaynaklarını gönderirken kişisel `data/tasks.json` dosyasını dahil etmeden:

```bash
git add .github/workflows/windows-package.yml app/build.gradle app/src docs packaging berkay-academic-os-road-map.md notes/design-references/academic-os-logo-master.png
git diff --cached --stat
git commit -m "release: add Windows updates, uninstaller access and branding for 1.6.0"
git push origin master
```

## Sonraki sürümü yayımlamak

Sürümü `app/build.gradle` ve kaynak `config.json` içinde birlikte artır. Kılavuzları ve sürüm notlarını güncelle.
Yeni sürümün kaynak/test/kurulum kontrolleri bittikten ve commit GitHub'a gönderildikten sonra örnek:

```bash
gh release create v1.6.1 \
  app/build/windows/dist/BerkayAcademicOS-1.6.1-setup.exe \
  app/build/windows/dist/BerkayAcademicOS-1.6.1-setup.exe.sha256 \
  app/build/windows/dist/BerkayAcademicOS-1.6.1-Windows-Kullanim.md \
  --target master --draft \
  --title "Academic OS 1.6.1" \
  --notes "Bu sürümdeki değişiklikler"

# Dosyalar yüklendikten sonra yayımla:
gh release edit v1.6.1 --draft=false --latest
```

İlk yayın için örnekteki bütün `1.6.1` değerlerini `1.6.0` yap.
`vX.Y.Z` etiketi, uygulama sürümü ve setup adı eşleşmeli. Draft/prerelease otomatik güncellemede kullanılmaz.
Dosyalar yüklenmeden yayını açma; yukarıdaki akış önce taslak oluşturur ve yükleme bittikten sonra yayımlar.
Aynı sürümdeki dosyayı değiştirmek yerine yeni bir sürüm çıkar.

## Doğrulama sınırı

Linux testleri sahte HTTP yanıtları ve geçici profiller kullanır; gerçek GitHub release'i veya kullanıcı planı değiştirilmez.
JavaFX testleri yalnızca ayrı sanal ekranda açılır. Bir Windows kabul scriptinin bulunması, Linux üzerinde Windows
PowerShell/installer çalıştırıldığı anlamına gelmez. Windows kabulünün sonucu sürüm tesliminde ayrıca belirtilir.
SHA-256 dosya bütünlüğünü denetler; Windows yayıncı kod imzası ayrıca ele alınır.
