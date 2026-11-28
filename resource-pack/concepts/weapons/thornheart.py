"""Thornheart (Valentines) weapons + back bling. Black steel, crimson edges, thorny vines, roses."""
from px import Sprite
import parts

OUT = (18, 8, 14)
STEEL = ((150, 132, 150), (88, 74, 90), (54, 42, 56))
EDGE = ((255, 120, 132), (214, 34, 60), (146, 14, 38))
WINE = ((132, 30, 56), (96, 18, 40), (70, 10, 30))
VINE = ((126, 194, 92), (74, 144, 62), (42, 98, 46))
TH = (226, 208, 160)
RO, ROL, ROD, ROK = (212, 30, 56), (255, 110, 124), (138, 12, 36), (92, 6, 26)
LEAF, LEAFD = (104, 178, 78), (54, 120, 54)

ROSE5 = [".LRR.",
         "LRdRR",
         "RdKdR",
         "RRdRD",
         ".RDD."]
ROSE3 = ["LR.",
         "RdR",
         ".RD"]
RKEY = {'L': ROL, 'R': RO, 'd': ROD, 'D': ROD, 'K': ROK}


def base(n=32):
    s = Sprite(OUT, n)
    s.ramp('steel', *STEEL)
    s.ramp('edge', *EDGE)
    s.ramp('wine', *WINE)
    s.ramp('vine', *VINE)
    return s


def thorns(s, pts):
    for x, y in pts:
        s.color(x, y, TH)


def vine_wrap(s, a, b, every=3):
    """green vine coiled round a haft from a to b, with thorns poking out."""
    (x0, y0), (x1, y1) = a, b
    n = int(max(abs(x1 - x0), abs(y1 - y0)))
    for k in range(0, n, every):
        x = round(x0 + (x1 - x0) * k / n)
        y = round(y0 + (y1 - y0) * k / n)
        s.put(x, y, 'vine'); s.put(x + 1, y, 'vine'); s.put(x, y + 1, 'vine')
        if k % (every * 2) == 0:
            s.color(x - 1, y - 1, TH)


def sword():
    """Thornblade: a black-steel blade whose edges grow sharp thorns, with a guard of curling thorny
    branches and a rose at the centre."""
    s = base()
    s.line((12, 20), (25, 7), 2.2, 'steel')
    s.line((24, 8), (29, 3), 1.3, 'steel')
    # thorn barbs pointing back towards the hilt along both edges
    for t in (0.22, 0.5, 0.78):
        x, y = 12 + 13 * t, 20 - 13 * t
        for side in (1, -1):
            nx, ny = .707 * side, .707 * side
            bx, by = x + nx * 2.2, y + ny * 2.2
            s.line((bx, by), (bx + nx * 1.6 - .707 * 1.8, by + ny * 1.6 + .707 * 1.8), .5, 'edge')
    s.line((13.5, 18.5), (25.5, 6.5), 0.55, 'edge')        # crimson core
    # guard: two thorny branches curling up towards the blade
    s.path([(12, 20), (9, 18), (7.5, 15), (8.5, 12.5)], .9, 'vine')
    s.path([(12, 20), (14, 23), (17, 24.5), (19.5, 23.5)], .9, 'vine')
    thorns(s, [(7, 13), (9, 11), (20, 24), (18, 25), (8, 17), (15, 24)])
    s.line((4.5, 27), (10.5, 21.5), 1.2, 'steel')
    vine_wrap(s, (4, 27), (10, 21), 2)
    s.stamp(10, 17, ROSE5, RKEY)
    s.stamp(1, 27, ROSE5[:4], RKEY)
    return s


