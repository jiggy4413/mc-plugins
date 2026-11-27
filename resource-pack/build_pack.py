"""Builds the Classic Prison resource pack for Minecraft 1.21.4 (pack format 46).

    python3 resource-pack/build_pack.py

Output: resource-pack/dist/ClassicPrison-1.21.4.zip (+ its SHA-1 for server.properties).

What's in it
- Custom armor sets (all NETHERITE items, look set by PrisonCore's items.yml `item-model` / `equipment`):
    winter (Winter Nova), thornheart, agamemnon, tidecaller
  * worn look: classicprison:equipment/<set>  (textures/entity/equipment/humanoid[_leggings]/<set>.png)
  * helmets are 3D models, worn on the head too (items.yml `equipment: model`)
  * icons: classicprison:item/<set>_<piece>
- Frost weapons (custom_model_data 2001-2004 normal, 2011-2014 gold) on the 1.21.4 item format.
Vanilla leather and chainmail look normal again (no more global armor texture replacements).

The set designs come from concepts/ (same code that draws the previews), so a tweak there shows up here.
"""
import hashlib
import json
import math
import os
import shutil
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
CONCEPTS = os.path.join(HERE, 'concepts')
OLD = os.path.join(HERE, 'classic-prison-pack', 'assets', 'minecraft')
OUT = os.path.join(HERE, 'dist', 'ClassicPrison-1.21.4')
NS = 'classicprison'

# ---------------------------------------------------------------- load the concept designs
design_src = open(os.path.join(CONCEPTS, 'new-sets', 'design.py')).read().split('# ---- overview sheet')[0]
G = {'__file__': os.path.join(CONCEPTS, 'new-sets', 'design.py'), '__name__': 'design'}
cwd = os.getcwd()
exec(compile(design_src, 'design.py', 'exec'), G)   # chdirs into concepts/helmets-3d
os.chdir(cwd)
R = G['R']
Image = G['Image']
from PIL import ImageDraw  # noqa: E402

SETS = ['winter', 'goldwinter', 'thornheart', 'agamemnon', 'tidecaller']
PIECES = ['helmet', 'chestplate', 'leggings', 'boots']

