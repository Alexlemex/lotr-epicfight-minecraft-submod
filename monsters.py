"""Epic Fight meshes + armatures for LOTR monsters, converted from LOTR's own cube models.

Input: models/<model>.json, dumped from the LOTR LayerDefinition by tools/modeldump/ModelDump.java
       (exact cubes, UVs, part pivots; units = model pixels, Minecraft model space: y down, ground at y=24).
Output: the mob patch (data/lotr/epicfight_mobpatch/<id>.json). Since 0.14.0 the mesh+armature JSON is generated
        in game by the Java port (fix/src/lotr_epicfight/olog); build_reference_assets() keeps this Python version
        as the offline reference used to check the port.

Skeleton: same joints and same joint ORIENTATIONS as Epic Fight's biped (so every biped animation plays on it:
animation keyframes are stored relative to each joint's bind transform, see JsonAssetLoader.getTransformSheet),
but the joints are MOVED to the monster's own pivots (wide shoulders, long arms, hunched head).

Spaces: "EF" = Epic Fight model space (blocks, y up, feet at 0, front = -z) = (-x/16, (24-y)/16, z/16) of MC model space.
        Files store Blender space: (X, -Z, Y) of EF space (loader applies a -90 deg X rotation).
"""
import json, math
from pathlib import Path

HERE = Path(__file__).resolve().parent
EF_ASSETS = HERE.parent / "epicfight-1.21.1/epicfight-1.21.1/src/main/resources/assets/epicfight"


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_inv_rigid(m):
    """Inverse of a rotation+translation matrix."""
    r = [[m[j][i] for j in range(3)] for i in range(3)]
    t = [-sum(r[i][k] * m[k][3] for k in range(3)) for i in range(3)]
    return [r[0] + [t[0]], r[1] + [t[1]], r[2] + [t[2]], [0, 0, 0, 1]]


def ef_to_blender(p):
    x, y, z = p
    return (x, -z, y)


def mc_to_ef(p):
    x, y, z = p
    return (-x / 16.0, (24.0 - y) / 16.0, z / 16.0)


def mc_normal_to_ef(n):
    x, y, z = n
    return (-x, -y, z)


def rot_x(p, a):
    x, y, z = p
    c, s = math.cos(a), math.sin(a)
    return (x, y * c - z * s, y * s + z * c)


def load_biped_armature():
    return json.loads((EF_ASSETS / "animmodels/entity/biped.json").read_text(encoding="utf-8"))["armature"]


