# Academic OS planlama kılavuzu

Bu Java 21 / JavaFX masaüstü uygulaması dersleri, teslimleri ve kişisel çalışma bloklarını birleştirir.
Ajan uygulamanın dışında çalışır. Yerel model, LLM istemcisi veya AI anahtarı ekleme.

## Altın kurallar

- Kullanıcı profilindeki JSON/properties dosyalarını **elle düzenleme**. Planlama için yalnızca `academic-os` CLI'ını kullan.
- Varsayılan akış öneridir: oku → planla → doğrula → öner. Kullanıcı öneriyi uygulamada kabul eder.
- Dersler ve Google etkinlikleri sabittir. Üzerlerine çalışma koyma. Teslimden sonraya çalışma planlama.
- OAuth tokenlarına, istemci sırlarına ve `config/google-*-tokens` klasörlerine dokunma; çıktıya koyma.
- Doğrudan yazım hem kullanıcının Ayarlar'dan açmasını hem komutta `--apply` gerektirir. Kullanıcının iznini varsayma.
- Snapshot'ta `errors` doluysa planlama yapmadan önce kullanıcıdan ilgili kaynağı düzeltmesini iste.
- Saat dilimi `snapshot.timezone` değeridir. Tarihler ISO `YYYY-MM-DD`, saatler `HH:mm` olur.
- Tercihler snapshot içindedir. Varsayılan hafta Pazartesi, çalışma aralığı 08:00–24:00, günlük azami 240 dk,
  en kısa blok 15 dk, en uzun blok 120 dk. Kullanıcının güncel tercihleri bu varsayılanlardan önceliklidir.
- Aynı gün içinde blok oluştur. Yerel oturum şeması nedeniyle son desteklenen bitiş saati **23:59**.

## CLI'ı bulma

Kaynak depoda `./gradlew :app:installDist --offline --no-daemon` sonrasında:

```sh
app/build/install/app/bin/academic-os --help
app/build/install/app/bin/academic-os --profile /tmp/academic-os-demo snapshot --week 2026-11-09
```

Linux kurulumunda `~/.local/bin/academic-os`; paketin içinde `academic-os-cli` bulunur.
Windows kurulumunda program klasöründeki `academic-os.cmd` kullanılır; Windows PATH'i otomatik değiştirilmez.
`--profile DIR` yoksa uygulamayla aynı `AppPaths` profil çözümlemesi kullanılır.
CLI JavaFX başlatmaz, Google/Classroom ağına bağlanmaz. Classroom yenilemesini kullanıcı uygulamadan yapar.
Tüm stdout çıktısı JSON'dur; hata çıkış kodu 2'dir. Her komut `--help` kabul eder.

## Veri sözlüğü

- **Görev** (`kind: task`): kişisel iş. UUID veya eski kayıttan türetilmiş kararlı kimlik; tarih boş olabilir.
- **Ödev/sınav** (`kind: work`): bir derse bağlı teslim. Classroom dersleri `gc-…`, ödevleri `gw-…` kararlı kimlikler taşır.
- **Aktarılmamış Classroom işi** (`kind: classroom`): yalnızca önbellekte bulunur. İlk planlamada tek ödev ve ders kabuğu yerelleştirilir.
- **Başvuru** (`kind: application`): burs/staj son tarihi. Planlandığında `application-…` kimlikli yerel görev üzerinden izlenir.
- **Ders saati / Google etkinliği**: snapshot `fixedBlocks`; ajan taşıyamaz veya silemez.
- **Çalışma bloğu**: gerçek bir tarih ve saat aralığı. UUID; `taskId` veya `workId` bağlantılarından en fazla biri doludur.
  Ödeve bağlanan bloğun `courseId` değeri ödevin dersiyle aynıdır. Bağlantıları CLI oluşturur.
- **Havuz öğesi**: `pool` içindeki tamamlanmamış iş. `remainingMinutes`, tahminden gerçekleşenin çıkarılmasıdır.
  Yeni planlanacak süre `max(0, remainingMinutes - plannedMinutes)`; tahmin yoksa toplam 60 dk varsay.
- **Odak**: `session` hedefi tamamlandığında süre bloğa bir kez yazılır; planlı ve odak süresi çift sayılmaz.

## Haftayı planlama

Her iş akışında şu sırayı kullan:

```sh
academic-os snapshot --week 2026-11-09 > snapshot.json
academic-os pool --week 2026-11-09 --until 2026-12-31 > pool.json
academic-os free-slots --week 2026-11-09 --min 30 > free-slots.json
academic-os schema > schema.json
# proposal.json dosyasını bu çıktılarla oluştur; profilin data/ dosyalarına yazma.
academic-os validate proposal.json
academic-os propose proposal.json
academic-os proposals --status pending
academic-os proposal-status ORNEGIN_DONDURDUGU_ID
```

Öneride `week` snapshot haftası, `fingerprint` snapshot çıktısındaki **güncel** hash olmalı.
Her komuta kısa bir Türkçe `reason` yaz. `itemId` için pool çıktısındaki gerçek kimliği kullan;
kimlik uydurma. Boşlukları 15 dakikalık aralıklara böl; günlük sınırı ve `preferences.closed` zamanlarını koru.
`validate` salt kuru çalıştırmadır. `valid: false` ise sorunları düzeltmeden `propose` çağırma.
`stale: true` ise yeniden snapshot alıp etkilenen komutları kontrol et; eski hash tek başına kabulü engellemez.

## Dört tipik iş akışı