def axe():
    """Bearded black-steel axe with hooked, razor points top and bottom and a thorn spike on the back."""
    s = base()
    s.line((4, 28.5), (21.5, 11), 1.2, 'steel')
    vine_wrap(s, (5, 27), (16, 16), 3)
    H = (18.5, 13.5)
    blade = parts.local(H, [(-2.5, 1), (-9, 7.5), (-7.5, 10.5), (-2, 8.5), (2.5, 9), (7.5, 11), (6.5, 6.5), (3, 1)])
    s.poly(blade, 'steel')
    edge = parts.local(H, [(-8.5, 9.6), (-2, 7.8), (2.5, 8.2), (7, 10.2)])
    s.path(edge, .6, 'edge')
    s.poly(parts.local(H, [(-2, -1.2), (2.5, -1.2), (0.5, -6)]), 'steel')          # thorn spike on the back
    s.put(int(parts.local(H, [(0.5, -5)])[0][0]), int(parts.local(H, [(0.5, -5)])[0][1]), 'edge')
    s.stamp(17, 11, ROSE3, RKEY)
    return s


def bow(pull=0):
    s = base()
    parts.bow(s, 'vine', (246, 160, 182), pull, arrow=((120, 96, 70), STEEL[0], RO), r=1.5)
    thorns(s, [(1, 21), (2, 14), (5, 8), (11, 3), (18, 1), (25, 1)])
    s.line((4, 18), (8, 11), 1.6, 'steel')               # black steel grip
    s.stamp(3, 27, ROSE5[:4], RKEY)
    s.stamp(26, 1, ROSE5[:4], RKEY)
    return s


def scythe():
    s = base()
    s.line((3, 29), (22, 10), 1.2, 'steel')
    vine_wrap(s, (4, 27), (19, 13), 3)
    parts.crescent(s, [(22.5, 9.5), (21, 5.5), (17, 3.2), (11, 3), (5.5, 4.5), (2, 8)],
                   [2.6, 2.8, 2.6, 2.1, 1.4, 0.6], 'steel')
    s.path([(19.5, 7.3), (16, 5.8), (11, 5.6), (6, 6.6), (3, 8.3)], 0.65, 'edge')
    s.stamp(19, 8, ROSE5, RKEY)
    return s


def backbling():
    """'Heart of Thorns': a black-steel heart with a crimson rim, a thorny vine wound round it and
    roses on both lobes and the point (48x48 sprite)."""
    s = base(48)
    def heart(x, y):
        y = -y * 1.15 + .15
        return (x * x + y * y - 1) ** 3 - x * x * y ** 3 <= 0
    cx, cy = 24, 24
    H = lambda k, dy=0: (lambda x, y: heart((x + .5 - cx) / k, (y + .5 - cy - dy) / k))
    s.where(H(20), 'edge')
    s.where(H(17), 'steel')
    s.where(H(8, 1), 'wine')
    import math
    # vine running round just inside the rim, as a wavy band
    for t in range(0, 360, 2):
        a = math.radians(t)
        x = 16 * math.sin(a) ** 3
        y = -(13 * math.cos(a) - 5 * math.cos(2 * a) - 2 * math.cos(3 * a) - math.cos(4 * a))
        k = 0.98 + 0.06 * math.sin(a * 9)
        px, py = cx + x * k, cy - 2 + y * k
        s.line((px, py), (px + .1, py + .1), 1.1, 'vine')
        if t % 24 == 0:
            s.color(int(px + 2 * math.sin(a)), int(py - 1), TH)
    big = ["..LLRR..",
           ".LRRRRR.",
           "LRRddRRR",
           "RRdKKdRR",
           "RRdKKdRD",
           "RRRddRRD",
           ".RRRRDD.",
           "..RDDD.."]
    s.stamp(7, 4, big, RKEY)
    s.stamp(33, 4, big, RKEY)
    s.stamp(20, 37, big, RKEY)
    s.stamp(20, 19, ROSE5, RKEY)
    for x, y in [(15, 8), (16, 9), (32, 9), (31, 10), (28, 41), (29, 42), (18, 41)]:
        s.color(x, y, LEAF)
    for x, y in [(16, 8), (32, 10), (29, 41)]:
        s.color(x, y, LEAFD)
    return s


