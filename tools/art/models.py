"""Writes every Lightworks block, part and item model as JSON.

Each block is modelled pointing up: the mount at the bottom (y = 0), moving parts pivoting about the
block centre (8, 8, 8). Blockstates turn the base to the block's facing; renderers turn the parts (see
render/MountFrame). Item models are the parts put together, aimed straight up.
Run from anywhere: python tools/art/models.py. Writes into src/main/resources/assets/lightworks/models/.
"""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/lightworks/models'

TEXTURES = {
    'metal': 'lightworks:block/light_metal',
    'brass': 'lightworks:block/light_brass',
    'iron': 'lightworks:block/light_iron',
    'base': 'lightworks:block/light_base',
    'base_side': 'lightworks:block/light_base_side',
    'lens': 'lightworks:block/lens',
    'rim': 'lightworks:block/rim_top',
    'casing': 'lightworks:block/pan_tilt_head_side',
    'casing_plain': 'lightworks:block/casing_plain',
    'beacon_casing': 'lightworks:block/beacon_casing',
    'shaft': 'lightworks:block/shaft_input',
    'turntable': 'lightworks:block/turntable',
    'turntable_side': 'lightworks:block/turntable_side',
    'cage': 'lightworks:block/lamp_cage',
    'copper': 'lightworks:block/copper_housing',
}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def uv(face, f, t):
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


def box(f, t, tex, **per_face):
    """A cuboid from f to t (0-16 units); tex for every face, per_face overrides (None drops a face)."""
    faces = {}
    for face in FACES:
        name = per_face.get(face, tex)
        if name is None:
            continue
        faces[face] = {'uv': [round(v, 3) for v in uv(face, f, t)], 'texture': '#' + name}
    return {'from': list(f), 'to': list(t), 'faces': faces}


def write(path, elements, particle='metal'):
    used = sorted({face['texture'][1:] for e in elements for face in e['faces'].values()})
    textures = {name: TEXTURES[name] for name in used}
    textures['particle'] = TEXTURES[particle]
    model = {'parent': 'block/block', 'textures': textures, 'elements': elements}
    file = OUT / f'{path}.json'
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(json.dumps(model, indent=2) + '\n')
    return elements


def item(name, *part_lists):
    elements = [e for parts in part_lists for e in parts]
    write(f'item/{name}', elements)


# Shared mounting plate of the Searchlight and Floodlight
def plate():
    return [box((2, 0, 2), (14, 2, 14), 'base_side', up='base'),
            box((5, 2, 5), (11, 3, 11), 'iron')]


# Searchlight: drum on a yoke
sl_base = write('block/searchlight/base', plate(), 'base_side')
sl_yoke = write('block/searchlight/yoke', [
    box((4, 3, 4), (12, 4, 12), 'iron'),
    box((2.5, 3, 6.5), (4, 10, 9.5), 'iron'),
    box((12, 3, 6.5), (13.5, 10, 9.5), 'iron'),
], 'iron')
sl_head = write('block/searchlight/head', [
    box((4.5, 4, 4.5), (11.5, 13, 11.5), 'metal'),
    box((4, 13, 4), (12, 15, 12), 'brass', up='rim'),
    box((5.5, 3, 5.5), (10.5, 4, 10.5), 'metal'),
    box((4, 7, 7), (4.5, 9, 9), 'brass'),
    box((11.5, 7, 7), (12, 9, 9), 'brass'),
])
sl_lens = write('block/searchlight/lens', [box((5, 15, 5), (11, 15.4, 11), 'lens')], 'lens')
item('searchlight', sl_base, sl_yoke, sl_head, sl_lens)

# Floodlight: wide flat head on a wide yoke
fl_base = write('block/floodlight/base', plate(), 'base_side')
fl_yoke = write('block/floodlight/yoke', [
    box((4, 3, 4), (12, 4, 12), 'iron'),
    box((0.5, 3, 6.5), (2, 10, 9.5), 'iron'),
    box((14, 3, 6.5), (15.5, 10, 9.5), 'iron'),
    box((2, 3, 7), (4, 4, 9), 'iron'),
    box((12, 3, 7), (14, 4, 9), 'iron'),
], 'iron')
fl_head = write('block/floodlight/head', [
    box((2, 5.5, 2), (14, 10.5, 14), 'metal'),
    box((2, 10.5, 2), (14, 11.5, 14), 'brass', up='rim'),
    box((4, 4.5, 4), (12, 5.5, 12), 'metal'),
])
fl_lens = write('block/floodlight/lens', [box((2.5, 11.5, 2.5), (13.5, 11.8, 13.5), 'lens')], 'lens')
item('floodlight', fl_base, fl_yoke, fl_head, fl_lens)

