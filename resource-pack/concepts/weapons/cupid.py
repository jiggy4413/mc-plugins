"""Cupid (Valentines, light set) weapons + back blings. Pearl-white steel, blush pink, soft gold, feathers, hearts."""
import math
from px import Sprite
import parts

OUT = (70, 34, 56)
PEARL = ((255, 255, 255), (238, 230, 240), (196, 182, 204))
PINK = ((255, 196, 214), (246, 140, 176), (208, 86, 132))
HEART = ((255, 128, 160), (232, 58, 108), (172, 28, 74))
GOLD = ((255, 234, 150), (240, 196, 92), (190, 136, 46))
FEATHER = ((255, 255, 255), (242, 238, 246), (210, 200, 222))

HEART5 = [".H.H.",
          "HhHHH",
          "HHHHD",
          ".HHD.",
          "..D.."]
HEART7 = [".HH.HH.",
          "HhHHHHH",
          "HhHHHHD",
          "HHHHHHD",
          ".HHHHD.",
          "..HHD..",
          "...D..."]
HKEY = {'H': HEART[1], 'h': HEART[0], 'D': HEART[2]}


def base(n=32):
    s = Sprite(OUT, n)
    for k, v in dict(pearl=PEARL, pink=PINK, heart=HEART, gold=GOLD, feather=FEATHER).items():
        s.ramp(k, *v)
    return s


def wing(s, root, direction, length, mat='feather', rows=3):
    """a little feathered wing: overlapping feather rows fanning from root along direction (dx,dy)."""
    dx, dy = direction
    n = math.hypot(dx, dy); dx, dy = dx / n, dy / n
    px, py = -dy, dx
    for r in range(rows):
        L = length * (1 - r * 0.22)
        off = r * 1.6
        for k in range(int(L) + 1):
            t = k / max(1, L)
            w = 1.6 * (1 - t * 0.6)
            x = root[0] + dx * k + px * off
            y = root[1] + dy * k + py * off
            s.line((x, y), (x + px * w, y + py * w), .6, mat)


def sword():
    s = base()
    s.line((12, 20), (25, 7), 2.6, 'pearl')
    s.line((24, 8), (28.5, 3.5), 1.7, 'pearl')
    s.line((14.8, 21.2), (27, 9), 0.8, 'pink')                 # blush cutting edge
    s.line((13.5, 18.5), (24, 8), 0.5, 'gold')                 # gold fuller
    wing(s, (11, 18), (-1, -1.4), 7, rows=2)                    # winged crossguard
    wing(s, (14, 21), (1.4, 1), 7, rows=2)
    s.line((4.5, 27), (10, 21.5), 1.2, 'gold')
    s.stamp(9, 17, HEART5, HKEY)
    s.stamp(1, 27, HEART5[:4], HKEY)                           # heart pommel
    return s


def axe():
    s = base()
    s.line((4, 28.5), (20, 12.5), 1.2, 'gold')
    s.where(lambda x, y: (x + .5 - 14) ** 2 + (y + .5 - 11) ** 2 <= 92 and (x + .5 - 21) ** 2 + (y + .5 - 15) ** 2 >= 40
            and x + y <= 27 and y <= 17, 'pearl')
    s.where(lambda x, y: s.mat[y][x] == 'pearl' and (x + .5 - 14) ** 2 + (y + .5 - 11) ** 2 >= 62 and x + y <= 26, 'pink')
    s.poly([(17, 9), (23, 4), (28, 9), (22, 15), (18, 14)], 'gold')
    s.stamp(12, 8, HEART5, HKEY)                                # heart set in the blade
    wing(s, (25, 8), (1, 1.6), 5, rows=2)
    return s


def spear():
    """Cupid's lance: a gold shaft with a heart-shaped head and white fletching at the butt."""
    s = base()
    parts.spear_shaft(s, 'gold', 0.9)
    for t in (0.35, 0.6):
        x, y = 1.5 + 20.5 * t, 30.5 - 20.5 * t
        s.put(int(x), int(y), 'pearl')
    # heart head pointing to the top right (rotated 45 degrees)
    def heart_rot(x, y):
        X, Y = x + .5 - 24.5, y + .5 - 7.5
        u = (X - Y) * .707 / 6.2 * -1
        v = (X + Y) * .707 / 6.2
        v2 = -v * 1.15 + .15
        return (u * u + v2 * v2 - 1) ** 3 - u * u * v2 ** 3 <= 0
    # rotate so the point aims at the corner
    def heart_tip(x, y):
        X, Y = x + .5 - 23.5, y + .5 - 8.5
        u = (X + Y) * .707 / 5.8
        v = (-X + Y) * .707 / 5.8
        v2 = v * 1.15 + .15
        return (u * u + v2 * v2 - 1) ** 3 - u * u * v2 ** 3 <= 0
    s.where(heart_tip, 'heart')
    s.where(lambda x, y: heart_tip(x, y) and not heart_tip(x - 1, y + 1), 'gold')
    for k, (x, y) in enumerate([(3, 25), (5, 27), (2, 23), (6, 29)]):
        s.line((x, y), (x - 1.5, y + 1.5 if k % 2 else y - 0.5), .6, 'feather')
    return s


def scythe():
    s = base()
    s.line((3, 29), (22, 10), 1.2, 'gold')
    parts.crescent(s, [(22.5, 9.5), (21, 5.5), (17, 3.2), (11, 3), (5.5, 4.5), (2, 8)],
                   [2.6, 2.8, 2.6, 2.1, 1.4, 0.6], 'pearl')
    s.path([(19.5, 7.3), (16, 5.8), (11, 5.6), (6, 6.6), (3, 8.3)], 0.65, 'pink')
    wing(s, (23, 10), (1, 1.2), 6, rows=2)
    s.stamp(19, 7, HEART5, HKEY)
    return s