def p(*parts):
    path = os.path.join(OUT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path

def write_json(obj, *parts):
    with open(p(*parts), 'w') as f:
        json.dump(obj, f, indent=2)

# ---------------------------------------------------------------- worn textures
S = 2
def rects(U, V, w, h, d):
    return {'top': (U+d, V, w, d), 'bottom': (U+d+w, V, w, d), 'right': (U, V+d, d, h),
            'front': (U+d, V+d, w, h), 'left': (U+d+w, V+d, d, h), 'back': (U+2*d+w, V+d, w, h)}

def fill_bottoms(img, boxes):
    """Close the underside of sleeves/legs/boots whose walls reach the bottom edge, using the
    colours of the walls' bottom rows (the 'see-through bottoms' fix)."""
    px = img.load()
    for (U, V, w, h, d) in boxes:
        r = rects(U, V, w, h, d)
        bu, bv, bw, bd = r['bottom']
        bx0, by0, BW, BD = bu*S, bv*S, bw*S, bd*S
        if any(px[bx0+x, by0+y][3] for x in range(BW) for y in range(BD)):
            continue                                   # already painted
        fu, fv, fw, fh = r['front']; ku, kv, kw, kh = r['back']
        frow = [px[fu*S+x, (fv+fh)*S-1] for x in range(fw*S)]
        krow = [px[ku*S+x, (kv+kh)*S-1] for x in range(kw*S)]
        if sum(1 for c in frow if c[3]) < len(frow)*0.6:
            continue                                   # open at the bottom on purpose (exposed skin)
        for y in range(BD):
            for x in range(BW):
                c = frow[x] if y < BD/2 else krow[BW-1-x]
                if not c[3]:
                    c = next((q for q in frow + krow if q[3]), (40, 40, 40, 255))
                px[bx0+x, by0+y] = c

import colorsys

def gold(c):
    """Recolour Winter Nova's icy blues into gold (Golden Winter Nova), like the gold frost weapons."""
    r, g, b = c[0] / 255, c[1] / 255, c[2] / 255
    a = c[3] if len(c) > 3 else 255
    h, sat, v = colorsys.rgb_to_hsv(r, g, b)
    if sat > 0.10 and 0.42 <= h <= 0.75:                 # blues -> gold
        h = 0.115; sat = min(1.0, sat * 1.9 + 0.25); v = min(1.0, v * 1.02)
    elif sat <= 0.10 and v > 0.55:                         # whites/greys -> warm cream
        h = 0.13; sat = 0.18 + (1 - v) * 0.3
    r, g, b = colorsys.hsv_to_rgb(h, sat, v)
    return (int(r * 255), int(g * 255), int(b * 255), a)

def gold_image(img):
    out = img.copy(); px = out.load()
    for y in range(out.size[1]):
        for x in range(out.size[0]):
            if px[x, y][3]:
                px[x, y] = gold(px[x, y])
    return out

def winter_layers():
    t1 = G['tint'](os.path.join(CONCEPTS, 'helmets-3d', 'leather_layer_1.png'),
                   os.path.join(CONCEPTS, 'helmets-3d', 'leather_layer_1_overlay.png'), '#A8DCFF')
    t2 = G['tint'](os.path.join(CONCEPTS, 'helmets-3d', 'leather_layer_2.png'),
                   os.path.join(CONCEPTS, 'helmets-3d', 'leather_layer_2_overlay.png'), '#A8DCFF')
    return t1, t2

LAYERS = {
    'winter': winter_layers(),
    'thornheart': (G['th1'].copy(), G['th2'].copy()),
    'agamemnon': (Image.open(os.path.join(CONCEPTS, 'agamemnon', 'chainmail_layer_1.png')).convert('RGBA'),
                  Image.open(os.path.join(CONCEPTS, 'agamemnon', 'chainmail_layer_2.png')).convert('RGBA')),
    'tidecaller': (G['tc1'].copy(), G['tc2'].copy()),
}
L1_BOXES = [(16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (0, 16, 4, 12, 4)]   # body, arm, boot
L2_BOXES = [(16, 16, 8, 12, 4), (0, 16, 4, 12, 4)]                    # waist, leg
fill_bottoms(LAYERS['winter'][0], L1_BOXES); fill_bottoms(LAYERS['winter'][1], L2_BOXES)
LAYERS['goldwinter'] = (gold_image(LAYERS['winter'][0]), gold_image(LAYERS['winter'][1]))
for s, (l1, l2) in LAYERS.items():
    fill_bottoms(l1, L1_BOXES)
    fill_bottoms(l2, L2_BOXES)
    l1.save(p('assets', NS, 'textures', 'entity', 'equipment', 'humanoid', s + '.png'))
    l2.save(p('assets', NS, 'textures', 'entity', 'equipment', 'humanoid_leggings', s + '.png'))
    write_json({'layers': {'humanoid': [{'texture': f'{NS}:{s}'}],
                           'humanoid_leggings': [{'texture': f'{NS}:{s}'}]}},
               'assets', NS, 'equipment', s + '.json')
    # also keep a copy next to the concepts so the previews use the fixed versions
    if s in ('winter', 'agamemnon'):
        l1.save(os.path.join(CONCEPTS, 'new-sets', f'{s}_layer_1.png'))
        l2.save(os.path.join(CONCEPTS, 'new-sets', f'{s}_layer_2.png'))

# ---------------------------------------------------------------- 3D helmet models
K = 16 / 10.2          # model units per player pixel: the 10.2 px helmet shell = the 16-unit cube
def capture(fn, *args):
    """Run a helmet builder and record its solid() boxes instead of drawing them."""
    boxes = []
    def rec(x0, y0, z0, w, h, d, f):
        boxes.append((x0, y0, z0, w, h, d, f)); return []
    saved = (R.solid, G['solid'], G['parts_mod']['solid'])
    R.solid = G['solid'] = G['parts_mod']['solid'] = rec
    try:
        fn(*args)
    finally:
        R.solid, G['solid'], G['parts_mod']['solid'] = saved
    return boxes

HELMETS = {
    'winter': (LAYERS['winter'][0], lambda: G['winter_parts']()),
    'goldwinter': (LAYERS['goldwinter'][0], lambda: G['winter_parts']()),
    'thornheart': (None, lambda: G['thornheart_helmet3d'](0)),        # a cap + crown, no full helmet
    'agamemnon': (R.H, lambda: R.helmet3d(0, 0, 0)),
    'tidecaller': (LAYERS['tidecaller'][0], lambda: G['tidecaller_helmet3d'](0)),
}
FACE = {'front': 'north', 'back': 'south', 'right': 'west', 'left': 'east', 'top': 'up', 'bottom': 'down'}

class Atlas:
    def __init__(self):
        self.items = []
    def add(self, img):
        self.items.append(img); return len(self.items) - 1
    def pack(self):
        order = sorted(range(len(self.items)), key=lambda i: -self.items[i].size[1])
        size = 64
        while True:
            pos = {}; x = y = rowh = 0; ok = True
            for i in order:
                w, h = self.items[i].size
                if x + w > size:
                    x = 0; y += rowh; rowh = 0
                if y + h > size or w > size:
                    ok = False; break
                pos[i] = (x, y); x += w; rowh = max(rowh, h)
            if ok:
                break
            size *= 2
        sheet = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        for i, (x, y) in pos.items():
            sheet.alpha_composite(self.items[i], (x, y))
        self.size, self.pos, self.sheet = size, pos, sheet
    def uv(self, i):
        x, y = self.pos[i]; w, h = self.items[i].size; f = 16 / self.size
        return [round(x*f, 4), round(y*f, 4), round((x+w)*f, 4), round((y+h)*f, 4)]

def face_image(fn, face, fw, fh):
    img = Image.new('RGBA', (fw, fh), (0, 0, 0, 0)); px = img.load(); any_px = False
    for v in range(fh):
        for u in range(fw):
            c = fn(face, u, v, fw, fh)
            if c and (len(c) < 4 or c[3] > 0):
                px[u, v] = tuple(c[:3]) + (255,); any_px = True
    return img if any_px else None

def helmet_model(name, shell_tex, builder):
    atlas = Atlas(); elements = []
    # the helmet shell: the head part of the worn texture, as a 16-unit cube (open underneath)
    if shell_tex is not None:
        sh = {}
        for face, (fu, fv, fw, fh) in rects(0, 0, 8, 8, 8).items():
            if face == 'bottom':
                continue
            img = shell_tex.crop((fu*S, fv*S, (fu+fw)*S, (fv+fh)*S))
            sh[FACE[face]] = atlas.add(img)
        elements.append(('shell', [0, 0, 0], [16, 16, 16], sh))
    boxes = capture(builder)
    if name == 'goldwinter':
        boxes = [(x0, y0, z0, w, h, d, (lambda f: (lambda *a: (gold(f(*a)) if f(*a) else None)))(fn))
                 for (x0, y0, z0, w, h, d, fn) in boxes]
    for (x0, y0, z0, w, h, d, fn) in boxes:
        W_, H_, D_ = max(1, int(w*2)), max(1, int(h*2)), max(1, int(d*2))
        faces = {}
        for face, (fw, fh) in {'front': (W_, H_), 'back': (W_, H_), 'right': (D_, H_), 'left': (D_, H_),
                               'top': (W_, D_), 'bottom': (W_, D_)}.items():
            img = face_image(fn, face, fw, fh)
            if img is not None:
                faces[FACE[face]] = atlas.add(img)
        if not faces:
            continue
        fr = [8 + x0*K, 8 + (y0-28)*K, 8 + z0*K]
        to = [8 + (x0+w)*K, 8 + (y0+h-28)*K, 8 + (z0+d)*K]
        fr = [max(-16, min(32, round(c, 3))) for c in fr]
        to = [max(-16, min(32, round(c, 3))) for c in to]
        elements.append(('part', fr, to, faces))
    atlas.pack()
    atlas.sheet.save(p('assets', NS, 'textures', 'item', 'helmet', name + '.png'))
    tex = f'{NS}:item/helmet/{name}'
    # fit the inventory icon: scale so the whole helmet (crests included) fits the slot, centred
    lo = [min(e[1][i] for e in elements) for i in range(3)]
    hi = [max(e[2][i] for e in elements) for i in range(3)]
    extent = max(hi[i] - lo[i] for i in range(3))
    gs = round(min(0.625, 0.625 * 16 / max(extent, 1) * 1.05), 3)
    gy = round(-((lo[1] + hi[1]) / 2 - 8) * gs, 3)
    model = {
        'parent': 'minecraft:block/block',
        'textures': {'0': tex, 'particle': tex},
        'elements': [{'from': fr, 'to': to,
                      'faces': {f: {'uv': atlas.uv(i), 'texture': '#0'} for f, i in faces.items()}}
                     for (_, fr, to, faces) in elements],
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, gy, 0], 'scale': [gs, gs, gs]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
            'fixed': {'rotation': [0, 180, 0], 'translation': [0, -1, 0], 'scale': [0.45, 0.45, 0.45]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2, 0], 'scale': [0.3, 0.3, 0.3]},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
            'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
        },
    }
    write_json(model, 'assets', NS, 'models', 'item', name + '_helmet.json')
    return model, atlas

