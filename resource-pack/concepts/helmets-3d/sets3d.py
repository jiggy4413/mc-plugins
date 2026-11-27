import render as R
from render import *
def tint(b,o,hexc):
    r,g,bb=int(hexc[1:3],16),int(hexc[3:5],16),int(hexc[5:7],16)
    b=Image.open(b).convert('RGBA'); o=Image.open(o).convert('RGBA')
    out=Image.new('RGBA',b.size); bp=b.load(); op=o.load(); p=out.load()
    for y in range(b.size[1]):
        for x in range(b.size[0]):
            if bp[x,y][3]: v=bp[x,y][0]; p[x,y]=(v*r//255,v*g//255,v*bb//255,255)
            if op[x,y][3]: p[x,y]=op[x,y]
    return out
def plate_x(x0,y0,z0,thick,h,d,mask):
    """thin panel in the y-z plane (side wings). mask(u,v) on the side face, u along z (back->front for right side)"""
    def fn(face,u,v,fw,fh):
        if face in ('left','right'):
            uu=u if face=='right' else fw-1-u
            return mask(uu,v,fw,fh)
        return None
    return solid(x0,y0,z0,thick,h,d,fn)
def plate_z(x0,y0,z0,w,h,thick,mask):
    def fn(face,u,v,fw,fh):
        if face in ('front','back'):
            uu=u if face=='front' else fw-1-u
            return mask(uu,v,fw,fh)
        return None
    return solid(x0,y0,z0,w,h,thick,fn)

def winter_parts():
    Q=[]; ICE=(191,232,255,255); ICES=(127,196,238,255); WH=(250,253,255,255)
    def spike(face,u,v,fw,fh):
        if v<2: return WH
        return ICES if face in ('left','back') else ICE
    # crown of ice spikes around the brow (front and sides), tallest in the middle
    for x,h in ((-4.6,3.5),(-3.0,5.0),(-1.0,7.0),(1.0,7.0),(2.5,5.0),(3.9,3.5)):
        Q+=solid(x-0.1,32.6,-4.8,1.0 if abs(x)>2 else 1.5,h,1.0,spike)
    for z,h in ((-2.5,4.0),(0.0,3.0),(2.5,2.0)):
        Q+=solid(-5.0,32.6,z,1.0,h,1.0,spike); Q+=solid(4.0,32.6,z,1.0,h,1.0,spike)
    Q+=solid(-4.8,32.0,-4.8,9.6,0.8,9.6,lambda f,u,v,fw,fh:(WH if f=='top' and (u+v)%3==0 else ICE))   # ice band
    # snowflake on the forehead
    sf=['..#.#..','...#...','#.###.#','.#####.','#.###.#','...#...','..#.#..']
    Q+=plate_z(-1.75,28.6,-5.25,3.5,3.5,0.25,lambda u,v,fw,fh: WH if sf[v*7//fh][u*7//fw]=='#' else None)
    return Q
def valentines_parts():
    Q=[]; WH=(252,248,250,255); PK=(255,143,189,255); PKD=(214,84,140,255); RED=(226,52,92,255)
    wing=['..........######',
          '.......#########',
          '.....###########',
          '...#############',
          '..##########ppp.',
          '.#########pppp..',
          '#######ppppp....',
          '#####pppp.......',
          '###ppp..........',
          '#pp.............']
    def wm(u,v,fw,fh):
        c=wing[v*len(wing)//fh][(fw-1-u)*16//fw]
        return {'#':WH,'p':PK}.get(c)
    for x in (-6.0,5.5):
        Q+=plate_x(x,29.5,-0.5,0.5,6.5,10.0,wm)            # feathered wings sweeping back and up
    heart=['.##.##.','#######','#######','.#####.','..###..','...#...']
    Q+=plate_z(-2.5,33.0,-0.5,5.0,4.2,1.0,lambda u,v,fw,fh:(RED if heart[v*6//fh][u*7//fw]=='#' else None))
    Q+=solid(-0.5,32.6,-0.4,1.0,0.6,0.8,lambda *a:PKD)
    return Q
def summer_parts():
    Q=[]; G=(240,184,40,255); GL=(255,226,120,255); GD=(200,130,20,255)
    import math
    def sun(u,v,fw,fh):
        x=(u+0.5)/fw*2-1; y=(v+0.5)/fh*2-1; r=math.hypot(x,y); a=math.atan2(y,x)
        if r<0.42: return None
        if r<0.52: return GD
        ray=(math.cos(a*12)>0.35)
        if r<0.62: return G
        if ray and r<0.98: return GL if r<0.8 else G
        return None
    Q+=plate_z(-8.0,20.5,4.7,16.0,16.0,0.5,sun)              # sunburst halo behind the head
    disc=['.###.','#####','##o##','#####','.###.']
    Q+=plate_z(-1.25,28.8,-5.25,2.5,2.5,0.25,lambda u,v,fw,fh:{'#':G,'o':GL}.get(disc[v*5//fh][u*5//fw]))
    return Q

def nova_player(L1,L2,parts):
    R.L1,R.L2=L1,L2
    Q=player(with_helmet3d=False)
    Q+=texbox(L1,0,0,-4,24,-4,8,8,8,1.1)
    return Q+parts
SETS=[('Warlord',None,None),('Winter Nova','#A8DCFF',winter_parts),('Valentines Nova','#FF8FBD',valentines_parts),('Summer Nova','#3A9E3F',summer_parts)]
sheet=Image.new('RGBA',(1600,1050),(26,34,48,255)); dr=ImageDraw.Draw(sheet)
f=ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf',26)
fs=ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf',16)
for i,(name,dye,parts) in enumerate(SETS):
    x0=i*400
    if dye is None:
        R.L1=Image.open('chainmail_layer_1.png').convert('RGBA'); R.L2=Image.open('chainmail_layer_2.png').convert('RGBA')
        Q=player(); hq=helmet3d(0,-24,0)
    else:
        L1=tint('leather_layer_1.png','leather_layer_1_overlay.png',dye); L2=tint('leather_layer_2.png','leather_layer_2_overlay.png',dye)
        pq=parts(); Q=nova_player(L1,L2,pq)
        hq=texbox(L1,0,0,-4,0,-4,8,8,8,1.1)+[( [(p[0],p[1]-24,p[2]) for p in pts],c,fc) for pts,c,fc in pq]
    tw=dr.textlength(name,font=f); dr.text((x0+200-tw/2,15),name,fill=(240,240,240),font=f)
    sheet.alpha_composite(render(Q,-30,-12,12,(200,560)),(x0-5,55))
    sheet.alpha_composite(render(Q,145,-12,12,(200,560)),(x0+195,55))
    sheet.alpha_composite(render(hq,-40,-15,17,(400,360)),(x0,640))
dr.text((20,1010),'Top: worn, front and back.  Bottom: the helmet as its 3D inventory icon.',fill=(170,170,170),font=fs)
sheet.convert('RGB').save('helmets-3d-preview.png')
