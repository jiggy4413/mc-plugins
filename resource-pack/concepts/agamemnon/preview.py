from PIL import Image, ImageDraw, ImageFont
S=2
def tint(b,o,hexc):
    r,g,bb=int(hexc[1:3],16),int(hexc[3:5],16),int(hexc[5:7],16)
    out=Image.new('RGBA',b.size); bp=b.load(); op=o.load(); p=out.load()
    for y in range(b.size[1]):
        for x in range(b.size[0]):
            if bp[x,y][3]: v=bp[x,y][0]; p[x,y]=(v*r//255,v*g//255,v*bb//255,255)
            if op[x,y][3]: p[x,y]=op[x,y]
    return out
def skin(back):
    s=Image.new('RGBA',(16*S,32*S)); d=ImageDraw.Draw(s)
    SK=(198,150,110,255); HAIR=(60,40,25,255)
    d.rectangle([4*S,0,12*S-1,8*S-1],fill=HAIR if back else SK)
    if not back:
        d.rectangle([4*S,0,12*S-1,2*S-1],fill=HAIR)
        for ex in (5,9):
            d.rectangle([ex*S,4*S,(ex+1)*S-1,5*S-1],fill=(255,255,255,255)); d.rectangle([(ex+1)*S,4*S,(ex+2)*S-1,5*S-1],fill=(70,50,140,255))
        d.rectangle([6*S,6*S,10*S-1,7*S-1],fill=(140,90,70,255))
    d.rectangle([4*S,8*S,12*S-1,20*S-1],fill=(0,170,170,255))
    for x in (0,12): d.rectangle([x*S,8*S,(x+4)*S-1,20*S-1],fill=SK)
    d.rectangle([4*S,20*S,12*S-1,32*S-1],fill=(60,60,190,255)); return s
def crop(t,b,m=False):
    f=t.crop(tuple(v*S for v in b)); return f.transpose(Image.FLIP_LEFT_RIGHT) if m else f
def fig(L1,L2,back):
    im=skin(back)
    lg=(12,20,16,32) if back else (4,20,8,32); wb=(32,20,40,32) if back else (20,20,28,32)
    ab=(52,20,56,32) if back else (44,20,48,32); hb=(24,8,32,16) if back else (8,8,16,16)
    for x,m in ((4,False),(8,True)): im.alpha_composite(crop(L2,lg,m),(x*S,20*S))
    im.alpha_composite(crop(L2,wb),(4*S,8*S))
    for x,m in ((4,False),(8,True)): im.alpha_composite(crop(L1,lg,m),(x*S,20*S))
    im.alpha_composite(crop(L1,wb),(4*S,8*S))
    for x,m in ((0,False),(12,True)): im.alpha_composite(crop(L1,ab,m),(x*S,8*S))
    im.alpha_composite(crop(L1,hb),(4*S,0)); return im
C1=Image.open('chainmail_layer_1.png'); C2=Image.open('chainmail_layer_2.png')
Z=10
sheet=Image.new('RGBA',(1180,700),(26,34,48,255)); d=ImageDraw.Draw(sheet)
f=ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf',28)
fs=ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf',17)
d.text((30,15),'Warlord (chainmail)   worn front / back',fill=(240,240,240),font=f)
for j,b in enumerate((False,True)):
    sheet.alpha_composite(fig(C1,C2,b).resize((16*Z,32*Z),Image.NEAREST),(40+j*(16*Z+40),70))
hd=C1.crop((16,16,32,32)).resize((16*14,16*14),Image.NEAREST); sheet.alpha_composite(hd,(420,80))
d.text((420,315),'helmet front (close-up)',fill=(200,200,200),font=fs)
for k,part in enumerate(['helmet','chestplate','leggings','boots']):
    ic=Image.open(f'warlord_{part}.png').resize((160,160),Image.NEAREST)
    sheet.alpha_composite(ic,(40+k*230,460))
    tw=d.textlength(part.capitalize(),font=fs); d.text((40+k*230+80-tw/2,630),part.capitalize(),fill=(230,230,230),font=fs)
sheet.convert('RGB').save('warlord-preview.png')
