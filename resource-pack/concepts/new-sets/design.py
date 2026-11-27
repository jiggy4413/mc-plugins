"""Brainstorm: new Valentines ("Heartbreaker") and Summer ("Tidecaller") sets.
Paints 1.21-style worn armor layers (128x64, 2x resolution) and renders them with the
helmets-3d renderer. Concept art only - nothing here is used by the server."""
import math, random, sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'helmets-3d'))
os.chdir(os.path.join(HERE, '..', 'helmets-3d'))
import render as R
from render import Image, ImageDraw, ImageFont, solid, texbox, player, render, helmet3d

S = 2  # texels per armor pixel

def face_rects(U, V, w, h, d):
    return {'top': (U+d, V, w, d), 'bottom': (U+d+w, V, w, d), 'right': (U, V+d, d, h),
            'front': (U+d, V+d, w, h), 'left': (U+d+w, V+d, d, h), 'back': (U+2*d+w, V+d, w, h)}

def paint(img, U, V, w, h, d, fn):
    px = img.load()
    for face, (fu, fv, fw, fh) in face_rects(U, V, w, h, d).items():
        FW, FH = fw*S, fh*S
        for v in range(FH):
            for u in range(FW):
                c = fn(face, u, v, FW, FH)
                if c:
                    px[fu*S+u, fv*S+v] = c if len(c) == 4 else c + (255,)

def noise(c, u, v, seed, amt=10):
    r = random.Random(u*7919 + v*104729 + seed).randint(-amt, amt)
    return tuple(max(0, min(255, x + r)) for x in c[:3]) + (255,)

def heart_at(x, y):
    """unit heart: x,y in [-1,1] (y down)."""
    y = -y*1.15 + 0.15
    return (x*x + y*y - 1) ** 3 - x*x * y**3 <= 0

# ============================================================== HEARTBREAKER (Valentines)
CR = (176, 22, 58); CRD = (112, 12, 38); WINE = (82, 10, 30); PK = (255, 122, 168); PKL = (255, 186, 210)
GD = (232, 176, 72); GDD = (170, 112, 36); WH = (250, 244, 246); VOID = (30, 8, 16)

def val_helmet(face, u, v, W, H):
    base = noise(CR, u, v, 1, 8)
    if face == 'top':
        if W//2-2 <= u <= W//2+1: return GD if u in (W//2-2, W//2+1) else GDD
        return base
    if face == 'bottom': return None
    if v >= H-2: return GD                                        # gold rim
    if face == 'front':
        x = (u+0.5)/W*2-1; y = (v+0.5-H*0.55)/(H*0.4)
        inner = heart_at(x/0.46, y*1.25)
        rim = heart_at(x/0.6, y*1.02)
        if inner: return VOID if v > H*0.42 else noise(CRD, u, v, 2, 4)
        if rim: return GD if (u+v) % 5 else GDD
        if v < 3 and W//2-2 <= u <= W//2+1: return GD
        return base
    if face in ('left', 'right'):
        if 5 <= v <= 6: return PK
        if v == 7: return CRD
        return base
    if face == 'back':
        if W//2-1 <= u <= W//2: return GD
        return noise(CRD, u, v, 3, 8) if v > H-6 else base
    return base

