"""Cupid (Valentines, light set) armor concept: pearl-white plate, blush pink, soft gold, feathers and hearts.
Paints 1.21-style worn layers (128x64, 2 texels per armor pixel) and a 3D helmet (winged gold circlet +
halo), then renders a preview next to Thornheart. Concept art only."""
import math, random, sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'helmets-3d'))
os.chdir(os.path.join(HERE, '..', 'helmets-3d'))
import render as R
from render import Image, ImageDraw, ImageFont, solid, texbox, player, render

S = 2
PE = (238, 232, 242); PEL = (255, 255, 255); PED = (204, 192, 214)
PK = (246, 150, 182); PKL = (255, 200, 218); PKD = (214, 96, 140)
HT = (232, 58, 108); HTD = (170, 28, 72)
GD = (240, 196, 92); GDL = (255, 232, 150); GDD = (186, 134, 46)
VOID = (40, 20, 34)


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


def noise(c, u, v, seed, amt=6):
    r = random.Random(u*7919 + v*104729 + seed).randint(-amt, amt)
    return tuple(max(0, min(255, x + r)) for x in c[:3]) + (255,)


def pearl(u, v, seed):
    """polished pearl plate: soft pink-lilac sheen sweeping across"""
    t = (0.5 + 0.5*math.sin(u*0.38 + v*0.12 + seed*0.7)) ** 3
    c = tuple(int(PED[i] + (PEL[i] - PED[i]) * (0.35 + 0.65*t)) for i in range(3))
    return noise(c, u, v, seed, 3)


def heart(u, v, cx, cy, r):
    x = (u - cx) / r; y = -(v - cy) / r * 1.15 + .15
    return (x*x + y*y - 1) ** 3 - x*x * y**3 <= 0


def feathers(u, v, W, seed=0, tip=False):
    """overlapping scallop rows of white feathers (rows of 4 texels), optional pink tips."""
    row = v // 4
    uu = (u + (row % 2) * 2) % 4
    vv = v % 4
    edge = (uu - 1.5) ** 2 / 2.2 + (vv - 3.2) ** 2 / 1.2 < 1 and vv >= 3
    if edge:
        return noise(PED, u, v, seed, 3)
    if tip and vv >= 2:
        return noise(PKL, u, v, seed, 3)
    return noise(PEL if vv == 0 else PE, u, v, seed, 3)


def gold(u, v, seed):
    return noise(GDL if (u + v) % 7 == 0 else GD, u, v, seed, 4)


# ---------------------------------------------------------------- worn layers
def c_helmet(face, u, v, W, H):          # flat helmet layer is unused (3D helmet), kept simple
    if face == 'bottom': return None
    return gold(u, v, 1) if v >= H-2 or face == 'top' else None