def spear():
    s = base()
    parts.spear_shaft(s, 'steel', 1.0)
    vine_wrap(s, (3, 28), (17, 14), 3)
    parts.spear_head(s, 'steel', 'edge', length=10, width=2.7)
    s.line((20.5, 11.5), (27, 5), 0.5, 'wine')
    s.stamp(17, 12, ROSE5, RKEY)                         # rose where the head meets the shaft
    s.color(16, 16, LEAF); s.color(22, 17, LEAF); s.color(23, 16, LEAFD)
    return s


def shield():
    """Black-steel heater shield, crimson rim, a big rose with thorny vines across it."""
    s = base()
    s.where(lambda x, y: parts.heater(x + .5, y + .5), 'edge')
    s.where(lambda x, y: parts.heater(x + .5, y + .5, top=4.6, bottom=27, half=10.4), 'steel')
    s.path([(6.5, 7), (12, 12), (16, 15), (21, 20), (24, 24)], 0.8, 'vine')
    s.path([(25.5, 7), (20, 12), (12, 20), (9, 22)], 0.8, 'vine')
    thorns(s, [(8, 7), (11, 10), (21, 19), (23, 22), (24, 8), (19, 11), (11, 19)])
    big = ["..LLRR..",
           ".LRRRRR.",
           "LRRddRRR",
           "RRdKKdRR",
           "RRdKKdRD",
           "RRRddRRD",
           ".RRRRDD.",
           "..RDDD.."]
    s.stamp(12, 11, big, RKEY)
    s.color(11, 18, LEAF); s.color(20, 18, LEAF); s.color(21, 18, LEAFD)
    return s


def backbling_wings():
    """Thorn Wings: two raised black-steel wings, crimson blade tips, a thorny vine along the top edge."""
    s = base(48)
    for side in (-1, 1):
        f = parts.wing_shape(24, side, root_y=20, span=21, rise=12, drop=17)
        s.where(lambda x, y: f(x, y) is not None, 'steel')
        s.where(lambda x, y: f(x, y) is not None and f(x, y)[2], 'edge')
        for k in range(1, 5):                                     # wing bones fanning out
            u = k / 5
            s.line((24 + side * 4, 19), (24 + side * (3 + 21 * u), 20 - 12 * u ** .75 + 13),
                   0.45, 'wine')
        top = [(24 + side * (3 + 21 * u / 10), 20 - 12 * (u / 10) ** .75) for u in range(0, 11)]
        s.path(top, 0.9, 'vine')
        thorns(s, [(int(x), int(y) - 2) for x, y in top[2::2]])
    s.stamp(20, 15, ["..LLRR..",
                     ".LRRRRR.",
                     "LRRddRRR",
                     "RRdKKdRR",
                     "RRdKKdRD",
                     "RRRddRRD",
                     ".RRRRDD.",
                     "..RDDD.."], RKEY)
    return s


def backbling_rose():
    """Black Rose: one giant dark rose with a crimson heart, on a thorny stem with leaves."""
    import math
    s = base(48)
    s.ramp('p1', (110, 60, 88), (70, 36, 58), (42, 18, 34))
    s.ramp('p2', (92, 46, 72), (54, 26, 44), (32, 12, 24))
    s.ramp('p3', (150, 30, 60), (104, 18, 42), (70, 10, 30))
    s.line((24, 30), (22, 47), 1.4, 'vine')
    thorns(s, [(25, 34), (20, 38), (25, 42), (20, 45)])
    s.poly([(22, 40), (13, 35), (11, 37), (17, 42)], 'vine')
    s.poly([(23, 36), (32, 31), (35, 33), (28, 38)], 'vine')
    c = (24, 19)
    def ring(R, lobes, ph):
        return lambda x, y: math.hypot(x + .5 - c[0], y + .5 - c[1]) <= R + R * .13 * math.cos(
            math.atan2(y + .5 - c[1], x + .5 - c[0]) * lobes + ph)
    s.where(ring(17, 5, 0), 'p1')
    s.where(ring(12.5, 5, 0.6), 'p2')
    s.where(ring(8.5, 4, 1.4), 'p1')
    s.where(ring(5, 3, 0.3), 'p3')
    s.where(ring(2.4, 3, 0), 'edge')
    return s
