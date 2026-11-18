"""List the method/field references (in order) inside each method of a class file. Usage: calls.py <class> [method-name-filter]"""
import struct, sys

b = open(sys.argv[1], 'rb').read()
flt = sys.argv[2] if len(sys.argv) > 2 else None
n = struct.unpack('>H', b[8:10])[0]; cp = [None] * n; i, p = 1, 10
while i < n:
    t = b[p]
    if t == 1:
        l = struct.unpack('>H', b[p+1:p+3])[0]; cp[i] = ('u', b[p+3:p+3+l].decode('utf-8', 'replace')); p += 3 + l
    elif t == 3: cp[i] = ('v', struct.unpack('>i', b[p+1:p+5])[0]); p += 5
    elif t == 4: cp[i] = ('v', round(struct.unpack('>f', b[p+1:p+5])[0], 4)); p += 5
    elif t in (5, 6): cp[i] = ('n',); p += 9; i += 1
    elif t == 7: cp[i] = ('c', struct.unpack('>H', b[p+1:p+3])[0]); p += 3
    elif t == 8: cp[i] = ('s', struct.unpack('>H', b[p+1:p+3])[0]); p += 3
    elif t in (9, 10, 11, 12): cp[i] = ('r', t) + struct.unpack('>HH', b[p+1:p+5]); p += 5
    elif t == 18: cp[i] = ('indy',) + struct.unpack('>HH', b[p+1:p+5]); p += 5
    elif t == 17: cp[i] = ('n',); p += 5
    elif t == 15: cp[i] = ('n',); p += 4
    elif t in (16, 19, 20): cp[i] = ('n',); p += 3
    i += 1
u = lambda k: cp[k][1]
def ref(k):
    e = cp[k]
    if e[0] == 'r':
        cls = u(cp[e[2]][1]); nt = cp[e[3]]; return f"{cls.split('/')[-1]}.{u(nt[2])}{u(nt[3])}"
    if e[0] == 'indy':
        nt = cp[e[2]]; return f"indy {u(nt[2])}"
    if e[0] == 'c': return u(e[1])
    if e[0] == 's': return repr(u(e[1]))
    if e[0] == 'v': return str(e[1])
    return '?'
p += 6; ni = struct.unpack('>H', b[p:p+2])[0]; p += 2 + 2 * ni
def members(code):
    global p
    c = struct.unpack('>H', b[p:p+2])[0]; p += 2; out = []
    for _ in range(c):
        acc, nm, ds, ac = struct.unpack('>HHHH', b[p:p+8]); p += 8; body = None
        for _ in range(ac):
            an, al = struct.unpack('>HI', b[p:p+6])
            if code and u(an) == 'Code':
                cl = struct.unpack('>I', b[p+10:p+14])[0]; body = b[p+14:p+14+cl]
            p += 6 + al
        out.append((u(nm), u(ds), body))
    return out
members(False)
# opcode lengths (enough for linear scan; tableswitch/lookupswitch handled)
L = {}
for o in range(256): L[o] = 1
for o in (0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc): L[o] = 2
for o in (0x11, 0x13, 0x14, 0x84, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7) + tuple(range(0x99, 0xa9)): L[o] = 3
L[0xc5] = 4; L[0xb9] = 5; L[0xba] = 5; L[0xc8] = 5; L[0xc9] = 5
for name, desc, code in members(True):
    if not code or (flt and flt not in name): continue
    print(f"== {name}{desc}")
    q = 0
    while q < len(code):
        o = code[q]
        if o in (0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xbb, 0xc0, 0xc1, 0x12, 0x13):
            k = code[q+1] if o == 0x12 else struct.unpack('>H', code[q+1:q+3])[0]
            op = {0xb2: 'getstatic', 0xb3: 'putstatic', 0xb4: 'getfield', 0xb5: 'putfield', 0xb6: 'invokevirtual', 0xb7: 'invokespecial',
                  0xb8: 'invokestatic', 0xb9: 'invokeinterface', 0xba: 'invokedynamic', 0xbb: 'new', 0xc0: 'checkcast', 0xc1: 'instanceof', 0x12: 'ldc', 0x13: 'ldc_w'}[o]
            print(f"  {q:4d} {op:15s} {ref(k)}")
        if 0x02 <= o <= 0x08: print(f"  {q:4d} iconst          {o-3}")
        if 0x0b <= o <= 0x0d: print(f"  {q:4d} fconst          {o-0x0b}.0")
        if o == 0x10: print(f"  {q:4d} bipush          {struct.unpack('>b', code[q+1:q+2])[0]}")
        if o == 0x11: print(f"  {q:4d} sipush          {struct.unpack('>h', code[q+1:q+3])[0]}")
        if o == 0xaa:
            pad = (4 - (q + 1) % 4) % 4; d, lo, hi = struct.unpack('>iii', code[q+1+pad:q+13+pad]); q += 1 + pad + 12 + 4 * (hi - lo + 1); continue
        if o == 0xab:
            pad = (4 - (q + 1) % 4) % 4; d, np_ = struct.unpack('>ii', code[q+1+pad:q+9+pad]); q += 1 + pad + 8 + 8 * np_; continue
        if o == 0xc4:
            q += 6 if code[q+1] == 0x84 else 4; continue
        q += L[o]
