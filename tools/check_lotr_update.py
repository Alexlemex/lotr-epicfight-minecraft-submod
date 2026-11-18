"""Check a (new) LOTR port jar against every LOTR internal this mod depends on.

Usage: python -I tools/check_lotr_update.py <lotr.jar>
Prints OK / MISSING per hook, plus candidates when an obfuscated class was renamed.
After a LOTR update: run this, fix what is MISSING (mixins in fix/src, ids in generate.py), bump the
`lotr` versionRange in generate.py (MODS_TOML), extract the new jar to ../lotr-Release, rebuild.
"""
import re, struct, sys, zipfile

jar = zipfile.ZipFile(sys.argv[1])
names = set(jar.namelist())
results = []


def check(ok, what):
    results.append(ok)
    print(("  OK       " if ok else "  MISSING  ") + what)


class ClassFile:
    """Minimal class-file reader: super class, fields, methods, member references used in each method."""

    def __init__(self, data):
        n = struct.unpack(">H", data[8:10])[0]
        cp, i, p = [None] * n, 1, 10
        while i < n:
            t = data[p]
            if t == 1:
                ln = struct.unpack(">H", data[p + 1:p + 3])[0]
                cp[i] = data[p + 3:p + 3 + ln].decode("utf-8", "replace"); p += 3 + ln
            elif t in (3, 4): p += 5
            elif t in (5, 6): p += 9; i += 1
            elif t in (7, 8, 16, 19, 20): cp[i] = ("ref", t, struct.unpack(">H", data[p + 1:p + 3])[0]); p += 3
            elif t in (9, 10, 11, 12, 17, 18): cp[i] = ("ref", t) + struct.unpack(">HH", data[p + 1:p + 5]); p += 5
            elif t == 15: p += 4
            else: raise ValueError(f"bad constant tag {t}")
            i += 1
        self.cp = cp
        _, this, sup = struct.unpack(">HHH", data[p:p + 6]); p += 6
        self.name, self.super = self.cls(this), self.cls(sup) if sup else None
        ni = struct.unpack(">H", data[p:p + 2])[0]; p += 2 + 2 * ni
        self.fields, p = self._members(data, p)
        self.methods, p = self._members(data, p)

    def cls(self, k):
        return self.cp[self.cp[k][2]]

    def member_ref(self, k):
        e = self.cp[k]
        nt = self.cp[e[3]]
        return f"{self.cls(e[2])}.{self.cp[nt[2]]}{self.cp[nt[3]]}"

    def _members(self, data, p):
        count = struct.unpack(">H", data[p:p + 2])[0]; p += 2
        out = {}
        for _ in range(count):
            _, ni, di, ac = struct.unpack(">HHHH", data[p:p + 8]); p += 8
            refs = []
            for _ in range(ac):
                an, ln = struct.unpack(">HI", data[p:p + 6])
                if self.cp[an] == "Code":
                    code_len = struct.unpack(">I", data[p + 10:p + 14])[0]
                    code = data[p + 14:p + 14 + code_len]
                    for j in range(len(code) - 2):  # cheap scan for invoke*/getfield operand indices
                        if code[j] in (0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9):
                            k = struct.unpack(">H", code[j + 1:j + 3])[0]
                            if 0 < k < len(self.cp) and isinstance(self.cp[k], tuple) and self.cp[k][1] in (9, 10, 11):
                                try:
                                    refs.append(self.member_ref(k))
                                except Exception:
                                    pass
                p += 6 + ln
            out.setdefault(self.cp[ni], []).append((self.cp[di], refs))
        return out, p

    def has_method(self, name, desc=None):
        return any(desc is None or d == desc for d, _ in self.methods.get(name, []))

    def method_refs(self, name, desc=None):
        return [r for d, refs in self.methods.get(name, []) if desc is None or d == desc for r in refs]


def load(cls):
    path = cls + ".class"
    return ClassFile(jar.read(path)) if path in names else None


def all_classes(prefix="lotr/"):
    for n in names:
        if n.startswith(prefix) and n.endswith(".class"):
            try:
                yield ClassFile(jar.read(n))
            except Exception:
                pass


toml = jar.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace") if "META-INF/neoforge.mods.toml" in names else ""
m = re.search(r'modId="lotr".*?version="([^"]+)"', toml, re.S)
print("LOTR version:", m.group(1) if m else "?", "(mod pins [0.47.3-preview] in generate.py MODS_TOML)")

print("\nNPC combat (NpcCombatGoalMixin, NpcMeleeHandoverMixin, stubs/lotr/modern/world/c.java)")
c = load("lotr/modern/world/c")
check(c is not None and c.super == "net/minecraft/world/entity/PathfinderMob", "lotr.modern.world.c = NPC class (extends PathfinderMob)")
g = load("lotr/modern/world/m")
check(g is not None and g.super == "net/minecraft/world/entity/ai/goal/Goal", "lotr.modern.world.m = NPC combat goal (extends Goal)")
if g:
    check(any(d == "Llotr/modern/world/c;" for d, _ in g.fields.get("acA", [])), "m.acA field of type c (@Shadow)")
    check(g.has_method("canUse", "()Z") and g.has_method("canContinueToUse", "()Z"), "m.canUse()Z / canContinueToUse()Z")
    check(any(r.startswith("lotr/modern/world/c.doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z") for r in g.method_refs("tick", "()V")),
          "m.tick() calls c.doHurtTarget(Entity)")