def val_body(face, u, v, W, H):
    base = noise(CR, u, v, 4, 8)
    if face in ('top', 'bottom'): return GD if face == 'top' else None
    if v <= 1: return GD                                            # gold collar
    if H-4 <= v <= H-1:                                             # belt
        if face == 'front' and W//2-2 <= u <= W//2+1: return PK if v in (H-3, H-2) else GD
        return GDD if v == H-1 else GD
    if face == 'front':
        x = (u+0.5)/W*2-1; y = (v+0.5-H*0.38)/(H*0.26)
        if heart_at(x/0.55, y):
            return PKL if heart_at((x+0.18)/0.18, (y+0.35)/0.3) else noise(PK, u, v, 5, 6)
        if heart_at(x/0.7, y*0.82): return GD
        if v in (H-7, H-6) and (u % 4 in (1, 2)): return WH            # lace trim
        return base
    if face == 'back':
        if u in (W//2-1, W//2): return GD
        return base
    return noise(CRD, u, v, 6, 6)

def val_arm(face, u, v, W, H):
    if face == 'bottom': return None
    if face == 'top': return PK
    if v < 8:                                                        # petal pauldron
        row = v // 3
        petal = ((u + row*2) // 3) % 2
        edge = v % 3 == 2
        return CRD if edge else (PK if petal else noise(CR, u, v, 7, 8))
    if v == 8: return GD
    if H-3 <= v: return WH if v < H-1 else GD                       # white cuff
    if 14 <= v <= 15: return GD
    return noise(CR, u, v, 8, 8)

def val_boot(face, u, v, W, H):
    if face == 'top': return None
    if face == 'bottom': return CRD
    if v < H-11: return None
    if v == H-11: return GD
    if v >= H-2: return GDD if v == H-1 else GD                     # sole
    if face == 'front' and v >= H-5: return noise(CRD, u, v, 9, 6)
    if face == 'right' and H-10 <= v <= H-5:                         # little cupid wing on the outside
        wing = ['....####', '..######', '.#######', '#####...', '###.....', '#.......']
        c = wing[v-(H-10)][u] if u < 8 else '.'
        if c == '#': return WH
    return noise(CR, u, v, 10, 8)

def val_legs(face, u, v, W, H):
    if face in ('top', 'bottom'): return None
    base = noise(WINE, u, v, 11, 6)
    if v <= 2: return GD if v < 2 else GDD
    if 10 <= v <= 13: return GD if v in (10, 13) else PK           # knee band
    if face == 'front' and 3 <= v <= 9 and u in (W//2-1, W//2):
        return GD if v % 3 == 0 else PK
    if face in ('left', 'right') and u in (W//2-1, W//2): return CR
    return base

def val_leg_body(face, u, v, W, H):
    if face in ('top', 'bottom'): return None
    if v < H-6: return None
    if v >= H-2: return GD
    return noise(WINE, u, v, 12, 6)

def heartbreaker_layers():
    l1 = Image.new('RGBA', (128, 64), (0, 0, 0, 0)); l2 = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    paint(l1, 0, 0, 8, 8, 8, val_helmet)
    paint(l1, 16, 16, 8, 12, 4, val_body)
    paint(l1, 40, 16, 4, 12, 4, val_arm)
    paint(l1, 0, 16, 4, 12, 4, val_boot)
    paint(l2, 16, 16, 8, 12, 4, val_leg_body)
    paint(l2, 0, 16, 4, 12, 4, val_legs)
    return l1, l2

def heartbreaker_helmet3d(dy=0):
    Q = []
    W_ = (250, 244, 246, 255); WS = (214, 200, 206, 255); G = (232, 176, 72, 255); GS = (170, 112, 36, 255)
    P = (255, 122, 168, 255); PD = (214, 60, 110, 255)
    # cupid's arrow pierced through the helmet, front-left to back-right, tilted up
    for i in range(14):
        t = i/13; x = -7.9 + t*14.9; y = 30.0 + t*3.5; z = -3.1 + t*4.5
        Q += solid(x, y+dy, z, 1.1, 0.9, 0.9, lambda f, u, v, fw, fh: G if f in ('top', 'front') else GS)
    heart = ['.##.##.', '#######', '#######', '.#####.', '..###..', '...#...']
    def hm(face, u, v, fw, fh):
        if face not in ('left', 'right', 'front', 'back'): return P
        c = heart[min(5, v*6//fh)][min(6, u*7//fw)]
        return (P if face in ('front', 'right') else PD) if c == '#' else None
    Q += solid(-8.9, 28.4+dy, -4.8, 1.0, 3.4, 3.4, hm)            # heart tip, left side
    for k, (yy, zz) in enumerate(((34.0, 2.4), (33.0, 3.2), (32.0, 4.0))):   # white fletching, back right
        Q += solid(6.8, yy+dy-0.6, zz-1.2, 0.5, 1.8, 2.6, lambda f, u, v, fw, fh: W_ if f != 'bottom' else WS)
    # small folded wings on both sides
    wing = ['......####', '....######', '..########', '.#####ppp.', '####pp....', '##p.......']
    def wm(face, u, v, fw, fh):
        if face not in ('left', 'right'): return None
        uu = u if face == 'right' else fw-1-u
        c = wing[min(5, v*6//fh)][min(9, (fw-1-uu)*10//fw)]
        return {'#': W_, 'p': (255, 186, 210, 255)}.get(c)
    for x in (-5.9, 5.4):
        Q += solid(x, 29.0+dy, -1.0, 0.5, 4.5, 7.0, wm)
    return Q

# ============================================================== TIDECALLER (Summer)
TQ = (38, 186, 176); TQL = (120, 226, 214); TD = (14, 110, 115); DEEP = (8, 70, 82)
SAND = (242, 213, 155); SANDD = (204, 168, 104); CO = (255, 127, 80); COD = (214, 84, 52); FOAM = (244, 252, 250)

def wave(u, v, W, H, top, amp=1.5, period=8):
    """True for the foam line of a wave crest starting at row `top`."""
    yy = top + amp*math.sin(u/period*2*math.pi)
    return abs(v - yy) < 0.9

def tide_helmet(face, u, v, W, H):
    base = noise(TQ, u, v, 21, 8)
    if face == 'bottom': return None
    if face == 'top':
        if W//2-2 <= u <= W//2+1: return SAND if u in (W//2-1, W//2) else SANDD
        return base
    if v >= H-2: return SANDD if v == H-1 else SAND
    if face == 'front':
        if 8 <= v <= 9 and 2 <= u <= W-3: return VOID if v == 9 else (20, 40, 46)   # visor slit
        if v == 7 and 2 <= u <= W-3: return FOAM
        if wave(u, v, W, H, 4.0, 1.2, 6): return FOAM
        if v < 4: return noise(TQL, u, v, 22, 6)
        if v >= 11 and (u in (2, 3, W-4, W-3)) and v < H-2: return CO           # coral cheek studs
        return base
    if face in ('left', 'right'):
        if wave(u, v, W, H, 5.0, 1.2, 6): return FOAM
        if v < 5: return noise(TQL, u, v, 23, 6)
        if 9 <= v <= 11 and 6 <= u <= 9: return SAND if (u+v) % 2 else SANDD   # shell ear guard
        return base
    if face == 'back':
        if wave(u, v, W, H, 6.0, 1.2, 6): return FOAM
        return noise(TD, u, v, 24, 6) if v > 6 else noise(TQL, u, v, 24, 6)
    return base

def tide_body(face, u, v, W, H):
    if face == 'bottom': return None
    if face == 'top': return SAND
    if v <= 1: return SAND
    if H-4 <= v:                                                     # sand belt + shell buckle
        if face == 'front' and W//2-2 <= u <= W//2+1: return CO if (u+v) % 2 else COD
        return SANDD if v == H-1 else SAND
    depth = v / H
    base = noise(TQL if depth < 0.3 else (TQ if depth < 0.6 else TD), u, v, 25, 6)
    for top in (6.0, 12.0, 17.0):
        if wave(u, v, W, H, top, 1.3, 7): return FOAM
    if face == 'front':                                              # coral starfish
        cx, cy = W/2-0.5, H*0.42
        dx, dy = u-cx, v-cy; r = math.hypot(dx, dy); a = math.atan2(dy, dx)
        arm = 2.0 + 3.6*max(0, math.cos(5*(a + math.pi/2))) ** 2
        if r <= arm: return CO if r < arm-0.8 else COD
    return base

def tide_arm(face, u, v, W, H):
    if face == 'bottom': return None
    if face == 'top': return SAND
    if v < 7:                                                        # scallop-shell pauldron
        ridge = u % 3 == 0
        return SANDD if ridge or v == 6 else (SAND if v < 4 else noise(CO, u, v, 26, 6))
    if H-3 <= v: return FOAM if v < H-1 else SAND
    if wave(u, v, W, H, 12.0, 1.0, 5): return FOAM
    return noise(TQ if v < 12 else TD, u, v, 27, 6)

def tide_boot(face, u, v, W, H):
    if face == 'top': return None
    if face == 'bottom': return SANDD
    if v < H-11: return None
    if v >= H-2: return SANDD if v == H-1 else SAND                 # sandy sole
    if v in (H-8, H-5): return CO                                    # coral straps
    if v == H-11: return FOAM
    return noise(TD, u, v, 28, 6)

def tide_legs(face, u, v, W, H):
    if face in ('top', 'bottom'): return None
    if v <= 2: return SAND if v < 2 else SANDD
    if wave(u, v, W, H, 9.0, 1.0, 5) or wave(u, v, W, H, 16.0, 1.0, 5): return TQL
    if 11 <= v <= 12 and face == 'front': return CO
    return noise(DEEP if v > 13 else TD, u, v, 29, 6)

def tide_leg_body(face, u, v, W, H):
    if face in ('top', 'bottom'): return None
    if v < H-6: return None
    if v >= H-2: return SAND
    return noise(TD, u, v, 30, 6)

def tidecaller_layers():
    l1 = Image.new('RGBA', (128, 64), (0, 0, 0, 0)); l2 = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    paint(l1, 0, 0, 8, 8, 8, tide_helmet)
    paint(l1, 16, 16, 8, 12, 4, tide_body)
    paint(l1, 40, 16, 4, 12, 4, tide_arm)
    paint(l1, 0, 16, 4, 12, 4, tide_boot)
    paint(l2, 16, 16, 8, 12, 4, tide_leg_body)
    paint(l2, 0, 16, 4, 12, 4, tide_legs)
    return l1, l2

def tidecaller_helmet3d(dy=0):
    Q = []
    SA = (242, 213, 155, 255); SAD = (204, 168, 104, 255); C = (255, 127, 80, 255); CD = (214, 84, 52, 255)
    # scallop-shell fan crest standing on top, front to back
    def fan(face, u, v, fw, fh):
        if face not in ('left', 'right'): return None
        x = (u+0.5)/fw*2-1; y = 1-(v+0.5)/fh
        r = math.hypot(x, y*1.05)
        if r > 1.0 or y < 0: return None
        if r < 0.22: return CD
        rib = int((math.atan2(y, x)/math.pi)*9) % 2
        return SA if rib else SAD
    Q += solid(-0.6, 33.0+dy, -4.8, 1.2, 6.5, 10.0, fan)
    # coral branches growing from the temples
    def coral(face, u, v, fw, fh): return C if face in ('front', 'top', 'right') else CD
    for side in (-1, 1):
        bx = 4.6 if side > 0 else -5.6
        Q += solid(bx, 28.0+dy, -1.0, 1.0, 4.0, 1.0, coral)
        Q += solid(bx+side*1.0, 31.0+dy, -1.0, 1.0, 1.0, 1.0, coral)
        Q += solid(bx+side*1.6, 31.0+dy, -1.0, 1.0, 2.5, 1.0, coral)
        Q += solid(bx, 32.0+dy, -2.0, 1.0, 1.5, 1.0, coral)
    return Q

# ============================================================== winter (unchanged Nova) + agamemnon
def tint(b, o, hexc):
    r, g, bb = int(hexc[1:3], 16), int(hexc[3:5], 16), int(hexc[5:7], 16)
    b = Image.open(b).convert('RGBA'); o = Image.open(o).convert('RGBA')
    out = Image.new('RGBA', b.size); bp = b.load(); op = o.load(); p = out.load()
    for y in range(b.size[1]):
        for x in range(b.size[0]):
            if bp[x, y][3]: v = bp[x, y][0]; p[x, y] = (v*r//255, v*g//255, v*bb//255, 255)
            if op[x, y][3]: p[x, y] = op[x, y]
    return out

import importlib.util
spec = importlib.util.spec_from_file_location('sets3d_parts', os.path.join(HERE, '..', 'helmets-3d', 'sets3d.py'))
src = open(spec.origin).read().split('SETS=[')[0]          # only the part builders, not its sheet
parts_mod = {}
exec(compile(src, 'sets3d_parts', 'exec'), parts_mod)
winter_parts = parts_mod['winter_parts']

def worn(l1, l2, extra, helmet_flat=True):
    R.L1, R.L2 = l1, l2
    Q = player(with_helmet3d=False)
    if helmet_flat:
        Q += texbox(l1, 0, 0, -4, 24, -4, 8, 8, 8, 1.1)
    return Q + extra

def icon_q(l1, extra_fn):
    return texbox(l1, 0, 0, -4, 0, -4, 8, 8, 8, 1.1) + extra_fn(-24)

hb1, hb2 = heartbreaker_layers(); tc1, tc2 = tidecaller_layers()
w1 = tint('leather_layer_1.png', 'leather_layer_1_overlay.png', '#A8DCFF')
w2 = tint('leather_layer_2.png', 'leather_layer_2_overlay.png', '#A8DCFF')
cm1 = Image.open('chainmail_layer_1.png').convert('RGBA'); cm2 = Image.open('chainmail_layer_2.png').convert('RGBA')

out = os.path.join(HERE)
for name, img in (('heartbreaker_layer_1', hb1), ('heartbreaker_layer_2', hb2),
                  ('tidecaller_layer_1', tc1), ('tidecaller_layer_2', tc2)):
    img.save(os.path.join(out, name + '.png'))

BG = (26, 34, 48, 255)
f = ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf', 26)
fm = ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf', 18)
fs = ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf', 15)

def winter_icon(dy):
    return [([(p[0], p[1]+dy, p[2]) for p in pts], c, fc) for pts, c, fc in winter_parts()]

SETS = [
    ('Agamemnon', 'existing concept', cm1, cm2, lambda dy=0: helmet3d(0, dy, 0), False),
    ('Winter Nova', 'existing, keeps its look', w1, w2, lambda dy=0: winter_icon(dy), True),
    ('Heartbreaker', 'NEW  -  Valentines', hb1, hb2, heartbreaker_helmet3d, True),
    ('Tidecaller', 'NEW  -  Summer', tc1, tc2, tidecaller_helmet3d, True),
]

# ---- overview sheet: all four sets
sheet = Image.new('RGBA', (1600, 1080), BG); dr = ImageDraw.Draw(sheet)
for i, (name, tag, l1, l2, h3d, flat) in enumerate(SETS):
    x0 = i*400
    tw = dr.textlength(name, font=f); dr.text((x0+200-tw/2, 12), name, fill=(240, 240, 240), font=f)
    col = (255, 200, 120) if tag.startswith('NEW') else (160, 170, 185)
    tw = dr.textlength(tag, font=fs); dr.text((x0+200-tw/2, 46), tag, fill=col, font=fs)
    Q = worn(l1, l2, h3d(0), flat)
    sheet.alpha_composite(render(Q, -30, -12, 12, (200, 560)), (x0-5, 80))
    sheet.alpha_composite(render(Q, 145, -12, 12, (200, 560)), (x0+195, 80))
    hq = (texbox(l1, 0, 0, -4, 0, -4, 8, 8, 8, 1.1) if flat else []) + h3d(-24)
    sheet.alpha_composite(render(hq, -40, -15, 17, (400, 360)), (x0, 665))
dr.text((20, 1045), 'All netherite items. Top: worn, front and back.  Bottom: the helmet as its 3D inventory icon.',
        fill=(170, 170, 170), font=fs)
sheet.convert('RGB').save(os.path.join(out, 'all-sets-preview.png'))

# ---- close-up sheets for the two new sets
NOTES = {
    'Heartbreaker': ['Crimson + rose-gold knight with a heart-shaped visor.',
                     'Pink heart emblem on the chest, lace trim, heart belt buckle.',
                     'Layered rose-petal pauldrons, white cuffs.',
                     "Helmet: Cupid's arrow pierced straight through it, plus small wings.",
                     'Boots: tiny cupid wings on the outer ankles.'],
    'Tidecaller':   ['Lagoon turquoise fading to deep teal, with foam-white waves.',
                     'Coral starfish on the chest, shell buckle on a sand belt.',
                     'Scallop-shell pauldrons, sand soles, coral straps.',
                     'Helmet: a scallop-shell fan crest and coral branches at the temples.',
                     'Leggings: deep-sea teal with lighter wave lines.'],
}
for name, tag, l1, l2, h3d, flat in SETS[2:]:
    sh = Image.new('RGBA', (1500, 820), BG); d = ImageDraw.Draw(sh)
    d.text((30, 18), name + '  (' + tag.split('-')[-1].strip() + ' set)', fill=(240, 240, 240), font=f)
    Q = worn(l1, l2, h3d(0), flat)
    for k, (yaw, pitch, lab) in enumerate(((-30, -12, 'front'), (150, -12, 'back'), (-90, -8, 'side'))):
        sh.alpha_composite(render(Q, yaw, pitch, 14, (310, 620)), (10+k*300, 70))
        tw = d.textlength(lab, font=fs); d.text((10+k*300+155-tw/2, 690), lab, fill=(210, 210, 210), font=fs)
    hq = texbox(l1, 0, 0, -4, 0, -4, 8, 8, 8, 1.1) + h3d(-24)
    sh.alpha_composite(render(hq, -50, -18, 19, (420, 420)), (930, 60))
    d.text((990, 470), 'helmet in inventory (3D)', fill=(210, 210, 210), font=fs)
    for j, line in enumerate(NOTES[name]):
        d.text((930, 530+j*34), '- ' + line, fill=(200, 205, 215), font=fs)
    flatimg = Image.new('RGBA', (128, 64), (0, 0, 0, 0)); flatimg.alpha_composite(l1);
    d.text((30, 735), 'worn textures (flat):', fill=(160, 170, 185), font=fs)
    sh.alpha_composite(l1.resize((256, 128), Image.NEAREST).crop((0, 0, 256, 128)), (220, 680))
    sh.alpha_composite(l2.resize((256, 128), Image.NEAREST), (500, 680))
    sh.convert('RGB').save(os.path.join(out, name.lower() + '-preview.png'))
print('ok')
