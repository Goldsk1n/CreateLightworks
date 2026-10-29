"""Structure of Create's 16x16 block textures vs ours: light direction, border treatment, flatness, dithering.
python structure_stats.py <create textures/block> <our textures/block or old textures dir>..."""
import statistics as st
import sys
from pathlib import Path
from PIL import Image


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def stats(files):
    rows = []
    for f in files:
        im = Image.open(f).convert('RGBA')
        if im.size != (16, 16):
            continue
        px = im.load()
        if any(px[x, y][3] < 255 for x in range(16) for y in range(16)):
            continue
        L = [[lum(px[x, y]) for x in range(16)] for y in range(16)]
        top = st.mean(L[0][1:15]); bottom = st.mean(L[15][1:15])
        left = st.mean(L[y][0] for y in range(1, 15)); right = st.mean(L[y][15] for y in range(1, 15))
        ring1 = [L[y][x] for y in range(16) for x in range(16) if min(x, y, 15 - x, 15 - y) == 0]
        ring2 = [L[y][x] for y in range(16) for x in range(16) if min(x, y, 15 - x, 15 - y) == 1]
        inner = [L[y][x] for y in range(16) for x in range(16) if min(x, y, 15 - x, 15 - y) >= 3]
        # flatness: share of horizontally/vertically adjacent pairs with the same colour
        same = sum(px[x, y] == px[x + 1, y] for y in range(16) for x in range(15)) + \
            sum(px[x, y] == px[x, y + 1] for y in range(15) for x in range(16))
        # isolated pixels: differ from all 4 neighbours
        iso = sum(1 for y in range(1, 15) for x in range(1, 15)
                  if all(px[x, y] != px[x + dx, y + dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))))
        # checkerboard dithering: 2x2 blocks with a == d, b == c, a != b
        chk = sum(1 for y in range(15) for x in range(15)
                  if px[x, y] == px[x + 1, y + 1] and px[x + 1, y] == px[x, y + 1] and px[x, y] != px[x + 1, y])
        colours = len({px[x, y] for x in range(16) for y in range(16)})
        rows.append(dict(name=f.stem, tl=(top + left) / 2 - (bottom + right) / 2, top_minus_bottom=top - bottom,
                         left_minus_right=left - right, ring1=st.mean(ring1) - st.mean(inner),
                         ring2=st.mean(ring2) - st.mean(inner), same=same / 480, iso=iso / 196, chk=chk,
                         colours=colours))
    return rows


def summary(label, rows):
    print(f'\n== {label}: {len(rows)} opaque 16x16 textures')
    for k in ('top_minus_bottom', 'left_minus_right', 'ring1', 'ring2', 'same', 'iso', 'chk', 'colours'):
        v = sorted(r[k] for r in rows)
        print(f'  {k:18s} median {st.median(v):7.3f}   p25 {v[len(v) // 4]:7.3f}   p75 {v[3 * len(v) // 4]:7.3f}')
    lit_tl = sum(1 for r in rows if r['top_minus_bottom'] > 3 and r['left_minus_right'] > -3)
    print(f'  top lighter than bottom (>3 lum): {sum(1 for r in rows if r["top_minus_bottom"] > 3)}; '
          f'bottom lighter: {sum(1 for r in rows if r["top_minus_bottom"] < -3)}; '
          f'left lighter than right: {sum(1 for r in rows if r["left_minus_right"] > 3)}; '
          f'right lighter: {sum(1 for r in rows if r["left_minus_right"] < -3)}')
    print(f'  outer ring darker than inside: {sum(1 for r in rows if r["ring1"] < -3)}, lighter: '
          f'{sum(1 for r in rows if r["ring1"] > 3)}')


create = stats(sorted(Path(sys.argv[1]).glob('*.png')))
summary('Create', create)
for d in sys.argv[2:]:
    summary(d, stats(sorted(Path(d).glob('*.png'))))
