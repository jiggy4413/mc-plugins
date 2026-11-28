"""Tiny pixel-art toolkit for 32x32 item sprites (same look as the frost weapons:
dark outline, light top-left edges, shaded bottom-right edges)."""
import math
from PIL import Image

N = 32


class Sprite:
    def __init__(self, outline, n=N):
        self.n = n
        self.mat = [[None] * n for _ in range(n)]   # material name per pixel
        self.fix = {}                               # pixels with a fixed colour (no shading)
        self.outline = outline
        self.ramps = {}

    def ramp(self, name, hi, mid, lo):
        self.ramps[name] = (hi, mid, lo)

    # ---------------------------------------------------------------- shapes
    def put(self, x, y, m):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.mat[y][x] = m
            self.fix.pop((x, y), None)

    def color(self, x, y, c):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.fix[(x, y)] = c
            if self.mat[y][x] is None:
                self.mat[y][x] = '_fixed'

    def line(self, p0, p1, r, m):
        (x0, y0), (x1, y1) = p0, p1
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy or 1
        for y in range(self.n):
            for x in range(self.n):
                px, py = x + .5, y + .5
                t = max(0, min(1, ((px - x0) * dx + (py - y0) * dy) / L2))
                if math.hypot(px - (x0 + t * dx), py - (y0 + t * dy)) <= r:
                    self.put(x, y, m)

    def path(self, pts, r, m):
        for a, b in zip(pts, pts[1:]):
            self.line(a, b, r, m)

    def poly(self, pts, m):
        for y in range(self.n):
            for x in range(self.n):
                if inside(x + .5, y + .5, pts):
                    self.put(x, y, m)

    def disc(self, c, r, m):
        for y in range(self.n):
            for x in range(self.n):
                if math.hypot(x + .5 - c[0], y + .5 - c[1]) <= r:
                    self.put(x, y, m)

    def where(self, fn, m, only=None):
        for y in range(self.n):
            for x in range(self.n):
                if fn(x, y) and (only is None or self.mat[y][x] in only):
                    self.put(x, y, m)

    def stamp(self, x0, y0, rows, key):
        """rows of characters; key maps char -> material name or (r,g,b) fixed colour."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in key:
                    v = key[ch]
                    if isinstance(v, tuple):
                        self.color(x0 + i, y0 + j, v)
                    else:
                        self.put(x0 + i, y0 + j, v)
                elif ch == '~':
                    self.mat[y0 + j][x0 + i] = None if 0 <= x0 + i < self.n and 0 <= y0 + j < self.n else None

    def erase(self, x, y):
        if 0 <= x < self.n and 0 <= y < self.n:
            self.mat[y][x] = None
            self.fix.pop((x, y), None)

    # ---------------------------------------------------------------- render
    def image(self, outline_diag=False):
        n = self.n
        img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
        px = img.load()
        filled = lambda x, y: 0 <= x < n and 0 <= y < n and self.mat[y][x] is not None
        same = lambda x, y, m: 0 <= x < n and 0 <= y < n and self.mat[y][x] == m
        for y in range(n):
            for x in range(n):
                m = self.mat[y][x]
                if m is None:
                    continue
                if (x, y) in self.fix:
                    c = self.fix[(x, y)]
                else:
                    hi, mid, lo = self.ramps[m]
                    if not same(x, y - 1, m) or not same(x - 1, y, m):
                        c = hi
                    elif not same(x, y + 1, m) or not same(x + 1, y, m):
                        c = lo
                    else:
                        c = mid
                px[x, y] = tuple(c) + (255,)
        for y in range(n):
            for x in range(n):
                if filled(x, y):
                    continue
                nb = [(1, 0), (-1, 0), (0, 1), (0, -1)]
                if outline_diag:
                    nb += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
                if any(filled(x + a, y + b) for a, b in nb):
                    px[x, y] = tuple(self.outline) + (255,)
        return img


def inside(x, y, pts):
    c = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            c = not c
        j = i
    return c


def sheet(rows, scale=8, gap=16, bg=(28, 30, 40), label_h=0):
    """rows: list of lists of PIL images."""
    w = max(len(r) for r in rows)
    cell = max(im.size[0] for r in rows for im in r) * scale
    out = Image.new('RGBA', (w * cell + (w + 1) * gap, len(rows) * cell + (len(rows) + 1) * gap), bg + (255,))
    for j, r in enumerate(rows):
        for i, im in enumerate(r):
            big = im.resize((im.size[0] * scale, im.size[1] * scale), Image.NEAREST)
            out.alpha_composite(big, (gap + i * (cell + gap) + (cell - big.size[0]) // 2,
                                      gap + j * (cell + gap) + (cell - big.size[1]) // 2))
    return out
