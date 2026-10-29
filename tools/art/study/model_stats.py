"""Geometry and UV habits of Create's block models. python model_stats.py <create models/block dir> <textures dir>"""
import json
import statistics as st
import sys
from collections import Counter
from pathlib import Path
from PIL import Image

models = Path(sys.argv[1])
textures = Path(sys.argv[2])
n_models = n_el = 0
el_counts, coord_kind, rot_angles, rot_axes = [], Counter(), Counter(), Counter()
thin, density, tex_sizes, faces_per_texture = Counter(), [], Counter(), Counter()
uv_rot = Counter()
size_cache = {}


def tex_size(ref, model_textures):
    seen = set()
    while ref.startswith('#'):
        if ref in seen:
            return None
        seen.add(ref)
        ref = model_textures.get(ref[1:], '')
    if not ref.startswith('create:'):
        return None
    path = textures / (ref.split(':', 1)[1] + '.png')
    if path not in size_cache:
        size_cache[path] = Image.open(path).size if path.exists() else None
    return size_cache[path]


for f in models.rglob('*.json'):
    try:
        m = json.loads(f.read_text(encoding='utf-8'))
    except Exception:
        continue
    els = m.get('elements')
    if not els:
        continue
    n_models += 1
    el_counts.append(len(els))
    texs = m.get('textures', {})
    for e in els:
        n_el += 1
        for v in e['from'] + e['to']:
            frac = abs(v - round(v))
            coord_kind['integer' if frac < 1e-6 else 'half' if abs(frac - 0.5) < 1e-6 else
                       'quarter' if abs(frac * 4 - round(frac * 4)) < 1e-6 else 'other'] += 1
        size = [e['to'][i] - e['from'][i] for i in range(3)]
        thin['<1' if min(size) < 1 - 1e-6 else '1' if min(size) < 1 + 1e-6 else '2' if min(size) < 2 + 1e-6
             else '3-4' if min(size) <= 4 else '>4'] += 1
        if 'rotation' in e:
            rot_angles[e['rotation'].get('angle')] += 1
            rot_axes[e['rotation'].get('axis')] += 1
        for face, d in e.get('faces', {}).items():
            uv_rot[d.get('rotation', 0)] += 1
            ts = tex_size(d.get('texture', ''), texs)
            if ts:
                tex_sizes[ts] += 1
            if 'uv' in d and ts:
                u = abs(d['uv'][2] - d['uv'][0]) * ts[0] / 16
                fw = {'north': size[0], 'south': size[0], 'east': size[2], 'west': size[2]}.get(face, size[0])
                if fw > 0 and u > 0:
                    density.append(u / fw)
print('models with elements', n_models, 'elements', n_el)
print('elements per model: median', st.median(el_counts), 'p90', sorted(el_counts)[9 * len(el_counts) // 10],
      'max', max(el_counts))
print('coordinate kinds', coord_kind.most_common())
print('smallest element side', thin.most_common())
print('rotations', rot_angles.most_common(), rot_axes.most_common())
print('face uv rotations', uv_rot.most_common())
print('texture sizes by face', tex_sizes.most_common(6))
d = sorted(density)
print('texels per model pixel (horizontal): median', round(st.median(d), 3), 'share within 0.9-1.1:',
      round(sum(1 for x in d if 0.9 <= x <= 1.1) / len(d), 2), ' share 2x (1.9-2.1):',
      round(sum(1 for x in d if 1.9 <= x <= 2.1) / len(d), 2))
