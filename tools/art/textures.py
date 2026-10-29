"""Painters for Lightworks block textures, following docs/create-art-style.md.

Every colour comes from one of Create's palette ramps (sampled from Create's block textures, which its
authors allowed us to use as references; no Create pixels are copied). Frames follow Create's anatomy:
an outline ring in the frame's own material (lighter top and left), a bright bevel ring lit from the top
left, an inset shadow on the top and left inside, then the fill. Fills are hand-placed patterns
(planks, flat panels with a diagonal sheen, recessed rings lit from one side), never noise, dithering or
pillow shading (rings stepping evenly to a bright centre).

Each painter draws one model face at its exact size in pixels. models.py lays every face of a block out
on the block's own sheet (textures/block/<block>.png) and calls these; run models.py, not this file.
tools/art/preview.py shows the result without starting the game.
"""
import math

from PIL import Image


def ramp(text):
    return [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,) for h in text.split()]


# Create's ramps, darkest first (docs/create-art-style.md, "Palette")
BRASS = ramp('#592424 #68372c #724731 #875137 #9e6947 #a46b47 #af794f #bf8e55 #c79758 #cea05a #d7aa5e '
             '#e4b763 #f7cb6c #fbdc7d #ffeb8c #fff9bf')
BRASS_WOOD = ramp('#291a0c #301e0e #3a2411 #442b15 #492f17 #4f3218 #53381a #5a3e1e')
ANDESITE = ramp('#353b39 #404543 #494c4a #505351 #585b58 #60635f #686c68 #707570 #828784 #8f9691 #9aa49d '
                '#a8b3ab')
STEEL = ramp('#17191c #1e2126 #23262b #272b30 #2a2e33 #2c3135 #303639 #34393c #353b3e #3a4043 #444b4c')
IRON = ramp('#2b2b2e #353438 #3f3e42 #444444 #494848 #4f4f4f #595858 #676161 #757171 #8d8b8b #989797')
# Lens glass in neutral greys, tinted by the renderer (see lens())
GLASS = ramp('#8e8e8e #a6a6a6 #bdbdbd #d2d2d2 #e6e6e6 #f5f5f5 #ffffff')
CLEAR = (0, 0, 0, 0)
B = {name: BRASS[i] for i, name in enumerate(
    'corner dark2 outline_shade 3 outline_lit 5 6 7 8 lo 10 mid2 mid hi2 hi peak'.split())}


def canvas(w, h, colour=CLEAR):
    return Image.new('RGBA', (w, h), colour)


def put(img, x, y, colour):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), colour)


