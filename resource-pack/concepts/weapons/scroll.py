"""Soulbound Scroll icon (32x32): an open parchment scroll between two wooden rods, sealed with a big
glowing pink heart."""
from px import Sprite

def scroll():
    s = Sprite((52, 24, 40))
    s.ramp('paper', (255, 246, 222), (238, 218, 176), (196, 166, 118))
    s.ramp('rod', (176, 124, 80), (128, 84, 50), (88, 56, 32))
    s.ramp('gold', (255, 234, 150), (240, 196, 92), (190, 136, 46))
    s.ramp('seal', (255, 150, 200), (232, 70, 150), (164, 30, 104))
    s.poly([(7, 6), (25, 6), (25, 26), (7, 26)], 'paper')       # the sheet
    for y in (5, 26):                                           # wooden rods with gold caps
        s.line((5, y), (27, y), 1.6, 'rod')
        s.disc((4.5, y + .5), 1.8, 'gold'); s.disc((27.5, y + .5), 1.8, 'gold')
    for y in (10, 13):                                          # writing lines
        s.line((9.5, y), (22.5, y), .35, 'rod')
    for y in (22, 24):
        s.line((9.5, y), (22.5, y), .35, 'rod')
    heart = ["..SSS..SSS..",
             ".SssSSSSSSS.",
             "SssSSSSSSSSS",
             "SsSSSSSSSSSD",
             "SSSSSSSSSSSD",
             ".SSSSSSSSSD.",
             "..SSSSSSSD..",
             "...SSSSSD...",
             "....SSSD....",
             ".....SD....."]
    s.stamp(10, 12, heart, {'S': (232, 70, 150), 's': (255, 190, 222), 'D': (164, 30, 104)})
    return s
