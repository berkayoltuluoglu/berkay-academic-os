# Academic OS CLI başvurusu

Bütün çıktılar JSON; başarı 0, doğrulama/komut hatası 2. Global seçenek: `--profile DIR`.
Bütün komutlarda `--help` JSON örnekler döndürür. CLI ağ isteği veya JavaFX başlatmaz.

| Komut | Seçenekler / davranış |
| --- | --- |
| `snapshot` | `--week YYYY-MM-DD` zorunlu; Pazartesiye yuvarlanır, yedi günü döndürür. |
| `pool` | `--week` ve `--until` isteğe bağlı; bütün açık işler ve kalan/planlı süreler. |
| `free-slots` | `--week` zorunlu, `--min N` isteğe bağlı; kapalı saatler ve günlük doluluk dikkate alınır. |
| `schema` | Öneri JSON şeması ve tüm komut örnekleri. İş kuralları ayrıca `validate` ile kontrol edilir. |
| `validate FILE` | Kuru çalıştırma; `valid`, `stale`, indeksli `issues`; profili değiştirmez. |
| `propose FILE` | Doğrulanan öneriyi kutuya koyar, `pending` kaydı döner. |
| `proposals` | İsteğe bağlı `--status pending/accepted/partially_accepted/rejected`. |
| `proposal-status ID` | Sonuç ve kullanıcı notu; arşivde de arar. |
| `suggest` | `--week` zorunlu; `proposal` ve `unscheduled`; kaydetmez. |
| `task add` | `--title`, isteğe bağlı `--due`, `--minutes`; ayrıca `--apply` ve kullanıcı ayarı gerekli. |
| `block add` | `--date`, `--start`, `--minutes`, `--title`; isteğe bağlı `--kind task/work/classroom/application`, `--item ID`; `--apply`. |
| `block move` | `--id`, `--date`, `--start`, `--apply`. |
| `block resize` | `--id`, `--minutes`, `--apply`. |
| `block delete` | `--id`, `--apply`. |
| `undo` / `redo` | `--apply` ve doğrudan yazım ayarı; en fazla 50 işlem. Harici değişiklik varsa eski veriyi ezmez. |

Öneri komutlarının ortak zorunlu alanları `type` ve boş olmayan `reason`:

| type | Diğer zorunlu alanlar |
| --- | --- |
| `schedule` | `date`, `start`, `minutes`; bağlantılıysa `kind`, `itemId`, bağlantısızsa `title` |
| `move` | `id`, `date`, `start` |
| `resize` | `id`, `minutes` |
| `delete` | `id` |
| `complete-block` | `id`, `minutes` (gerçekleşen süre) |
| `complete-item` | `kind: task/work`, `id` |
| `estimate` | `kind: task/work`, `id`, `minutes` |
| `create-task` | `title`; isteğe bağlı kararlı `id`, ISO `date`, tahmini `minutes`, `details.note` |

`preferences`: saat dilimi, çalışma başlangıç/bitişi, günlük dakika sınırı, min/max blok,
`closed: [{date: "2026-11-13", day: 0, start: "18:00", end: "23:59"}]`.
Haftalık kapalı aralık için `date: ""` ve `day: 1..7` kullanılır.
Tercihler uygulamadaki Ayarlar'dan değiştirilir; ajan önerisi ayarları veya doğrudan yazım iznini değiştiremez.

Öneri kabulü tek geri al adımıdır. Undo sonrasında öneri tarihsel olarak kabul edilmiş kalır;
ajan gerçek planı öğrenmek için her zaman yeni snapshot okumalıdır.
İşlem günlükleri yalnızca planlama verisini içerir; tokenlar/istemci sırları dahil edilmez.
Geri alma, araya odak kredisi veya başka modülden düzenleme girdiyse veri kaybetmemek için reddedilebilir.

Yerel oturum dosyası şema 1 kalır; `24:00` bitişini temsil etmediği için son bitiş **23:59**.
Saat dilimi değişimi mevcut ISO yerel saatlerini dönüştürmez; snapshot'taki saat diliminde yorumla.
