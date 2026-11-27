import math
from PIL import Image, ImageDraw, ImageFont
L1=Image.open('chainmail_layer_1.png').convert('RGBA'); L2=Image.open('chainmail_layer_2.png').convert('RGBA')
# helmet shell texture for the 3D version: same paint, but the flat crest on top is replaced by a gold strip
H=L1.copy(); hp=H.load()
STEEL=(54,64,86,255); GOLD=(230,172,64,255); GOLDS=(170,112,36,255)
for y in range(0,16):
    for x in range(16,32):
        if 20<=x<=27: hp[x,y]=GOLD if x in (20,27) or y%4 else GOLDS
for y in range(16,32):          # back face: crest strip -> gold seam
    for x in range(48,64):
        if 52<=x<=59 and y<26: hp[x,y]=STEEL if 54<=x<=57 else ((40,48,66,255))
        if x in (55,56) and y<26: hp[x,y]=GOLD
for y in range(16,19):          # front face top: crest end -> steel
    for x in range(21,27): hp[x,y]=STEEL

SK=(198,150,110,255); HAIR=(60,40,25,255); SHIRT=(0,160,160,255); PANTS=(60,60,190,255); SHOE=(70,70,70,255)
def skin(part,face,u,v,w,h):
    if part=='head':
        if face=='top': return HAIR
        if face=='front':
            if v<2: return HAIR
            if v==4 and u in (1,5): return (255,255,255,255)
            if v==4 and u in (2,6): return (70,50,140,255)
            if v==6 and 2<=u<=5: return (140,90,70,255)
            return SK
        return HAIR if v<3 or face=='back' else SK
    if part=='body': return SHIRT
    if part=='arm': return SK
    if part=='leg': return SHOE if v>=10 else PANTS

def box_quads(x0,y0,z0,w,h,d,inflate,colorfn):
    """quads for a Minecraft-style box; colorfn(face,u,v,fw,fh) -> rgba or None. texel grid w x h etc."""
    X0,Y0,Z0=x0-inflate,y0-inflate,z0-inflate; X1,Y1,Z1=x0+w+inflate,y0+h+inflate,z0+d+inflate
    sx,sy,sz=(X1-X0)/w,(Y1-Y0)/h,(Z1-Z0)/d
    Q=[]
    def add(face,fw,fh,pt):
        for u in range(fw):
            for v in range(fh):
                c=colorfn(face,u,v,fw,fh)
                if c and c[3]>0: Q.append((pt(u,v),c,face))
    # front (z=Z1): u -> +x, v -> -y
    add('front',w,h,lambda u,v:[(X0+u*sx,Y1-v*sy,Z1),(X0+(u+1)*sx,Y1-v*sy,Z1),(X0+(u+1)*sx,Y1-(v+1)*sy,Z1),(X0+u*sx,Y1-(v+1)*sy,Z1)])
    add('back',w,h,lambda u,v:[(X1-u*sx,Y1-v*sy,Z0),(X1-(u+1)*sx,Y1-v*sy,Z0),(X1-(u+1)*sx,Y1-(v+1)*sy,Z0),(X1-u*sx,Y1-(v+1)*sy,Z0)])
    # right (player's right = -x): u -> +z (back..front)
    add('right',d,h,lambda u,v:[(X0,Y1-v*sy,Z0+u*sz),(X0,Y1-v*sy,Z0+(u+1)*sz),(X0,Y1-(v+1)*sy,Z0+(u+1)*sz),(X0,Y1-(v+1)*sy,Z0+u*sz)])
    add('left',d,h,lambda u,v:[(X1,Y1-v*sy,Z1-u*sz),(X1,Y1-v*sy,Z1-(u+1)*sz),(X1,Y1-(v+1)*sy,Z1-(u+1)*sz),(X1,Y1-(v+1)*sy,Z1-u*sz)])
    add('top',w,d,lambda u,v:[(X0+u*sx,Y1,Z0+v*sz),(X0+(u+1)*sx,Y1,Z0+v*sz),(X0+(u+1)*sx,Y1,Z0+(v+1)*sz),(X0+u*sx,Y1,Z0+(v+1)*sz)])
    add('bottom',w,d,lambda u,v:[(X0+u*sx,Y0,Z1-v*sz),(X0+(u+1)*sx,Y0,Z1-v*sz),(X0+(u+1)*sx,Y0,Z1-(v+1)*sz),(X0+u*sx,Y0,Z1-(v+1)*sz)])
    return Q
