"""Software preview of an Epic Fight mesh+armature (as generated), optionally posed by an Epic Fight animation.
Reproduces Epic Fight's math (JsonAssetLoader + Armature): Blender->MC root correction, keyframe = inv(bipedLocal) * K,
joint global = parentGlobal * local * keyframe, skinning = global * inv(bindGlobal).
Usage: preview.py <mesh.json> <texture.png> <out.png> [<animation.json> <time>] [<weapon_mesh.json> <weapon_tex.png>]
"""
import json, math, struct, sys, zlib
sys.path.insert(0, __import__("os").path.dirname(__file__))
from png import decode

EF = str(__import__("pathlib").Path(__file__).resolve().parents[2]
         / "epicfight-1.21.1/epicfight-1.21.1/src/main/resources/assets/epicfight/animmodels")


def mul(a, b): return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]
def rows(t): return [t[0:4], t[4:8], t[8:12], t[12:16]]
def inv(m):
    r = [[m[j][i] for j in range(3)] for i in range(3)]
    t = [-sum(r[i][k] * m[k][3] for k in range(3)) for i in range(3)]
    return [r[0] + [t[0]], r[1] + [t[1]], r[2] + [t[2]], [0, 0, 0, 1]]
def xf(m, p): return tuple(sum(m[i][k] * (p[k] if k < 3 else 1) for k in range(4)) for i in range(3))
B2M = [[1, 0, 0, 0], [0, 0, 1, 0], [0, -1, 0, 0], [0, 0, 0, 1]]  # -90 deg about X: (x,y,z)->(x,z,-y)
I4 = [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def locals_of(arm):
    out = {}
    def walk(n, root):
        L = rows(n["transform"])
        if root: L = mul(B2M, L)
        out[n["name"]] = L
        for c in n.get("children", []): walk(c, False)
    walk(arm["hierarchy"][0], True)
    return out


def globals_of(arm, local, delta=None):
    g = {}
    def walk(n, parent):
        m = mul(parent, local[n["name"]])
        if delta and n["name"] in delta: m = mul(m, delta[n["name"]])
        g[n["name"]] = m
        for c in n.get("children", []): walk(c, m)
    walk(arm["hierarchy"][0], I4)
    return g


def quat_to_mat(x, y, z, w):
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]


def anim_deltas(anim_path, t, biped_local):
    a = json.load(open(anim_path))
    out, root = {}, True
    for j in a["animation"]:
        name = j["name"]
        times, mats = j["time"], j["transform"]
        if isinstance(times, str): times, mats = json.loads(times), json.loads(mats)
        k = min(range(len(times)), key=lambda i: abs(times[i] - t))
        K = rows(mats[k])
        if root: K = mul(B2M, K)
        if name in biped_local:
            out[name] = mul(inv(biped_local[name]), K)
        root = False
    return out


def mesh_tris(mesh_json, skin):
    v = mesh_json["vertices"]
    P, U, N = v["positions"]["array"], v["uvs"]["array"], v["normals"]["array"]
    vc, vi, W = v["vcounts"]["array"], v["vindices"]["array"], v["weights"]["array"]
    starts, s = [], 0
    for c in vc: starts.append(s); s += c
    tris = []
    for part in v["parts"].values():
        a = part["array"]
        for i in range(0, len(a), 9):
            tri = []
            for k in range(3):
                pi, ui, ni = a[i + k * 3: i + k * 3 + 3]
                p = xf(B2M, P[pi * 3: pi * 3 + 3])
                out = [0.0, 0.0, 0.0]
                for c in range(vc[pi]):
                    jid, wid = vi[(starts[pi] + c) * 2], vi[(starts[pi] + c) * 2 + 1]
                    q = xf(skin[jid], p)
                    out = [o + W[wid] * qq for o, qq in zip(out, q)]
                tri.append((tuple(out), (U[ui * 2], U[ui * 2 + 1])))
            tris.append(tri)
    return tris


def raster(img, zb, w, h, tris, tex, view, scale, ox, oy):
    tw, th, tp = tex
    for tri in tris:
        pts = []
        for (x, y, z), uv in tri:
            if view == "front": sx, sy, d = x, y, z        # looking at the face (front = -z)
            else: sx, sy, d = z, y, x                      # side view (right side)
            pts.append((ox + sx * scale, oy - sy * scale, d, uv))
        (x0, y0, d0, t0), (x1, y1, d1, t1), (x2, y2, d2, t2) = pts
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9: continue
        for py in range(max(0, int(min(y0, y1, y2))), min(h, int(max(y0, y1, y2)) + 1)):
            for px in range(max(0, int(min(x0, x1, x2))), min(w, int(max(x0, x1, x2)) + 1)):
                cx, cy = px + 0.5, py + 0.5
                l1 = ((x1 - cx) * (y2 - cy) - (x2 - cx) * (y1 - cy)) / area
                l2 = ((x2 - cx) * (y0 - cy) - (x0 - cx) * (y2 - cy)) / area
                l3 = 1 - l1 - l2
                if l1 < -1e-6 or l2 < -1e-6 or l3 < -1e-6: continue
                d = l1 * d0 + l2 * d1 + l3 * d2
                if d >= zb[py][px]: continue
                u = l1 * t0[0] + l2 * t1[0] + l3 * t2[0]; v = l1 * t0[1] + l2 * t1[1] + l3 * t2[1]
                c = tp[min(th - 1, max(0, int(v * th)))][min(tw - 1, max(0, int(u * tw)))]
                if c[3] < 10: continue
                zb[py][px] = d; img[py][px] = c[:3]


def write_png(path, img):
    h, w = len(img), len(img[0])
    raw = b"".join(b"\x00" + bytes(c for px in r for c in px) for r in img)
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def main():
    mesh = json.load(open(sys.argv[1])); tex = decode(sys.argv[2]); out = sys.argv[3]
    anim = sys.argv[4] if len(sys.argv) > 5 and sys.argv[4] != "-" else None
    t = float(sys.argv[5]) if anim else 0
    weapon = (json.load(open(sys.argv[6])), decode(sys.argv[7])) if len(sys.argv) > 7 else None
    arm = mesh["armature"]
    local = locals_of(arm)
    bind = globals_of(arm, local)
    biped_local = locals_of(json.load(open(EF + "/entity/biped.json"))["armature"])
    posed = globals_of(arm, local, anim_deltas(anim, t, biped_local) if anim else None)
    skin = {i: mul(posed[n], inv(bind[n])) for i, n in enumerate(arm["joints"])}
    W, H, S = 520, 360, 70
    img = [[(40, 44, 52)] * W for _ in range(H)]
    zb = [[1e9] * W for _ in range(H)]
    tris = mesh_tris(mesh, skin)
    for view, ox in (("front", 130), ("side", 390)):
        zb = [[1e9] * W for _ in range(H)]
        raster(img, zb, W, H, tris, tex, view, S, ox, 340)
        if weapon: raster(img, zb, W, H, mesh_tris(weapon[0], skin), weapon[1], view, S, ox, 340)
    for x in range(W): img[340][x] = (90, 90, 90)
    write_png(out, img)
    print("ok", out)


main()
