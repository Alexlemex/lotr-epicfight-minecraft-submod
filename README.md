# LOTRDL X Epic Fight

Brings [Epic Fight](https://www.curseforge.com/minecraft/mc-mods/epic-fight-mod) combat to the
**Lord of the Rings mod 1.21.1 port** (NeoForge). Fight Middle-earth with Epic Fight's animated combat:
dodges, parries, finishers and real attack animations, for you **and** for every LOTR NPC.

> Fan-made compatibility add-on. Not affiliated with the LOTR mod team, the Epic Fight team,
> the Tolkien Estate or Middle-earth Enterprises.

## Features

**Weapons**
- 339 LOTR weapons get an Epic Fight moveset: swords, daggers, spears, axes, greatswords, bows, crossbows,
  tools, and 47 shields that really block.

**NPCs (360 humanoid types: all factions, Gandalf, Saruman, bandits...)**
- Epic Fight animations and AI: combos, hit reactions, stuns, archers keep shooting.
- LOTR armour (including custom helmets), hair, beards, elf ears and sizes follow the animations.
- Olog-hai: custom Epic Fight skeleton, heavy hammer swings, armour, rock throwing.
- NPC vs NPC battles: Epic Fight drives melee, LOTR keeps archery, mounted combat and weapon switching.

**Player**
- Dodge (Epic Fight Roll, faster and cheaper) and Parry (perfect guard) given automatically on first join.
- Finishers: hit a low-health (<= 20 %) or stunned enemy to execute it with a random finisher animation.
- Eating heals instantly (food points = health) and is twice as fast. Stamina 30.
- Weapons and armour are unbreakable.

**Fixes**
- LOTR's race player model is replaced by the vanilla one (it crashed with Epic Fight).
- Crash when joining a world while it rains (LOTR ambience sound bug).
- Missing Epic Fight cape when the Epic Fight cosmetics server is unreachable.
- Old 64x32 orc skins are padded at load time for Epic Fight's model (no LOTR texture is shipped).

## Requirements

| Mod | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.266 or newer |
| Lord of the Rings mod (1.21.1 port) | 0.47.3-preview **exactly** |
| Epic Fight | 21.17.x |

Install the same three mods on the **server and on every client**.
Keep `function-permission-level=2` (default) in `server.properties`: dodge/parry are granted by a function.

## Known issues

- NPC capes, outfits and back shields do not follow animations (drawn frozen).
- Monsters other than the Olog-hai (trolls, wargs, spiders, Balrog, ents, wraiths, Gollum) and animals keep
  their normal LOTR behaviour, without Epic Fight animations.
- Dwarves and hobbits use human animations.
- In melee range, NPCs no longer raise their shields (Epic Fight drives the fight).
- The Olog-hai rock throw has no Epic Fight animation.
- Multiplayer on a dedicated server: designed for it, still being tested.
- The mod targets obfuscated LOTR classes: a new LOTR port version will need an update of this mod.

## Building from source

The jar is **generated**: Python reads the LOTR and Epic Fight files and writes the data + compiled mixins.

Folder layout expected (paths are relative to this folder's parent):
```
<root>/
  compat-generator/        <- this repository
  lotr-Release/            <- the LOTR port jar, extracted (not included, All Rights Reserved)
  epicfight-1.21.1/epicfight-1.21.1/   <- Epic Fight source (GPL-3, github.com/Epic-Fight/epicfight)
  build/                   <- output
```
Requirements: Python 3.10+, JDK 21, a NeoForge 21.1.266 client installed through the CurseForge launcher
(`fix/build_fix.py` compiles against its libraries; adjust the paths at the top of that file otherwise).

```
python -I fix/build_fix.py   # compile the mixins (fix/src) -> fix/classes
python -I generate.py        # build build/lotr_epicfight-<version>.jar
```

- `generate.py`: weapon capabilities, NPC mob patches, advancements, jar packaging.
- `monsters.py`: Olog-hai mob patch, plus the offline Python reference of its skeleton/mesh.
- `fix/src/lotr_epicfight/olog`: the Olog-hai skeleton and meshes are built **in game, at load time**, from the
  installed Epic Fight biped armature and the installed LOTR troll model (server: skeleton only), so no file derived
  from either mod is shipped.
- `fix/src`: mixins (rendering and gameplay fixes). `fix/stubs`: compile-time stubs of LOTR classes.
- `tools/`: class-file inspection, model dump and a software previewer used during development.

## Contributing

Bug reports, ideas and pull requests are welcome. Please include your `logs/latest.log` and the versions of
LOTR, Epic Fight and NeoForge.

## License

Code: [GPL-3.0](LICENSE). This project contains no LOTR or Epic Fight textures, sounds or models.
The Lord of the Rings mod and Epic Fight assets belong to their respective authors.
