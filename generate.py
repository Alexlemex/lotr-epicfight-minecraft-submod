"""
LOTR (1.21.1 port) x Epic Fight compat generator.

Builds a NeoForge mod jar (javafml with an empty @Mod class, data + mixins) that contains:
  - data/lotr/capabilities/weapons/<item>.json   -> Epic Fight weapon movesets for LOTR weapons
  - data/lotr/epicfight_mobpatch/<entity>.json   -> Epic Fight humanoid AI/animations for LOTR NPCs
  - mixins (fix/)                                -> rendering/gameplay fixes; 64x32 orc skins are padded to 64x64
                                                     at load time by OrcSkinPadMixin (no LOTR texture is shipped)

Usage:  python -I generate.py
Edit the WEAPON_RULES / EXTRA_HUMANOIDS tables below to tweak mappings, then re-run.
"""
import json, os, re, struct, zipfile, zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
LOTR = ROOT / "lotr-Release"
EF_SRC = ROOT / "epicfight-1.21.1" / "epicfight-1.21.1" / "src" / "main" / "java" / "yesman" / "epicfight"
OUT_DIR = ROOT / "build"
MOD_ID = "lotr_epicfight"
VERSION = "1.0.0"
JAR = OUT_DIR / f"{MOD_ID}-{VERSION}.jar"

# --------------------------------------------------------------------------------------
# 1. Weapons: first matching rule wins. (regex on item path, epic fight type, attributes)
#    Attribute values copied from Epic Fight's own iron-tier weapons.
# --------------------------------------------------------------------------------------
A_SWORD = {"common": {"impact": 0.9, "max_strikes": 1}}
A_LONGSWORD = {"common": {"impact": 2.0, "max_strikes": 2}}
A_DAGGER = {"one_hand": {"armor_negation": 10.0, "max_strikes": 0, "impact": 0.7},
            "two_hand": {"armor_negation": 10.0, "max_strikes": 1, "impact": 0.7}}
A_SPEAR = {"one_hand": {"armor_negation": 12.0, "impact": 2.3, "max_strikes": 1},
           "two_hand": {"armor_negation": 0.0, "impact": 1.6, "max_strikes": 3}}
A_GREAT = {"common": {"impact": 3.6, "max_strikes": 4}}
A_AXE = {"common": {"armor_negation": 20.0, "impact": 1.3}}

SKIP = r"(spawn_egg|_parts_|^body_|^helmet_|^legs_|^boots_|horse_armor|elk_armor|crossbow_bolt|_dart|blowgun|^sling$|staff|balrog_whip|^mithril_(ingot|nugget|book|ring|mail)$|^raw_)"
WEAPON_RULES = [
    (r"^shield_.*$", "shield", None),
    (r"^(anduril|glamdring)$", "longsword", A_LONGSWORD),
    (r"^(dagger_.*|ancient_dagger|morgul_blade)$", "dagger", A_DAGGER),
    (r"^(sword_.*|scimitar_.*|ancient_sword|command_sword|sting)$", "sword", A_SWORD),
    (r"^(spear_.*|longspear_.*|pike_.*|polearm_.*|lance_.*|halberd_.*)$", "spear", A_SPEAR),
    (r"^(battleaxe_.*|hammer_.*|poleaxe_.*|sauron_mace)$", "greatsword", A_GREAT),
    (r"^(axe_.*|throwing_axe_.*|mace_.*|club_.*|dunlending_club|mattock_.*|blacksmith_hammer)$", "axe", A_AXE),
    (r"^(pickaxe_.*|utumno_pickaxe)$", "pickaxe", None),
    (r"^hoe_.*$", "hoe", None),
    (r"^shovel_.*$", "shovel", None),
    (r".*_crossbow$", "crossbow", None),
    (r".*_bow$", "bow", None),
    (r"^(.*_)?trident(_.*)?$", "trident", None),
]

# --------------------------------------------------------------------------------------
# 2. Humanoid NPCs: every entity in data/lotr/npc/profiles.json + these extras.
#    Monsters (trolls, wargs, spiders, ents, balrog...) and animals are deliberately left out.
# --------------------------------------------------------------------------------------
EXTRA_HUMANOIDS = ["gandalf", "saruman", "bandit", "bandit_harad", "ruffian_brute", "ruffian_spy",
                   # generic NPCs registered in lotr.modern.shire.ShireContent (class lotr.modern.world.c, no profile)
                   "orc_npc", "uruk_npc", "human_npc", "elf_npc", "dwarf_npc"]
