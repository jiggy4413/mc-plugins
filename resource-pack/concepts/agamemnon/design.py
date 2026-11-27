# "Warlord" chainmail set: black steel + gold, crested Greek-style helm (eyes only). Drawn at 2x (128x64).
from PIL import Image
S=2
PAL={1:(54,64,86),2:(32,38,54),3:(230,172,64),4:(170,112,36),5:(10,12,18),6:(255,214,120),
     7:(84,98,126),8:(255,226,146),9:(20,24,36),10:(62,74,100)}
def new(): return [[0]*(64*S) for _ in range(32*S)]
L1=new(); L2=new()
def P(g,x,y,r):
    if 0<=x<64*S and 0<=y<32*S: g[y][x]=r
def R(g,x,y,w,h,r):
    for yy in range(y,y+h):
        for xx in range(x,x+w): P(g,xx,yy,r)
def hline(g,x0,x1,y,r): R(g,x0,y,x1-x0,1,r)
def vline(g,x,y0,y1,r): R(g,x,y0,1,y1-y0,r)
def steel(g,x0,y0,w,h,seed=0):
    """dark steel with blocky highlight/shadow patches like brushed plates"""
    for y in range(h):
        for x in range(w):
            r=1
            v=((x//2)*7+(y//2)*13+seed*5)%11
            if v==0 and y<h*0.6: r=7
            elif v==5 or y>=h*0.72: r=2
            P(g,x0+x,y0+y,r)
def gold(g,x,y,w,h=2):
    R(g,x,y,w,1,3); 
    if h>1: R(g,x,y+1,w,h-1,4)
    P(g,x,y,8)
def crest(g,x,y,w,h):
    for yy in range(h):
        for xx in range(w): P(g,x+xx,y+yy,10 if (xx+yy//3)%3==0 else 9)

# ================= HELMET (faces 16x16): top 16,0  right 0,16  front 16,16  left 32,16  back 48,16
steel(L1,16,0,16,16,1); crest(L1,21,0,6,16); vline(L1,20,0,16,3); vline(L1,27,0,16,3)   # crest along the top, gold-edged
for i,(sx,sy) in enumerate(((0,16),(32,16),(48,16),(16,16))): steel(L1,sx,sy,16,16,i+2)
fx,fy=16,16
crest(L1,fx+5,fy,6,3); gold(L1,fx+4,fy+3,8,2)                       # crest end + gold crest base
gold(L1,fx,fy+5,16,2)                                               # brow band
R(L1,fx+1,fy+8,14,3,0)                                              # eye slit: open, the face shows
R(L1,fx+5,fy+11,6,5,0)                                              # mouth/chin opening
for x in range(fx+1,fx+15): P(L1,x,fy+7,4)                          # gold frame around the opening
for y in range(fy+8,fy+11): P(L1,fx,y,3); P(L1,fx+15,y,3)
for y in range(fy+11,fy+16): P(L1,fx+4,y,3); P(L1,fx+11,y,3)
for x0 in (fx,fx+12): steel(L1,x0,fy+11,4,5,7)                      # cheek guards
R(L1,fx,fy+15,4,1,3); R(L1,fx+12,fy+15,4,1,3)
for sx,sy in ((0,16),(32,16)):                                      # sides
    crest(L1,sx+4,sy,8,2); gold(L1,sx,sy+5,16,2); hline(L1,sx,sx+16,sy+7,5)
    R(L1,sx+5,sy+9,6,5,3); R(L1,sx+6,sy+10,4,3,5); R(L1,sx+7,sy+11,2,1,2)   # gold ear guard
    R(L1,sx,sy+15,16,1,3)
bx,by=48,16
crest(L1,bx+5,by,6,10); vline(L1,bx+4,by,by+10,3); vline(L1,bx+11,by,by+10,3)
gold(L1,bx,by+5,4,2); gold(L1,bx+12,by+5,4,2)
for y in (by+11,by+13): hline(L1,bx,bx+16,y,5)                      # neck guard ridges
R(L1,bx,by+15,16,1,3)

# ================= CHESTPLATE: front 40,40 (16x24) back 64,40 sides 32,40 & 56,40 top 40,32
steel(L1,40,32,16,8,9); R(L1,40,32,16,1,3); R(L1,40,39,16,1,4)
for x0 in (40,64): steel(L1,x0,40,16,24,10+x0)
for sx in (32,56): steel(L1,sx,40,8,24,sx)
cx,cy=40,40
gold(L1,cx,cy,16,2)                                                 # neck rim
for i in range(4): P(L1,cx+3+i,cy+2+i,3); P(L1,cx+12-i,cy+2+i,3)     # gold V collar
for x in range(cx+1,cx+7): P(L1,x,cy+10-(1 if x in (cx+1,cx+6) else 0),3)   # pec outlines
for x in range(cx+9,cx+15): P(L1,x,cy+10-(1 if x in (cx+9,cx+14) else 0),3)
vline(L1,cx+7,cy+4,cy+18,5); vline(L1,cx+8,cy+4,cy+18,7)
R(L1,cx+6,cy+2,4,2,3); P(L1,cx+7,cy+2,8)                            # gold clasp at the neck
for yy in (13,16): hline(L1,cx+2,cx+7,cy+yy,2); hline(L1,cx+9,cx+14,cy+yy,2)   # sculpted abs
for x0 in (40,64,32,56):
    w=16 if x0 in (40,64) else 8
    gold(L1,x0,cy+19,w,2)                                           # belt
    R(L1,x0,cy+21,w,3,2)
    for i in range(1,w,3): vline(L1,x0+i,cy+21,cy+24,5)
R(L1,cx+7,cy+19,2,2,8)
vline(L1,71,42,59,5); vline(L1,72,42,59,7)                          # back spine
gold(L1,64,40,16,2)

# ================= ARMS: faces 80,88,96,104 at y40 (8x24), top 88,32
steel(L1,88,32,8,8,3); R(L1,88,32,8,1,3)
for ax in (80,88,96,104):
    steel(L1,ax,40,8,8,ax); gold(L1,ax,40,8,1); gold(L1,ax,44,8,1); gold(L1,ax,47,8,1)   # big layered pauldron
    gold(L1,ax,56,8,2)                                              # armband; the rest of the arm is bare skin
    R(L1,ax,58,8,2,9); gold(L1,ax,60,8,2)                           # leather + gold bracer on the wrist
    for y in range(48,56): pass

# ================= BOOTS (layer 1 legs 0,8,16,24 at y40, lower part y52..63)
for lx in (0,8,16,24):
    steel(L1,lx,52,8,12,lx+1)
    gold(L1,lx,52,8,2)
    gold(L1,lx,56,8,2); R(L1,lx+2,56,1,2,5); R(L1,lx+5,56,1,2,5)
    gold(L1,lx,62,8,2)

# ================= LEGGINGS (layer 2): strip skirt on body faces + legs
for x0,w in ((32,8),(40,16),(56,8),(64,16)):
    R(L2,x0,54,w,2,4); hline(L2,x0,x0+w,54,3)
    for i in range(w):
        R(L2,x0+i,56,1,8,(1 if (i//2)%2 else 2)); P(L2,x0+i,63,3 if (i//2)%2 else 4)
    for i in range(1,w,2): vline(L2,x0+i,56,63,5) if i%4==3 else None
for lx in (0,8,16,24):
    for i in range(8):
        R(L2,lx+i,40,1,12,(1 if (i//2)%2 else 2))                   # long strips down to the knee
        P(L2,lx+i,51,3 if (i//2)%2 else 4)
    for i in (1,3,5,7): vline(L2,lx+i,40,51,5) if i in (3,7) else None
    R(L2,lx,52,8,12,0)                                              # bare lower leg (boots cover it)

for name,g in (('chainmail_layer_1.png',L1),('chainmail_layer_2.png',L2)):
    im=Image.new('RGBA',(64*S,32*S),(0,0,0,0)); p=im.load()
    for y in range(32*S):
        for x in range(64*S):
            if g[y][x]: p[x,y]=PAL[g[y][x]]+(255,)
    im.save(name)