# Headlight: compact lamp on a stalk, no yoke
hl_base = write('block/headlight/base', [
    box((4, 0, 4), (12, 1.5, 12), 'base_side', up='base'),
    box((7, 1.5, 7), (9, 4.5, 9), 'iron'),
], 'base_side')
hl_head = write('block/headlight/head', [
    box((4.5, 4.5, 4.5), (11.5, 11, 11.5), 'metal'),
    box((4, 11, 4), (12, 12.5, 12), 'iron', up='rim'),
])
hl_lens = write('block/headlight/lens', [box((5, 12.5, 5), (11, 12.8, 11), 'lens')], 'lens')
item('headlight', hl_base, hl_head, hl_lens)

# Pan-Tilt Head: casing with a turntable on top, shaft socket underneath
pt_base = write('block/pan_tilt_head/base', [
    box((0, 0, 0), (16, 13, 16), 'casing', up='casing_plain', down='shaft'),
], 'casing')
pt_turntable = write('block/pan_tilt_head/turntable', [
    box((1, 13, 1), (15, 16, 15), 'turntable_side', up='turntable', down=None),
], 'turntable_side')
item('pan_tilt_head', pt_base, pt_turntable)

# Rotating Beacon: casing with a rotor carrying two opposite lamps
rb_base = write('block/rotating_beacon/base', [
    box((1, 0, 1), (15, 4, 15), 'beacon_casing', up='casing_plain', down='shaft'),
], 'beacon_casing')
rb_rotor = write('block/rotating_beacon/rotor', [
    box((4, 4, 4), (12, 12, 12), 'metal'),
    box((5, 12, 5), (11, 13.5, 11), 'brass'),
    box((4.5, 4.5, 12), (11.5, 11.5, 12.5), 'brass', south='rim'),
    box((4.5, 4.5, 3.5), (11.5, 11.5, 4), 'brass', north='rim'),
])
rb_lens = write('block/rotating_beacon/lens', [
    box((5, 5, 12.5), (11, 11, 12.8), 'lens'),
    box((5, 5, 3.2), (11, 11, 3.5), 'lens'),
], 'lens')
item('rotating_beacon', rb_base, rb_rotor, rb_lens)

# Warning Light: lamp dome in a guard cage
wl_base = write('block/warning_light/base', [
    box((4, 0, 4), (12, 2, 12), 'metal'),
    box((4.5, 2, 4.5), (5.5, 9, 5.5), 'iron'),
    box((10.5, 2, 4.5), (11.5, 9, 5.5), 'iron'),
    box((4.5, 2, 10.5), (5.5, 9, 11.5), 'iron'),
    box((10.5, 2, 10.5), (11.5, 9, 11.5), 'iron'),
    box((4.5, 9, 4.5), (11.5, 10, 11.5), 'metal'),
])
wl_lens = write('block/warning_light/lens', [
    box((5.5, 2, 5.5), (10.5, 8.5, 10.5), 'lens'),
    box((6.5, 8.5, 6.5), (9.5, 9, 9.5), 'lens'),
], 'lens')
item('warning_light', wl_base, wl_lens)

# Tail and Brake Light: flat rectangular lamp
tl_base = write('block/tail_light/base', [
    box((3, 0, 4), (13, 2, 12), 'metal'),
], 'metal')
tl_lens = write('block/tail_light/lens', [box((3.5, 2, 4.5), (12.5, 3, 11.5), 'lens')], 'lens')
item('tail_light', tl_base, tl_lens)

# Navigation Light: small dome
nl_base = write('block/navigation_light/base', [
    box((5, 0, 5), (11, 1.5, 11), 'metal'),
], 'metal')
nl_lens = write('block/navigation_light/lens', [
    box((5.5, 1.5, 5.5), (10.5, 5.5, 10.5), 'lens'),
    box((6.5, 5.5, 6.5), (9.5, 6, 9.5), 'lens'),
], 'lens')
item('navigation_light', nl_base, nl_lens)

print('models written to', OUT)
