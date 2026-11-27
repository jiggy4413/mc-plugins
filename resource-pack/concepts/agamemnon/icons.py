# 32x32 icons as role maps (1 plate, 2 plate shade, 3 accent, 4 accent shade, 5 outline/dark, 6 glow)
import json, sys
from PIL import Image, ImageDraw
N=32
def poly_mask(polys):
    m=Image.new('L',(N,N),0); d=ImageDraw.Draw(m)
    for p in polys: d.polygon(p,fill=255)
    return m.load()
class Icon:
    def __init__(s,shape):
        s.g=[[0]*N for _ in range(N)]; s.m=poly_mask(shape)
        for y in range(N):
            for x in range(N):
                if s.m[x,y]: s.g[y][x]=1
        # shade the lower-right of the silhouette
        for y in range(N):
            for x in range(N):
                if s.g[y][x] and ((x+1<N and not s.m[x+1,y]) or (y+1<N and not s.m[x,y+1]) or y>20):
                    s.g[y][x]=2
    def fill(s,polys,r,shade=None):
        mm=poly_mask(polys)
        for y in range(N):
            for x in range(N):
                if mm[x,y] and s.m[x,y]:
                    s.g[y][x]=shade if (shade and (y+1>=N or not mm[x,y+1])) else r
    def px(s,pts,r):
        for x,y in pts: s.g[y][x]=r
ICONS={}
# HELMET: crest, V brow, eye slits, nose guard, cheek guards, swept fins
h=Icon([[(8,10),(12,5),(20,5),(24,10),(25,23),(21,27),(11,27),(7,23)],
        [(8,13),(2,5),(4,4),(11,9)],[(24,13),(30,5),(28,4),(21,9)]])
h.fill([[(2,5),(4,4),(11,9),(8,13)],[(30,5),(28,4),(21,9),(24,13)]],3,4)
h.fill([[(15,3),(17,3),(17,12),(15,12)]],3,4)
h.fill([[(8,11),(11,11),(15,15),(17,15),(21,11),(24,11),(24,13),(18,17),(14,17),(8,13)]],3,4)
h.px([(x,18) for x in range(9,15)]+[(x,18) for x in range(18,24)],5)
h.px([(10,18),(11,18),(12,18),(20,18),(21,18),(22,18)],6)
h.fill([[(15,17),(17,17),(17,24),(15,24)]],3,4)
h.fill([[(8,20),(12,20),(12,26),(9,26)],[(20,20),(24,20),(23,26),(20,26)]],2)
h.px([(x,26) for x in range(11,22)],4)
ICONS['helmet']=h
# CHESTPLATE: pauldrons, collar, two pecs with a ridge, segmented belly, belt gem, faulds
c=Icon([[(3,5),(11,4),(13,7),(19,7),(21,4),(29,5),(30,13),(25,14),(25,29),(7,29),(7,14),(2,13)]])
c.fill([[(2,5),(11,3),(12,9),(3,13)],[(30,5),(21,3),(20,9),(29,13)]],3,4)
c.fill([[(11,6),(21,6),(19,9),(13,9)]],3,4)
c.fill([[(9,10),(15,10),(15,16),(9,16)],[(17,10),(23,10),(23,16),(17,16)]],1)
c.px([(x,16) for x in range(9,24)],5); c.px([(16,y) for y in range(10,16)],2)
for y in (18,20,22):
    c.px([(x,y) for x in range(9,24)],1); c.px([(x,y+1) for x in range(9,24)],2)
