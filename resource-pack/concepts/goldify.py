"""Gold versions of the Valentines sets: every metal, feather, vine and plate goes to the vanilla gold
palette (no white); red roses and hearts stay red like gems. Shared by the concept sheet and build_pack.py."""
GOLD_RAMP = [(117, 40, 2), (162, 88, 12), (204, 129, 16), (233, 177, 21), (245, 214, 52), (253, 245, 95), (255, 255, 139)]
OUTLINE_GOLD = (56, 22, 2)


def ramp(t):
    t = max(0.0, min(1.0, t)) * (len(GOLD_RAMP) - 1)
    i = min(int(t), len(GOLD_RAMP) - 2)
    f = t - i
    a, b = GOLD_RAMP[i], GOLD_RAMP[i + 1]
    return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


def is_gem(c):
    r, g, b = c[:3]
    return r > 120 and r > 2.4 * g and r > 1.3 * b


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def is_vine(c):
    r, g, b = c[:3]
    return g > r and g > b


class Golder:
    """maps colours to gold by brightness, stretched over the brightness range of one asset.
    With vines_dark, green vine/leaf pixels get the deep end of the ramp and the plate the bright end,
    so the armor reads as bright gold with darker gold vines on it."""
    def __init__(self, colors, lo=0.2, hi=0.84, vines_dark=False):
        self.vd = vines_dark
        plate = [lum(c) for c in colors if not is_gem(c) and not (vines_dark and is_vine(c))]
        vines = [lum(c) for c in colors if vines_dark and is_vine(c)]
        self.l0, self.l1 = (min(plate), max(plate)) if plate else (0, 255)
        self.v0, self.v1 = (min(vines), max(vines)) if vines else (0, 255)
        self.lo, self.hi = (0.42, 0.86) if vines_dark else (lo, hi)

    def __call__(self, c):
        if is_gem(c):
            return tuple(c[:3])
        if self.vd and is_vine(c):
            t = (lum(c) - self.v0) / max(1, self.v1 - self.v0)
            return ramp(0.1 + 0.26 * t)
        t = (lum(c) - self.l0) / max(1, self.l1 - self.l0)
        return ramp(self.lo + (self.hi - self.lo) * t)


def gold_image(img, outline=None, vines_dark=False):
    img = img.convert('RGBA')
    px = img.load()
    cols = [px[x, y] for y in range(img.size[1]) for x in range(img.size[0])
            if px[x, y][3] and (outline is None or px[x, y][:3] != tuple(outline))]
    g = Golder(cols, vines_dark=vines_dark)
    out = img.copy(); po = out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            c = px[x, y]
            if not c[3]:
                continue
            po[x, y] = (OUTLINE_GOLD if outline is not None and c[:3] == tuple(outline) else g(c)) + (c[3],)
    return out


def gold_quads(Q, vines_dark=False):
    g = Golder([c for _, c, _ in Q], vines_dark=vines_dark)
    return [(p, g(c) + (255,), f) for p, c, f in Q]


