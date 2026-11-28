"""Everything in the Valentines collection on one sheet: Thornheart, Gold Thornheart, Cupid, Gold Cupid.
Gold versions recolour every metal, feather, vine and plate to the vanilla gold palette (no white), and keep
the red roses and hearts as red gems."""
import os, sys, io, contextlib, importlib.util
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'weapons'))
sys.path.insert(0, os.path.join(HERE, 'helmets-3d'))
from PIL import Image, ImageDraw, ImageFont

GOLD_RAMP = [(117, 40, 2), (162, 88, 12), (204, 129, 16), (233, 177, 21), (245, 214, 52), (253, 245, 95), (255, 255, 139)]
OUTLINE_GOLD = (56, 22, 2)


def ramp(t):
    t = max(0.0, min(1.0, t)) * (len(GOLD_RAMP) - 1)
    i = min(int(t), len(GOLD_RAMP) - 2)
    f = t - i
    a, b = GOLD_RAMP[i], GOLD_RAMP[i + 1]
    return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


def is_gem(c):
    r, g, b = c[:3]
    return r > 120 and r > 2.4 * g and r > 1.3 * b


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def is_vine(c):
    r, g, b = c[:3]
    return g > r and g > b


class Golder:
    """maps colours to gold by brightness, stretched over the brightness range of one asset.
    With vines_dark, green vine/leaf pixels get the deep end of the ramp and the plate the bright end,
    so the armor reads as bright gold with darker gold vines on it."""
    def __init__(self, colors, lo=0.2, hi=0.84, vines_dark=False):
        self.vd = vines_dark
        plate = [lum(c) for c in colors if not is_gem(c) and not (vines_dark and is_vine(c))]
        vines = [lum(c) for c in colors if vines_dark and is_vine(c)]
        self.l0, self.l1 = (min(plate), max(plate)) if plate else (0, 255)
        self.v0, self.v1 = (min(vines), max(vines)) if vines else (0, 255)
        self.lo, self.hi = (0.42, 0.86) if vines_dark else (lo, hi)

    def __call__(self, c):
        if is_gem(c):
            return tuple(c[:3])
        if self.vd and is_vine(c):
            t = (lum(c) - self.v0) / max(1, self.v1 - self.v0)
            return ramp(0.1 + 0.26 * t)
        t = (lum(c) - self.l0) / max(1, self.l1 - self.l0)
        return ramp(self.lo + (self.hi - self.lo) * t)


def gold_image(img, outline=None, vines_dark=False):
    img = img.convert('RGBA')
    px = img.load()
    cols = [px[x, y] for y in range(img.size[1]) for x in range(img.size[0])
            if px[x, y][3] and (outline is None or px[x, y][:3] != tuple(outline))]
    g = Golder(cols, vines_dark=vines_dark)
    out = img.copy(); po = out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            c = px[x, y]
            if not c[3]:
                continue
            po[x, y] = (OUTLINE_GOLD if outline is not None and c[:3] == tuple(outline) else g(c)) + (c[3],)
    return out


def gold_quads(Q, vines_dark=False):
    g = Golder([c for _, c, _ in Q], vines_dark=vines_dark)
    return [(p, g(c) + (255,), f) for p, c, f in Q]


# ---------------------------------------------------------------- load the sets
with contextlib.redirect_stdout(io.StringIO()):
    spec = importlib.util.spec_from_file_location('design', os.path.join(HERE, 'new-sets', 'design.py'))
    design = importlib.util.module_from_spec(spec); spec.loader.exec_module(design)
    spec = importlib.util.spec_from_file_location('cupid_armor', os.path.join(HERE, 'new-sets', 'cupid.py'))
    cupid_armor = importlib.util.module_from_spec(spec); spec.loader.exec_module(cupid_armor)
import render as R
from render import render, player
import thornheart as T, cupid as C

th1, th2 = design.thornheart_layers()
cu1, cu2 = cupid_armor.cupid_layers()

SETS = [
    ('Thornheart', T, th1, th2, design.thornheart_helmet3d, False, (240, 90, 110)),
    ('Gold Thornheart', T, th1, th2, design.thornheart_helmet3d, True, (245, 214, 52)),
    ('Cupid', C, cu1, cu2, cupid_armor.cupid_helmet3d, False, (255, 160, 196)),
    ('Gold Cupid', C, cu1, cu2, cupid_armor.cupid_helmet3d, True, (245, 214, 52)),
]

