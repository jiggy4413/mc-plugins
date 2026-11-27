from render import *
sheet=Image.new('RGBA',(1400,760),(26,34,48,255)); dr=ImageDraw.Draw(sheet)
f=ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf',28)
fs=ImageFont.truetype('/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf',17)
dr.text((30,15),'Warlord with a 3D crested helmet',fill=(240,240,240),font=f)
P=player()
for i,(yaw,pitch,lab) in enumerate(((-30,-12,'front'),(150,-12,'back'),(-90,-8,'side'))):
    im=render(P,yaw,pitch,15,(330,640)); sheet.alpha_composite(im,(20+i*330,60))
    tw=dr.textlength(lab,font=fs); dr.text((20+i*330+165-tw/2,705),lab,fill=(210,210,210),font=fs)
hq=helmet3d(0,-24,0)
im=render(hq,-55,-18,20,(380,460)); sheet.alpha_composite(im,(1010,110))
dr.text((1060,600),'helmet in inventory',fill=(210,210,210),font=fs)
dr.text((1060,625),'(3D model icon)',fill=(160,160,160),font=fs)
sheet.convert('RGB').save('warlord-3d-preview.png')
