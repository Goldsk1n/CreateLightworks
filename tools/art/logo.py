"""Draws the mod logo (src/main/resources/logo.png, 256x256): our Searchlight, drawn from its real models and textures,
throwing a beam out of a blueprint circle, the look of Create's add-ons. The circle is drawn here, not copied from
anywhere. Also writes a 1024x1024 copy for mod pages and a sheet at the sizes it is seen at, into build/art-preview/.

python -I tools/art/logo.py    (Needs Pillow and numpy, and models.py to have run.)
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import preview as P  # noqa: E402

MOD = Path(__file__).resolve().parents[2]
OUT = MOD / 'src/main/resources/logo.png'
SHEET = MOD / 'build/art-preview'
SIZE = 1024
RING = 0.468        # of the image: outer edge of the white ring
RING_WIDTH = 0.026
BLUE = np.array((84, 150, 212), np.float32) / 255
EDGE_BLUE = np.array((52, 104, 158), np.float32) / 255
LINE = np.array((96, 164, 226), np.float32) / 255
MAIN_LINE = np.array((122, 188, 240), np.float32) / 255
BEAM = np.array((255, 240, 205), np.float32) / 255
# The icon's pose: Minecraft's block angle with the head tilted 45 degrees, lens up and to the right
YAW, PITCH, TILT = 135, 30, -45
SCALE = 31
CENTRE = (0.43, 0.56)    # of the image: where the lamp's block centre goes
BEAM_SPREAD = 0.33       # half the beam's width per unit of its length
BEAM_ALPHA = 0.8
LENS_BOOST = 1.1
GLOW = 0.15
GLOW_RADIUS = 0.2
OUTLINE_WIDTH = 10     # pixels of the 1024 image: how far the white outline reaches round the lamp


def circle():
    """The blueprint disc on a clear background, RGBA floats."""
    y, x = np.mgrid[0:SIZE, 0:SIZE] + 0.5
    r = np.hypot(x / SIZE - 0.5, y / SIZE - 0.5)
    inner = RING - RING_WIDTH
    shade = np.clip((r / inner - 0.62) / 0.38, 0, 1) ** 2
    rgb = BLUE[None, None] + (EDGE_BLUE - BLUE)[None, None] * shade[..., None]
    # A blueprint's grid, a fine line every quarter and one main line each way
    for at, width, colour in [(0.25, 0.012, LINE), (0.5, 0.012, LINE), (0.75, 0.012, LINE)]:
        for coord in (x, y):
            hit = np.abs(coord / SIZE - at) < width
            rgb[hit] = rgb[hit] * 0.45 + colour * 0.55
    for coord, at in ((x, 0.25), (y, 0.25)):
        hit = np.abs(coord / SIZE - at) < 0.024
        rgb[hit] = rgb[hit] * 0.2 + MAIN_LINE * 0.8
    # Grid lines melt into the shaded rim
    rgb = rgb * (1 - shade[..., None] * 0.35) + (BLUE + (EDGE_BLUE - BLUE) * shade[..., None]) * shade[..., None] * 0.35
    ring = np.clip((RING - r) * SIZE, 0, 1)
    disc = np.clip((inner - r) * SIZE, 0, 1)
    rgb = rgb * disc[..., None] + (1 - disc[..., None])
    return np.dstack((rgb, ring))


def beam_direction():
    """The lens's axis on the screen, x right and y down."""
    a = math.radians(-TILT)
    axis = (0, math.cos(a), -math.sin(a))
    cy, sy = math.cos(math.radians(YAW)), math.sin(math.radians(YAW))
    cp, sp = math.cos(math.radians(PITCH)), math.sin(math.radians(PITCH))
    x1 = cy * axis[0] - sy * axis[2]
    z1 = sy * axis[0] + cy * axis[2]
    y2 = cp * axis[1] - sp * z1
    length = math.hypot(x1, y2)
    return x1 / length, -y2 / length


def add_beam(image, start):
    """A beam widening from the lens to the ring, fading as it goes: light over what is there, and only inside the ring,
    since the image's square corners would cut it off outside."""
    dx, dy = beam_direction()
    y, x = np.mgrid[0:SIZE, 0:SIZE] + 0.5
    px, py = x - start[0], y - start[1]
    along = px * dx + py * dy
    across = np.abs(px * -dy + py * dx)
    half = SIZE * 0.05 + along * BEAM_SPREAD
    inside = np.clip((half - across) / (SIZE * 0.012), 0, 1) * (along > 0)
    fade = np.clip(1 - along / (SIZE * 0.74), 0, 1) ** 0.8
    alpha = BEAM_ALPHA * inside * fade * (0.72 + 0.28 * np.clip(1 - across / np.maximum(half, 1), 0, 1))
    r = np.hypot(x / SIZE - 0.5, y / SIZE - 0.5)
    alpha = alpha * np.clip((RING - RING_WIDTH - r) * SIZE, 0, 1)
    a = alpha[..., None]
    return np.dstack((image[..., :3] * (1 - a) + BEAM[None, None] * a, image[..., 3:]))