def build_armature(joint_pos_ef):
    """Biped hierarchy + orientations, joints moved to joint_pos_ef[name] (EF space). Returns armature JSON."""
    biped = load_biped_armature()

    def rows(t):
        return [t[0:4], t[4:8], t[8:12], t[12:16]]

    def convert(node, parent_g_old, parent_g_new):
        g_old = mat_mul(parent_g_old, rows(node["transform"]))  # global bind (Blender space)
        bx, by, bz = ef_to_blender(joint_pos_ef[node["name"]])
        g_new = [g_old[0][:3] + [bx], g_old[1][:3] + [by], g_old[2][:3] + [bz], [0, 0, 0, 1]]
        local = mat_mul(mat_inv_rigid(parent_g_new), g_new)
        out = {"name": node["name"], "transform": [round(v, 6) for r in local for v in r]}
        if node.get("children"):
            out["children"] = [convert(c, g_old, g_new) for c in node["children"]]
        return out

    ident = [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return {"joints": biped["joints"], "hierarchy": [convert(biped["hierarchy"][0], ident, ident)]}


class MeshBuilder:
    def __init__(self, joints):
        self.joint_id = {n: i for i, n in enumerate(joints)}
        self.pos, self.uv, self.nrm, self.vjoint = [], [], [], []
        self.index = {"pos": {}, "uv": {}, "nrm": {}}
        self.parts = {}

    def _idx(self, kind, key, store):
        d = self.index[kind]
        if key not in d:
            d[key] = len(store)
            store.append(key)
        return d[key]

    def add_quad(self, part, verts_ef, uvs, normal_ef, joints_per_vertex):
        """verts_ef: 4 EF-space points, uvs: 4 (u, v), joints_per_vertex: 4 joint names (weight 1.0 each)."""
        ids = []
        for p, uv, j in zip(verts_ef, uvs, joints_per_vertex):
            bp = tuple(round(c, 6) for c in ef_to_blender(p))
            pi = self._idx("pos", (bp, j), self.pos)
            if len(self.vjoint) < len(self.pos):
                self.vjoint.append(self.joint_id[j])
            ui = self._idx("uv", (round(uv[0], 6), round(uv[1], 6)), self.uv)
            ni = self._idx("nrm", tuple(round(c, 6) for c in ef_to_blender(normal_ef)), self.nrm)
            ids.append((pi, ui, ni))
        tris = self.parts.setdefault(part, [])
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            for k in (a, b, c):
                tris.extend(ids[k])

    def to_json(self):
        flat = lambda seq: [c for item in seq for c in item]
        positions = flat(p for p, _ in self.pos)
        return {
            "positions": {"stride": 3, "count": len(self.pos), "array": positions},
            "uvs": {"stride": 2, "count": len(self.uv), "array": flat(self.uv)},
            "normals": {"stride": 3, "count": len(self.nrm), "array": flat(self.nrm)},
            "vcounts": {"stride": 1, "count": len(self.pos), "array": [1] * len(self.pos)},
            "weights": {"stride": 1, "count": 1, "array": [1.0]},
            "vindices": {"stride": 1, "count": 2 * len(self.pos), "array": flat((j, 0) for j in self.vjoint)},
            "parts": {name: {"stride": 3, "count": len(t) // 3, "array": t} for name, t in self.parts.items()},
        }


def find_part(node, name):
    if node["name"] == name:
        return node
    for c in node["children"]:
        r = find_part(c, name)
        if r:
            return r
    return None


def add_model_part(mb, model, part_name, mesh_part, joint_for_vertex, extra_rot_x=0.0):
    """Adds a LOTR model part (all cubes) to the mesh. joint_for_vertex(cube_index, local_mc_point) -> joint name.
    extra_rot_x: rotation (radians) applied to the part's local geometry before its pivot (weapon held at -90 deg)."""
    part = find_part(model["root"], part_name)
    px, py, pz = part["pose"][:3]
    for ci, cube in enumerate(part["cubes"]):
        for poly in cube["polys"]:
            verts, uvs, joints = [], [], []
            for vx, vy, vz, u, v in poly["v"]:
                local = rot_x((vx, vy, vz), extra_rot_x)
                verts.append(mc_to_ef((local[0] + px, local[1] + py, local[2] + pz)))
                uvs.append((u, v))
                joints.append(joint_for_vertex(ci, (vx, vy, vz)))
            n = mc_normal_to_ef(rot_x(tuple(poly["n"]), extra_rot_x))
            mb.add_quad(mesh_part, verts, uvs, n, joints)


# ======================================================================================
# Trolls (lotr.modern.client.ag): head, body, arms (upper + forearm cubes), legs (thigh + shin cubes),
# weapons attached to the right arm with xRot = arm.xRot - 90 deg (setupAnim).
# ======================================================================================
def troll_joints():
    ef = lambda x, y, z: mc_to_ef((x, y, z))
    arm_x = 18.0  # arm cubes span x 12..24 from the body axis -> joint on the arm's centre line, like the biped
    leg_x = 6.5
    return {
        "Root": ef(0, 0, 0),
        "Thigh_R": ef(-leg_x, 0, 0), "Leg_R": ef(-leg_x, 12, 0), "Knee_R": ef(-leg_x, 12, 0),
        "Thigh_L": ef(leg_x, 0, 0), "Leg_L": ef(leg_x, 12, 0), "Knee_L": ef(leg_x, 12, 0),
        "Torso": ef(0, -1, 0), "Chest": ef(0, -14, 0), "Head": ef(0, -27, -6),
        "Shoulder_R": ef(0, -27, 0), "Shoulder_L": ef(0, -27, 0),
        "Arm_R": ef(-arm_x, -23, 0), "Elbow_R": ef(-arm_x, -14, 0), "Hand_R": ef(-arm_x, -14, 0), "Tool_R": ef(-arm_x, 3, -2),
        "Arm_L": ef(arm_x, -23, 0), "Elbow_L": ef(arm_x, -14, 0), "Hand_L": ef(arm_x, -14, 0), "Tool_L": ef(arm_x, 3, -2),
    }


def build_troll_mesh(model, weapon, legs_model=None):
    """Body mesh. With legs_model: armour mesh (LOTR TrollRenderer$a draws the 'shirt' model, t(1.0), for
    head/body/arms and the 'trousers' model, t(0.75), for the legs, both with the armour texture)."""
    armature = build_armature(troll_joints())
    mb = MeshBuilder(armature["joints"])
    legs = legs_model or model
    add_model_part(mb, model, "head", "head", lambda ci, p: "Head")
    # body: bottom vertices follow the waist (Torso), top vertices the Chest -> bends smoothly, arms stay attached
    add_model_part(mb, model, "body", "torso", lambda ci, p: "Chest" if p[1] < -14 else "Torso")
    add_model_part(mb, model, "right_arm", "rightArm", lambda ci, p: "Arm_R" if ci == 0 else "Hand_R")
    add_model_part(mb, model, "left_arm", "leftArm", lambda ci, p: "Arm_L" if ci == 0 else "Hand_L")
    add_model_part(mb, legs, "right_leg", "rightLeg", lambda ci, p: "Thigh_R" if ci == 0 else "Leg_R")
    add_model_part(mb, legs, "left_leg", "leftLeg", lambda ci, p: "Thigh_L" if ci == 0 else "Leg_L")
    return {"vertices": mb.to_json(), "armature": armature}


def build_troll_weapon_mesh(model, weapon):
    """Weapon drawn with LOTR's weapon texture: separate mesh on the same armature, rigid on the right hand."""
    armature = build_armature(troll_joints())
    mb = MeshBuilder(armature["joints"])
    parts = {"spiked_club": ["club", "club_spikes"], "warhammer": ["warhammer"], "battleaxe": ["battleaxe"]}[weapon]
    for p in parts:
        add_model_part(mb, model, p, p, lambda ci, pt: "Hand_R", extra_rot_x=-math.pi / 2)
    return {"vertices": mb.to_json()}


TROLLS = {  # entity id: (LOTR variant scale, weapon)  from lotr.modern.monster.Monsters (Troll$b)
    "olog_hai": (1.25, "warhammer"),
}


def build_reference_assets():
    """OFFLINE reference only (development): the same assets as the runtime Java generator
    (fix/src/lotr_epicfight/olog/OlogAssets.java), from model dumps in models/ (not distributed: LOTR-derived)."""
    model = json.loads((HERE / "models/troll.json").read_text(encoding="utf-8"))
    shirt = json.loads((HERE / "models/troll_1.0.json").read_text(encoding="utf-8"))     # TrollRenderer 'shirt' layer
    trousers = json.loads((HERE / "models/troll_0.75.json").read_text(encoding="utf-8"))  # 'trousers' layer
    files = {}
    for eid, (scale, weapon) in TROLLS.items():
        files[f"{eid}.json"] = build_troll_mesh(model, weapon)
        files[f"{eid}_weapon.json"] = build_troll_weapon_mesh(model, weapon)
        files[f"{eid}_armor.json"] = build_troll_mesh(shirt, weapon, legs_model=trousers)
    return files


def build_monsters(a, behaviors, stun, default_motions):
    """Mob patches only. The armature/meshes they name (lotr_epicfight:entity/<id>) are NOT shipped: they are built
    in game by OlogAssets (Epic Fight's biped armature + the installed LOTR troll model) and served by OlogAssetMixin."""
    files = {}
    for eid, (scale, weapon) in TROLLS.items():
        # One-handed heavy swings (illager axe), reach 1.5x (matches TrollColliderMixin area); long rest between series.
        series = []
        for ser in behaviors["VINDICATOR_ONEHAND"]:
            s = json.loads(json.dumps(ser))
            s["cooldown"] = max(s.get("cooldown", 0), 40)
            for b in s["behaviors"]:
                for c in b["conditions"]:
                    if c["predicate"] == "within_distance":
                        c["max"] = round(c["max"] * 1.5, 2)
                b["conditions"] = [c for c in b["conditions"] if c["predicate"] != "within_eye_height"]
            series.append(s)
        files[f"data/lotr/epicfight_mobpatch/{eid}.json"] = {
            "model": f"lotr_epicfight:entity/{eid}",
            "armature": f"lotr_epicfight:entity/{eid}",
            "renderer": "epicfight:custom",
            "isHumanoid": False,
            "faction": "neutral",
            "attributes": {"impact": 2.5, "armor_negation": 20.0, "max_strikes": 3, "chasing_speed": 1.0,
                           "scale": scale, "stun_armor": 12.0},
            "default_livingmotions": default_motions,
            "combat_behavior": series,
            "stun_animations": stun,
        }
    return files
