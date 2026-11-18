"""Minimal Java .class parser: dumps class -> super, interfaces, referenced classes, string constants.
Usage: python -I cls.py <root_dir> <out.json>
"""
import json, os, struct, sys

def parse(path):
    with open(path, 'rb') as f:
        b = f.read()
    if b[:4] != b'\xca\xfe\xba\xbe':
        return None
    n = struct.unpack('>H', b[8:10])[0]
    cp = [None] * n
    i, p = 1, 10
    while i < n:
        t = b[p]
        if t == 1:
            l = struct.unpack('>H', b[p+1:p+3])[0]
            cp[i] = ('utf', b[p+3:p+3+l].decode('utf-8', 'replace')); p += 3 + l
        elif t in (3, 4):
            cp[i] = ('num',); p += 5
        elif t in (5, 6):
            cp[i] = ('num',); p += 9; i += 1
        elif t == 7:
            cp[i] = ('class', struct.unpack('>H', b[p+1:p+3])[0]); p += 3
        elif t == 8:
            cp[i] = ('str', struct.unpack('>H', b[p+1:p+3])[0]); p += 3
        elif t in (9, 10, 11, 12, 17, 18):
            cp[i] = ('ref', struct.unpack('>HH', b[p+1:p+5])); p += 5
        elif t == 15:
            p += 4
        elif t in (16, 19, 20):
            cp[i] = ('mod', struct.unpack('>H', b[p+1:p+3])[0]); p += 3
        else:
            raise ValueError(f'tag {t} in {path}')
        i += 1
    u = lambda k: cp[k][1]
    cname = lambda k: u(cp[k][1]) if k else None
    _, this, sup, ni = struct.unpack('>HHHH', b[p:p+8]); p += 8
    itf = [cname(struct.unpack('>H', b[p+2*j:p+2*j+2])[0]) for j in range(ni)]
    refs = sorted({u(e[1]) for e in cp if e and e[0] == 'class'})
    strs = sorted({u(e[1]) for e in cp if e and e[0] == 'str'})
    utfs = [e[1] for e in cp if e and e[0] == 'utf']
    return {'name': cname(this), 'super': cname(sup), 'itf': itf, 'refs': refs, 'strs': strs, 'utf': utfs}

root, out = sys.argv[1], sys.argv[2]
res = {}
for d, _, fs in os.walk(root):
    for fn in fs:
        if fn.endswith('.class'):
            r = parse(os.path.join(d, fn))
            if r:
                res[r['name']] = r
json.dump(res, open(out, 'w', encoding='utf-8'))
print(len(res))
