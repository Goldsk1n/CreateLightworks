"""Draws the Create palette ramps documented in docs/create-art-style.md as a swatch sheet.

Colours only (sampled from Create 6.0.10's block textures with palette_stats.py); no Create pixels.
python tools/art/study/swatches.py <out.png>
"""
import colorsys
import sys

from PIL import Image, ImageDraw

RAMPS = [
    ('Andesite (frames, low tier)', '#353b39 #404543 #494c4a #505351 #585b58 #60635f #686c68 #707570 #828784 '
                                    '#8f9691 #9aa49d #a8b3ab'),
    ('Brass (frames, precision tier)', '#592424 #68372c #724731 #875137 #9e6947 #a46b47 #af794f #bf8e55 #c79758 '
                                       '#cea05a #d7aa5e #e4b763 #f7cb6c #fbdc7d #ffeb8c #fff9bf'),
    ('Copper (fluids, pressure)', '#904931 #9a5038 #a75a40 #b26247 #c26b4c #c87456 #d67b5b #e3826c'),
    ('Wood in andesite casing', '#553a1f #5a4424 #614b2e #70522e #7a5a34 #82613a #886539'),
    ('Wood in brass casing', '#291a0c #301e0e #3a2411 #442b15 #492f17 #4f3218 #53381a #5a3e1e'),
    ('Train steel (railway casing)', '#17191c #1e2126 #23262b #272b30 #2a2e33 #2c3135 #303639 #34393c #353b3e '
                                     '#3a4043 #444b4c'),
    ('Industrial iron', '#2b2b2e #353438 #3f3e42 #444444 #494848 #4f4f4f #595858 #676161 #757171 #8d8b8b '
                        '#989797'),
    ('Dark metal block', '#282829 #2b2b2b #323233 #363638 #3b3b3c #3f4041 #464647 #4a4a4b #525255 #606063'),
    ('Copper casing inside', '#444140 #4b4948 #4f4f4f #555453 #5e5b5a #67625f #726c69'),
    ('Redstone, off', '#300200 #3d0100 #4b0101 #560101'),
    ('Redstone, powered', '#750000 #970000 #b40000 #cd0000'),
    ('Rose quartz lamp, off', '#5b0e2f #641336 #741a41 #821e47 #8f224c #b12e57'),
    ('Rose quartz lamp, powered', '#8f224c #aa2957 #c83264 #f74572 #f95a77 #fb6b7c #ff8585 #ffa596 #ffebd1'),
    ('Lamp frame (andesite, warm)', '#3a4445 #525653 #6a6e63 #888b79 #9aa490 #adc6a9'),
]
CHIP, STEP, LABEL_W, ROW_H = 44, 58, 230, 64


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


width = LABEL_W + 16 * STEP + 10
img = Image.new('RGB', (width, len(RAMPS) * ROW_H + 10), (24, 24, 28))
d = ImageDraw.Draw(img)
for r, (name, ramp) in enumerate(RAMPS):
    y = 8 + r * ROW_H
    d.text((10, y + 14), name, fill=(235, 235, 235))
    for i, h in enumerate(ramp.split()):
        x = LABEL_W + i * STEP
        c = rgb(h)
        d.rectangle((x, y, x + CHIP, y + CHIP - 10), fill=c)
        hue, sat, val = colorsys.rgb_to_hsv(*(v / 255 for v in c))
        d.text((x, y + CHIP - 8), h[1:], fill=(200, 200, 200))
        d.text((x, y + CHIP + 2), f'{round(hue * 360)}/{round(sat * 100)}/{round(val * 100)}', fill=(140, 140, 150))
img.save(sys.argv[1])
