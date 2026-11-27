from PIL import Image
PAL={1:(54,64,86),2:(32,38,54),3:(230,172,64),4:(170,112,36),5:(10,12,18),6:(255,214,120),7:(84,98,126),8:(255,226,146),9:(20,24,36),10:(62,74,100)}
def grid(): return [[0]*32 for _ in range(32)]
def P(g,x,y,r):
    if 0<=x<32 and 0<=y<32: g[y][x]=r
def R(g,x,y,w,h,r):
    for yy in range(y,y+h):
        for xx in range(x,x+w): P(g,xx,yy,r)
def save(g,name):
    # outline everything
    out=[row[:] for row in g]
    for y in range(32):
        for x in range(32):
            if not g[y][x] and any(0<=x+dx<32 and 0<=y+dy<32 and g[y+dy][x+dx] not in (0,5) for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): out[y][x]=5
    im=Image.new('RGBA',(32,32)); p=im.load()
    for y in range(32):
        for x in range(32):
            if out[y][x]: p[x,y]=PAL[out[y][x]]+(255,)
    im.save(name)
# helmet
g=grid()
for y in range(2,12):                                    # crest
    for x in range(10,22):
        if (x-16)**2/36+(y-12)**2/100<=1: P(g,x,y,10 if (x+y//2)%3==0 else 9)
R(g,11,11,10,2,3); R(g,11,12,10,1,4)                       # crest base
for y in range(13,30):                                    # dome + cheeks
    for x in range(6,26):
        if (x-15.5)**2/100+(y-21)**2/90<=1 or (y>=21 and 7<=x<=24): P(g,x,y,1 if x<16 else 2)
for y in range(13,30):
    for x in range(6,26):
        if g[y][x] and ((x//2)*7+(y//2)*13)%11==0 and y<22: g[y][x]=7
R(g,7,17,18,2,3); R(g,7,19,18,1,4)                       # brow band
R(g,8,20,16,3,5)                                         # T-shaped open face (dark inside)
R(g,12,23,8,6,5)
for x in range(8,24): pass
for y in range(20,23): P(g,7,y,3); P(g,24,y,3)
for y in range(23,29): P(g,11,y,3); P(g,20,y,3)
R(g,7,28,5,1,3); R(g,20,28,5,1,3)
save(g,'warlord_helmet.png')
# chestplate
g=grid()
R(g,4,6,24,6,1); R(g,9,6,14,22,1); R(g,4,12,5,5,2); R(g,23,12,5,5,2)
for y in range(6,28):
    for x in range(4,28):
        if g[y][x] and ((x//2)*7+(y//2)*13)%11==0: g[y][x]=7
        if g[y][x] and y>21: g[y][x]=2
R(g,4,6,6,2,3); R(g,22,6,6,2,3); R(g,4,10,5,1,3); R(g,23,10,5,1,3)   # gold shoulder edges
R(g,12,5,8,2,3); R(g,13,4,6,1,0)
for i in range(4): P(g,12+i,7+i,3); P(g,19-i,7+i,3)      # V collar
R(g,14,12,4,3,3); P(g,15,11,8); P(g,16,11,8); P(g,13,13,3); P(g,18,13,3)  # emblem
R(g,10,17,12,1,5); R(g,10,19,12,1,5)
R(g,9,22,14,2,3); R(g,9,23,14,1,4); R(g,15,22,2,2,8)    # belt
for x in range(9,23,3): R(g,x,24,2,4,2 if (x//3)%2 else 1); P(g,x,27,3)
save(g,'warlord_chestplate.png')
# leggings
g=grid()
R(g,7,4,18,3,3); R(g,7,6,18,1,4)
for x in range(7,25):
    R(g,x,7,1,15,1 if (x//2)%2 else 2); P(g,x,21,3 if (x//2)%2 else 4)
for x in range(7,25,4): R(g,x+3,7,1,14,5)
R(g,8,22,6,6,9); R(g,18,22,6,6,9)
save(g,'warlord_leggings.png')
# boots
g=grid()
for x0 in (5,18):
    R(g,x0,8,9,20,1)
    for y in range(8,28):
        for x in range(x0,x0+9):
            if ((x//2)*7+(y//2)*13)%11==0: g[y][x]=7
            if x==x0+8: g[y][x]=2
    R(g,x0,8,9,2,3); R(g,x0,9,9,1,4)
    R(g,x0,15,9,2,3); R(g,x0+2,15,1,2,5); R(g,x0+5,15,1,2,5)
    R(g,x0,25,9,2,3); R(g,x0,26,9,1,4)
    R(g,x0-1,27,11,2,2)
save(g,'warlord_boots.png')
