"""Offline preview of Lightworks block models: renders every block's parts, textured and shaded like
the game, as a presentation sheet, without launching Minecraft.

python tools/art/preview.py [--out FILE] [--compare ASSETS_DIR [--compare-label TEXT]]... [--label TEXT]
                            [--front-off] [block ...]

Each block is drawn from the front above (lenses lit, as at night) and from behind below (lenses off,
showing the shaft in a kinetic block's bottom), next to Create's casings for comparison. Parts are the
JSON models under models/block/<block>/; lenses are tinted with the light's default colour and drawn
full-bright when lit, as PartRenderer does (lens_core parts glow warm white on top). Kinetic blocks get
Create's half shaft, as render/KineticParts draws it. create: textures are read from the reference
clone (../reference/create). --compare renders another assets folder (an older version or a variant)
after each block; repeat it for more. --front-off adds an unlit front view (how a light looks by day).
Output defaults to build/art-preview/preview.png. Needs Python 3, Pillow and numpy.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.dont_write_bytecode = True
MOD = Path(__file__).resolve().parents[2]
ASSETS = MOD / 'src/main/resources/assets/lightworks'
CREATE = MOD.parent / 'reference/create/src/main/resources/assets/create'

BLOCKS = ['searchlight', 'floodlight', 'headlight', 'pan_tilt_head', 'rotating_beacon', 'warning_light',
          'tail_light', 'navigation_light']
# Default lens colours (LightType); the Navigation Light shown red (port)
TINT = {'searchlight': 0xFFEDC7, 'floodlight': 0xFFEDC7, 'headlight': 0xFFEDC7, 'rotating_beacon': 0xFFF4DC,
        'warning_light': 0xFFA21A, 'tail_light': 0xFF2418, 'navigation_light': 0xFF2A1E}
KINETIC = {'pan_tilt_head', 'rotating_beacon'}
UNLIT_LENS = 0.3          # PartRenderer.UNLIT_LENS
CORE_WHITE = 0xFFF6E6     # PartRenderer: colour of a lit lens core
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}
# Parts a directional light aims, tilted for the preview so the lens faces the front camera
AIMED = {'head', 'lens', 'lens_core'}
AIM_TILT = 35
SCALE = 14                # screen pixels per model pixel
VIEWS = [('front, lit', 45, 30, True), ('back, below, off', 225, -28, False)]


def corners(face, f, t):
    """Face corners as seen from outside: top-left, top-right, bottom-right, bottom-left of the texture."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        'north': [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        'south': [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        'east': [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        'west': [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        'up': [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        'down': [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }[face]


NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0),
           'down': (0, -1, 0)}


def default_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1], 'south': [x1, 16 - y2, x2, 16 - y1],
            'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1], 'west': [z1, 16 - y2, z2, 16 - y1],
            'up': [x1, z1, x2, z2], 'down': [x1, 16 - z2, x2, 16 - z1]}[face]


class Textures:
    def __init__(self, assets):
        self.assets, self.cache = assets, {}

    def get(self, ref):
        if ref not in self.cache:
            ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
            root = self.assets if ns == 'lightworks' else CREATE if ns == 'create' else None
            file = root / 'textures' / f'{path}.png' if root else None
            if file and file.exists():
                im = Image.open(file).convert('RGBA')
                im = im.crop((0, 0, im.width, im.width))          # first frame of animated strips
                self.cache[ref] = np.asarray(im, dtype=np.float32) / 255
            else:
                magenta = np.zeros((2, 2, 4), np.float32)
                magenta[..., 3] = 1
                magenta[0, 0, :3] = magenta[1, 1, :3] = (1, 0, 1)
                self.cache[ref] = magenta
        return self.cache[ref]


def load_model(file):
    m = json.loads(Path(file).read_text(encoding='utf-8'))
    textures = m.get('textures', {})

    def resolve(ref):
        seen = 0
        while ref.startswith('#') and seen < 8:
            ref = textures.get(ref[1:], '')
            seen += 1
        return ref

    out = []
    for e in m.get('elements', []):
        faces = {}
        for face, d in e.get('faces', {}).items():
            faces[face] = dict(uv=d.get('uv') or default_uv(face, e['from'], e['to']),
                               texture=resolve(d['texture']), rotation=d.get('rotation', 0))
        out.append(dict(f=e['from'], t=e['to'], faces=faces, shade=e.get('shade', True)))
    return out


