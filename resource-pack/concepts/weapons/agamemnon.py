"""Agamemnon (Greek warlord) weapons + back bling. Navy-black steel, gold trim, black horsehair crest."""
import math
from px import Sprite
import parts

OUT = (10, 12, 20)
STEEL = ((110, 122, 156), (60, 68, 94), (38, 44, 64))
GOLD = ((255, 222, 120), (226, 170, 52), (160, 108, 26))
HAIR = ((70, 74, 92), (36, 38, 50), (20, 22, 30))
WOOD = ((150, 112, 76), (110, 78, 50), (76, 52, 34))


def base(n=32):
    s = Sprite(OUT, n)
    s.ramp('steel', *STEEL); s.ramp('gold', *GOLD); s.ramp('hair', *HAIR); s.ramp('wood', *WOOD)
    return s


def sword():
    """Xiphos: a leaf-shaped blade that swells before the point, gold edges and guard."""
    s = base()
    # leaf blade along the diagonal: width grows towards 3/4 of the length then tapers to the tip
    a, b = (12, 20), (28.5, 3.5)
    for k in range(0, 101):
        t = k / 100
        x = a[0] + (b[0] - a[0]) * t
        y = a[1] + (b[1] - a[1]) * t
        w = 1.6 + 1.6 * math.sin(math.pi * min(1, t / 0.85)) if t < 0.85 else 3.2 * (1 - t) / 0.15
        s.line((x, y), (x + .01, y + .01), max(.5, w), 'steel')
    s.where(lambda x, y: s.mat[y][x] == 'steel' and abs((x - y) - (12 - 20) - 0) > 0 and
            (abs((x + .5) + (y + .5) - 32) >= 0) and
            abs(((x + .5) - (y + .5)) - (-8 + 0)) > 1e9, 'gold')  # (no-op, keeps API symmetrical)
    # gold edge on the lower-right side, gold midrib
    for k in range(0, 101):
        t = k / 100
        x = a[0] + (b[0] - a[0]) * t
        y = a[1] + (b[1] - a[1]) * t
        w = 1.6 + 1.6 * math.sin(math.pi * min(1, t / 0.85)) if t < 0.85 else 3.2 * (1 - t) / 0.15
        if w > .8:
            ex, ey = x + (w - .4) * .707, y + (w - .4) * .707
            s.put(int(ex), int(ey), 'gold')
    s.line((13.5, 18.5), (25, 7), 0.5, 'hair')            # dark midrib
    s.line((8, 15), (16, 23), 1.5, 'gold')                # gold crossguard
    s.line((5, 26.5), (10, 21.5), 1.2, 'hair')            # leather grip
    for x, y in [(6, 25), (8, 23)]:
        s.put(x, y, 'gold')
    s.disc((4, 28), 1.8, 'gold')                          # round gold pommel
    return s


def axe():
    """Labrys: a double-headed axe on a dark haft with gold bands."""
    s = base()
    s.line((4, 28.5), (24, 8.5), 1.2, 'wood')
    for t in (0.25, 0.5, 0.75):
        x, y = 4 + 20 * t, 28.5 - 20 * t
        s.line((x - .5, y + .5), (x + .5, y - .5), 1.5, 'gold')
    # two crescent blades, mirrored across the haft, centred on the head
    cx, cy = 18.5, 13.5
    ux, uy = .707, -.707       # along the haft
    nx, ny = .707, .707        # across the haft
    def blade(sign):
        def f(x, y):
            dx, dy = x + .5 - cx, y + .5 - cy
            a = dx * ux + dy * uy           # along haft
            c = (dx * nx + dy * ny) * sign  # away from haft
            return 1.2 <= c <= 10 and abs(a) <= 2.2 + (c - 1.2) * 0.85 and (c - 2) ** 2 + a * a <= 72
        return f
    s.where(blade(1), 'steel')
    s.where(blade(-1), 'steel')
    s.where(lambda x, y: s.mat[y][x] == 'steel' and (lambda dx, dy: (abs(dx * nx + dy * ny) - 2) ** 2 + (dx * ux + dy * uy) ** 2 >= 50)(x + .5 - cx, y + .5 - cy), 'gold')
    s.line((cx - 1.4, cy + 1.4), (cx + 1.4, cy - 1.4), 1.8, 'gold')   # gold collar where the heads meet
    s.disc((24.5, 8), 1.3, 'gold')
    return s


def bow(pull=0):
    """Greek recurve: navy limbs that flick outward at the tips, gold caps and grip."""
    s = base()
    parts.bow(s, 'steel', (236, 224, 190), pull, arrow=((150, 112, 76), GOLD[1], (40, 42, 56)), r=1.4)
    s.path([(4, 28.5), (2.5, 30), (1.5, 30.5)], 1.0, 'gold')      # recurved tips
    s.path([(28.5, 3.5), (30, 2.5), (30.5, 1.5)], 1.0, 'gold')
    s.line((3.8, 18), (7.5, 11.5), 1.7, 'gold')                   # gold grip
    s.line((4.2, 17), (7, 12.5), 0.6, 'hair')
    for x, y in [(4, 24), (11, 7), (19, 4)]:
        s.put(x, y, 'gold')
    return s


def scythe():
    """Navy crescent with a gold edge; a black horsehair tuft (like the helmet crest) at the joint."""
    s = base()
    s.line((3, 29), (22, 10), 1.2, 'wood')
    for t in (0.2, 0.45, 0.7):
        x, y = 3 + 19 * t, 29 - 19 * t
        s.line((x - .5, y + .5), (x + .5, y - .5), 1.5, 'gold')
    parts.crescent(s, [(22.5, 9.5), (21, 5.5), (17, 3.2), (11, 3), (5.5, 4.5), (2, 8)],
                   [2.6, 2.8, 2.6, 2.1, 1.4, 0.6], 'steel')
    s.path([(19.5, 7.3), (16, 5.8), (11, 5.6), (6, 6.6), (3, 8.3)], 0.65, 'gold')
    s.disc((22, 10), 2.0, 'gold')
    for i in range(4):                                            # horsehair tuft
        s.line((23.5, 11.5 + i * .4), (26 + i * .8, 14 + i * 1.2), 0.7, 'hair')
    return s


def backbling():
    """Hoplite shield (aspis): navy face, a raised gold rim and a gold lambda (48x48)."""
    s = base(48)
    c = (24, 24)
    s.disc(c, 22.5, 'gold')
    s.disc(c, 19.5, 'steel')
    # gold studs around the rim
    for k in range(16):
        a = 2 * math.pi * k / 16
        s.disc((c[0] + 21 * math.cos(a), c[1] + 21 * math.sin(a)), 0.7, 'hair')
    # lambda
    s.line((24, 11), (13, 36), 2.2, 'gold')
    s.line((24, 11), (35, 36), 2.2, 'gold')
    s.line((24, 12), (24, 13), 2.4, 'gold')
    # Greek-key ring
    for k in range(32):
        a = 2 * math.pi * k / 32
        if k % 2 == 0:
            s.put(int(c[0] + 17 * math.cos(a)), int(c[1] + 17 * math.sin(a)), 'gold')
    return s