if not (c and g):
    print("    candidates (Goal subclasses holding a PathfinderMob subclass field):")
    for k in all_classes("lotr/modern/"):
        if k.super == "net/minecraft/world/entity/ai/goal/Goal" and k.has_method("tick", "()V"):
            for f, ds in k.fields.items():
                for d, _ in ds:
                    if d.startswith("Llotr/") and any(r.endswith("doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z") for r in k.method_refs("tick", "()V")):
                        print(f"      {k.name}  field {f} {d}")

print("\nOlog-hai (olog/TrollGeometry.java, TrollColliderMixin, TrollRangedGoalMixin, TrollWeaponRenderMixin)")
ag = load("lotr/modern/client/ag")
check(ag is not None and ag.has_method("t", "(F)Lnet/minecraft/client/model/geom/builders/LayerDefinition;"),
      "lotr.modern.client.ag.t(float) -> troll LayerDefinition")
if not (ag and ag.has_method("t", "(F)Lnet/minecraft/client/model/geom/builders/LayerDefinition;")):
    print("    candidates (static (F)LayerDefinition factories building a 'warhammer' part):")
    for k in all_classes("lotr/modern/client/"):
        for mname, ds in k.methods.items():
            for d, _ in ds:
                if d == "(F)Lnet/minecraft/client/model/geom/builders/LayerDefinition;":
                    raw = jar.read(k.name + ".class")
                    if b"warhammer" in raw:
                        print(f"      {k.name}.{mname}{d}")
check(load("lotr/modern/monster/Troll") is not None, "lotr.modern.monster.Troll")
check(any(k.name.startswith("lotr/modern/monster/Troll$") and k.super and "RangedAttackGoal" in k.super
          for k in all_classes("lotr/modern/monster/")), "a Troll$N RangedAttackGoal (rock throw)")
for tex in ["troll/weapons.png", "troll/stone.png", "troll/olog_hai_armor/0.png", "troll/olog_hai_armor/1.png", "troll/mirk_troll_armor/0.png"]:
    check(f"assets/lotr/textures/entity/{tex}" in names, f"texture lotr:textures/entity/{tex}")
raw_ag = jar.read("lotr/modern/client/ag.class") if ag else b""
for part in ["head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "warhammer"]:
    check(part.encode() in raw_ag, f"troll model part '{part}'")

print("\nClient fixes")
amb = load("lotr/modern/client/sound/Ambience")
check(amb is not None and amb.has_method("replace") and any("SoundInstance.getVolume" in r for r in amb.method_refs("replace")),
      "Ambience.replace reads SoundInstance.getVolume (AmbienceRainFixMixin; may be fixed upstream -> mixin harmless)")
cc = load("lotr/modern/client/CharacterClient")
check(cc is not None and cc.has_method("render", "(Lnet/neoforged/neoforge/client/event/RenderPlayerEvent$Pre;)V"),
      "CharacterClient.render(RenderPlayerEvent.Pre) (CharacterClientMixin)")
check(cc is not None and cc.has_method("arm", "(Lnet/neoforged/neoforge/client/event/RenderArmEvent;)V"),
      "CharacterClient.arm(RenderArmEvent) (CharacterClientMixin)")
check("lotr/modern/client/WizardHatLayer.class" in names, "WizardHatLayer (WizardHatMixin)")

print("\nData read by generate.py")
check("data/lotr/npc/profiles.json" in names, "data/lotr/npc/profiles.json (NPC list)")
check("assets/lotr/lang/en_us.json" in names, "assets/lotr/lang/en_us.json (item/entity ids)")
check(any(n.startswith("assets/lotr/textures/entity/npc/orc/") for n in names), "textures/entity/npc/orc/ (OrcSkinPadMixin)")
check(any(n.startswith("assets/lotr/textures/entity/faction_npc/") for n in names), "textures/entity/faction_npc/ (OrcSkinPadMixin)")
shire = jar.read("lotr/modern/shire/ShireContent.class") if "lotr/modern/shire/ShireContent.class" in names else b""
for eid in ["orc_npc", "uruk_npc", "human_npc", "elf_npc", "dwarf_npc"]:
    check(eid.encode() in shire, f"generic NPC '{eid}' (EXTRA_HUMANOIDS)")
check(b"olog_hai" in jar.read("lotr/modern/monster/Monsters.class") if "lotr/modern/monster/Monsters.class" in names else False,
      "entity lotr:olog_hai")

print(f"\n{sum(results)}/{len(results)} OK" + ("" if all(results) else "  -> fix the MISSING items before releasing"))
