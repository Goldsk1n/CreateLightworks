"""Palette statistics over Create's block textures: shared colours, family ramps, hue shifting.
python palette_stats.py <create textures/block dir>"""
import colorsys
import sys
from collections import Counter, defaultdict
from pathlib import Path
from PIL import Image

root = Path(sys.argv[1])
files = [f for f in root.rglob('*.png')]
pix = Counter()        # colour -> pixel count
tex = Counter()        # colour -> number of textures using it
by_file = {}
for f in files:
    im = Image.open(f).convert('RGBA')
    c = Counter(p[:3] for p in im.get_flattened_data() if p[3] == 255) if hasattr(im, 'get_flattened_data') else \
        Counter(p[:3] for p in im.getdata() if p[3] == 255)
    by_file[f.relative_to(root).as_posix()] = c
    pix.update(c)
    for k in c:
        tex[k] += 1

print('textures', len(files), 'distinct opaque colours', len(pix))
shared = [k for k, v in tex.items() if v >= 10]
print('colours used in >=10 textures', len(shared), ' >=25:', sum(1 for v in tex.values() if v >= 25),
      ' only in one texture:', sum(1 for v in tex.values() if v == 1))
cover = sum(pix[k] for k in shared) / sum(pix.values())
print(f'share of all opaque pixels drawn with colours used in >=10 textures: {cover:.0%}')


def hsv(c):
    h, s, v = colorsys.rgb_to_hsv(*(x / 255 for x in c))
    return round(h * 360), round(s * 100), round(v * 100)


def hexc(c):
    return '#%02x%02x%02x' % c


# Family ramps: textures named by material; colours used in >= 3 of them, sorted by value
families = {
    'andesite': ['andesite_casing', 'gearbox', 'andesite_encased', 'andesite_belt_cover', 'axis', 'andesite_block',
                 'ladder_andesite', 'andesite_door', 'andesite_funnel', 'andesite_tunnel'],
    'brass': ['brass_casing', 'brass_gearbox', 'brass_encased', 'brass_block', 'brass_belt_cover', 'ladder_brass',
              'brass_door', 'brass_funnel', 'brass_tunnel', 'clockwork_bearing'],
    'copper': ['copper_casing', 'copper_backtank', 'copper_door', 'copper_underside', 'ladder_copper', 'fluid_valve',
               'copper_redstone_plate', 'spout', 'engine'],
    'railway/dark': ['railway_casing', 'dark_metal_block', 'controls', 'station', 'signal_box', 'track_signal',
                     'chain_signal_box', 'contraption_controls'],
    'industrial iron': ['industrial_iron_block', 'weathered_iron_block', 'roller_metal', 'bracket_metal',
                        'waterwheel_metal', 'mechanical_bearing_side', 'windmill_bearing_side'],
    'wood (casing insides)': ['andesite_casing', 'brass_casing', 'gearbox_top'],
}
ramps = {}
for fam, keys in families.items():
    use = Counter()
    n = 0
    for name, c in by_file.items():
        stem = name.rsplit('/', 1)[-1]
        if any(stem.startswith(k) for k in keys):
            n += 1
            for k, v in c.items():
                use[k] += 1
    common = [k for k, v in use.items() if v >= max(2, n // 3)]
    common.sort(key=lambda c: hsv(c)[2])
    ramps[fam] = common
    print(f'\n{fam}: {n} textures, {len(common)} recurring colours')
    for c in common:
        print('  ', hexc(c), 'hsv', hsv(c), 'in', use[c], 'textures')

# Most used colours overall
print('\ntop 40 colours by number of textures')
for k, v in tex.most_common(40):
    print('  ', hexc(k), hsv(k), v)