def add_glow(image, centre):
    """The lens lighting the air round it, over everything, inside the ring."""
    y, x = np.mgrid[0:SIZE, 0:SIZE] + 0.5
    d = np.hypot(x - centre[0], y - centre[1]) / (SIZE * GLOW_RADIUS)
    r = np.hypot(x / SIZE - 0.5, y / SIZE - 0.5)
    glow = np.clip(1 - d, 0, 1) ** 2 * GLOW * np.clip((RING - RING_WIDTH - r) * SIZE, 0, 1)
    return np.dstack((np.clip(image[..., :3] + BEAM[None, None] * glow[..., None], 0, 1), image[..., 3:]))


def lamp():
    """The Searchlight's parts on a clear background, and where its lens came out."""
    P.SCALE = SCALE
    canvas = P.Canvas(SIZE, SIZE, (0, 0, 0))
    origin = (SIZE * CENTRE[0], SIZE * CENTRE[1])
    textures = P.Textures(P.ASSETS)
    folder = P.ASSETS / 'models/block/searchlight'
    pose = P.tilt(TILT)
    # Brighter than a lens in the game, whose glow comes from the dark round it
    tint = tuple(c * LENS_BOOST for c in P.rgb01(0xFFEDC7))
    for part in ('base', 'yoke', 'head'):
        P.draw_part(canvas, textures, P.load_model(folder / f'{part}.json'), origin, YAW, PITCH,
                    transform=pose if part == 'head' else None)
    P.draw_part(canvas, textures, P.load_model(folder / 'lens.json'), origin, YAW, PITCH, tint, lit=True, transform=pose)
    P.draw_part(canvas, textures, P.load_model(folder / 'lens_core.json'), origin, YAW, PITCH,
                tuple((c + w) / 2 for c, w in zip(tint, P.rgb01(P.CORE_WHITE))), lit=True, transform=pose)
    drawn = canvas.depth > -1e8
    return np.dstack((canvas.rgb, drawn.astype(np.float32)))


def lens_on_screen():
    dx, dy = beam_direction()
    # The lens sits about seven pixels of the model out from the block centre along the axis
    a = math.radians(-TILT)
    cy, sy = math.cos(math.radians(YAW)), math.sin(math.radians(YAW))
    cp, sp = math.cos(math.radians(PITCH)), math.sin(math.radians(PITCH))
    z1 = cy * -math.sin(a)
    x1 = -sy * -math.sin(a)
    y2 = cp * math.cos(a) - sp * z1
    reach = 7 * SCALE
    return SIZE * CENTRE[0] + x1 * reach, SIZE * CENTRE[1] - y2 * reach


def outline(shape):
    """A white shape round the lamp, as Create's add-ons draw round the object on their icons. Grown a pixel at a time,
    by turns straight and diagonally, which keeps its corners crisp and its width even along the slanted edges."""
    grown = shape[..., 3] > 0.5
    for step in range(OUTLINE_WIDTH):
        out = grown | np.roll(grown, 1, 0) | np.roll(grown, -1, 0) | np.roll(grown, 1, 1) | np.roll(grown, -1, 1)
        if step % 2:
            for dy, dx in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
                out |= np.roll(np.roll(grown, dy, 0), dx, 1)
        grown = out
    mask = Image.fromarray(grown.astype(np.uint8) * 255, 'L').filter(ImageFilter.GaussianBlur(0.8))
    return np.dstack((np.ones_like(shape[..., :3]), np.asarray(mask, np.float32) / 255))


def over(under, top):
    a = top[..., 3:]
    out = a + under[..., 3:] * (1 - a)
    rgb = (top[..., :3] * a + under[..., :3] * under[..., 3:] * (1 - a)) / np.maximum(out, 1e-4)
    return np.dstack((rgb, out))


def main():
    picture = add_beam(circle(), lens_on_screen())
    shape = lamp()
    picture = add_glow(over(over(picture, outline(shape)), shape), lens_on_screen())
    big = Image.fromarray((np.clip(picture, 0, 1) * 255).astype(np.uint8), 'RGBA')
    SHEET.mkdir(parents=True, exist_ok=True)
    big.save(SHEET / 'logo-1024.png')
    big.resize((256, 256), Image.LANCZOS).save(OUT)
    sizes = (512, 128, 64, 48, 32)
    sheet = Image.new('RGB', (sum(sizes) + 20 * (len(sizes) + 1), 2 * 552), (35, 37, 41))
    ImageDraw.Draw(sheet).rectangle((0, 552, sheet.width, 1104), fill=(246, 246, 246))
    for row, top in enumerate((20, 572)):
        x = 20
        for size in sizes:
            small = big.resize((size, size), Image.LANCZOS)
            sheet.paste(small, (x, top), small)
            x += size + 20
    sheet.save(SHEET / 'logo-sheet.png')
    print('logo written to', OUT)


if __name__ == '__main__':
    main()