# hitbox heights from ShireContent's EntityType builders (scale = height / 1.8)
EXTRA_HEIGHTS = {"orc_npc": 1.8, "uruk_npc": 1.9, "human_npc": 1.8, "elf_npc": 1.9, "dwarf_npc": 1.45}

def a(name):  # animation key
    return "epicfight:" + name

DEFAULT_MOTIONS = {
    "idle": a("biped/living/idle"), "walk": a("biped/living/walk"), "chase": a("biped/living/walk"),
    "run": a("biped/living/run"), "sneak": a("biped/living/sneak"), "swim": a("biped/living/swim"),
    "float": a("biped/living/float"), "fall": a("biped/living/fall"), "mount": a("biped/living/mount"),
    "death": a("biped/living/death"),
}
STUN = {
    "short": a("biped/combat/hit_short"), "long": a("biped/combat/hit_long"), "hold": a("biped/combat/hit_short"),
    "knockdown": a("biped/combat/knockdown"), "neutralize": a("biped/skill/guard_break1"),
    "fall": a("biped/living/landing"),
}
WEAPON_MOTIONS = [
    {"weapon_categories": ["greatsword"], "style": "two_hand", "livingmotions": {
        "idle": a("biped/living/hold_greatsword"), "walk": a("biped/living/walk_greatsword"),
        "chase": a("biped/living/walk_greatsword"), "run": a("biped/living/run_greatsword")}},
    {"weapon_categories": ["spear"], "style": "two_hand", "livingmotions": {
        "idle": a("biped/living/hold_spear"), "walk": a("biped/living/walk_spear"),
        "chase": a("biped/living/walk_spear"), "run": a("biped/living/run_spear")}},
    {"weapon_categories": ["longsword"], "style": "two_hand", "livingmotions": {
        "idle": a("biped/living/hold_longsword"), "walk": a("biped/living/walk_longsword"),
        "chase": a("biped/living/walk_longsword"), "run": a("biped/living/run_longsword")}},
]
# category -> {style: builder constant in MobCombatBehaviors.java} (same as Epic Fight's HumanoidMobPatch)
CATEGORY_BEHAVIORS = [
    # Epic Fight's own tool moveset (0.08s wind-up) is unreadable; use the Vindicator axe swings (telegraphed).
    (["axe", "hoe", "pickaxe", "shovel"], "common", "VINDICATOR_ONEHAND"),
    (["sword"], "one_hand", "HUMANOID_ONEHAND_TOOLS"),
    (["sword"], "two_hand", "HUMANOID_DUAL_SWORD"),
    (["greatsword"], "two_hand", "HUMANOID_GREATSWORD"),
    (["uchigatana"], "two_hand", "HUMANOID_KATANA"),
    (["longsword"], "two_hand", "HUMANOID_LONGSWORD"),
    (["longsword"], "one_hand", "HUMANOID_ONEHAND_TOOLS"),
    (["tachi"], "two_hand", "HUMANOID_TACHI"),
    (["spear"], "one_hand", "HUMANOID_SPEAR_ONEHAND"),
    (["spear"], "two_hand", "HUMANOID_SPEAR_TWOHAND"),
    (["fist"], "common", "HUMANOID_FIST"),
    (["dagger"], "one_hand", "HUMANOID_ONEHAND_DAGGER"),
    (["dagger"], "two_hand", "HUMANOID_TWOHAND_DAGGER"),
    (["trident"], "common", "HUMANOID_SPEAR_ONEHAND"),
]


# ======================================================================================
# Rest (ticks) after each NPC attack series. 0.13.3: only DAGGER NPCs are slowed (the user's original request:
# dagger combos chain too fast); every other weapon keeps Epic Fight's own rhythm (0 = EF default cooldowns).
MIN_SERIES_COOLDOWN = 25
DEFAULT_MIN_SERIES_COOLDOWN = 0
SLOWED_BEHAVIORS = {"HUMANOID_ONEHAND_DAGGER", "HUMANOID_TWOHAND_DAGGER"}

# Players get the Epic Fight "Roll" dodge on first join (skill books only come from vanilla loot, absent in Middle-earth).
GIVE_ROLL_ADVANCEMENT = {"criteria": {"join": {"trigger": "minecraft:tick"}},
                         "rewards": {"function": "lotr_epicfight:give_roll"}}