def fade(n, tones, seed=0, spark=None):
    """
    Colours of a line n pixels long, from its lit end (top or left). A single colour gives a flat line,
    as Create's brass, train and iron outlines are. A list of tones, brightest first, fades like Create's
    bevels: a tone per stretch (one per five pixels, at most len(tones)), each change trading one pixel
    with its neighbour, and on lit lines of eight pixels or more one spark pixel in the middle of the
    second stretch (Create's sparks are rare: most of a bevel is runs of two to eight).
    The seed shifts the changes by a pixel so neighbouring lines don't change tone in step.
    """
    if not isinstance(tones, list):
        return [tones] * n
    k = max(1, min(len(tones), 1 + n // 5))
    cuts = [round(n * j / k) + (seed + j) % 2 for j in range(1, k)]
    base = [tones[sum(i >= c for c in cuts)] for i in range(n)]
    out = base[:]
    for c in cuts:
        if 2 <= c < n - 1:
            out[c - 1], out[c] = base[c], base[c - 1]
    if spark and n >= 8 and cuts:
        out[(cuts[0] + (cuts[1:] or [n])[0]) // 2] = spark
    return out


def paint(img, points, tones, seed=0, spark=None):
    """Paints a line through points (from its lit end) with fade()."""
    for (x, y), colour in zip(points, fade(len(points), tones, seed, spark)):
        put(img, x, y, colour)


def frame(img, outline_lit, outline_shade, bevel_hi, bevel_mid, bevel_lo, corner, corner_dark, shadow=None,
          peak=None):
    """
    Create's frame: ring 0 outline (top and left lighter), ring 1 bevel (bright top and left, mid at the
    top-right and bottom-left turns, low bottom and right), and an optional inset shadow on the top and
    left of ring 2. Corners are darker than the outline beside them; the bottom-right is the darkest.
    Outlines and bevels may be tone lists, which fade along each side from the top left (see fade());
    the peak, if given, sits in the lit corner and sparks along the lit bevels.
    """
    w, h = img.width, img.height
    paint(img, [(x, 0) for x in range(w)], outline_lit, 0)
    paint(img, [(0, y) for y in range(h)], outline_lit, 1)
    paint(img, [(x, h - 1) for x in range(w)], outline_shade, 2)
    paint(img, [(w - 1, y) for y in range(h)], outline_shade, 3)
    put(img, 0, 0, fade(1, outline_shade)[0])
    put(img, w - 1, 0, corner)
    put(img, 0, h - 1, corner)
    put(img, w - 1, h - 1, corner_dark)
    if w >= 4 and h >= 4:
        paint(img, [(x, 1) for x in range(1, w - 1)], bevel_hi, 0, peak)
        paint(img, [(1, y) for y in range(1, h - 1)], bevel_hi, 1, peak)
        paint(img, [(x, h - 2) for x in range(1, w - 1)], bevel_lo, 2)
        paint(img, [(w - 2, y) for y in range(1, h - 1)], bevel_lo, 3)
        put(img, w - 2, 1, bevel_mid)
        put(img, 1, h - 2, bevel_mid)
        if peak:
            for x, y in ((2, 1), (3, 1), (1, 2)):
                put(img, x, y, peak)
    if shadow and w >= 6 and h >= 6:
        for x in range(2, w - 2):
            put(img, x, 2, shadow)
        for y in range(2, h - 2):
            put(img, 2, y, shadow)


def down(ramp_, i, k=3):
    """k tones of a ramp from ramp_[i] downwards: a fade from a line's lit end."""
    return [ramp_[max(0, i - j)] for j in range(k)]


# Bevel fades per material, lit side and shaded side
BRASS_HI, BRASS_LO = down(BRASS, 14), down(BRASS, 10)
STEEL_HI, STEEL_LO = down(STEEL, 10, 2), down(STEEL, 4)
IRON_HI, IRON_LO = down(IRON, 10), down(IRON, 6)
ANDESITE_HI, ANDESITE_LO = down(ANDESITE, 11), down(ANDESITE, 8)
# Andesite's outline is the one Create varies too
ANDESITE_OUT_LIT, ANDESITE_OUT_SHADE = down(ANDESITE, 5, 2), down(ANDESITE, 3, 2)


def brass_frame(img, shadow=None):
    frame(img, B['outline_lit'], B['outline_shade'], BRASS_HI, B['mid'], BRASS_LO, B['corner'], B['corner'],
          shadow, B['peak'])


def steel_frame(img, shadow=None):
    frame(img, STEEL[8], STEEL[1], STEEL_HI, STEEL[7], STEEL_LO, STEEL[2], STEEL[0], shadow)


def iron_frame(img, shadow=None):
    frame(img, IRON[2], IRON[1], IRON_HI, IRON[8], IRON_LO, IRON[1], IRON[0], shadow)


def andesite_frame(img, shadow=None):
    frame(img, ANDESITE_OUT_LIT, ANDESITE_OUT_SHADE, ANDESITE_HI, ANDESITE[9], ANDESITE_LO, ANDESITE[1],
          ANDESITE[0], shadow)


def strip(w, h, light, mid, dark, end_light=None, end_dark=None):
    """
    A face one or two pixels across: lit along its top (or left), shaded along its bottom (or right).
    Line colours may be tone lists, faded along the strip's length (see fade()).
    """
    img = canvas(w, h)
    along = w >= h
    n, m = (w, h) if along else (h, w)
    for j in range(m):
        tones = light if j == 0 else dark if j == m - 1 else mid
        paint(img, [(i, j) if along else (j, i) for i in range(n)], tones, j)
    if end_light:
        lit, shade = end_light, fade(1, end_dark or dark)[0]
        for j in range(m):
            put(img, *((0, j) if along else (j, 0)), lit)
            put(img, *((w - 1, j) if along else (j, h - 1)), shade)
    return img


def sheen(img, box, tones):
    """Fills box with a diagonal sheen: tones[0] at the top left through tones[-1] at the bottom right."""
    x0, y0, x1, y1 = box
    span = max(1, (x1 - x0) + (y1 - y0) - 2)
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = ((x - x0) + (y - y0)) / span
            put(img, x, y, tones[min(len(tones) - 1, int(k * len(tones)))])


def streaks(x, y, seed):
    """Deterministic grain: runs of 2-4 pixels along a plank, not per-pixel noise."""
    run = (x + seed * 5) // (2 + (seed + y) % 3)
    return (run * 7 + y * 3 + seed) % 5


def bolt(img, x, y, head, shadow):
    """A one-pixel bolt head with its shadow below and to the right."""
    put(img, x + 1, y + 1, shadow)
    put(img, x, y, head)


# Brass

def brass_plate(w, h):
    """Solid brass (bezels, caps, small plates): Create's frame round a diagonal sheen."""
    if min(w, h) <= 2:
        return strip(w, h, BRASS_HI[:2], BAND_BODY, B['outline_shade'], B['outline_lit'], B['outline_shade'])
    img = canvas(w, h)
    sheen(img, (0, 0, w, h), [B['hi2'], B['mid'], B['mid'], B['mid2'], BRASS[10]])
    if min(w, h) >= 4:
        brass_frame(img)
    else:
        frame(img, B['outline_lit'], B['outline_shade'], B['hi'], B['mid'], B['lo'], B['corner'], B['corner'])
    return img


BAND_BODY = down(BRASS, 12, 2)


def brass_band(w, h):
    """
    A brass ring seen from the side: bright top edge, warm body, outline below, darker ends. The top
    edge, body and low edge fade along the band like Create's bevels.
    """
    if min(w, h) <= 1:
        return strip(w, h, BRASS_HI[:2], BAND_BODY, B['lo'], B['outline_lit'], B['outline_shade'])
    img = canvas(w, h)
    xs = range(1, w - 1)
    for y in range(h):
        tones = (BRASS_HI[:2] if y == 0 else B['outline_shade'] if y == h - 1 else BRASS_LO if y == h - 2
                 else BAND_BODY)
        paint(img, [(x, y) for x in xs], tones, y, B['peak'] if y == 0 else None)
    for y in range(h):
        put(img, 0, y, B['outline_lit'])
        put(img, w - 1, y, B['outline_shade'])
    put(img, 1, 0, B['peak'])
    return img


def planks(img, box, tones, seam, horizontal, width=3):
    """Wood planks with seams, grain in runs along each plank."""
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            a, b = (y - y0, x - x0) if horizontal else (x - x0, y - y0)
            if a % (width + 1) == width:
                put(img, x, y, seam)
            else:
                plank = a // (width + 1)
                put(img, x, y, tones[1 + streaks(b, a, plank) % (len(tones) - 1)])


def brass_casing(w, h):
    """Create's brass casing at any size: brass frame, inset shadow, dark horizontal planks."""
    if min(w, h) < 6:
        return brass_plate(w, h)
    img = canvas(w, h)
    planks(img, (2, 2, w - 2, h - 2), BRASS_WOOD[2:], BRASS_WOOD[1], horizontal=True)
    brass_frame(img, shadow=BRASS_WOOD[0])
    return img


def brass_slab_side(w, h):
    """Side of a low brass casing (the beacon's base): outline, bevel, a row of planks, outline."""
    img = canvas(w, h)
    if h >= 4:
        planks(img, (1, 2, w - 1, h - 1), BRASS_WOOD[2:], BRASS_WOOD[1], horizontal=True, width=h)
        paint(img, [(x, 1) for x in range(1, w - 1)], BRASS_HI, 0, B['peak'])
        for x in range(1, w - 1):
            put(img, x, 2, BRASS_WOOD[0])
    for x in range(w):
        put(img, x, 0, B['outline_lit'])
        put(img, x, h - 1, B['outline_shade'])
    for y in range(h):
        put(img, 0, y, B['outline_lit'])
        put(img, w - 1, y, B['outline_shade'])
    put(img, w - 1, 0, B['corner'])
    put(img, 0, h - 1, B['corner'])
    put(img, w - 1, h - 1, B['corner'])
    put(img, 2, 1, B['peak'])
    return img


def plate_top(w, h):
    """Top of a lamp's mounting plate: brass frame, inset shadow, train steel inside, a bolt per corner."""
    img = canvas(w, h)
    sheen(img, (2, 2, w - 2, h - 2), [STEEL[6], STEEL[6], STEEL[8]])
    brass_frame(img, shadow=STEEL[0])
    for x, y in ((3, 3), (w - 5, 3), (3, h - 5), (w - 5, h - 5)):
        bolt(img, x, y, B['hi'], STEEL[0])
    return img


def bezel_top(w, h):
    """
    Front of a lamp bezel: a brass ring round the dark seat of the lens. The lens covers all but the
    outer pixel or two, so the outer ring is drawn in the bevel's bright tones, the inner in the outline's.
    """
    img = canvas(w, h, STEEL[0])
    frame(img, BRASS_HI[:2], BRASS_LO[1:], down(BRASS, 4, 2), B['mid2'], down(BRASS, 2, 2), B['mid2'],
          B['outline_lit'], peak=B['peak'])
    return img


def turntable_top(w, h):
    """The Pan-Tilt Head's turntable: brass plate, a recessed ring of bolts, a steel hub, a pointer."""
    img = brass_plate(w, h)
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for y in range(h):
        for x in range(w):
            r = math.hypot(x - cx, y - cy)
            if r <= 1.6:
                put(img, x, y, STEEL[6] if x + y < cx + cy else STEEL[3])
            elif r <= 2.5:
                put(img, x, y, B['outline_shade'] if x + y >= cx + cy else B['outline_lit'])
            elif 4.5 <= r < 5.3:
                put(img, x, y, B['lo'] if x + y < cx + cy else B['hi2'])     # recess: shadow upper left
    for a in range(8):
        x = round(cx + 3.6 * math.cos(a * math.pi / 4 + math.pi / 8))
        y = round(cy + 3.6 * math.sin(a * math.pi / 4 + math.pi / 8))
        bolt(img, x, y, B['peak'], B['outline_shade'])
    for y in range(2, int(cy) - 2):
        put(img, int(cx), y, B['outline_shade'])
        put(img, int(cx) + 1, y, B['outline_lit'])
    return img


def turntable_side(w, h):
    """Rim of the turntable: a brass band notched like a gear's teeth."""
    img = brass_band(w, h)
    for x in range(2, w - 2):
        if x % 3 == 2:
            for y in range(1, h - 1):
                put(img, x, y, B['outline_lit'] if y == 1 else B['outline_shade'])
    return img


# Train steel (lamp housings: Create's railway steel, the dark of its train parts)

def housing(w, h):
    """Lamp housing: train-steel panel with a bevel, a panel seam a third of the way down, steel rivets."""
    if min(w, h) <= 2:
        return strip(w, h, STEEL_HI, down(STEEL, 6, 2), STEEL[1])
    img = canvas(w, h)
    sheen(img, (0, 0, w, h), [STEEL[6], STEEL[6], STEEL[8]])
    if h >= 9:
        s = max(3, h // 3)
        for x in range(w):
            put(img, x, s, STEEL[0])
        paint(img, [(x, s + 1) for x in range(w)], down(STEEL, 9), 1)
    steel_frame(img)
    if min(w, h) >= 7:
        for x in (2, w - 3):
            bolt(img, x, h - 3, STEEL[10], STEEL[0])
    return img


def fins(w, h):
    """Back of a lamp housing: cooling fins, lit along their top edges."""
    if min(w, h) <= 2:
        return strip(w, h, STEEL_HI, down(STEEL, 5, 2), STEEL[1])
    img = canvas(w, h, STEEL[5])
    for y in range(h):
        for x in range(w):
            if x % 2 == 1:
                put(img, x, y, STEEL[0] if y else STEEL[3])
            elif y == 0:
                put(img, x, y, STEEL[10])
    steel_frame(img)
    return img


def seat(w, h):
    """A dark steel surface something sits on or behind."""
    if min(w, h) <= 2:
        return strip(w, h, down(STEEL, 4, 2), STEEL[2], STEEL[0])
    img = canvas(w, h, STEEL[2])
    frame(img, STEEL[4], STEEL[1], down(STEEL, 5, 2), STEEL[3], down(STEEL, 2, 2), STEEL[1], STEEL[0])
    return img


# Industrial iron (signal lamps, yokes)

def iron_panel(w, h):
    """Create's industrial iron: frame, then on larger faces a recessed inner panel."""
    if min(w, h) <= 2:
        return strip(w, h, down(IRON, 9), down(IRON, 6, 2), IRON[1], IRON[8], IRON[2])
    img = canvas(w, h)
    sheen(img, (0, 0, w, h), [IRON[7], IRON[6], IRON[6], IRON[5]])
    iron_frame(img)
    if min(w, h) >= 8:
        frame_box = canvas(w - 4, h - 4)
        sheen(frame_box, (0, 0, w - 4, h - 4), [IRON[5], IRON[4], IRON[4], IRON[3]])
        frame(frame_box, IRON[2], IRON[8], IRON[3], IRON[4], IRON[7], IRON[3], IRON[7])  # recessed: lit low right
        img.paste(frame_box, (2, 2))
    return img


def arm(w, h, bolt_row=None):
    """A yoke arm: industrial iron with a brass pivot bolt {@code bolt_row} pixels from the top."""
    img = iron_panel(w, h)
    if bolt_row is not None and w >= 3:
        cx = (w - 1) // 2
        put(img, cx, bolt_row, B['peak'])
        put(img, cx + 1, bolt_row, B['mid'])
        put(img, cx, bolt_row + 1, B['mid'])
        put(img, cx + 1, bolt_row + 1, B['outline_shade'])
    return img


def bar(w, h):
    """A thin iron bar or post."""
    if min(w, h) <= 2:
        return strip(w, h, IRON_HI, down(IRON, 7, 2), IRON[2], IRON[9], IRON[1])
    return iron_panel(w, h)


# Andesite (the Headlight, the basic tier)

def andesite_panel(w, h):
    """Andesite alloy plate: Create's andesite frame round a flat two-tone sheen."""
    if min(w, h) <= 2:
        return strip(w, h, ANDESITE_HI, down(ANDESITE, 8, 2), ANDESITE_OUT_SHADE, ANDESITE[9], ANDESITE[3])
    img = canvas(w, h)
    sheen(img, (0, 0, w, h), [ANDESITE[8], ANDESITE[7], ANDESITE[7], ANDESITE[6]])
    if min(w, h) >= 4:
        andesite_frame(img)
    else:
        frame(img, down(ANDESITE, 9, 2), down(ANDESITE, 4, 2), ANDESITE_HI, ANDESITE[9], ANDESITE_LO, ANDESITE[3],
              ANDESITE[1])
    return img


def andesite_ring(w, h):
    """Front of the Headlight's andesite bezel round the dark lens seat."""
    img = canvas(w, h, STEEL[0])
    andesite_frame(img)
    return img


# Glass

# Glass tones by role, darkest first. The layout is Create's powered rose quartz pane (brightness:
# border 71-101, body 101-127, streaks 152-190, glint 238), the spread narrower, since a lit lens is
# drawn full-bright and must still read as lit once tinted
RIM_SHADE, RIM_LIT = GLASS[0], GLASS[1]
BODY_SHADE, BODY_LIT = GLASS[2], GLASS[3]
STREAK_FAR, STREAK_NEAR, GLINT = GLASS[4], GLASS[5], GLASS[6]
RIM_CORNER = RIM_SHADE
STREAK_SHARE = 0.4                       # streak length, as a share of the inside of the face's side


def streak_length(n):
    """Pixels a glint's streak runs along a side whose inside is n pixels long (the glint not counted)."""
    return max(1, round(n * STREAK_SHARE)) if n >= 3 else n - 1


def glass(w, h):
    """
    The pixels of a w x h lens face, as {(x, y): colour}, laid out like a pane of Create's rose quartz
    lamp: a dim border (darker along the bottom and right, darkest in the corners), a glint on the first
    pixel inside the upper-left corner with streaks fading along the top and left edges, and a body in
    two flat tones, the darker one in the lower-right corner (half the face on small faces, a smaller
    corner on faces with six or more pixels inside, so no long staircase crosses them). Faces with six or more pixels inside
    get a smaller glint in the lower-right corner, as Create's larger panes and its centre disc do. No
    rings round a centre: that is pillow shading.
    """
    iw, ih = w - 2, h - 2
    px = {}
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            k = (x - 1) / max(iw - 1, 1) + (y - 1) / max(ih - 1, 1)
            px[(x, y)] = BODY_LIT if k < (1 if min(iw, ih) < 6 else 1.25) else BODY_SHADE
    for i in range(1, streak_length(iw) + 1):
        px[(1 + i, 1)] = STREAK_NEAR if i <= (streak_length(iw) + 1) // 2 else STREAK_FAR
    for i in range(1, streak_length(ih) + 1):
        px[(1, 1 + i)] = STREAK_NEAR if i <= (streak_length(ih) + 1) // 2 else STREAK_FAR
    px[(1, 1)] = GLINT
    if min(iw, ih) >= 6:
        px[(w - 2, h - 2)] = STREAK_NEAR
        px[(w - 3, h - 2)] = px[(w - 2, h - 3)] = STREAK_FAR
    glass_border(px, w, h)
    return px


def glass_border(px, w, h):
    """A lens face's dim border: lighter along the top and left, darkest in the corners."""
    for x in range(w):
        px[(x, 0)], px[(x, h - 1)] = RIM_LIT, RIM_SHADE
    for y in range(h):
        px[(0, y)], px[(w - 1, y)] = RIM_LIT, RIM_SHADE
    for corner in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        px[corner] = RIM_CORNER


def fresnel_glass(w, h):
    """
    A Fresnel lens (Searchlight, beacon): square rings two pixels apart round a flat centre, each lit on
    its upper-left half and shaded on its lower-right half like Create's recessed rings, and the pane's
    glint in the upper-left corner. The outermost ring runs just inside the border.
    """
    iw, ih = w - 2, h - 2
    cx, cy = (w - 1) / 2, (h - 1) / 2
    px = {}
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            q = math.floor(max(abs(x - cx), abs(y - cy)) + 0.5)      # ring number, 0 at the centre
            if q >= 1 and q % 2 == (min(iw, ih) // 2) % 2:
                px[(x, y)] = STREAK_FAR if (x - cx) + (y - cy) < 0 else BODY_SHADE
            else:
                px[(x, y)] = BODY_LIT
    px[(1, 1)] = GLINT
    glass_border(px, w, h)
    return px


PRISM_CELLS = {3: ((5, 4, 3), (4, 3, 3), (3, 3, 2)), 2: ((5, 3), (3, 2))}


def prism_glass(w, h):
    """
    A prismatic lens (Floodlight): a grid of small cells (three pixels from six inside, else two), each
    with its own glint at the upper left and shade at the lower right, like the many little panes of a
    floodlight's prism glass.
    """
    s = 3 if min(w, h) - 2 >= 6 else 2
    px = {(x, y): GLASS[PRISM_CELLS[s][(y - 1) % s][(x - 1) % s]]
          for y in range(1, h - 1) for x in range(1, w - 1)}
    glass_border(px, w, h)
    return px


def dome_glass(w, h):
    """
    A lamp dome lit from above (Warning Light): rows stepping a tone darker from the top down, the right
    edge a tone darker still, and a glint in the upper-left corner.
    """
    rows = (STREAK_NEAR, STREAK_FAR, BODY_LIT, BODY_SHADE)
    px = {}
    for y in range(1, h - 1):
        k = min(len(rows) - 1, (y - 1) * len(rows) // max(h - 2, 1))
        for x in range(1, w - 1):
            px[(x, y)] = rows[min(len(rows) - 1, k + (1 if x == w - 2 else 0))]
    px[(1, 1)] = GLINT
    glass_border(px, w, h)
    return px


GLASS_STYLES = {'pane': glass, 'fresnel': fresnel_glass, 'prism': prism_glass, 'dome': dome_glass}


def lens(w, h, style='pane'):
    """Lens glass in one of GLASS_STYLES (Create's lamp pane by default). Tinted by the renderer."""
    if min(w, h) <= 2:
        return canvas(w, h, BODY_LIT)
    img = canvas(w, h)
    for (x, y), colour in GLASS_STYLES[style](w, h).items():
        put(img, x, y, colour)
    return img


def lens_core(w, h, style='pane'):
    """
    The glow drawn over a lit lens, warmer and whiter than the tinted glass: clear but for the upper-left
    glint (from five pixels across) and, from eight, a dimmer glow on what catches the light in the
    lens's style (the pane's streaks near the glint, the Fresnel rings' lit halves, each prism cell's
    glint, the dome's top row; the last two from five pixels). Faces four pixels or less across get none:
    their glass alone carries the light.
    """
    img = canvas(w, h)
    if min(w, h) <= 4:
        return img
    glow = {'pane': STREAK_NEAR, 'fresnel': STREAK_FAR, 'prism': GLASS[5], 'dome': STREAK_NEAR}[style]
    if min(w, h) >= 8 or style in ('prism', 'dome'):
        for (x, y), colour in GLASS_STYLES[style](w, h).items():
            if colour == glow and 0 < x < w - 1 and 0 < y < h - 1 and (style != 'pane' or x + y < w):
                put(img, x, y, GLASS[-3])     # the pane's far glint stays unlit
    put(img, 1, 1, GLASS[-1])
    return img


PAINTERS = {f.__name__: f for f in (
    brass_plate, brass_band, brass_casing, brass_slab_side, plate_top, bezel_top, turntable_top,
    turntable_side, housing, fins, seat, iron_panel, arm, bar, andesite_panel, andesite_ring, lens, lens_core)}
