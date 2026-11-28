import thornheart as T, cupid as C
from px import sheet
from PIL import Image, ImageDraw, ImageFont
import glob, sys
S = '/tmp/claude-0/-home-claude/d56c6e2b-0be7-5200-b38a-cd1da8a4abf2/scratchpad/'
rows = []
for M in (T, C):
    rows.append([M.sword().image(), M.axe().image(), M.spear().image(), M.scythe().image(),
                 M.bow().image(), M.bow(3).image(), M.shield().image()])
w = sheet(rows, scale=6)
blings = sheet([[T.backbling().image(), T.backbling_wings().image(), T.backbling_rose().image()],
                [C.backbling_wings().image(), C.backbling_quiver().image(), C.backbling_heart().image()]], scale=5)
fp = glob.glob('/usr/share/fonts/**/Poppins-Bold.ttf', recursive=True)[0]
f = ImageFont.truetype(fp, 26); g = ImageFont.truetype(fp, 18)
W = 200 + w.width + 30 + blings.width
H = 70 + max(w.height, blings.height)
out = Image.new('RGBA', (W, H), (28, 30, 40, 255)); d = ImageDraw.Draw(out)
out.alpha_composite(w, (200, 60)); out.alpha_composite(blings, (200 + w.width + 30, 60))
cell = 32 * 6 + 16
for i, c in enumerate(['Sword', 'Axe', 'Spear', 'Scythe', 'Bow', 'Bow (drawn)', 'Shield']):
    d.text((200 + 16 + i * cell + 96, 35), c, font=g, fill=(200, 205, 220), anchor='mm')
bc = 48 * 5 + 16
for i in range(3):
    d.text((200 + w.width + 30 + 16 + i * bc + 120, 35), 'Back bling %d' % (i + 1), font=g, fill=(200, 205, 220), anchor='mm')
for j, (n, col) in enumerate([('Thornheart', (240, 90, 110)), ('Cupid', (255, 160, 196))]):
    d.text((24, 60 + 16 + j * (bc) + 110), n, font=f, fill=col, anchor='lm')
out.save(S + 'valentines.png')
out.save('valentines-preview.png')
print(out.size)