def bow(pull=0):
    """Cupid's bow: gold limbs ending in little white wings; draws a heart-tipped arrow."""
    s = base()
    parts.bow(s, 'gold', (255, 214, 230), pull, arrow=(GOLD[1], HEART[1], (255, 255, 255)), r=1.4)
    wing(s, (4, 27), (-1, .3), 4, rows=2)
    wing(s, (27, 4), (-.3, -1), 4, rows=2)
    s.stamp(3, 13, HEART5, HKEY)                                # heart on the grip
    if pull:
        tip = (int(7.5 - pull * .4), int(7.5 - pull * .4))
        s.stamp(tip[0] - 3, tip[1] - 3, HEART5[:4], HKEY)
    return s


def shield():
    """White heater shield with a gold rim, a big pink heart and two wings."""
    s = base()
    s.where(lambda x, y: parts.heater(x + .5, y + .5), 'gold')
    s.where(lambda x, y: parts.heater(x + .5, y + .5, top=4.6, bottom=27, half=10.4), 'pearl')
    s.stamp(12, 11, HEART7, HKEY)
    s.stamp(13, 18, ["HH", "D."], HKEY)
    wing(s, (11, 13), (-1, -.5), 6, rows=2, mat='pink')
    wing(s, (20, 13), (1, -.5), 6, rows=2, mat='pink')
    return s


# ---------------------------------------------------------------- back blings (48x48)
def feather_wing(s, cx, side, span=22):
    """a big angel wing: rows of feathers, longest at the bottom, pink tips."""
    for row in range(5):
        L = span * (0.45 + 0.14 * row)
        y0 = 9 + row * 3.2
        for k in range(int(L)):
            u = k
            x = cx + side * (2 + u)
            y = y0 + u * (0.25 + row * 0.12) - 0.02 * u * u
            tip = k > L - 3
            s.line((x, y), (x, y + 3.2 - row * .2), .7, 'pink' if tip and row >= 2 else 'feather')
    for row in range(4):                                      # feather separation lines
        L = span * (0.45 + 0.14 * row)
        y0 = 9 + row * 3.2
        for k in range(1, int(L), 3):
            x = cx + side * (2 + k)
            y = y0 + k * (0.25 + row * 0.12) - 0.02 * k * k + 2.6
            s.color(int(x), int(y), FEATHER[2])


def backbling_wings():
    """Angel Wings: big raised white feathered wings with blush-pink feather tips and a gold heart clasp."""
    s = base(48)
    for side in (-1, 1):
        f = parts.wing_shape(24, side, root_y=20, span=22, rise=13, drop=18, scallops=6)
        s.where(lambda x, y: f(x, y) is not None, 'feather')
        s.where(lambda x, y: f(x, y) is not None and f(x, y)[1] > .62, 'pink')
        s.where(lambda x, y: f(x, y) is not None and .55 < f(x, y)[1] <= .62, 'gold')
        for k in range(1, 6):                                   # feather separations
            u = k / 6
            x0 = 24 + side * (3 + 22 * u)
            y0 = 20 - 13 * u ** .75
            for t in range(2, 9):
                s.color(int(x0 - side * t * .35), int(y0 + t * 1.1), FEATHER[2])
    s.disc((24, 19), 3.4, 'gold')
    s.stamp(22, 17, HEART5, HKEY)
    return s


def backbling_quiver():
    """Cupid's Quiver: a pink-and-gold quiver of heart-tipped arrows with white fletching, worn slanted."""
    s = base(48)
    a, b = (13, 40), (29, 16)
    s.line(a, b, 5.0, 'pink')
    s.line(a, b, 2.2, 'heart')
    for t in (0.0, 0.5, 1.0):
        x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
        s.line((x - 5.5, y - 2.5), (x + 5.5, y + 2.5), 1.0, 'gold')
    for k, off in enumerate((-3, 0, 3)):                      # arrows sticking out of the top
        x0, y0 = 29 + off * .9, 16 + off * .4
        x1, y1 = x0 + 6, y0 - 9
        s.line((x0, y0), (x1, y1), .5, 'gold')
        s.stamp(int(x1) - 2, int(y1) - 3, HEART5, HKEY)
    s.stamp(18, 25, HEART7, HKEY)
    return s


def backbling_heart():
    """Winged Heart: a big pink heart with a gold rim, white wings and a floating gold halo."""
    s = base(48)
    def heart(x, y, k, cx=24, cy=26):
        X, Y = (x + .5 - cx) / k, -(y + .5 - cy) / k * 1.15 + .15
        return (X * X + Y * Y - 1) ** 3 - X * X * Y ** 3 <= 0
    for side in (-1, 1):
        for row in range(3):
            L = 13 - row * 2.5
            for k in range(int(L)):
                x = 24 + side * (9 + k)
                y = 19 + row * 3 - k * .55 + 0.03 * k * k
                s.line((x, y), (x, y + 3), .7, 'feather')
    s.where(lambda x, y: heart(x, y, 13), 'gold')
    s.where(lambda x, y: heart(x, y, 11), 'heart')
    s.where(lambda x, y: heart(x, y, 4, 21, 22), 'pink')
    for t in range(0, 360, 3):                               # halo
        a = math.radians(t)
        s.line((24 + 9 * math.cos(a), 5 + 2.2 * math.sin(a)), (24 + 9 * math.cos(a) + .1, 5 + 2.2 * math.sin(a)), .7, 'gold')
    return s