GIVE_ROLL_FUNCTION = "epicfight skill add @s dodge epicfight:roll\n"
ROLL_STAMINA_COST = 3.0  # Epic Fight default 4.0 (player max stamina 15)
GIVE_PARRY_ADVANCEMENT = {"criteria": {"join": {"trigger": "minecraft:tick"}},
                          "rewards": {"function": "lotr_epicfight:give_parry"}}
GIVE_PARRY_FUNCTION = "epicfight skill add @s guard epicfight:parrying\n"  # perfect parry (timed guard)
COMMON_MIXINS = {"NpcCombatGoalMixin", "OlogAssetMixin", "NpcMeleeHandoverMixin", "FasterRollMixin", "FoodHealMixin", "ExecutionMixin", "GrapplingKillMixin",
                 "FinisherKillMixin", "FinisherInvulnerabilityMixin", "FasterEatingMixin", "StaminaBonusMixin",
                 "EatingContextMixin", "UnbreakableItemsMixin", "SlowerDaggerMixin", "TrollColliderMixin", "TrollRangedGoalMixin"}  # server-side logic, must also load on dedicated servers


def load_animation_keys():
    src = (EF_SRC / "gameasset" / "Animations.java").read_text(encoding="utf-8")
    return dict(re.findall(r"^\s+([A-Z0-9_]+) = builder\.nextAccessor\(\"([a-z0-9_/]+)\"", src, re.M))


def load_behaviors(anim_keys, min_cooldown=MIN_SERIES_COOLDOWN):
    """Parse MobCombatBehaviors.java humanoid builders into Epic Fight datapack JSON."""
    src = (EF_SRC / "gameasset" / "MobCombatBehaviors.java").read_text(encoding="utf-8")
    out = {}
    for m in re.finditer(r"public static final CombatBehaviors\.Builder<[^>]*>+ (\w+) = (.*?);\s*\n", src, re.S):
        name, body = m.group(1), m.group(2)
        if not (name.startswith("HUMANOID_") or name == "VINDICATOR_ONEHAND"):
            continue
        series = []
        for s in body.split(".newBehaviorSeries(")[1:]:
            head = s.split(".nextBehavior(")[0]
            ser = {"weight": float(re.search(r"weight\(([\d.]+)F\)", head).group(1)),
                   "canBeInterrupted": "canBeInterrupted(true)" in head,
                   "looping": "looping(true)" in head}
            cd = re.search(r"cooldown\((\d+)\)", head)
            # Pause between combos so NPC attacks are slower and leave room to dodge (EF default: none).
            ser["cooldown"] = max(int(cd.group(1)) if cd else 0, min_cooldown)
            behaviors = []
            for b in s.split(".nextBehavior(")[1:]:
                anim = re.search(r"animationBehavior\(Animations\.(\w+)\)", b).group(1)
                conds = []
                for rc in re.findall(r"randomChance\(([\d.]+)F\)", b):
                    conds.append({"predicate": "random_chance", "chance": float(rc)})
                if "withinEyeHeight()" in b:
                    conds.append({"predicate": "within_eye_height"})
                for lo, hi in re.findall(r"withinDistance\(([\d.]+)D, ([\d.]+)D\)", b):
                    conds.append({"predicate": "within_distance", "min": float(lo), "max": float(hi)})
                behaviors.append({"animation": "epicfight:" + anim_keys[anim], "conditions": conds})
            ser["behaviors"] = behaviors
            series.append(ser)
        out[name] = series
    return out


def collect_items():
    lang = json.loads((LOTR / "assets/lotr/lang/en_us.json").read_text(encoding="utf-8"))
    ids = {k[len("item.lotr."):] for k in lang if k.startswith("item.lotr.") and k.count(".") == 2}
    models = {p.stem for p in (LOTR / "assets/lotr/models/item").glob("*.json")}
    profiles = json.loads((LOTR / "data/lotr/npc/profiles.json").read_text(encoding="utf-8"))
    ids |= {i.split(":")[1] for p in profiles for v in p["equipment"].values() for i, _ in v if i.startswith("lotr:")}
    return sorted(ids & models)


def build_weapons(items):
    files, report = {}, {}
    for item in items:
        if re.search(SKIP, item):
            continue
        for rx, typ, attrs in WEAPON_RULES:
            if re.match(rx, item):
                data = {"type": f"epicfight:{typ}"}
                if attrs:
                    data["attributes"] = attrs
                files[f"data/lotr/capabilities/weapons/{item}.json"] = data
                report.setdefault(typ, []).append(item)
                break
    return files, report