def uvtex(tex,U,V,w,h,d,scale=2,mirror=False):
    px=tex.load()
    off={'top':(U+d,V),'bottom':(U+d+w,V),'right':(U,V+d),'front':(U+d,V+d),'left':(U+d+w,V+d),'back':(U+2*d+w,V+d)}
    def f(face,u,v,fw,fh):
        if mirror:
            face={'right':'left','left':'right'}.get(face,face); u=fw-1-u
        ou,ov=off[face]
        return px[int((ou+u/ (fw/ (fw)) )*scale) if False else int((ou+u)*scale), int((ov+v)*scale)]
    return f
def subdiv(q,n=2):
    return q
def texbox(tex,U,V,x0,y0,z0,w,h,d,inflate,mirror=False,scale=2):
    # render at texture resolution: 2 texels per unit
    W,Hh,D=w*scale,h*scale,d*scale
    px=tex.load()
    off={'top':(U+d,V),'bottom':(U+d+w,V),'right':(U,V+d),'front':(U+d,V+d),'left':(U+d+w,V+d),'back':(U+2*d+w,V+d)}
    def f(face,u,v,fw,fh):
        fc=face
        if mirror:
            fc={'right':'left','left':'right'}.get(face,face); u=fw-1-u
        ou,ov=off[fc]
        return px[ou*scale+u, ov*scale+v]
    Q=box_quads(x0,y0,z0,W,Hh,D,0,lambda *a:None)
    # build with scaled grid
    X0,Y0,Z0=x0-inflate,y0-inflate,z0-inflate
    Qs=box_quads_scaled(X0,Y0,Z0,w+2*inflate,h+2*inflate,d+2*inflate,W,Hh,D,f)
    return Qs
def box_quads_scaled(X0,Y0,Z0,LW,LH,LD,W,H_,D,f):
    X1,Y1,Z1=X0+LW,Y0+LH,Z0+LD; sx,sy,sz=LW/W,LH/H_,LD/D; Q=[]
    def add(face,fw,fh,pt):
        for u in range(fw):
            for v in range(fh):
                c=f(face,u,v,fw,fh)
                if c and c[3]>0: Q.append((pt(u,v),c,face))
    add('front',W,H_,lambda u,v:[(X0+u*sx,Y1-v*sy,Z1),(X0+(u+1)*sx,Y1-v*sy,Z1),(X0+(u+1)*sx,Y1-(v+1)*sy,Z1),(X0+u*sx,Y1-(v+1)*sy,Z1)])
    add('back',W,H_,lambda u,v:[(X1-u*sx,Y1-v*sy,Z0),(X1-(u+1)*sx,Y1-v*sy,Z0),(X1-(u+1)*sx,Y1-(v+1)*sy,Z0),(X1-u*sx,Y1-(v+1)*sy,Z0)])
    add('right',D,H_,lambda u,v:[(X0,Y1-v*sy,Z0+u*sz),(X0,Y1-v*sy,Z0+(u+1)*sz),(X0,Y1-(v+1)*sy,Z0+(u+1)*sz),(X0,Y1-(v+1)*sy,Z0+u*sz)])
    add('left',D,H_,lambda u,v:[(X1,Y1-v*sy,Z1-u*sz),(X1,Y1-v*sy,Z1-(u+1)*sz),(X1,Y1-(v+1)*sy,Z1-(u+1)*sz),(X1,Y1-(v+1)*sy,Z1-u*sz)])
    add('top',W,D,lambda u,v:[(X0+u*sx,Y1,Z0+v*sz),(X0+(u+1)*sx,Y1,Z0+v*sz),(X0+(u+1)*sx,Y1,Z0+(v+1)*sz),(X0+u*sx,Y1,Z0+(v+1)*sz)])
    add('bottom',W,D,lambda u,v:[(X0+u*sx,Y0,Z1-v*sz),(X0+(u+1)*sx,Y0,Z1-v*sz),(X0+(u+1)*sx,Y0,Z1-(v+1)*sz),(X0+u*sx,Y0,Z1-(v+1)*sz)])
    return Q
def solid(x0,y0,z0,w,h,d,fn):
    """fn(face,u,v) at 2 texels/unit. Parts are written with -z = front, so flip to the renderer's +z = front."""
    z0=-(z0+d)
    W,H_,D=max(1,int(w*2)),max(1,int(h*2)),max(1,int(d*2))
    return box_quads_scaled(x0,y0,z0,w,h,d,W,H_,D,lambda face,u,v,fw,fh:fn(face,u,v,fw,fh))

