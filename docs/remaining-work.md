# Kalan işler — 10 Kasım 2026

Kaynak kodu ve güncel teslim notlarına göre sıralama. Bunlar bu renk güncellemesinde uygulanmış özellikler değildir.

| Öncelik | Eksik | Beklenen davranış |
| --- | --- | --- |
| 1 | Uygulama içi bildirim alanı | Sistem tepsisi yokken `TrayController.notify` hâlâ `Alert.show()` kullanıyor. Yeni öneri ve hatırlatma ana pencereyi bölmeyen bir bildirim alanına düşmeli; geçmişi okunabilmeli. |
| 2 | Dar ekran yerleşimi | Bugün'de odak kartı dar görünümde uzun zaman çizelgesinin altında kalıyor. Üstte küçük odak çubuğu; haftalık planda havuzu kapatıp açma/çekmece ile takvime alan kazandırma eksik. |
| 3 | Elle planlama son tarih politikası | Ajan önerisi `AFTER_DEADLINE` ile reddedilirken elle haftalık planlama aynı denetimi uygulamıyor. Gecikmiş işe bilerek çalışma ekleme kararı açıklığa kavuşturulmalı; arayüz tutarlı bir sonuç vermeli. |
| 4 | Anlaşılır işlem ve hata metinleri | Öneri incelemesinde `schedule` gibi CLI adları yerine Türkçe işlem adları; genel `INVALID_PROPOSAL` hatasında hangi alanın bozuk olduğunu söyleme eksik. |
| 5 | Dağıtım kabulü | 1.5.1 Java dahil Windows setup üretildi; 210 uygulama ve 11 paketleme testi geçti, son EXE arşivi ve 366 dosya hash'i doğrulandı. Gerçek Windows kurulum/güncelleme/kaldırma ve tepsi akışı için kullanıcı kabulü bekler. Kod imzalama ve otomatik güncelleme ayrıca bekleyen işlerdir. |

Mevcut özellik yol haritasının sonraki genişletmeleri: UART log/hex/Windows uyarlaması, hata raporu paneli,
belgelerde PDF/Markdown önizleme ve içerik araması, Referans arama/etiket/kategori yönetimi.
Sesli kullanım sonraki kapsamdır. Uygulama içi model/LLM eklenmez; dış ajan CLI ve kullanıcı onaylı öneri kararı sürer.

Tamamlanan son işler: hafta/geçmiş gün/ızgara doğrulamaları, boş otomatik öneri sonucu,
son 7 günlük geçmiş görünümü, sabit teslim şeridi, kapatılabilir ayrıntı paneli ve testlerde pencere açmanın
varsayılan kapatılması. Native arayüze kullanıcının kahverengi–altın paleti eklendi.
Temaya uymayan finans HTML renkleri ayrıca ele alınabilir; finansın çalışma yapısını değiştirmek gerekmiyor.
