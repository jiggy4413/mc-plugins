import sys, importlib
from px import sheet
mods = sys.argv[1:] or ['thornheart']
rows = []
for m in mods:
    M = importlib.import_module(m)
    row = [M.sword().image(), M.axe().image(), M.bow().image(), M.bow(3).image(), M.scythe().image()]
    bb = M.backbling().image()
    row.append(bb.resize((32, 32)) if False else bb.crop((0, 0, 48, 48)))
    rows.append(row)
out = sheet(rows, scale=6)
out.save('/tmp/claude-0/-home-claude/d56c6e2b-0be7-5200-b38a-cd1da8a4abf2/scratchpad/wp.png')
