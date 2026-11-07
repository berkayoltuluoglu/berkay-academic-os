# Berkay Academic OS 1.5.0 — Linux masaüstü sürümü

Linux x64 sürümü Java 21 ve JavaFX dahil gelir. Çalıştırmak için ayrıca Java veya Gradle kurmak gerekmez. GTK 3 ve standart masaüstü kütüphaneleri gerekir; hedef ortam Linux Mint 22 / Ubuntu 24.04 tabanlı masaüstüdür.

## Kurulum ve açılış

`BerkayAcademicOS-1.5.0-linux-x64.tar.gz` dosyasını açıp içindeki klasörde `./install.sh` çalıştır. Sudo kullanma. Kurulum uygulama menüsüne **Berkay Academic OS** ekler; tanımlı bir masaüstü klasörü varsa oraya da kısayol koyar. Masaüstü yöneticisi isterse kısayolun menüsünden **Başlatmaya izin ver** seçilir.

Mevcut geliştirici profilini bu bilgisayarda kopyalamak için:

```bash
./install.sh --import-profile /home/berkay/projects/berkay-academic-os
```

Aktarım öncesinde açık Academic OS'tan sistem tepsisi üzerinden tamamen çık. Görevler, dersler, notlar, tercihler ve bu bilgisayardaki Google bağlantıları kopyalanır. Kaynak dosyalar değiştirilmez. Hedef profil varsa aktarım reddedilir. `backups/before-linux-install-*.zip` uygulama içinden okunabilen, Google tokenlarını içermeyen bir veri yedeğidir. Google tokenları yalnızca yerel profil aktarımında korunur; dağıtım arşivinde kişisel veri veya Google istemci/token dosyası bulunmaz. Yeni bir bilgisayarda Ayarlar üzerinden Desktop OAuth istemcisi seçilir ve Google'a giriş yapılır.

- Program: `~/.local/opt/berkay-academic-os/releases/1.5.0`
- Etkin sürüm: `~/.local/opt/berkay-academic-os/current`
- Veriler: `${XDG_DATA_HOME:-~/.local/share}/berkay-academic-os`
- Başlatıcı: `~/.local/share/applications/berkay-academic-os.desktop` (XDG_DATA_HOME desteklenir)
- Hata kaydı: `${XDG_STATE_HOME:-~/.local/state}/berkay-academic-os/application.log`

Program verileri çalışma klasörüne bağlı değildir. Ayarlar'dan veri konumu değiştirilebilir. Güncellemeler mevcut kullanıcı profiline dokunmaz, önceki sürüm klasörleri korunur. Kurulu programı kaldırırken kullanıcı profilini ayrıca silmedikçe veriler kalır.

İlk paketli açılışta Belge Merkezi profil altındaki `data/documents` klasörünü kullanır; geliştiricinin Drive mount yoluna bağlı değildir. Linux'ta UART ve script modülleri korunur. UART için mevcut sistemin seri port erişimi, scriptler için seçilen yorumlayıcı gerekir.

## Günlük kullanım

`Haftalık Plan` havuzundan işi ızgaraya sürükle veya boş alanda aralık seçip başlığı yaz.
Bloklar taşınır ve alt kenardan uzatılır; ayrıntılar yan panelde otomatik kaydedilir.
Bloktan `Odakla` ile çalış; `Otomatik yerleştir` ve dış ajan önerileri kullanıcı kabulünü bekler.
CLI `~/.local/bin/academic-os` konumuna kurulur (bu dizin PATH'inde olmalı); paket içinden `./academic-os-cli` da kullanılabilir.
Profilde AGENTS.md / CLAUDE.md / CLI.md kılavuzları oluşturulur. Ajanların doğrudan yazımı Ayarlar'dan açılmadıkça kapalıdır.

Görev eklemek için Bugün ekranındaki eski akış da kullanılabilir. Aynı görev için farklı günlerde oturumlar eklenebilir. Oturum ve görevin tamamlanma işaretleri ayrıdır. Her gün otomatik tekrar oluşturma yoktur.

Pencere kapanınca arka planda çalışma Ayarlar'dan yönetilir; sistem tepsisi desteğine bağlıdır. Tam çıkış veya bilgisayarın kapanması hatırlatmaları durdurur. Oturum açılışında otomatik başlama kurulmaz.

## Yeniden üretme ve doğrulama

Kaynak depoda JDK 21 ve Python 3.11+ ile:

```bash
python3 packaging/build_linux.py --java-home /usr/lib/jvm/java-21-openjdk-amd64 --offline
```

Çıktı `app/build/linux/dist/` altındadır; yanında SHA-256 sağlama toplamı bulunur. Paket yalnızca uygulama kütüphaneleri, Java runtime, başlatıcı, kurulum aracı ve örnek scripti içerir. `./academic-os --self-check` kaynakları ve Java sürümünü kontrol eder. Gerçek JavaFX kontrolü geçici profilde `./academic-os --smoke-test /tmp/academic-os-smoke-profile` ile yapılır.

Kurulum planını yazmadan görmek için `./install.sh --dry-run` kullanılır. Paket içeriği kurulumdan önce ve kopyalandıktan sonra SHA-256 manifestiyle doğrulanır.

Geliştirici doğrulaması: `python3 -m unittest discover -s packaging -p 'test_linux_installer.py' -v`. Tam paket kontrolü için `python3 packaging/verify_linux.py --package app/build/linux/payload/BerkayAcademicOS-1.5.0-linux-x64 --gui` kullanılır. Bu akış yalıtılmış profilde veri aktarımı, uygulamanın Java yedek okuyucusu, yeniden kurulum, menü girdisi ve paketli JavaFX penceresini doğrular; gerçek kullanıcı profiline dokunmaz.