def c_body(face, u, v, W, H):
    if face == 'top': return gold(u, v, 10)
    if face == 'bottom': return pearl(u, v, 11)
    if v <= 1: return gold(u, v, 12)                                  # gold collar
    if H-3 <= v: return gold(u, v, 13) if v < H-1 else noise(GDD, u, v, 13)   # gold belt
    if face == 'front':
        if H-4 <= v <= H-1 and abs(u - (W/2-0.5)) < 2.5:
            return noise(HT, u, v, 14)                                # heart buckle
        cx, cy = W/2-0.5, H*0.38
        if heart(u, v, cx, cy, 5.2): return noise(HT if not heart(u, v, cx-1.2, cy-1.2, 1.6) else PKL, u, v, 15)
        if heart(u, v, cx, cy, 6.6): return gold(u, v, 16)
        if v > H*0.66: return noise(PK, u, v, 17, 4) if (u // 2) % 2 else noise(PKD, u, v, 17, 4)  # pleated skirt
        return pearl(u, v, 18)
    if face == 'back':
        # two folded wings painted on the back plate
        def inw(uu, vv):
            x = abs(uu - (W/2 - 0.5))
            return 1 <= x <= 7.5 and 2 <= vv <= 2 + (8.5 - x) * 1.7
        if inw(u, v):
            if not all(inw(u + a, v + b) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                return gold(u, v, 19)
            x = abs(u - (W/2 - 0.5))
            return feathers(u, v, W, 19, tip=v > 2 + (8.5 - x) * 1.05)
        if v > H*0.66: return noise(PK, u, v, 20, 4) if (u // 2) % 2 else noise(PKD, u, v, 20, 4)
        return pearl(u, v, 21)
    if v > H*0.66: return noise(PK, u, v, 22, 4)
    return pearl(u, v, 23)


def c_arm(face, u, v, W, H):
    """Feathered shoulder guards only, like vanilla: the rest of the arm is the player's skin."""
    if face == 'top': return feathers(u, v, W, 30)
    if face == 'bottom': return None
    if v >= 10: return None
    if v == 9: return gold(u, v, 31)
    return feathers(u, v, W, 32, tip=v >= 6)


def c_boot(face, u, v, W, H):
    if face == 'top': return None
    if face == 'bottom': return noise(PED, u, v, 40)
    if v < H-11: return None
    if v >= H-2: return gold(u, v, 41) if v == H-2 else noise(GDD, u, v, 41)
    if v == H-11 or v == H-10: return gold(u, v, 42)
    if face in ('left', 'right'):                                     # little ankle wings
        x = u; y = v - (H-9)
        if 0 <= y <= 5 and x >= W - 7 + y:
            return feathers(u, v, W, 43, tip=y >= 3)
    if face == 'front' and heart(u, v, W/2-0.5, H-6, 2.2):
        return noise(HT, u, v, 44)
    return pearl(u, v, 45)


def c_legs(face, u, v, W, H):
    if face == 'top': return None
    if face == 'bottom': return noise(PKD, u, v, 50)
    if v <= 2: return gold(u, v, 51) if v < 2 else noise(GDD, u, v, 51)
    if face == 'front' and 8 <= v <= 15:                             # pearl knee guard with a gold heart
        if heart(u, v, W/2-0.5, 11.5, 2.4): return gold(u, v, 52)
        if abs(u - (W/2-0.5)) <= 3.6: return pearl(u, v, 53)
    return noise(PK if (u + v // 3) % 6 else PKL, u, v, 54, 4)


def c_leg_body(face, u, v, W, H):
    if face in ('top', 'bottom'): return None if face == 'top' else noise(PK, u, v, 55)
    if v < H-6: return None
    if v >= H-2: return gold(u, v, 56)
    return noise(PK, u, v, 57, 4)


def cupid_layers():
    l1 = Image.new('RGBA', (128, 64), (0, 0, 0, 0)); l2 = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    paint(l1, 0, 0, 8, 8, 8, c_helmet)
    paint(l1, 16, 16, 8, 12, 4, c_body)
    paint(l1, 40, 16, 4, 12, 4, c_arm)
    paint(l1, 0, 16, 4, 12, 4, c_boot)
    paint(l2, 16, 16, 8, 12, 4, c_leg_body)
    paint(l2, 0, 16, 4, 12, 4, c_legs)
    return l1, l2


# ---------------------------------------------------------------- 3D helmet: winged gold circlet + halo
def cupid_helmet3d(dy=0):
    Q = []
    G = lambda f, u, v, fw, fh: (GDL + (255,)) if f in ('top',) else ((GD + (255,)) if f in ('front', 'left') else (GDD + (255,)))
    # circlet round the brow (open top and bottom, face stays visible)
    Q += solid(-4.6, 29.0 + dy, -4.6, 9.2, 1.0, 9.2, lambda f, u, v, fw, fh: None if f in ('top', 'bottom') else G(f, u, v, fw, fh))
    # a pink heart gem on the front of the circlet
    HTc = lambda f, u, v, fw, fh: (HT + (255,)) if f != 'bottom' else (HTD + (255,))
    Q += solid(-1.0, 29.0 + dy, -5.0, 2.0, 1.4, 0.5, HTc)
    Q += solid(-0.6, 28.6 + dy, -5.0, 1.2, 0.5, 0.5, HTc)
    # feathered wings on both sides sweeping back and up
    def fc(f, u, v, fw, fh):
        if f == 'bottom': return PED + (255,)
        return (PEL if f in ('top', 'front') else PE) + (255,)
    def tipc(f, u, v, fw, fh):
        return (PKL if f in ('top', 'front') else PK) + (255,)
    for side in (-1, 1):
        x = 4.6 if side > 0 else -5.1
        for k, (z, y, L, h) in enumerate([(-2.0, 29.2, 4.5, 1.1), (-1.2, 30.2, 5.4, 1.1), (-0.4, 31.2, 6.0, 1.0), (0.4, 32.1, 5.6, 0.9)]):
            Q += solid(x, y + dy, z, 0.5, h, L, fc)
            Q += solid(x, y + dy, z + L, 0.5, h, 1.0, tipc)
            Q += solid(x, y + h + dy, z + L - 0.6 + k * 0.4, 0.5, 0.6, 1.4, fc)
    # halo floating above the head
    n = 36
    for i in range(n):
        a = i / n * 2 * math.pi
        Q += solid(math.cos(a) * 4.2 - 0.4, 34.6 + dy, math.sin(a) * 4.2 - 0.4, 0.8, 0.45, 0.8,
                   lambda f, u, v, fw, fh: (GDL + (255,)) if f in ('top', 'bottom') else (GD + (255,)))
    return Q


if __name__ == '__main__':
    l1, l2 = cupid_layers()
    l1.save(os.path.join(HERE, 'cupid_layer_1.png')); l2.save(os.path.join(HERE, 'cupid_layer_2.png'))
    R.L1, R.L2 = l1, l2
    Q = player(with_helmet3d=False) + cupid_helmet3d(0)
    BG = (26, 34, 48, 255)
    fp = '/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf'
    f = ImageFont.truetype(fp, 26); fs = ImageFont.truetype(fp, 15)
    sh = Image.new('RGBA', (1500, 820), BG); d = ImageDraw.Draw(sh)
    d.text((30, 18), 'Cupid  (Valentines set, light)', fill=(240, 240, 240), font=f)
    for k, (yaw, pitch, lab) in enumerate(((-30, -12, 'front'), (150, -12, 'back'), (-90, -8, 'side'))):
        sh.alpha_composite(render(Q, yaw, pitch, 14, (310, 620)), (10+k*300, 70))
        tw = d.textlength(lab, font=fs); d.text((10+k*300+155-tw/2, 690), lab, fill=(210, 210, 210), font=fs)
    sh.alpha_composite(render(cupid_helmet3d(-24), -50, -18, 19, (420, 420)), (930, 60))
    d.text((990, 470), 'helmet in inventory (3D)', fill=(210, 210, 210), font=fs)
    for j, line in enumerate(['An angelic knight: the bright opposite of Thornheart.',
                              'Pearl-white plate, a gold-rimmed pink heart on the chest.',
                              'Folded wings on the back; feathered shoulder guards.',
                              'Helmet: a gold circlet with little wings and a halo.',
                              'Blush-pink leggings, gold hearts on the knees, winged boots.']):
        d.text((930, 530+j*34), '- ' + line, fill=(200, 205, 215), font=fs)
    d.text((30, 735), 'worn textures (flat):', fill=(160, 170, 185), font=fs)
    sh.alpha_composite(l1.resize((256, 128), Image.NEAREST), (220, 680))
    sh.alpha_composite(l2.resize((256, 128), Image.NEAREST), (500, 680))
    sh.convert('RGB').save(os.path.join(HERE, 'cupid-preview.png'))
    print('ok')
