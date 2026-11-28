"""Tidecaller (Summer) weapons + back bling. Turquoise steel, sea foam, sand and coral."""
import math
from px import Sprite
import parts

OUT = (6, 34, 42)
STEEL = ((150, 240, 228), (40, 186, 176), (14, 112, 116))
DEEP = ((40, 140, 150), (14, 100, 112), (8, 66, 78))
FOAM = ((255, 255, 255), (232, 248, 246), (176, 220, 220))
SAND = ((255, 236, 186), (238, 206, 146), (196, 158, 98))
CORAL = ((255, 170, 128), (255, 120, 78), (204, 74, 48))
WOOD = ((206, 178, 136), (164, 132, 92), (118, 92, 62))   # driftwood


def base(n=32):
    s = Sprite(OUT, n)
    for k, v in dict(steel=STEEL, deep=DEEP, foam=FOAM, sand=SAND, coral=CORAL, wood=WOOD).items():
        s.ramp(k, *v)
    return s


def waves(s, a, b, mat, side=1.0, amp=1.1, period=5.0, r=0.6):
    """a foam line that ripples along a->b (offset to one side)."""
    (x0, y0), (x1, y1) = a, b
    L = math.hypot(x1 - x0, y1 - y0)
    nx, ny = (y1 - y0) / L * side, -(x1 - x0) / L * side
    prev = None
    for k in range(int(L * 4) + 1):
        t = k / (L * 4)
        w = amp * math.sin(2 * math.pi * t * L / period)
        p = (x0 + (x1 - x0) * t + nx * w, y0 + (y1 - y0) * t + ny * w)
        if prev:
            s.line(prev, p, r, mat)
        prev = p


def sword():
    s = base()
    s.line((12, 20), (25, 7), 2.6, 'steel')
    s.line((24, 8), (28.5, 3.5), 1.7, 'steel')
    waves(s, (13.5, 21), (27, 7.5), 'foam', side=-1, amp=.9, period=4.5)    # foam along the edge
    s.line((13, 18.5), (23, 8.5), .5, 'deep')
    # scallop-shell guard
    s.where(lambda x, y: math.hypot(x + .5 - 12, y + .5 - 20) <= 4.3 and (x + .5 - 12) - (y + .5 - 20) <= 1.2, 'sand')
    for ang in (-150, -120, -90, -60, -30, 0):
        a = math.radians(ang + 45 + 90)
        s.line((12, 20), (12 + 3.8 * math.cos(a), 20 + 3.8 * math.sin(a)), .3, 'coral')
    s.line((4.5, 27), (10, 21.5), 1.2, 'wood')
    for x, y in [(5, 25), (7, 23), (9, 21)]:
        s.put(x, y, 'steel')
    s.disc((3.5, 28.5), 1.7, 'coral')                    # coral pommel
    return s


def axe():
    """Scallop-shell axe head: a ribbed sand fan with a turquoise rim on a driftwood haft."""
    s = base()
    s.line((4, 28.5), (20, 12.5), 1.2, 'wood')
    for t in (0.3, 0.6):
        x, y = 4 + 16 * t, 28.5 - 16 * t
        s.line((x - .5, y + .5), (x + .5, y - .5), 1.4, 'steel')
    hx, hy = 20, 12          # hinge of the shell
    def fan(x, y):
        dx, dy = x + .5 - hx, y + .5 - hy
        r = math.hypot(dx, dy)
        a = math.degrees(math.atan2(dy, dx))
        aa = a if a < 30 else a - 360
        return r <= 12.5 - 0.8 * abs(math.sin(math.radians(aa) * 7)) and -215 <= aa <= -80 and r >= 1
    s.where(fan, 'sand')
    s.where(lambda x, y: fan(x, y) and math.hypot(x + .5 - hx, y + .5 - hy) >= 10.6, 'steel')
    for ang in range(-207, -85, 16):                       # ribs
        a = math.radians(ang)
        s.line((hx + 2 * math.cos(a), hy + 2 * math.sin(a)), (hx + 10 * math.cos(a), hy + 10 * math.sin(a)), .35, 'coral')
    s.disc((hx + .5, hy - .5), 2.2, 'steel')
    s.put(hx + 1, hy - 2, 'foam')
    return s


def bow(pull=0):
    """Coral bow: branching orange coral limbs with a turquoise grip and a pearl."""
    s = base()
    limb = parts.bow(s, 'coral', (226, 250, 246), pull, arrow=((206, 178, 136), STEEL[1], (255, 255, 255)), r=1.4)
    for (x0, y0), (x1, y1) in [((4, 24), (1, 21.5)), ((6, 13), (3, 10)), ((12, 6.5), (11, 3)),
                               ((20, 4), (21.5, 1)), ((3.8, 22), (1.5, 25))]:
        s.line((x0, y0), (x1, y1), .7, 'coral')
    s.line((3.8, 18), (7.5, 11.5), 1.7, 'steel')
    s.disc((5.6, 14.7), 1.1, 'foam')
    return s


def scythe():
    """A breaking wave: turquoise crescent with a foam crest that curls over at the tip."""
    s = base()
    s.line((3, 29), (22, 10), 1.2, 'wood')
    for t in (0.25, 0.55):
        x, y = 3 + 19 * t, 29 - 19 * t
        s.line((x - .5, y + .5), (x + .5, y - .5), 1.4, 'steel')
    parts.crescent(s, [(22.5, 9.5), (21, 5.5), (17, 3.2), (11, 3), (5.5, 4.5), (2.5, 7.5)],
                   [2.6, 2.8, 2.6, 2.1, 1.6, 1.0], 'steel')
    parts.crescent(s, [(21, 4.2), (17, 2.2), (11, 2), (6, 3), (3, 5), (2.5, 7.5), (4.5, 8.5), (5.5, 7)],
                   [0.9, 1.0, 0.9, 0.8, 0.8, 0.7, 0.6, 0.5], 'foam')      # foam crest curling over
    s.path([(19.5, 7.6), (16, 6.2), (11, 6), (7, 7)], 0.5, 'deep')
    s.disc((21.5, 10.5), 1.8, 'sand')
    return s


def backbling():
    """A giant scallop shell with a pearl (48x48)."""
    s = base(48)
    hx, hy = 24, 42
    def fan(x, y, R):
        dx, dy = x + .5 - hx, y + .5 - hy
        r = math.hypot(dx, dy)
        a = math.degrees(math.atan2(-dy, dx))
        scallop = R - 1.3 * abs(math.sin(math.radians(a) * 9))
        return r <= scallop and 12 <= a <= 168
    s.where(lambda x, y: fan(x, y, 37), 'sand')
    s.where(lambda x, y: fan(x, y, 37) and not fan(x, y, 33), 'coral')
    for ang in range(20, 165, 16):
        a = math.radians(ang)
        s.line((hx + 5 * math.cos(a), hy - 5 * math.sin(a)), (hx + 33 * math.cos(a), hy - 33 * math.sin(a)), .55, 'wood')
    s.poly([(18, 47), (30, 47), (28, 40), (20, 40)], 'sand')                  # hinge ears
    s.where(lambda x, y: 39 <= y <= 47 and 16 <= x <= 32 and abs(x + .5 - 24) <= 8 - (y - 39) * 0.1, 'sand')
    s.disc((24, 25), 3.6, 'foam')                                              # pearl
    s.put(23, 23, 'foam')
    s.disc((36, 18), 1.2, 'steel'); s.disc((12, 18), 1.2, 'steel')            # turquoise gems
    return s