HELMET_MODELS = {}
for s, (tex, builder) in HELMETS.items():
    HELMET_MODELS[s] = helmet_model(s, tex, builder)

# ---------------------------------------------------------------- inventory icons (32x32)
OUTLINE = (18, 14, 20, 255)
CHEST = ['................', '..###......###..', '.#####....#####.', '.######..######.', '.##############.',
         '.##############.', '..############..', '...##########...', '...##########...', '...##########...',
         '...##########...', '...##########...', '...##########...', '...##########...', '....########....',
         '................']
LEGS = ['................', '..############..', '..############..', '..############..', '..#####..#####..',
        '..####....####..', '..####....####..', '..####....####..', '..####....####..', '..####....####..',
        '..####....####..', '..####....####..', '..####....####..', '..####....####..', '..####....####..',
        '................']
BOOTS = ['................', '................', '................', '................', '................',
         '................', '..####....####..', '..####....####..', '..####....####..', '..####....####..',
         '..####....####..', '.#####...#####..', '.#####...#####..', '.######..######.', '.######..######.',
         '................']

def icon(mask, colour):
    """mask: 16x16 shape (drawn at 32x32); colour(x, y) -> rgb for every pixel inside."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); px = img.load()
    inside = lambda x, y: 0 <= x < 32 and 0 <= y < 32 and mask[y//2][x//2] == '#'
    for y in range(32):
        for x in range(32):
            if inside(x, y):
                c = colour(x, y)
                edge_l = not inside(x-1, y); edge_r = not inside(x+1, y)
                k = 1.18 if edge_l else (0.78 if edge_r else 1.0)
                px[x, y] = tuple(max(0, min(255, int(ch*k))) for ch in c[:3]) + (255,)
            elif any(inside(x+dx, y+dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUTLINE
    return img

def rose_px(x, y, cx, cy, r):
    c = G['rose'](x, y, cx, cy, r)
    return c

def thorn_icons():
    BK = G['BK']; ROD = G['ROD']; WN = G['WN']; LF = G['LF']; LFD = G['LFD']
    steel = lambda x, y: G['metal'](x, y, 7)[:3]
    def chest(x, y):
        if y < 10 and (x < 11 or x > 20):                       # rose shoulders
            r = rose_px(x, y, 6 if x < 16 else 25, 5, 5.5)
            if r: return r
        r = rose_px(x, y, 15.5, 13, 4.2)
        if r: return r
        if abs((x - 15.5) - (y - 18) * 0.0) < 1 and 17 <= y <= 22: return LFD
        if 26 <= y <= 27: return ROD
        if y >= 23: return WN
        if (x + y) % 9 == 0 and 9 < y < 23: return LF
        return steel(x, y)
    def legs(x, y):
        if y < 7: return ROD if y in (5, 6) else WN
        if 18 <= y <= 21:
            r = rose_px(x, y, 8.5 if x < 16 else 23.5, 19.5, 2.4)
            if r: return r
        if (x - y // 2) % 7 == 0: return LF
        return WN if (x + y // 3) % 5 else G['WNL']
    def boots(x, y):
        if y >= 28: return ROD if y == 28 else BK
        if y == 12: return ROD
        if (x + y) % 6 == 0: return LF
        return steel(x, y)
    return {'chestplate': icon(CHEST, chest), 'leggings': icon(LEGS, legs), 'boots': icon(BOOTS, boots)}

def tide_icons():
    TQ, TD, DEEP = G['TQ'], G['TD'], G['DEEP']
    SAND, SANDD, CO, COD, FOAM = G['SAND'], G['SANDD'], G['CO'], G['COD'], G['FOAM']
    st = lambda base, x, y: G['steel'](base, x, y, 9)[:3]
    def chest(x, y):
        if y < 10 and (x < 11 or x > 20):                       # scallop shoulders
            return SANDD if x % 3 == 0 or y == 9 else SAND
        if y <= 7: return SAND
        if 26 <= y <= 27: return CO if 14 <= x <= 17 else SAND
        dx, dy = x - 15.5, y - 14; r = math.hypot(dx, dy); a = math.atan2(dy, dx)
        arm = 1.8 + 3.2 * max(0, math.cos(5 * (a + math.pi / 2))) ** 1.1
        if r <= arm: return CO if r < arm - 0.8 else COD
        yy = 21 + 1.2 * math.sin(x / 8 * 2 * math.pi)
        if abs(y - yy) < 0.8: return FOAM
        return st(TQ if y < 18 else TD, x, y)
    def legs(x, y):
        if y < 7: return SAND if y < 6 else SANDD
        if 16 <= y <= 18: return CO if y == 17 else COD
        yy = 24 + 1.0 * math.sin(x / 6 * 2 * math.pi)
        if abs(y - yy) < 0.8: return FOAM
        return st(TD if y < 24 else DEEP, x, y)
    def boots(x, y):
        if y >= 28: return SAND if y == 28 else SANDD
        if y == 12: return FOAM
        if y == 21: return CO
        return st(TD, x, y)
    return {'chestplate': icon(CHEST, chest), 'leggings': icon(LEGS, legs), 'boots': icon(BOOTS, boots)}

ICONS = {
    'winter': {k: Image.open(os.path.join(OLD, 'textures', 'item', 'nova', f'winter_nova_{k}.png')).convert('RGBA')
               for k in ('chestplate', 'leggings', 'boots')},
    'agamemnon': {k: Image.open(os.path.join(CONCEPTS, 'agamemnon', f'warlord_{k}.png')).convert('RGBA')
                  for k in ('chestplate', 'leggings', 'boots')},
    'thornheart': thorn_icons(),
    'goldwinter': None,
    'tidecaller': tide_icons(),
}

ICONS['goldwinter'] = {k: gold_image(v) for k, v in ICONS['winter'].items()}
for s in SETS:
    for piece in PIECES:
        model_id = f'{NS}:item/{s}_{piece}'
        write_json({'model': {'type': 'minecraft:model', 'model': model_id}},
                   'assets', NS, 'items', f'{s}_{piece}.json')
        if piece == 'helmet':
            continue                                           # 3D model written above
        ICONS[s][piece].save(p('assets', NS, 'textures', 'item', f'{s}_{piece}.png'))
        write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': model_id}},
                   'assets', NS, 'models', 'item', f'{s}_{piece}.json')

# ---------------------------------------------------------------- frost weapons (1.21.4 item definitions)
for sub in ('models/item/frost', 'textures/item/frost'):
    src = os.path.join(OLD, *sub.split('/'))
    dst = p('assets', 'minecraft', *sub.split('/'), 'x'); dst = os.path.dirname(dst)
    shutil.copytree(src, dst, dirs_exist_ok=True)

def m(model):
    return {'type': 'minecraft:model', 'model': model}

def by_cmd(vanilla, entries):
    """Pick a model by custom_model_data (PrisonCore's model-data), vanilla otherwise."""
    out = []
    for value, model in entries:
        out.append({'threshold': value, 'model': model})
        out.append({'threshold': value + 1, 'model': vanilla})   # only that exact number
    return {'model': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:custom_model_data',
                      'index': 0, 'fallback': vanilla, 'entries': out}}