1. **Bu haftayı planla:** snapshot → pool → free-slots → proposal → validate → propose.
   Önce en yakın son tarih ve yüksek öncelik; günlük yükü dengele. İstersen
   `academic-os suggest --week 2026-11-09` çıktısındaki `proposal` nesnesini başlangıç olarak kullan.
   `unscheduled` içindeki sığmayan işleri kullanıcıya açıkça bildir. `suggest` kendiliğinden kaydetmez.
2. **Yeni Classroom ödevlerini yerleştir:** aynı sırayla oku; pool'da `kind: classroom` öğelerini seç.
   `schedule.kind` değerini `classroom`, `itemId` değerini pool kimliği yap. İlk kabulde tek ödev aktarılır;
   tekrar aktarım aynı `gw-…` kimliğini korur. Classroom'a teslim yapılmaz.
3. **Kaçırılan blokları yeniden dağıt:** aynı sırayla oku; geçmiş tarihli açık blokları snapshot'ta belirle.
   `move` komutunda `id`, yeni `date`, `start` ve `reason` ver. Tamamlanan blokları koru.
   Eski haftadaki bir bloğu taşıyacaksan önerinin `week` değeri eski bloğun haftası olmalı.
4. **Sınav haftasına göre tekrar planı:** aynı sırayla oku; `work` öğelerindeki sınavları ve kalan süreyi kullan.
   Tek uzun blok yerine teslimden önceki farklı günlere parçala. Kullanıcının “cuma akşamı boş” koşulunu
   öneride uygula; kullanıcı tercihlerindeki kapalı aralıkları değiştirmeye çalışma.

Her senaryoda örnek bir bağlı komutun biçimi şöyledir (alanları gerçek çıktıyla doldur):
`{"type":"schedule","kind":"work","itemId":"pool çıktısındaki kimlik","date":"2026-11-10","start":"10:00","minutes":60,"reason":"Sınavdan önce tekrar"}`.
Taşıma: `{"type":"move","id":"snapshot blok kimliği","date":"2026-11-11","start":"14:00","reason":"Kaçırılan çalışmayı boş aralığa al"}`.

## Tam öneri örneği

Aşağıdaki örnek boş bir geçici profilde doğrulanabilir. Sıfırlardan oluşan hash bilinçli olarak eskidir;
`validate` sonucu `valid: true, stale: true` olur. Gerçek profilde **snapshot hash'ini kullan**.
Bu örnek bağlantısız bir tekrar bloğudur; gerçek iş planında havuzdan `kind` / `itemId` ekle.
Aynı içerik `examples/planner-proposal.json` dosyasındadır.

```json
{
  "schemaVersion": 1,
  "id": "guide-example",
  "agent": "Codex veya Claude Code",
  "note": "Pazartesi sabahı kısa tekrar önerisi",
  "week": "2026-11-09",
  "fingerprint": "0000000000000000000000000000000000000000000000000000000000000000",
  "commands": [
    {
      "type": "schedule",
      "title": "Final tekrarı",
      "date": "2026-11-09",
      "start": "10:00",
      "minutes": 60,
      "reason": "Boş sabah aralığında bir saat tekrar"
    }
  ]
}
```

Şema 1 alanları: `agent`, `note`, `week`, `fingerprint`, gerekçeli `commands` zorunludur.
`id` ve ISO `createdAt` verilmezse oluşturulur. Yeni öneri `pending` olur.
`commands[].type`: `schedule`, `move`, `resize`, `delete`, `complete-block`, `complete-item`, `estimate`, `create-task`.
Komuta göre zorunlu alanlar `academic-os schema` ve `CLI.md` / `docs/agent-cli.md` içinde açıklanır.
Kullanıcı kabulünden sonra `status`: `accepted`, `partially_accepted`, `rejected`;
`userNote`, `decisions[].index/status/applied`, `processedAt` geri bildirimi içerir.
Değiştirilerek kabul edilen komut `modified` olarak döner. Bir öneri tekrar uygulanamaz.
İşlenmiş öneriler 30 gün sonra arşivlenir; `proposal-status` arşivi de okur.

## Hatalar

| Kod | Yapılacak işlem |
| --- | --- |
| `STALE_SNAPSHOT` | Yeniden snapshot al, planı yeniden doğrula. Eski dosyanın üzerine yazmaya çalışma. |
| `BUSY` | Başka kayıt işlemi bitince komutu tekrar çalıştır. Kilit dosyasını silme. |
| `CONFLICT` | `free-slots` ile sabit ders/Google/çalışma dışındaki bir aralık seç. |
| `AFTER_DEADLINE` | Bloğun bitişini teslimden önceye al. |
| `INVALID_REQUEST`, `INVALID_PROPOSAL`, `INVALID_COMMAND` | `schema` ve komutun `--help` çıktısını kontrol et. |
| `DIRECT_WRITES_DISABLED`, `APPLY_REQUIRED` | Öneri akışını kullan; ayarı kendin açmaya çalışma. |
| `NOT_FOUND`, `DUPLICATE_ID` | Güncel snapshot/durumu oku; yeni öneriye yeni kimlik ver. |

## Bu kaynak depoda geliştirme

Profil kuralları kullanıcının `data/` ve kurulu profiline yöneliktir. Ürün kodunu ve test fixture'larını düzenleyebilirsin.
Testlerde yalnızca geçici profil kullan; gerçek Google hesabına yazma. Java 21 / JavaFX ile devam et,
ağır bağımlılık ekleme ve kapsam dışındaki finans/belge/UART modüllerini yeniden düzenleme.
Kontrol: `./gradlew :app:test :app:installDist --offline --no-daemon`.
Commit/push işlemleri kullanıcıya aittir; kullanıcı açıkça istemedikçe sen yapma.