def png_size(path):
    b = path.open("rb").read(24)
    return struct.unpack(">II", b[16:24])


def pad_png_to_square(path):
    """Return PNG bytes with the image height doubled (new rows blank, filter 0). No decode needed."""
    data = path.read_bytes()
    pos, chunks = 8, []
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        chunks.append((typ, data[pos + 8:pos + 8 + ln]))
        pos += 12 + ln
    ihdr = chunks[0][1]
    w, h, depth, ctype, comp, filt, interlace = struct.unpack(">IIBBBBB", ihdr)
    if interlace:
        raise ValueError(f"interlaced png not supported: {path}")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    row_bytes = (w * channels * depth + 7) // 8
    raw = zlib.decompress(b"".join(c for t, c in chunks if t == b"IDAT"))
    raw += (b"\x00" + b"\x00" * row_bytes) * h
    new_ihdr = struct.pack(">IIBBBBB", w, h * 2, depth, ctype, comp, filt, interlace)

    def chunk(t, c):
        return struct.pack(">I", len(c)) + t + c + struct.pack(">I", zlib.crc32(t + c) & 0xFFFFFFFF)

    out = data[:8] + chunk(b"IHDR", new_ihdr)
    idat_done = False
    for t, c in chunks[1:]:
        if t == b"IDAT":
            if not idat_done:
                out += chunk(b"IDAT", zlib.compress(raw, 9))
                idat_done = True
        else:
            out += chunk(t, c)
    return out


def build_mobs(behaviors, slow_behaviors):
    profiles = json.loads((LOTR / "data/lotr/npc/profiles.json").read_text(encoding="utf-8"))
    tex_root = LOTR / "assets/lotr/textures/entity/npc"
    combat = []
    for cats, style, const in CATEGORY_BEHAVIORS:
        series = slow_behaviors[const] if const in SLOWED_BEHAVIORS else behaviors[const]
        combat.append({"weapon_categories": cats, "style": style, "behavior_series": series})

    files, binary, report = {}, {}, {"player": [], "minecraft:zombie": []}
    entries = [(p["id"], p.get("height", 1.8), p) for p in profiles]
    known = {p["id"] for p in profiles}
    entries += [(e, EXTRA_HEIGHTS.get(e, 1.8), None) for e in EXTRA_HUMANOIDS if e not in known]

    for eid, height, prof in entries:
        renderer = "minecraft:zombie"  # LOTR skins use the classic layout (rows 32+ hold LOTR extras), see report
        if prof:
            skins = {s for v in prof["skins"].values() for s in v}
            old = [s for s in skins if (tex_root / f"{s}.png").exists() and png_size(tex_root / f"{s}.png")[1] * 2 == png_size(tex_root / f"{s}.png")[0]]
            if old:
                renderer = "minecraft:zombie"  # Epic Fight BIPED_OLD_TEX mesh (classic layout on a 64x64 sheet)
                for s in skins:
                    p = tex_root / f"{s}.png"
                    if p.exists() and png_size(p)[0] == 2 * png_size(p)[1]:
                        binary[f"assets/lotr/textures/entity/npc/{s}.png"] = None  # padded at runtime, not shipped
        scale = round(max(0.5, min(1.6, height / 1.8)), 3)
        files[f"data/lotr/epicfight_mobpatch/{eid}.json"] = {
            "model": "epicfight:entity/biped",
            "armature": "epicfight:entity/biped",
            "renderer": renderer,
            "isHumanoid": True,
            "faction": "neutral",
            "attributes": {"impact": 0.5, "armor_negation": 0.0, "max_strikes": 1,
                           "chasing_speed": 1.2, "scale": scale},
            "default_livingmotions": DEFAULT_MOTIONS,
            "humanoid_weapon_motions": WEAPON_MOTIONS,
            "combat_behavior": combat,
            "stun_animations": STUN,
        }
        report[renderer].append(eid)
    return files, binary, report


MODS_TOML = f'''modLoader="javafml"
loaderVersion="[4,)"
license="GPL-3.0-only"

[[mods]]
modId="{MOD_ID}"
version="{VERSION}"
displayName="LOTRDL X Epic Fight"
description=\'\'\'Brings Epic Fight combat to the Lord of the Rings mod (1.21.1 port): Epic Fight movesets for LOTR weapons, Epic Fight animations and AI for LOTR NPCs and Olog-hai, finishers, dodge and parry, plus rendering fixes.\'\'\'

[[dependencies.{MOD_ID}]]
    modId="epicfight"
    type="required"
    versionRange="[21.17,21.18)"
    ordering="AFTER"
    side="BOTH"

[[dependencies.{MOD_ID}]]
    modId="lotr"
    type="required"
    versionRange="[0.47.3-preview]"
    ordering="AFTER"
    side="BOTH"
'''


