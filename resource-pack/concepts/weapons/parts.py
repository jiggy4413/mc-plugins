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
