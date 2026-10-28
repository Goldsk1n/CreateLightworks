"""Draws every Lightworks block texture as a 16x16 PNG.

Our own art in a Create-like palette (brass, andesite, dark iron); nothing is copied from Create or any
other mod. Run from anywhere: python tools/art/textures.py. Writes into
src/main/resources/assets/lightworks/textures/block/.
"""
import math
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/lightworks/textures/block'

# Palette
DARK = [(38, 41, 47), (47, 51, 58), (58, 63, 71), (72, 77, 86)]
IRON = [(118, 122, 128), (146, 150, 156), (172, 176, 181), (199, 202, 206)]
BRASS = [(120, 82, 36), (161, 115, 52), (201, 154, 70), (232, 190, 102)]
ANDESITE = [(98, 101, 96), (122, 125, 119), (143, 146, 139), (165, 168, 160)]
COPPER = [(110, 60, 40), (150, 82, 52), (188, 108, 70), (222, 142, 98)]
GLASS = [(206, 208, 204), (226, 228, 224), (240, 241, 238), (255, 255, 255)]


def image():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def noise_fill(img, palette, seed, weights=(1, 4, 4, 1), box=(0, 0, 16, 16)):
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), rnd.choices(palette, weights)[0] + (255,))


def frame(img, palette, inset=0, light_top_left=True):
    """A bevelled 1-pixel frame: light on the top and left edges, dark on the bottom and right."""
    lo, hi = palette[0], palette[-1]
    a, b = inset, 15 - inset
    for i in range(a, b + 1):
        img.putpixel((i, a), (hi if light_top_left else lo) + (255,))
        img.putpixel((a, i), (hi if light_top_left else lo) + (255,))
        img.putpixel((i, b), (lo if light_top_left else hi) + (255,))
        img.putpixel((b, i), (lo if light_top_left else hi) + (255,))


def rivets(img, colour, points):
    for x, y in points:
        img.putpixel((x, y), colour + (255,))


def save(img, name):
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / f'{name}.png')


def light_metal():
    """Dark gunmetal housing with a panel seam and rivets."""
    img = image()
    noise_fill(img, DARK, 1)
    for x in range(16):
        img.putpixel((x, 7), DARK[0] + (255,))
        img.putpixel((x, 8), DARK[3] + (255,))
    rivets(img, IRON[2], [(2, 2), (13, 2), (2, 13), (13, 13)])
    save(img, 'light_metal')


def light_brass():
    img = image()
    noise_fill(img, BRASS[1:], 2, (3, 5, 2))
    for x in range(16):
        img.putpixel((x, 0), BRASS[3] + (255,))
        img.putpixel((x, 15), BRASS[0] + (255,))
    save(img, 'light_brass')


def light_iron():
    img = image()
    noise_fill(img, IRON, 3)
    frame(img, IRON)
    save(img, 'light_iron')


def light_base():
    """Top of a mounting plate: andesite with a brass ring and four bolts."""
    img = image()
    noise_fill(img, ANDESITE, 4)
    frame(img, ANDESITE)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if 4.6 <= r <= 5.6:
                img.putpixel((x, y), BRASS[2 if (x + y) % 3 else 3] + (255,))
    rivets(img, DARK[0], [(2, 2), (13, 2), (2, 13), (13, 13)])
    save(img, 'light_base')


def light_base_side():
    img = image()
    noise_fill(img, ANDESITE, 5)
    frame(img, ANDESITE)
    save(img, 'light_base_side')


def lens():
    """Fresnel glass: concentric rings, brightest in the middle. Tinted by the renderer."""
    img = image()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            ring = int(r) % 3 == 0
            k = GLASS[3] if r < 2.5 else (GLASS[1] if ring else GLASS[2])
            img.putpixel((x, y), k + (255,))
    save(img, 'lens')


def rim_top():
    """Front of a lamp housing: a brass bezel around a dark opening the lens sits in."""
    img = image()
    noise_fill(img, [(22, 24, 28), (30, 32, 37)], 6, (1, 1))
    for i in range(16):
        for t in (0, 15):
            img.putpixel((i, t), BRASS[2] + (255,))
            img.putpixel((t, i), BRASS[1] + (255,))
    save(img, 'rim_top')


def casing_side(name, seed, band=None):
    """Andesite casing side, optionally with a coloured band across the middle."""
    img = image()
    noise_fill(img, ANDESITE, seed)
    frame(img, ANDESITE)
    if band:
        for x in range(1, 15):
            for y in (6, 7, 8, 9):
                img.putpixel((x, y), band[1 if y in (6, 9) else 2] + (255,))
    save(img, name)


def shaft_input():
    """Back of a kinetic block: casing with a round shaft socket."""
    img = image()
    noise_fill(img, ANDESITE, 7)
    frame(img, ANDESITE)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 3:
                img.putpixel((x, y), DARK[0] + (255,))
            elif r < 4:
                img.putpixel((x, y), BRASS[1] + (255,))
    save(img, 'shaft_input')


def turntable():
    """Top of the Pan-Tilt Head's turntable: brass disc with a pointer so turning shows."""
    img = image()
    noise_fill(img, DARK, 8)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r <= 7.6:
                img.putpixel((x, y), BRASS[2 if r < 6.5 else 1] + (255,))
            if r <= 2:
                img.putpixel((x, y), DARK[1] + (255,))
    for y in range(1, 6):
        img.putpixel((7, y), DARK[0] + (255,))
        img.putpixel((8, y), DARK[0] + (255,))
    save(img, 'turntable')


def turntable_side():
    img = image()
    noise_fill(img, BRASS[:3], 9, (2, 4, 3))
    for x in range(0, 16, 4):
        for y in range(16):
            img.putpixel((x, y), BRASS[0] + (255,))
    save(img, 'turntable_side')


def lamp_cage():
    """Bars of a warning lamp's guard cage (transparent between bars)."""
    img = image()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                img.putpixel((x, y), IRON[1 if (x + y) % 2 else 2] + (255,))
    save(img, 'lamp_cage')


def copper_housing():
    img = image()
    noise_fill(img, COPPER, 10)
    frame(img, COPPER)
    save(img, 'copper_housing')


for draw in (light_metal, light_brass, light_iron, light_base, light_base_side, lens, rim_top, shaft_input,
             turntable, turntable_side, lamp_cage, copper_housing):
    draw()
casing_side('pan_tilt_head_side', 11, BRASS)
casing_side('beacon_casing', 12, DARK)
casing_side('casing_plain', 13)
print('textures written to', OUT)
