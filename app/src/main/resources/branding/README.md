# Academic OS logosu

Kaynak referans: kullanıcının 13 Kasım 2026'da onayladığı kahverengi komut simgesi,
gri sağ-alt gölge ve açık gümüş yuvarlatılmış kare görseli.
Sohbetteki görsel referans alınarak built-in `image_gen` ile ikon uyarlaması üretildi.
Seçilen bitmap master: `notes/design-references/academic-os-logo-master.png` (1254 × 1254).

`logo.png`: 512 × 512; uygulama başlığı ve tepsi.
`logo-32.png`, `logo-64.png`, `logo-128.png`, `logo-256.png`: pencere ikonları.
`app-icon.ico`: 16, 20, 24, 32, 40, 48, 64, 128, 256 piksel; EXE, Windows kısayolları,
setup ve kaldırıcı. ICO, NSIS uyumluluğu için 32 bit DIB çerçeveler kullanır.

İkon boyutlandırma/dosya dönüşümünde içerik değiştirilmez. Yeniden üretmek için geliştirme ortamında Pillow gerekir:

```sh
python3 packaging/build_branding.py notes/design-references/academic-os-logo-master.png
```

Normal uygulama ve Windows setup derlemesi hazır varlıkları kullanır; Pillow çalışma zamanı bağımlılığı değildir.

## Kullanılan üretim istemi

```text
Use case: precise-object-edit. Asset type: production desktop application icon for Academic OS. Input image 1 is the user's APPROVED FINAL LOGO and is the edit target, not a loose style reference. Export a faithful, clean square application-icon adaptation of that exact image. Preserve the same continuous brown command-symbol glyph (⌘) with four rounded loop corners, exactly the same geometry, orientation, relative scale and position, the same muted warm brown around #8a5a44, the same gray hard offset shadow toward bottom right, and the same very light silver-gray rounded-square tile/background with broad rounded corners. Preserve the generous original padding and whole composition. Do not redesign, reinterpret, embellish, recolor, invent details, add typography or text, add objects, add borders, or change the mark. No mockup or perspective. The output is just the single approved logo artwork as a clean high-resolution square bitmap, suitable for PNG and Windows ICO export.
```