class Canvas:
    def __init__(self, w, h, background):
        self.rgb = np.empty((h, w, 3), np.float32)
        self.rgb[:] = np.array(background, np.float32) / 255
        self.depth = np.full((h, w), -1e9, np.float32)

    def image(self):
        return Image.fromarray((np.clip(self.rgb, 0, 1) * 255).astype(np.uint8), 'RGB')


def draw_part(canvas, textures, elements, origin, yaw, pitch, tint=(1, 1, 1), lit=False, transform=None):
    """Draws elements with an orthographic camera; lit parts are unshaded (full-bright lenses)."""
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x1 = cy * x - sy * z
        z1 = sy * x + cy * z
        y2 = cp * y - sp * z1
        z2 = sp * y + cp * z1
        return origin[0] + x1 * SCALE, origin[1] - y2 * SCALE, z2

    def project_dir(n):
        x, y, z = n
        z1 = sy * x + cy * z
        return sp * y + cp * z1

    for e in elements:
        for face, d in e['faces'].items():
            pts = corners(face, e['f'], e['t'])
            normal = NORMALS[face]
            if transform:
                pts = [transform(p) for p in pts]
                normal = tuple(np.subtract(transform(np.add((8, 8, 8), normal)), transform((8, 8, 8))))
            if project_dir(normal) <= 1e-4:
                continue
            tex = textures.get(d['texture'])
            th, tw = tex.shape[:2]
            u0, v0, u1, v1 = d['uv']
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            k = (d['rotation'] // 90) % 4
            uvs = [uvs[(i - k) % 4] for i in range(4)]
            scr = [project(p) for p in pts]
            o = np.array(scr[0][:2])
            a = np.array(scr[1][:2]) - o
            b = np.array(scr[3][:2]) - o
            det = a[0] * b[1] - a[1] * b[0]
            if abs(det) < 1e-6:
                continue
            xs = [p[0] for p in scr]
            ys = [p[1] for p in scr]
            x0, x1 = max(0, int(min(xs))), min(canvas.rgb.shape[1], int(math.ceil(max(xs))))
            y0, y1 = max(0, int(min(ys))), min(canvas.rgb.shape[0], int(math.ceil(max(ys))))
            if x0 >= x1 or y0 >= y1:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            px, py = gx - o[0], gy - o[1]
            s = (px * b[1] - py * b[0]) / det
            t = (a[0] * py - a[1] * px) / det
            inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
            if not inside.any():
                continue
            u = uvs[0][0] + s * (uvs[1][0] - uvs[0][0]) + t * (uvs[3][0] - uvs[0][0])
            v = uvs[0][1] + s * (uvs[1][1] - uvs[0][1]) + t * (uvs[3][1] - uvs[0][1])
            ti = np.clip((u / 16 * tw).astype(int), 0, tw - 1)
            tj = np.clip((v / 16 * th).astype(int), 0, th - 1)
            texel = tex[tj, ti]
            depth = scr[0][2] + s * (scr[1][2] - scr[0][2]) + t * (scr[3][2] - scr[0][2])
            region = canvas.depth[y0:y1, x0:x1]
            draw = inside & (texel[..., 3] >= 0.5) & (depth > region)
            shade = 1.0 if lit or not e['shade'] else face_shade(normal)
            colour = texel[..., :3] * shade * np.array(tint, np.float32)
            canvas.rgb[y0:y1, x0:x1][draw] = colour[draw]
            region[draw] = depth[draw]


def face_shade(n):
    """Vanilla face shading, blended for turned faces as PartRenderer.shade does."""
    x, y, z = n
    return x * x * 0.6 + z * z * 0.8 + y * y * (1.0 if y > 0 else 0.5)


def tilt(angle):
    """Turns a part about the block centre's x axis, top toward +z (toward the front camera)."""
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))

    def f(p):
        y, z = p[1] - 8, p[2] - 8
        return (p[0], 8 + y * c - z * s, 8 + y * s + z * c)
    return f


def rgb01(c):
    return ((c >> 16 & 255) / 255, (c >> 8 & 255) / 255, (c & 255) / 255)


