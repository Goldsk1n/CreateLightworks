"""Draws the schematic of Create's casing frame anatomy used in docs/create-art-style.md.

A generic 16x16 example built by the rules in that document (our own drawing, Create's palette colours),
enlarged, with each ring labelled. python tools/art/study/anatomy.py <out.png>
"""
import sys

from PIL import Image, ImageDraw


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


OUTLINE_LIT, OUTLINE_SHADE, OUTLINE_CORNER, OUTLINE_DARKEST = (rgb(c) for c in ('#60635f', '#505351', '#404543',
                                                                                 '#353b39'))
BEVEL_HI, BEVEL_MID, BEVEL_LO = (rgb(c) for c in ('#a8b3ab', '#8f9691', '#707570'))
SHADOW = rgb('#553a1f')
WOOD = [rgb(c) for c in ('#5a4424', '#614b2e', '#70522e', '#7a5a34', '#82613a', '#886539')]

tex = Image.new('RGB', (16, 16))
for y in range(16):
    for x in range(16):
        ring = min(x, y, 15 - x, 15 - y)
        lit = x + y < 15                      # upper-left half of the ring
        if ring == 0:
            c = OUTLINE_LIT if (y == 0 or x == 0) and not (x == 15 or y == 15) else OUTLINE_SHADE
        elif ring == 1:
            c = BEVEL_HI if (y == 1 or x == 1) and lit else BEVEL_LO if (y == 14 or x == 14) else BEVEL_MID
        elif ring == 2 and (x == 2 or y == 2):
            c = SHADOW                        # the frame's shadow falls on the top and left of the inside
        else:
            plank = (x - 3) // 3              # vertical planks three pixels wide, a dark seam between
            c = WOOD[0] if (x - 3) % 3 == 2 else WOOD[1 + (plank * 2 + (y * 3 + x) % 4) % 5]
        tex.putpixel((x, y), c)
tex.putpixel((15, 0), OUTLINE_CORNER)
tex.putpixel((0, 15), OUTLINE_CORNER)
tex.putpixel((15, 15), OUTLINE_DARKEST)

S = 26
img = Image.new('RGB', (16 * S + 430, 16 * S + 20), (24, 24, 28))
img.paste(tex.resize((16 * S, 16 * S), Image.NEAREST), (10, 10))
d = ImageDraw.Draw(img)
notes = [
    (0, 'Ring 0, outline: a mid-dark tone of the same material (never black);',
     '  top and left a step lighter than bottom and right.'),
    (1, 'Ring 1, bevel: the brightest tones, at the top and left;',
     '  fading to mid tones along the bottom and right.'),
    (2, 'Ring 2, inset shadow: the darkest tone of the inside,',
     '  on the top and left edges, where the frame casts it.'),
    (4, 'Inside: the casing\'s fill (here wood planks with seams),',
     '  low contrast, no checkerboard dithering.'),
    (15, 'Corners: top-right and bottom-left take an in-between',
     '  tone; bottom-right is the darkest pixel of the frame.'),
]
for row, a, b in notes:
    y = 10 + row * S
    d.line((10 + 16 * S + 4, y + S // 2, 10 + 16 * S + 24, y + S // 2), fill=(230, 200, 90), width=2)
    d.text((16 * S + 40, y), a, fill=(235, 235, 235))
    d.text((16 * S + 40, y + 13), b, fill=(235, 235, 235))
img.save(sys.argv[1])
