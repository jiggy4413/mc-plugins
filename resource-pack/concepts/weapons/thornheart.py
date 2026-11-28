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
    s = base()
    s.line((12, 20), (25, 7), 2.6, 'steel')
    s.line((24, 8), (28.5, 3.5), 1.7, 'steel')
    s.line((14.8, 21.2), (27, 9), 0.8, 'edge')            # crimson cutting edge
    s.line((13.5, 18.5), (24, 8), 0.6, 'wine')            # fuller
    s.line((7, 13), (18, 24), 1.6, 'steel')               # crossguard
    s.line((4, 27.5), (10, 21.5), 1.3, 'steel')
    vine_wrap(s, (4, 27), (10, 21), 2)
    s.stamp(10, 16, ROSE5, RKEY)                          # rose on the guard
    s.stamp(1, 27, ROSE5[:4], RKEY)                       # rose pommel
    return s


def axe():
    s = base()
    s.line((4, 28.5), (20, 12.5), 1.3, 'steel')
    vine_wrap(s, (5, 27), (17, 15), 3)
    # broad crescent blade swinging out to the upper left of the head
    s.where(lambda x, y: (x + .5 - 14) ** 2 + (y + .5 - 11) ** 2 <= 92 and (x + .5 - 21) ** 2 + (y + .5 - 15) ** 2 >= 40
            and x + y <= 27 and y <= 17, 'steel')
    s.where(lambda x, y: s.mat[y][x] == 'steel' and (x + .5 - 14) ** 2 + (y + .5 - 11) ** 2 >= 62
            and x + y <= 26, 'edge')
    s.poly([(17, 9), (23, 4), (28, 9), (22, 15), (18, 14)], 'steel')   # head block
    s.stamp(20, 7, ROSE5, RKEY)
    s.color(26, 12, LEAF); s.color(27, 12, LEAFD); s.color(19, 5, LEAF); s.color(18, 5, LEAFD)
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