def bow(still, p0, p1, p2):
    return {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
            'on_false': m(still),
            'on_true': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:use_duration', 'scale': 0.05,
                        'fallback': m(p0),
                        'entries': [{'threshold': 0.65, 'model': m(p1)}, {'threshold': 0.9, 'model': m(p2)}]}}

write_json(by_cmd(m('minecraft:item/netherite_sword'),
                  [(2001, m('minecraft:item/frost/frost_sword')), (2011, m('minecraft:item/frost/gold_frost_sword'))]),
           'assets', 'minecraft', 'items', 'netherite_sword.json')
write_json(by_cmd(m('minecraft:item/netherite_axe'),
                  [(2002, m('minecraft:item/frost/frost_axe')), (2012, m('minecraft:item/frost/gold_frost_axe'))]),
           'assets', 'minecraft', 'items', 'netherite_axe.json')
write_json(by_cmd(m('minecraft:item/netherite_hoe'),
                  [(2004, m('minecraft:item/frost/frost_scythe')), (2014, m('minecraft:item/frost/gold_frost_scythe'))]),
           'assets', 'minecraft', 'items', 'netherite_hoe.json')
vb = bow('minecraft:item/bow', 'minecraft:item/bow_pulling_0', 'minecraft:item/bow_pulling_1', 'minecraft:item/bow_pulling_2')
fb = bow('minecraft:item/frost/frost_bow', 'minecraft:item/frost/frost_bow_pulling_0',
         'minecraft:item/frost/frost_bow_pulling_1', 'minecraft:item/frost/frost_bow_pulling_2')