TOOLS = [('Sword', lambda M: M.sword()), ('Axe', lambda M: M.axe()), ('Spear', lambda M: M.spear()),
         ('Scythe', lambda M: M.scythe()), ('Pickaxe', lambda M: M.pickaxe()), ('Bow', lambda M: M.bow()),
         ('Bow (drawn)', lambda M: M.bow(3)), ('Shield', lambda M: M.shield())]
BLINGS = {T: [T.backbling, T.backbling_wings, T.backbling_rose],
          C: [C.backbling_wings, C.backbling_quiver, C.backbling_heart]}
BLING_NAMES = {T: ['Heart of Thorns', 'Thorn Wings', 'Black Rose'], C: ['Angel Wings', "Cupid's Quiver", 'Winged Heart']}

fp = '/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf'
F = ImageFont.truetype(fp, 30); FM = ImageFont.truetype(fp, 20); FS = ImageFont.truetype(fp, 15)
BG = (24, 26, 36, 255)

TS, BS = 5, 4                     # tool / back bling pixel scale
TC, BC = 32 * TS + 18, 48 * BS + 18
LEFT = 230
ARMOR_W = 4 * 2 * 190
W = max(LEFT + len(TOOLS) * TC + 30 + 3 * BC + 30, ARMOR_W + 60)
ARMOR_H = 600
H = 70 + ARMOR_H + 60 + 4 * (max(TC, BC) + 26) + 40
sheet = Image.new('RGBA', (W, H), BG); d = ImageDraw.Draw(sheet)
d.text((30, 18), 'Valentines Collection  -  Thornheart & Cupid, regular and gold', font=F, fill=(240, 240, 240))

# ---- armor: four sets, front and back
for i, (name, M, l1, l2, h3d, gold, col) in enumerate(SETS):
    vd = M is T
    a1, a2 = (gold_image(l1, vines_dark=vd), gold_image(l2, vines_dark=vd)) if gold else (l1, l2)
    R.L1, R.L2 = a1, a2
    hq = h3d(0)
    if gold:
        hq = gold_quads(hq, vines_dark=vd)
    Q = player(with_helmet3d=False) + hq
    x0 = 30 + i * 2 * 190 + (W - 60 - ARMOR_W) // 2
    sheet.alpha_composite(render(Q, -30, -12, 11, (190, 520)), (x0, 90))
    sheet.alpha_composite(render(Q, 150, -12, 11, (190, 520)), (x0 + 190, 90))
    tw = d.textlength(name, font=FM); d.text((x0 + 190 - tw / 2, 620), name, font=FM, fill=col)

# ---- tools and back blings
y0 = 70 + ARMOR_H + 50
for i, (n, _) in enumerate(TOOLS):
    d.text((LEFT + i * TC + TC / 2, y0), n, font=FS, fill=(190, 195, 210), anchor='mm')
for k in range(3):
    d.text((LEFT + len(TOOLS) * TC + 30 + k * BC + BC / 2, y0), 'Back bling %d' % (k + 1), font=FS, fill=(190, 195, 210), anchor='mm')
row_h = max(TC, BC) + 26
for j, (name, M, l1, l2, h3d, gold, col) in enumerate(SETS):
    y = y0 + 20 + j * row_h
    d.text((24, y + row_h / 2 - 10), name, font=FM, fill=col, anchor='lm')
    for i, (n, fn) in enumerate(TOOLS):
        s = fn(M)
        im = s.image()
        if gold:
            im = gold_image(im, s.outline, vines_dark=M is T)
        big = im.resize((32 * TS, 32 * TS), Image.NEAREST)
        sheet.alpha_composite(big, (LEFT + i * TC + 9, y + (row_h - 26 - 32 * TS) // 2))
    for k, fn in enumerate(BLINGS[M]):
        s = fn()
        im = s.image()
        if gold:
            im = gold_image(im, s.outline, vines_dark=M is T)
        big = im.resize((48 * BS, 48 * BS), Image.NEAREST)
        bx = LEFT + len(TOOLS) * TC + 30 + k * BC
        sheet.alpha_composite(big, (bx + 9, y))
        label = ('Gold ' if gold else '') + BLING_NAMES[M][k]
        d.text((bx + BC / 2, y + 48 * BS + 10), label, font=FS, fill=(150, 156, 172), anchor='mm')

out = os.path.join(HERE, 'valentines-collection.png')
sheet.convert('RGB').save(out)
print(out, sheet.size)
