"""Shared weapon shapes (layout matches the frost weapons so they hold the same way in hand)."""
import math
from px import Sprite

PULL = {0: None, 1: (19.5, 19.5), 2: (21, 21), 3: (22.5, 22.5)}


def bow(s, limb_mat, string_rgb, pull=0, arrow=None, r=1.5):
    """Limbs bend from the bottom left, through the upper left, to the top right. pull 0..3."""
    limb = [(4, 28.5), (3.6, 22), (5, 15), (8.5, 9.5), (14, 5.5), (21, 3.8), (28.5, 3.5)]
    s.path(limb, r, limb_mat)
    a, b = (5.5, 27), (27, 5.5)
    v = PULL[pull]
    segs = [(a, b)] if v is None else [(a, v), (v, b)]
    for p, q in segs:
        n = 60
        for k in range(n + 1):
            x = p[0] + (q[0] - p[0]) * k / n
            y = p[1] + (q[1] - p[1]) * k / n
            if s.mat[int(y)][int(x)] is None or (int(x), int(y)) in s.fix:
                s.color(int(x), int(y), string_rgb)
    if v is not None and arrow:
        shaft, head, fletch = arrow
        tip = (7.5 - pull * .4, 7.5 - pull * .4)
        n = 60
        for k in range(n + 1):
            x = v[0] + (tip[0] - v[0]) * k / n
            y = v[1] + (tip[1] - v[1]) * k / n
            s.color(int(x), int(y), shaft)
        tx, ty = int(tip[0]), int(tip[1])
        for dx, dy in [(0, 0), (-1, -1), (-1, 0), (0, -1)]:
            s.color(tx + dx, ty + dy, head)
        vx, vy = int(v[0]), int(v[1])
        for dx, dy in [(-1, -2), (-2, -1), (-2, -2), (-3, -2), (-2, -3)]:
            s.color(vx + dx, vy + dy, fletch)
    return limb


def crescent(s, pts, rs, mat):
    for (a, ra), (b, rb) in zip(zip(pts, rs), zip(pts[1:], rs[1:])):
        steps = 6
        for k in range(steps):
            t0, t1 = k / steps, (k + 1) / steps
            p = (a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0)
            q = (a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1)
            s.line(p, q, ra + (rb - ra) * (t0 + t1) / 2, mat)


def spear_shaft(s, mat, r=0.9):
    """long shaft from the bottom left corner almost to the top right; returns the tip base point."""
    s.line((1.5, 30.5), (22, 10), r, mat)
    return (22, 10)


def spear_head(s, mat, edge=None, length=9.5, width=2.6):
    """leaf-shaped head from (21,11) to the top right corner."""
    a = (20.5, 11.5)
    d = (0.707, -0.707)
    for k in range(101):
        t = k / 100
        w = width * math.sin(math.pi * min(1, t / 0.55)) if t < 0.55 else width * (1 - t) / 0.45
        x, y = a[0] + d[0] * length * t, a[1] + d[1] * length * t
        s.line((x, y), (x + .01, y + .01), max(.45, w), mat)
    if edge:
        for k in range(10, 96):
            t = k / 100
            w = width * math.sin(math.pi * min(1, t / 0.55)) if t < 0.55 else width * (1 - t) / 0.45
            x, y = a[0] + d[0] * length * t, a[1] + d[1] * length * t
            if w > .9:
                s.put(int(x + (w - .5) * .707), int(y + (w - .5) * .707), edge)


def heater(x, y, cx=16, top=3, bottom=29, half=12):
    """heater-shield outline: flat top with rounded shoulders, sides curving to a point."""
    if y < top or y > bottom:
        return False
    t = (y - top) / (bottom - top)
    w = half if t < 0.45 else half * math.sqrt(max(0, 1 - ((t - 0.45) / 0.55) ** 1.6))
    if t < 0.08:
        w = half - (0.08 - t) * 30
    return abs(x - cx) <= w


def wing_shape(cx, side, root_y=22, span=21, rise=17, drop=13, scallops=5):
    """(x,y) -> (inside, u along span 0..1, depth 0..1 from top edge, on_scallop_tip) for one wing."""
    def f(x, y):
        X = (x + .5 - cx) * side - 3
        if X < 0 or X > span:
            return None
        u = X / span
        top = root_y - rise * (u ** 0.75)
        h = 4 + drop * math.sin(math.pi * (0.15 + 0.8 * u)) * (1 - 0.35 * u)
        sc = (u * scallops) % 1
        bottom = top + h + 2.6 * math.sin(math.pi * sc)
        Y = y + .5
        if top <= Y <= bottom:
            return (u, (Y - top) / (bottom - top), bottom - Y < 2.2)
        return None
    return f