gb = bow('minecraft:item/frost/gold_frost_bow', 'minecraft:item/frost/gold_frost_bow_pulling_0',
         'minecraft:item/frost/gold_frost_bow_pulling_1', 'minecraft:item/frost/gold_frost_bow_pulling_2')
write_json(by_cmd(vb, [(2003, fb), (2013, gb)]), 'assets', 'minecraft', 'items', 'bow.json')

# ---------------------------------------------------------------- pack.mcmeta, icon, zip
write_json({'pack': {'pack_format': 46, 'description': '§6Classic Prison §7- 1.21.4 items & armor'}},
           'pack.mcmeta')
logo = Image.new('RGBA', (128, 128), (26, 34, 48, 255))
logo.alpha_composite(ICONS['agamemnon']['chestplate'].resize((64, 64), Image.NEAREST), (0, 0))
logo.alpha_composite(ICONS['winter']['chestplate'].resize((64, 64), Image.NEAREST), (64, 0))
logo.alpha_composite(ICONS['thornheart']['chestplate'].resize((64, 64), Image.NEAREST), (0, 64))
logo.alpha_composite(ICONS['tidecaller']['chestplate'].resize((64, 64), Image.NEAREST), (64, 64))
logo.save(p('pack.png'))

zpath = OUT + '.zip'
if os.path.exists(zpath):
    os.remove(zpath)
with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(OUT):
        for f in sorted(files):
            full = os.path.join(root, f)
            z.write(full, os.path.relpath(full, OUT))
sha1 = hashlib.sha1(open(zpath, 'rb').read()).hexdigest()
open(OUT + '.sha1', 'w').write(sha1 + '\n')
print('built', zpath, sha1)
