"""Tiny PNG decoder (8-bit RGBA/RGB/palette, non-interlaced) + analysis helpers."""
import struct, zlib, sys

def decode(path):
    d = open(path, 'rb').read()
    pos, idat, plte, trns = 8, b'', None, None
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]; t = d[pos+4:pos+8]; c = d[pos+8:pos+8+ln]; pos += 12 + ln
        if t == b'IHDR': w, h, depth, ct, _, _, il = struct.unpack('>IIBBBBB', c)
        elif t == b'IDAT': idat += c
        elif t == b'PLTE': plte = c
        elif t == b'tRNS': trns = c
    assert depth == 8 and il == 0, (path, depth, il)
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    raw = zlib.decompress(idat); stride = w * ch; rows = []; prev = bytearray(stride); p = 0
    for y in range(h):
        f = raw[p]; line = bytearray(raw[p+1:p+1+stride]); p += 1 + stride
        for x in range(stride):
            a = line[x-ch] if x >= ch else 0; b = prev[x]; c = prev[x-ch] if x >= ch else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(bytes(line)); prev = line
    px = []
    for r in rows:
        row = []
        for x in range(w):
            v = r[x*ch:(x+1)*ch]
            if ct == 6: row.append(tuple(v))
            elif ct == 2: row.append(tuple(v) + (255,))
            elif ct == 3:
                i = v[0]; rgb = tuple(plte[i*3:i*3+3]); al = trns[i] if trns and i < len(trns) else 255
                row.append(rgb + (al,))
            elif ct == 4: row.append((v[0],)*3 + (v[1],))
            else: row.append((v[0],)*3 + (255,))
        px.append(row)
    return w, h, px

if __name__ == '__main__':
    for f in sys.argv[1:]:
        w, h, px = decode(f)
        dup = sum(px[2*y] == px[2*y+1] for y in range(h // 2))
        opaque_bottom = sum(1 for y in range(h // 2, h) for x in range(w) if px[y][x][3] > 0)
        print(f.split('textures/entity/')[-1], w, h, 'row-pairs identical:', dup, '/', h // 2, 'opaque px in bottom half:', opaque_bottom)
