#!/usr/bin/env python3
"""Export the approved square logo to PNG sizes and Windows ICO; no artwork changes."""
from pathlib import Path
import argparse
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SIZES = (16, 20, 24, 32, 40, 48, 64, 128, 256)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    args = parser.parse_args()
    with Image.open(args.source) as source:
        source.load()
        if source.width != source.height or source.width < 512:
            parser.error('Use a square logo master of at least 512 pixels.')
        image = source.convert('RGBA')
    destination = ROOT / 'app/src/main/resources/branding'
    destination.mkdir(parents=True, exist_ok=True)
    image.resize((512, 512), Image.Resampling.LANCZOS).save(destination / 'logo.png', optimize=True)
    for size in (32, 64, 128, 256):
        image.resize((size, size), Image.Resampling.LANCZOS).save(destination / f'logo-{size}.png', optimize=True)
    # Uncompressed DIB frames work with NSIS's installer/uninstaller icon replacement.
    image.save(destination / 'app-icon.ico', format='ICO', sizes=[(size, size) for size in SIZES], bitmap_format='bmp')
    print(f'Branding exported: {destination}; ICO sizes: {", ".join(map(str, SIZES))}')


if __name__ == '__main__':
    main()
