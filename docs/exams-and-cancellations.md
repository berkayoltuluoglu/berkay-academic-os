# Saat aralıklı sınavlar ve tarih bazlı ders iptalleri

## Sınav eklemek

**Dersler → Ödev ve sınavlar → Ödev / sınav ekle** yolunu aç. Dersi seç, başlığı yaz ve türü
**Sınav** yap. Tarih, başlangıç ve bitiş saatini girip kaydet. Örneğin 18 Kasım 2026 için
`09:00`–`10:20` geçerlidir; sınav saatleri çalışma bloklarının 15 dakika ızgarasıyla sınırlanmaz.
Başlangıç ve bitiş aynı gündedir; bitiş daha geç olmalıdır. Hatalı girişte form kapanmaz, yazılanlar korunur.
Tarih kutusuna elle yazılan değer de kaydetmeden önce işlenir. Saatler `SS:DD` biçimindedir.

Sınav; Bugün, Haftalık Plan, Dersler planlayıcısı ve yerel kayıtların açık olduğu aylık Takvim'de
başlangıç–bitiş aralığıyla gösterilir. Aylık Takvim'de ayrıca sıfır süreli bir teslim kopyası oluşturulmaz.
Dersler planlayıcısındaki sınav bloğuna tıklamak düzenleme formunu açar.

Sınav süresi sabit bloktur; normal çalışma ekleme ve ajan önerileri bu aralığa yerleştirilemez.
Elle açıkça çakışmaya izin verilen çalışma işlemleri mevcut davranışını korur.
Sınava bağlı hazırlık önerilerinin son sınırı sınav **başlangıcıdır**. `snapshot.fixedBlocks` içindeki
`kind: Sınav` kaydının `start`/`end` alanları dış ajanlara gerçek süreyi gösterir.
Sınav tamamlandı işaretlense de takvimdeki sabit süresi korunur.

## Yalnızca sınav haftasının derslerini iptal etmek

**Dersler → Ders iptalleri → Tarih aralığında ders iptal et** yolunu aç.
Kapsamı **Tüm dersler** veya tek bir ders seç. İlk gün `16.11.2026`, son gün `20.11.2026`
seçip açıklamaya örneğin “Vize haftası” yaz ve kaydet. Takvimden gün seçmek de mümkündür.

Her iki gün dahildir. Bu aralıkta haftalık ders oturumları Bugün, ders takvimi, aylık Takvim ve
planlama snapshot'ından çıkar; sınavlar, Google etkinlikleri ve çalışma blokları korunur.
Derslerin haftalık şablonu ve diğer haftaları değişmez. Bir iptal aralığı en fazla 367 gün olabilir.

İptal kaydını seçerek **Düzenle** veya **İptali kaldır** kullanabilirsin. Kaldırırken onay istenir.
Aynı tarihe başka bir iptal de uygulanıyorsa o iptal geçerli kalır. Tek bir dersi silmek ona bağlı
iptalleri temizler, tüm dersler için oluşturulmuş iptalleri korur.

## Veri uyumluluğu ve kontrol

Ders dosyası şeması 3 oldu: sınavlarda isteğe bağlı `endTime`, üst seviyede `cancellations` bulunur.
Şema 1/2 dosyaları okunabilir. Okuma diskteki dosyayı değiştirmez; ilk kayıt önceki dosyanın tam
baytlarını `.bak` olarak saklar. Eski sınavlara otomatik süre atanmaz; başlangıç/bitişini düzenleme
formundan tamamlayabilirsin. Eski uygulama şema 3 dosyasını açamaz; sürüm düşürmek gerekirse uygun
eski yedeği uygulamanın geri yükleme akışıyla kullan.

Kullanıcının ilettiği sekiz sınav test verisi olarak kullanılır. Geliştirme sırasında gerçek profile
sınav veya iptal eklenmez. Testler; saat çakışması, tamamlanan sınav, tarih sınırları, iptali geri alma,
şema geçişi/yedekleme, planlayıcı düzenlemelerinde alanların korunması, önerilerin sınavdan önce
bitmesi, aylık takvimde tek gösterim ve izole ekranda gerçek klavye girişini kapsar.

CLI sınav oluşturma veya ders iptal etme komutu eklenmedi; bu kayıtlar uygulamadan yönetilir.
Dış ajan sınavları sabit blok olarak okuyup CLI öneri akışıyla çalışma planlayabilir.
