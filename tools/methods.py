import struct,sys
b=open(sys.argv[1],'rb').read()
n=struct.unpack('>H',b[8:10])[0];cp=[None]*n;i,p=1,10
while i<n:
    t=b[p]
    if t==1:
        l=struct.unpack('>H',b[p+1:p+3])[0];cp[i]=b[p+3:p+3+l].decode('utf-8','replace');p+=3+l
    elif t in(3,4):p+=5
    elif t in(5,6):p+=9;i+=1
    elif t in(7,8,16,19,20):p+=3
    elif t in(9,10,11,12,17,18):p+=5
    elif t==15:p+=4
    i+=1
p+=6;ni=struct.unpack('>H',b[p:p+2])[0];p+=2+2*ni
def skip_members():
    global p
    c=struct.unpack('>H',b[p:p+2])[0];p+=2;res=[]
    for _ in range(c):
        acc,nm,ds,ac=struct.unpack('>HHHH',b[p:p+8]);p+=8;anns=[]
        for _ in range(ac):
            an,al=struct.unpack('>HI',b[p:p+6]);anns.append(cp[an]);p+=6+al
        res.append((hex(acc),cp[nm],cp[ds],anns))
    return res
skip_members()
for m in skip_members():print(m)
