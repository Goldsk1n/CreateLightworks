"""Writes every Lightworks block, part and item model as JSON, and each block's texture sheet.

Each block is modelled pointing up: the mount at the bottom (y = 0), moving parts pivoting about the
block centre (8, 8, 8). Blockstates turn the base to the block's facing; renderers turn the parts (see
render/MountFrame). Item models are the parts put together, aimed straight up.

Every face names a skin: own(...) is drawn at the face's exact size by a painter in textures.py and laid
out on the block's sheet (textures/block/<block>.png), so frames and bolts sit on its edges; create(...)
points at one of Create's textures by resource location (Create is a hard dependency, so it is always
there, and we ship none of its files; every texture used here exists from Create 6.0.0).
Run from anywhere: python tools/art/models.py (Python 3 + Pillow). Writes into
src/main/resources/assets/lightworks/.
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import textures  # noqa: E402

ASSETS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/lightworks'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
SIDES = ('north', 'south', 'east', 'west')
BRASS_CASING = 'create:block/brass_casing'
BRASS_GEARBOX = 'create:block/brass_gearbox'
ANDESITE_CASING = 'create:block/andesite_casing'
INDUSTRIAL_IRON = 'create:block/industrial_iron_block'


def own(painter, **params):
    """A face painted by textures.<painter> at its own size."""
    assert painter in textures.PAINTERS, painter
    return ('own', painter, tuple(sorted(params.items())))


def create(texture):
    """A face showing Create's texture where the face sits in the block, as Create's own models do."""
    return ('create', texture)


def uv(face, f, t):
    """Vanilla's default UV for a face: the part of a 16x16 texture the face covers."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    if face == 'north':
        return [16 - x2, 16 - y2, 16 - x1, 16 - y1]
    if face == 'south':
        return [x1, 16 - y2, x2, 16 - y1]
    if face == 'east':
        return [16 - z2, 16 - y2, 16 - z1, 16 - y1]
    if face == 'west':
        return [z1, 16 - y2, z2, 16 - y1]
    if face == 'up':
        return [x1, z1, x2, z2]
    return [x1, 16 - z2, x2, 16 - z1]


def size(face, f, t):
    """Width and height of a face as seen from outside, in pixels."""
    dx, dy, dz = (t[i] - f[i] for i in range(3))
    if face in ('north', 'south'):
        return dx, dy
    if face in ('east', 'west'):
        return dz, dy
    return dx, dz


def pixels(v):
    return max(1, math.ceil(v - 1e-6))


def box(f, t, skin, **per_face):
    """A cuboid from f to t (0-16 units); skin for every face, per_face overrides (None drops a face)."""
    faces = {}
    for face in FACES:
        s = per_face.get(face, skin)
        if s is not None:
            faces[face] = s
    return {'from': list(f), 'to': list(t), 'faces': faces}


def write_json(path, model):
    file = ASSETS / 'models' / f'{path}.json'
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(json.dumps(model, indent=2) + '\n')


CORE_GAP = 0.02
OUTWARD = {'north': (2, -1), 'south': (2, 1), 'west': (0, -1), 'east': (0, 1), 'down': (1, -1), 'up': (1, 1)}


def glowing(e):
    """
    Marks an element of a block's own model as a lens: full-bright and tinted by the block's colour handler,
    so it glows on contraptions and vehicles with no renderer. For lamps placed by the hundred.
    """
    e['glow'] = True
    return e


def core_face(e, face):
    """A zero-thickness element holding only {@code face} of e, moved CORE_GAP outward, in e's lens style."""
    axis, sign = OUTWARD[face]
    f, t = list(e['from']), list(e['to'])
    plane = (t if sign > 0 else f)[axis] + sign * CORE_GAP
    f[axis] = t[axis] = plane
    skin = e['faces'][face]
    style = dict(skin[2]) if skin[0] == 'own' and skin[1] == 'lens' else {}
    return {'from': f, 'to': t, 'faces': {face: own('lens_core', **style)}}