def shaft_down(p):
    """Create's half shaft model points south (+z); turned to point down out of the bottom."""
    x, y, z = p
    return (x, 16 - z, y)


def render_block(textures, assets, block, view):
    _, yaw, pitch, lit = view
    size = int(SCALE * 24)
    canvas = Canvas(size, size, (52, 54, 60))
    origin = (size / 2, size / 2 + SCALE)
    folder = assets / 'models/block' / block
    tint = rgb01(TINT.get(block, 0xFFFFFF))
    for file in sorted(folder.glob('*.json')):
        part = file.stem
        elements = load_model(file)
        pose = tilt(AIM_TILT) if part in AIMED and (folder / 'yoke.json').exists() or (
            block == 'headlight' and part in AIMED) else None
        if part == 'lens':
            k = 1.0 if lit else UNLIT_LENS
            draw_part(canvas, textures, elements, origin, yaw, pitch, tuple(c * k for c in tint), lit=lit,
                      transform=pose)
        elif part == 'lens_core':
            if lit:
                core = rgb01(CORE_WHITE)
                draw_part(canvas, textures, elements, origin, yaw, pitch,
                          tuple((c + w) / 2 for c, w in zip(tint, core)), lit=True, transform=pose)
        else:
            draw_part(canvas, textures, elements, origin, yaw, pitch, transform=pose)
    if block in KINETIC:
        shaft = load_model(CREATE / 'models/block/shaft_half.json')
        draw_part(canvas, textures, shaft, origin, yaw, pitch, transform=shaft_down)
    return canvas.image()


def render_cube(textures, texture, view):
    _, yaw, pitch, _ = view
    size = int(SCALE * 24)
    canvas = Canvas(size, size, (52, 54, 60))
    cube = [dict(f=[0, 0, 0], t=[16, 16, 16], shade=True,
                 faces={f: dict(uv=[0, 0, 16, 16], texture=texture, rotation=0) for f in SHADE})]
    draw_part(canvas, textures, cube, (size / 2, size / 2), yaw, pitch)
    return canvas.image()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('blocks', nargs='*', default=BLOCKS)
    ap.add_argument('--out', default=str(MOD / 'build/art-preview/preview.png'))
    ap.add_argument('--compare', action='append', default=[],
                    help='another assets/lightworks folder, drawn after each block (repeatable)')
    ap.add_argument('--label', default='current')
    ap.add_argument('--compare-label', action='append', default=[], help='a label per --compare')
    ap.add_argument('--front-off', action='store_true', help='add an unlit front view')
    args = ap.parse_args()
    views = VIEWS[:1] + [('front, off', 45, 30, False)] + VIEWS[1:] if args.front_off else VIEWS
    sets = [(args.label, ASSETS)]
    for i, folder in enumerate(args.compare):
        label = args.compare_label[i] if i < len(args.compare_label) else f'compare {i + 1}'
        sets.append((label, Path(folder)))
    cell = int(SCALE * 24)
    rows = []
    reference = [('create:block/brass_casing', 'Create brass casing'),
                 ('create:block/andesite_casing', 'Create andesite casing'),
                 ('create:block/industrial_iron_block', 'Create industrial iron')]
    tex = Textures(ASSETS)
    rows.append([(name, render_cube(tex, t, VIEWS[0])) for t, name in reference])
    for block in args.blocks:
        for label, assets in sets:
            textures = Textures(assets)
            rows.append([(f'{block} ({label}): {v[0]}', render_block(textures, assets, block, v)) for v in views])
    # Two blocks per line after the reference casings (one when each has three views)
    per_line = 1 if len(views) > 2 else 2
    lines = [rows[0]] + [sum(rows[i:i + per_line], []) for i in range(1, len(rows), per_line)]
    cols = max(len(r) for r in lines)
    sheet = Image.new('RGB', (cols * cell, len(lines) * (cell + 18)), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    for r, row in enumerate(lines):
        for c, (name, im) in enumerate(row):
            x, y = c * cell, r * (cell + 18)
            sheet.paste(im, (x, y + 18))
            d.text((x + 6, y + 3), name, fill=(235, 235, 235))
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print('preview written to', out)


if __name__ == '__main__':
    main()