def main():
    anim_keys = load_animation_keys()
    behaviors = load_behaviors(anim_keys)
    for _, _, const in CATEGORY_BEHAVIORS:
        assert behaviors.get(const), const
    # validate every animation key we reference exists in Epic Fight
    valid = {"epicfight:" + v for v in anim_keys.values()}
    used = set(DEFAULT_MOTIONS.values()) | set(STUN.values()) | {x for m in WEAPON_MOTIONS for x in m["livingmotions"].values()}
    used |= {b["animation"] for s in behaviors.values() for ser in s for b in ser["behaviors"]}
    missing = used - valid
    assert not missing, missing

    weapons, wrep = build_weapons(collect_items())
    mobs, textures, mrep = build_mobs(load_behaviors(anim_keys, DEFAULT_MIN_SERIES_COOLDOWN), behaviors)
    import sys; sys.path.insert(0, str(HERE)); from monsters import build_monsters  # -I drops the script dir
    monsters = build_monsters(a, behaviors, STUN, DEFAULT_MOTIONS)
    print("monsters:", sorted(k for k in monsters if "epicfight_mobpatch" in k))

    fix_classes = HERE / "fix" / "classes"
    all_classes = sorted(fix_classes.rglob("*.class")) if fix_classes.exists() else []
    mixin_classes = [c for c in all_classes if c.parent.name == "mixin"]  # helpers live outside the mixin package
    toml = MODS_TOML
    if mixin_classes:
        toml += '\n[[mixins]]\n    config="lotr_epicfight.mixins.json"\n'
    else:
        print("WARNING: fix/classes missing -> jar built WITHOUT the player-renderer crash fix (run fix/build_fix.py)")

    OUT_DIR.mkdir(exist_ok=True)
    with zipfile.ZipFile(JAR, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        z.writestr("META-INF/neoforge.mods.toml", toml)
        if mixin_classes:
            for c in all_classes:
                z.write(c, c.relative_to(fix_classes).as_posix())
            z.writestr("lotr_epicfight.mixins.json", json.dumps({
                "required": True, "minVersion": "0.8", "package": "lotr_epicfight.mixin",
                "compatibilityLevel": "JAVA_21",
                "mixins": [c.stem for c in mixin_classes if c.stem in COMMON_MIXINS],
                "client": [c.stem for c in mixin_classes if "$" not in c.stem and c.stem not in COMMON_MIXINS],
                "injectors": {"defaultRequire": 1}}, indent=2))
        z.writestr("pack.mcmeta", json.dumps({"pack": {"pack_format": 34, "description": "LOTRDL X Epic Fight"}}, indent=2))
        for path, data in {**weapons, **mobs, **monsters}.items():
            z.writestr(path, json.dumps(data, indent=2))
        z.writestr("data/lotr_epicfight/advancement/give_roll.json", json.dumps(GIVE_ROLL_ADVANCEMENT, indent=2))
        z.writestr("data/lotr_epicfight/function/give_roll.mcfunction", GIVE_ROLL_FUNCTION)
        z.writestr("data/lotr_epicfight/advancement/give_parry.json", json.dumps(GIVE_PARRY_ADVANCEMENT, indent=2))
        z.writestr("data/lotr_epicfight/function/give_parry.mcfunction", GIVE_PARRY_FUNCTION)
        z.writestr("data/epicfight/skill_parameters/roll.json", json.dumps({"consumption": ROLL_STAMINA_COST}, indent=2))

    report = {"weapons": {k: len(v) for k, v in wrep.items()}, "weapon_items": wrep,
              "mobs_by_renderer": {k: len(v) for k, v in mrep.items()}, "mobs": mrep,
              "padded_at_runtime": sorted(textures)}
    (OUT_DIR / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("jar:", JAR)
    print("weapons:", sum(len(v) for v in wrep.values()), report["weapons"])
    print("mobs:", sum(len(v) for v in mrep.values()), report["mobs_by_renderer"])
    print("orc skins padded at runtime:", len(textures))


if __name__ == "__main__":
    main()