c.fill([[(7,24),(25,24),(25,26),(7,26)]],3,4)
c.px([(15,24),(16,24),(15,25),(16,25)],6); c.px([(14,24),(17,24),(14,25),(17,25)],5)
for x in (8,13,18,23): c.px([(x,y) for y in range(27,29)],5)
ICONS['chestplate']=c
# LEGGINGS: belt, tassets, thighs, knee cops
l=Icon([[(7,4),(25,4),(25,9),(24,28),(18,28),(17,13),(15,13),(14,28),(8,28),(7,9)]])
l.fill([[(7,4),(25,4),(25,7),(7,7)]],3,4)
l.px([(x,8) for x in range(8,25,4)],5)
l.fill([[(8,16),(14,16),(14,21),(8,21)],[(18,16),(24,16),(24,21),(18,21)]],3,4)
l.px([(10,18),(11,18),(20,18),(21,18)],1)
l.px([(x,26) for x in list(range(8,15))+list(range(18,25))],4)
ICONS['leggings']=l
# BOOTS: cuffs, plates, toe caps, dark soles
b=Icon([[(5,7),(13,7),(13,19),(15,21),(15,27),(3,27),(3,21),(5,19)],
        [(19,7),(27,7),(27,19),(29,21),(29,27),(17,27),(17,21),(19,19)]])
b.fill([[(5,7),(13,7),(13,11),(5,11)],[(19,7),(27,7),(27,11),(19,11)]],3,4)
b.fill([[(9,21),(15,21),(15,25),(9,25)],[(23,21),(29,21),(29,25),(23,25)]],3,4)
b.px([(x,26) for x in list(range(3,16))+list(range(17,30))],5)
ICONS['boots']=b

def render(icon,pal,motif,part):
    im=Image.new('RGBA',(N,N)); p=im.load()
    for y in range(N):
        for x in range(N):
            r=icon.g[y][x]
            if r: p[x,y]=pal[r]
    # top-edge highlight on plates
    for y in range(N):
        for x in range(N):
            if icon.g[y][x] in (1,3) and (y==0 or not icon.g[y-1][x]): p[x,y]=pal['hi1' if icon.g[y][x]==1 else 'hi3']
    spot={'helmet':(16,8),'chestplate':(16,21),'leggings':(16,5),'boots':(9,15)}[part]
    cx,cy=spot
    if motif=='snowflake':
        for dx,dy in [(0,0),(1,0),(-1,0),(0,1),(0,-1),(2,2),(-2,-2),(2,-2),(-2,2)]: p[cx+dx,cy+dy]=(250,253,255,255)
        p[cx,cy]=(140,210,255,255)
        for y in range(N):
            for x in range(N):
                if p[x,y][3] and (y==0 or p[x,y-1][3]==0) and (x+y)%3==0: p[x,y]=(255,255,255,255)
    elif motif=='heart':
        for dx,dy in [(-2,-1),(-1,-2),(1,-2),(2,-1),(-2,0),(-1,0),(0,0),(1,0),(2,0),(-1,1),(0,1),(1,1),(0,2),(0,-1),(-1,-1),(1,-1)]:
            p[cx+dx,cy+dy]=(230,30,70,255)
        p[cx-1,cy-1]=(255,170,195,255)
    elif motif=='sun':
        for dx in range(-1,2):
            for dy in range(-1,2): p[cx+dx,cy+dy]=(255,225,60,255)
        for dx,dy in [(0,-3),(0,3),(-3,0),(3,0),(2,2),(-2,-2),(2,-2),(-2,2)]: p[cx+dx,cy+dy]=(255,160,20,255)
        p[cx,cy]=(255,250,200,255)
    # dark outline around the item
    src=im.copy(); s=src.load()
    for y in range(N):
        for x in range(N):
            if s[x,y][3]==0 and any(0<=x+dx<N and 0<=y+dy<N and s[x+dx,y+dy][3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))):
                p[x,y]=(12,10,16,255)
    return im
SETS={'warlord':{'pal':{1:(54,64,86),2:(32,38,54),3:(230,172,64),4:(170,112,36),5:(10,12,18),6:(255,214,120),'hi1':(84,98,126),'hi3':(255,226,146)},'motif':None}}
for name,cfg in SETS.items():
    pal={k:(v+(255,)) for k,v in cfg['pal'].items()}
    for part,icon in ICONS.items():
        fn=('chainmail_'+part) if name=='supernova' else (name+'_'+part)
        render(icon,pal,cfg['motif'],part).save(fn+'.png')