def player(with_helmet3d=True, flat_helmet=False):
    Q=[]
    sk=lambda part:(lambda face,u,v,fw,fh: skin(part,face,u,v,fw,fh))
    Q+=box_quads(-4,24,-4,8,8,8,0,sk('head'))
    Q+=box_quads(-4,12,-2,8,12,4,0,sk('body'))
    Q+=box_quads(-8,12,-2,4,12,4,0,sk('arm')); Q+=box_quads(4,12,-2,4,12,4,0,sk('arm'))
    Q+=box_quads(-4,0,-2,4,12,4,0,sk('leg')); Q+=box_quads(0,0,-2,4,12,4,0,sk('leg'))
    # leggings (layer 2, inflate .5)
    Q+=texbox(L2,16,16,-4,12,-2,8,12,4,0.5)
    Q+=texbox(L2,0,16,-4,0,-2,4,12,4,0.5); Q+=texbox(L2,0,16,0,0,-2,4,12,4,0.5,mirror=True)
    # chest, arms, boots (layer 1, inflate 1)
    Q+=texbox(L1,16,16,-4,12,-2,8,12,4,1.0)
    Q+=texbox(L1,40,16,-8,12,-2,4,12,4,1.0); Q+=texbox(L1,40,16,4,12,-2,4,12,4,1.0,mirror=True)
    Q+=texbox(L1,0,16,-4,0,-2,4,12,4,1.0); Q+=texbox(L1,0,16,0,0,-2,4,12,4,1.0,mirror=True)
    if flat_helmet: Q+=texbox(L1,0,0,-4,24,-4,8,8,8,1.0)
    if with_helmet3d: Q+=helmet3d()
    return Q
def helmet3d(dx=0,dy=0,dz=0):
    Q=texbox(H,0,0,-4+dx,24+dy,-4+dz,8,8,8,1.1)
    CR=(18,22,34,255); CRH=(66,78,104,255); CRM=(36,44,62,255)
    def crestc(face,u,v,fw,fh):
        if face in ('left','right'):
            if v>=fh-1: return CRM
            return CRH if u%3==0 and v<fh*0.5 else (CRM if u%3==1 else CR)
        if face=='top': return CRH if u==0 else CRM
        return CR
    def goldc(face,u,v,fw,fh):
        if face=='top': return (255,226,146,255) if u%2==0 else (230,172,64,255)
        return (170,112,36,255) if v==fh-1 or face=='bottom' else (230,172,64,255)
    Q+=solid(-1.5+dx,33.1+dy,-4.4+dz,3.0,1.0,8.8,goldc)                   # gold crest holder
    # bristle crest: rises over the front, peaks in the middle, sweeps down the back
    for z0,zl,y0,hh in ((-4.4,1.5,34.1,4.0),(-2.9,1.5,34.1,5.5),(-1.4,2.0,34.1,6.5),(0.6,1.5,34.1,6.0),
                        (2.1,1.5,34.1,5.0),(3.6,1.2,33.1,5.0),(4.8,1.2,30.5,6.0),(5.9,1.0,27.5,6.0)):
        Q+=solid(-1.0+dx,y0+dy,z0+dz,2.0,hh,zl,crestc)
    return Q

def render(Q,yaw,pitch,scale,size):
    cy,sy_=math.cos(math.radians(yaw)),math.sin(math.radians(yaw)); cp,sp=math.cos(math.radians(pitch)),math.sin(math.radians(pitch))
    def tr(p):
        x,y,z=p; x,z=x*cy+z*sy_,-x*sy_+z*cy
        y,z=y*cp-z*sp,y*sp+z*cp
        return x,y,z
    light={'top':1.0,'front':0.9,'back':0.75,'right':0.78,'left':0.78,'bottom':0.55}
    items=[]
    for pts,c,face in Q:
        t=[tr(p) for p in pts]
        # backface cull via normal z
        ax,ay=t[1][0]-t[0][0],t[1][1]-t[0][1]; bx,by=t[3][0]-t[0][0],t[3][1]-t[0][1]
        nz=ax*by-ay*bx
        if nz>=0: continue
        depth=sum(p[2] for p in t)/4
        items.append((depth,t,c,face))
    items.sort(key=lambda i:i[0])
    W,Hh=size; img=Image.new('RGBA',(W,Hh),(0,0,0,0)); d=ImageDraw.Draw(img)
    for depth,t,c,face in items:
        k=light[face]; col=(int(c[0]*k),int(c[1]*k),int(c[2]*k),255)
        poly=[(W/2+p[0]*scale, Hh*0.93-p[1]*scale) for p in t]
        d.polygon(poly,fill=col,outline=col)
    return img