class Block:
    """The parts of one block, sharing one texture sheet."""

    def __init__(self, name, particle):
        self.name, self.particle, self.parts = name, particle, {}

    def part(self, part, *elements):
        self.parts[part] = list(elements)
        if part == 'lens':
            # The glow PartRenderer draws over a lit lens: each lens face again, as a flat face of the same
            # size a hair in front of it, clear but for its hot centre (same pixel size, so it lines up)
            self.parts['lens_core'] = [core_face(e, face) for e in elements for face in e['faces']]
        return part

    def finish(self, *item_parts):
        layout, sheet = self._pack()
        create_keys = {}
        for e in (e for elements in self.parts.values() for e in elements):
            for s in e['faces'].values():
                if s[0] == 'create' and s[1] not in create_keys:
                    create_keys[s[1]] = f'c{len(create_keys)}'
        if sheet:
            out = ASSETS / 'textures/block' / f'{self.name}.png'
            out.parent.mkdir(parents=True, exist_ok=True)
            sheet.save(out)
        scale = 16 / sheet.width if sheet else 1

        def resolve(elements):
            used, out = {}, []
            for e in elements:
                faces = {}
                for face, s in e['faces'].items():
                    if s[0] == 'own':
                        w, h = size(face, e['from'], e['to'])
                        u, v = layout[(s, pixels(w), pixels(h))]
                        rect = [u * scale, v * scale, (u + w) * scale, (v + h) * scale]
                        used['sheet'] = f'lightworks:block/{self.name}'
                        faces[face] = {'uv': [round(c, 4) for c in rect], 'texture': '#sheet'}
                        if e.get('glow'):
                            faces[face]['tintindex'] = 0
                    else:
                        key = create_keys[s[1]]
                        used[key] = s[1]
                        faces[face] = {'uv': [round(c, 4) for c in uv(face, e['from'], e['to'])],
                                       'texture': '#' + key}
                element = {'from': e['from'], 'to': e['to'], 'faces': faces}
                if e.get('glow'):
                    element['shade'] = False
                    element['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
                out.append(element)
            used['particle'] = self.particle
            return {'parent': 'block/block', 'textures': used, 'elements': out}

        for part, elements in self.parts.items():
            write_json(f'block/{self.name}/{part}', resolve(elements))
        write_json(f'item/{self.name}', resolve([e for p in item_parts for e in self.parts[p]]))

    def _pack(self):
        """Shelf-packs every distinct painted face onto the smallest square sheet that holds them."""
        wanted = {}
        for e in (e for elements in self.parts.values() for e in elements):
            for face, s in e['faces'].items():
                if s[0] == 'own':
                    w, h = size(face, e['from'], e['to'])
                    wanted[(s, pixels(w), pixels(h))] = None
        if not wanted:
            return {}, None
        order = sorted(wanted, key=lambda k: (-k[2], -k[1], k[0][1]))
        side = 16
        while True:
            layout, x, y, row = {}, 0, 0, 0
            for key in order:
                w, h = key[1], key[2]
                if x + w > side:
                    x, y, row = 0, y + row, 0
                layout[key] = (x, y)
                x, row = x + w, max(row, h)
            if y + row <= side and all(k[1] <= side for k in order):
                break
            side *= 2
        sheet = Image.new('RGBA', (side, side), (0, 0, 0, 0))
        for (s, w, h), pos in layout.items():
            sheet.paste(textures.PAINTERS[s[1]](w, h, **dict(s[2])), pos)
        return layout, sheet


BRASS_PLATE = own('brass_plate')
BRASS_BAND = own('brass_band')
HOUSING = own('housing')
FINS = own('fins')
SEAT = own('seat')
IRON = own('iron_panel')
BAR = own('bar')
ANDESITE = own('andesite_panel')
LENS = own('lens')                   # Create's lamp pane
FRESNEL_LENS = own('lens', style='fresnel')
PRISM_LENS = own('lens', style='prism')


def plate(collar=3):
    """
    Shared brass mounting plate of the Searchlight and Floodlight, with the iron collar the yoke turns on,
    {@code collar} pixels to each side of the centre.
    """
    return [box((2, 0, 2), (14, 2, 14), BRASS_BAND, up=own('plate_top'), down=SEAT),
            box((8 - collar, 2, 8 - collar), (8 + collar, 3, 8 + collar), BAR)]


def yoke_arm(x0, x1, top, outside, bolt_row):
    """An arm of a yoke from y 3 to {@code top}, its pivot bolt on the {@code outside} face."""
    return box((x0, 3, 6), (x1, top, 10), BAR, **{outside: own('arm', bolt_row=bolt_row)})


# Searchlight: slim drum with a flared brass bezel on a yoke
b = Block('searchlight', BRASS_CASING)
b.part('base', *plate())
b.part('yoke',
       box((4, 3, 4), (12, 4, 12), IRON),
       yoke_arm(2, 4, 11, 'west', 2),
       yoke_arm(12, 14, 11, 'east', 2))
b.part('head',
       box((5, 4, 5), (11, 13, 11), HOUSING, down=FINS, up=None),
       box((4, 13, 4), (12, 15, 12), BRASS_BAND, up=own('bezel_top'), down=BRASS_PLATE),
       box((4, 7, 7), (5, 9, 9), BRASS_PLATE),
       box((11, 7, 7), (12, 9, 9), BRASS_PLATE))
b.part('lens', box((5, 15, 5), (11, 15.4, 11), FRESNEL_LENS))   # lens 7.4/16 from the centre, 6 wide: LightType
b.finish('base', 'yoke', 'head', 'lens')

# Floodlight: wide flat head with one big lens, on a wide yoke. The lens comes down past the yoke plate and the
# collar when level, 3.8 pixels from the pivot: they stay inside that at every pan, or they poke through it
b = Block('floodlight', BRASS_CASING)
b.part('base', *plate(collar=2.5))
b.part('yoke',
       box((4, 3, 5), (12, 4, 11), IRON),
       yoke_arm(1, 2, 10, 'west', 1),
       yoke_arm(14, 15, 10, 'east', 1),
       box((2, 3, 7), (4, 4, 9), BAR),
       box((12, 3, 7), (14, 4, 9), BAR))
b.part('head',
       box((2, 5.5, 2), (14, 10.5, 14), HOUSING, down=FINS, up=None),
       box((2, 10.5, 2), (14, 11.5, 14), BRASS_BAND, up=own('bezel_top'), down=None))
b.part('lens', box((2.5, 11.5, 2.5), (13.5, 11.8, 13.5), PRISM_LENS))   # 3.8/16 from the centre, 11 wide: LightType
b.finish('base', 'yoke', 'head', 'lens')

# Headlight: compact andesite-rimmed lamp on a stalk, no yoke
b = Block('headlight', ANDESITE_CASING)
b.part('base',
       box((4, 0, 4), (12, 2, 12), ANDESITE, down=SEAT),
       box((7, 2, 7), (9, 5, 9), BAR))
b.part('head',
       box((5, 5, 5), (11, 11, 11), HOUSING, down=FINS, up=None),
       box((4, 11, 4), (12, 12.5, 12), ANDESITE, up=own('andesite_ring')))
b.part('lens', box((5, 12.5, 5), (11, 12.8, 11), LENS))   # 4.8/16 from the centre, 6 wide
b.finish('base', 'head', 'lens')


def recessed_bottom(x0, x1, frame):
    """
    Create's way of showing a shaft through a casing's bottom: the bottom face sits 0.95 px up, so the
    shaft (drawn by the renderer, out to y 0) shows in its hole; a {@code frame}-wide skirt hides the
    recess. Sides and top belong to the caller's box, whose bottom face is dropped.
    """
    r = 0.95
    gearbox = create(BRASS_GEARBOX)
    i0, i1 = x0 + frame, x1 - frame
    return [
        box((x0, 0, x0), (x1, r, i0), SEAT, down=gearbox, north=None, east=None, west=None, up=None),
        box((x0, 0, i1), (x1, r, x1), SEAT, down=gearbox, south=None, east=None, west=None, up=None),
        box((x0, 0, i0), (i0, r, i1), SEAT, down=gearbox, west=None, north=None, south=None, up=None),
        box((i1, 0, i0), (x1, r, i1), SEAT, down=gearbox, east=None, north=None, south=None, up=None),
        box((i0, r, i0), (i1, r + 0.05, i1), None, down=gearbox),
    ]


# Pan-Tilt Head: Create's brass casing with a turntable on top, the shaft coming in underneath
b = Block('pan_tilt_head', BRASS_CASING)
b.part('base',
       box((0, 0, 0), (16, 13, 16), create(BRASS_CASING), down=None),
       *recessed_bottom(0, 16, 2))
b.part('turntable', box((1, 13, 1), (15, 16, 15), own('turntable_side'), up=own('turntable_top'), down=None))
b.finish('base', 'turntable')

# Rotating Beacon: low brass casing with a rotor carrying two opposite lamps
b = Block('rotating_beacon', BRASS_CASING)
b.part('base',
       box((1, 0, 1), (15, 4, 15), own('brass_slab_side'), up=own('brass_casing'), down=None),
       *recessed_bottom(1, 15, 1))
b.part('rotor',
       box((4, 4, 4), (12, 12, 12), HOUSING, down=None, north=SEAT, south=SEAT),
       box((5, 12, 5), (11, 13.5, 11), BRASS_BAND, up=BRASS_PLATE, down=None),
       box((4, 4, 12), (12, 12, 12.5), BRASS_BAND, south=own('bezel_top'), north=None),
       box((4, 4, 3.5), (12, 12, 4), BRASS_BAND, north=own('bezel_top'), south=None))
b.part('lens',
       box((5, 5, 12.5), (11, 11, 12.8), FRESNEL_LENS),   # 4.8/16 from the centre, 6 wide
       box((5, 5, 3.2), (11, 11, 3.5), FRESNEL_LENS))
b.finish('base', 'rotor', 'lens')


def rim(x0, z0, x1, z1, y0, y1):
    """Four iron bars round a lamp from y0 to y1, without inner faces (the lens fills the inside)."""
    return [box((x0, y0, z0), (x1, y1, z0 + 1), BAR, south=None),
            box((x0, y0, z1 - 1), (x1, y1, z1), BAR, north=None),
            box((x0, y0, z0 + 1), (x0 + 1, y1, z1 - 1), BAR, east=None, north=None, south=None),
            box((x1 - 1, y0, z0 + 1), (x1, y1, z1 - 1), BAR, west=None, north=None, south=None)]


# Warning Light: brass lantern, a lamp dome between a brass foot and a brass collar with a knob
b = Block('warning_light', BRASS_CASING)
b.part('base',
       box((4, 0, 4), (12, 2, 12), BRASS_BAND, up=own('plate_top'), down=SEAT),
       box((5, 8, 5), (11, 9, 11), BRASS_BAND, up=BRASS_PLATE, down=None),
       box((7, 9, 7), (9, 10, 9), BRASS_PLATE, down=None))
b.part('lens', box((5, 2, 5), (11, 8, 11), own('lens', style='dome'), down=None, up=None))
b.finish('base', 'lens')

# Tail and Brake Light: flat rectangular lamp in an iron frame
b = Block('tail_light', INDUSTRIAL_IRON)
b.part('base',
       box((3, 0, 4), (13, 2, 12), IRON, down=SEAT),
       *rim(3, 4, 13, 12, 2, 3))
b.part('lens', box((4, 2, 5), (12, 2.8, 11), LENS, down=None))
b.finish('base', 'lens')

# Navigation Light: small dome on an iron foot
b = Block('navigation_light', INDUSTRIAL_IRON)
b.part('base', box((5, 0, 5), (11, 2, 11), IRON, down=SEAT))
b.part('lens',
       box((6, 2, 6), (10, 5, 10), LENS, down=None),
       box((7, 5, 7), (9, 5.5, 9), LENS, down=None))
b.finish('base', 'lens')

# Edge Light: a low lens on a flat iron foot, one baked model (see glowing)
b = Block('edge_light', INDUSTRIAL_IRON)
b.part('base',
       box((5, 0, 5), (11, 1, 11), IRON, down=SEAT),
       glowing(box((6, 1, 6), (10, 3, 10), LENS, down=None)))
# Drawn over it in Ponder scenes only, which light every block alike (EdgeLightRenderer)
b.part('lens', box((6, 1, 6), (10, 3, 10), LENS, down=None))
b.finish('base')

print('models and sheets written to', ASSETS)
