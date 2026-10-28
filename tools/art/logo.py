"""Draws the placeholder mod logo (src/main/resources/logo.png, 256x256): a brass searchlight on a
dark iron mount throwing a beam across a night sky. Pixel art at 64x64, scaled up 4x so it matches the
block textures. The final logo is the owner's call before release.

Run from anywhere: python tools/art/logo.py
"""
import math
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/logo.png'
SIZE = 64

SKY_TOP = (14, 18, 34)
SKY_BOTTOM = (34, 40, 66)
DARK = [(38, 41, 47), (47, 51, 58), (58, 63, 71), (72, 77, 86)]
BRASS = [(120, 82, 36), (161, 115, 52), (201, 154, 70), (232, 190, 102)]
LENS = (255, 244, 220)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def main():
    img = Image.new('RGB', (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            px[x, y] = lerp(SKY_TOP, SKY_BOTTOM, y / (SIZE - 1))
    rnd = random.Random(7)
    for _ in range(40):
        x, y = rnd.randrange(SIZE), rnd.randrange(SIZE * 2 // 3)
        px[x, y] = lerp(px[x, y], (220, 226, 255), rnd.uniform(0.3, 0.8))

    # Beam: from the lens at (22, 40) up and to the right, widening, fading with distance
    lens = (22.0, 40.0)
    axis = (math.cos(math.radians(-35)), math.sin(math.radians(-35)))
    half_angle = math.radians(11)
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - lens[0], y + 0.5 - lens[1]
            along = dx * axis[0] + dy * axis[1]
            if along <= 0:
                continue
            across = abs(-dx * axis[1] + dy * axis[0])
            edge = 2.5 + along * math.tan(half_angle)
            if across > edge:
                continue
            core = 1 - across / edge
            strength = (0.25 + 0.55 * core) * max(0.0, 1 - along / 62)
            px[x, y] = lerp(px[x, y], LENS, strength)

    # Ground
    for y in range(52, SIZE):
        for x in range(SIZE):
            px[x, y] = DARK[0] if (x + y) % 5 else DARK[1]

    # Mount: dark iron pedestal
    for y in range(44, 52):
        for x in range(14, 31):
            px[x, y] = DARK[2] if x in (14, 30) or y == 44 else DARK[1]
    # Yoke arms
    for y in range(34, 45):
        for x in (13, 14, 30, 31):
            px[x, y] = DARK[3] if x in (14, 30) else DARK[2]

    # Brass drum, tilted along the beam
    centre = (22.0, 38.0)
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - centre[0], y + 0.5 - centre[1]
            along = dx * axis[0] + dy * axis[1]
            across = -dx * axis[1] + dy * axis[0]
            if -7 <= along <= 3 and abs(across) <= 6:
                shade = 3 if across < -3 else 2 if across < 1 else 1 if across < 4 else 0
                px[x, y] = BRASS[shade]
                if along > 2:
                    px[x, y] = BRASS[3]
                if -1 <= along <= 0:
                    px[x, y] = DARK[1]
            elif 3 < along <= 4.5 and abs(across) <= 5:
                px[x, y] = LENS

    img.resize((SIZE * 4, SIZE * 4), Image.NEAREST).save(OUT)


if __name__ == '__main__':
    main()
