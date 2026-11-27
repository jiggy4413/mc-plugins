"""Concept: giant snowflake back bling (blue + gold), shown on Winter Nova / Golden Winter Nova."""
import math, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, '..', 'new-sets', 'design.py')).read().split('# ---- overview sheet')[0]
G = {'__file__': os.path.join(HERE, '..', 'new-sets', 'design.py'), '__name__': 'design'}
exec(compile(src, 'design.py', 'exec'), G)
R = G['R']; Image = G['Image']; solid = G['solid']; render = G['render']
from PIL import ImageDraw, ImageFont

def flake_mask(x, y):
    """x, y in -1..1. Returns 'core', 'arm', 'tip', 'edge' or None for a 6-armed snowflake."""
    r = math.hypot(x, y)
    if r > 1.0:
        return None
    a = math.atan2(y, x)
    best = None
    for k in range(6):
        ang = k * math.pi / 3 + math.pi / 2
        ux, uy = math.cos(ang), math.sin(ang)
        along = x * ux + y * uy
        across = abs(-x * uy + y * ux)
        if along < -0.05:
            continue
        w = 0.10 - 0.03 * along                           # arms taper
        if across < w and along < 0.97:
            best = 'tip' if along > 0.82 else 'arm'
        for (pos, ln) in ((0.40, 0.30), (0.68, 0.22)):     # two pairs of side branches on each arm
            for side in (-1, 1):
                bang = ang + side * math.pi / 3
                bx, by = math.cos(bang), math.sin(bang)
                px, py = x - ux * pos, y - uy * pos
                b_al = px * bx + py * by
                b_ac = abs(-px * by + py * bx)
                if 0 <= b_al < ln and b_ac < 0.045:
                    best = best or ('tip' if b_al > ln * 0.75 else 'arm')
    if r < 0.27:
        hexr = 0.27 * math.cos(math.pi / 6) / max(1e-6, math.cos(((a % (math.pi / 3)) - math.pi / 6)))
        if r < hexr:
            return 'core'
    return best

def palette(kind):
    if kind == 'blue':
        return {'core': (70, 150, 225), 'arm': (190, 232, 255), 'tip': (250, 253, 255), 'edge': (90, 150, 210), 'outline': (28, 62, 104)}
    return {'core': (204, 129, 16), 'arm': (253, 245, 95), 'tip': (255, 255, 139), 'edge': (162, 88, 12), 'outline': (117, 40, 2)}

def snowflake(kind, size=19.0, cx=0.0, cy=20.0, z=4.2, thick=1.5):
    pal = palette(kind)
    def fn(face, u, v, fw, fh):
        if face in ('front', 'back'):
            uu = u if face == 'front' else fw - 1 - u
            x = (uu + 0.5) / fw * 2 - 1; y = (v + 0.5) / fh * 2 - 1
            m = flake_mask(x, y)
            if m is None:
                # dark outline one texel around the flake so it pops against the armor
                step = 2.0 / fw
                if any(flake_mask(x + dx * step, y + dy * step) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    return pal['outline'] + (255,)
                return None
            c = pal[m]
            if face == 'front':                           # the side facing the player's back is a bit darker
                c = tuple(int(q * 0.8) for q in c)
            return c + (255,)
        return None
    Q = solid(cx - size / 2, cy - size / 2, z, size, size, thick, fn)
    # rim: thin sides so it reads as a solid crystal from an angle
    def side(face, u, v, fw, fh):
        return pal['edge'] + (255,)
    return Q

def worn(l1, l2, helmet_fn, flake):
    R.L1, R.L2 = l1, l2
    Q = R.player(with_helmet3d=False)
    return Q + helmet_fn() + flake

L = {}
w1 = G['tint'](os.path.join(HERE, '..', 'helmets-3d', 'leather_layer_1.png'), os.path.join(HERE, '..', 'helmets-3d', 'leather_layer_1_overlay.png'), '#A8DCFF')
w2 = G['tint'](os.path.join(HERE, '..', 'helmets-3d', 'leather_layer_2.png'), os.path.join(HERE, '..', 'helmets-3d', 'leather_layer_2_overlay.png'), '#A8DCFF')
gw1 = Image.open(os.path.join(HERE, '..', '..', 'dist', 'ClassicPrison-1.21.4', 'assets', 'classicprison', 'textures', 'entity', 'equipment', 'humanoid', 'goldwinter.png')).convert('RGBA')
gw2 = Image.open(os.path.join(HERE, '..', '..', 'dist', 'ClassicPrison-1.21.4', 'assets', 'classicprison', 'textures', 'entity', 'equipment', 'humanoid_leggings', 'goldwinter.png')).convert('RGBA')

sheet = Image.new('RGBA', (1500, 760), (26, 34, 48, 255)); d = ImageDraw.Draw(sheet)
f = ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf', 26)
fs = ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf', 16)
for i, (name, kind, l1, l2) in enumerate((('Snowflake (blue)', 'blue', w1, w2), ('Golden Snowflake', 'gold', gw1, gw2))):
    x0 = i * 750
    d.text((x0 + 30, 15), name, fill=(240, 240, 240), font=f)
    flake = snowflake(kind)
    def helmet(kind=kind):
        crest = G['winter_parts']()
        if kind == 'gold':
            def g(c):
                v = max(c[:3]) / 255
                ramp = [(204, 129, 16), (233, 177, 21), (245, 214, 52), (253, 245, 95)]
                return ramp[min(3, int(v * 4))] + (255,)
            crest = [(pts, g(c), fc) for pts, c, fc in crest]
        return G['texbox'](l1, 0, 0, -4, 24, -4, 8, 8, 8, 1.1) + crest
    Q = worn(l1, l2, helmet, flake)
    sheet.alpha_composite(render(Q, 150, -12, 11, (250, 600)), (x0 + 10, 70))      # back
    sheet.alpha_composite(render(Q, 115, -10, 11, (250, 600)), (x0 + 250, 70))     # back-side
    sheet.alpha_composite(render(Q, -35, -12, 11, (250, 600)), (x0 + 490, 70))     # front
    for k, lab in enumerate(('back', 'back / side', 'front')):
        tw = d.textlength(lab, font=fs); d.text((x0 + 135 + k * 240 - tw / 2, 680), lab, fill=(200, 205, 215), font=fs)
sheet.convert('RGB').save(os.path.join(HERE, 'snowflake-backbling-preview.png'))
print('ok')
