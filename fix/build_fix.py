"""Compile the mixin fix against the CurseForge instance's jars.

Needs a Java compiler: either `javac` (JDK 21, on PATH or in JAVA_HOME) or an Eclipse ECJ jar passed as ECJ=<path>.
Usage: python -I build_fix.py
Output: fix/classes/  (picked up automatically by generate.py)
"""
import os, shutil, subprocess, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LIB = Path(os.path.expanduser("~")) / "curseforge/minecraft/Install/libraries"
INSTANCE = Path(os.path.expanduser("~")) / "curseforge/minecraft/Instances/LOTRD DL Mod"
JRE = Path(os.path.expanduser("~")) / "curseforge/minecraft/Install/java/java-runtime-delta/bin/java.exe"

def find(pattern_dir, name_part):
    hits = sorted(p for p in pattern_dir.rglob("*.jar") if name_part in p.name)
    if not hits:
        sys.exit(f"missing library: {name_part}")
    return hits[-1]

cp = [
    find(LIB / "net/minecraft/client", "client-1.21.1-20240808.144430-srg"),
    find(LIB / "net/fabricmc/sponge-mixin", "sponge-mixin-0.16.4"),
    find(LIB / "net/neoforged/neoforge", "neoforge-21.1.266-universal"),
    find(LIB / "com/mojang", "blaze3d") if list((LIB / "com/mojang").rglob("*blaze3d*.jar")) else None,
    find(INSTANCE / "mods", "epic-fight"),
    find(LIB / "net/neoforged/fancymodloader/loader", "loader-4"),  # @Mod annotation
]
# MixinExtras is shipped inside the NeoForge universal jar (jar-in-jar); extract it.
out_lib = HERE / "lib"
out_lib.mkdir(exist_ok=True)
import zipfile
with zipfile.ZipFile(cp[2]) as z:
    for n in z.namelist():
        if "mixinextras" in n and n.endswith(".jar"):
            (out_lib / Path(n).name).write_bytes(z.read(n))
cp += list(out_lib.glob("*.jar"))
cp += list(LIB.rglob("joml-*.jar"))[-1:] + list(LIB.rglob("guava-*.jar"))[-1:] + list(LIB.rglob("gson-*.jar"))[-1:]
cp += list((LIB / "com/mojang").rglob("*.jar")) + list(LIB.rglob("slf4j-api-*.jar"))[-1:]
cp = [str(p) for p in cp if p]

classes = HERE / "classes"
shutil.rmtree(classes, ignore_errors=True)
classes.mkdir()
src = [str(p) for p in (HERE / "src").rglob("*.java")]

javac = shutil.which("javac") or (os.environ.get("JAVA_HOME") and str(Path(os.environ["JAVA_HOME"]) / "bin/javac.exe"))
if not (javac and Path(javac).exists()):
    found = sorted(Path("C:/Program Files").glob("*/jdk-21*/bin/javac.exe"))  # e.g. Eclipse Adoptium, Microsoft
    javac = str(found[-1]) if found else None
# Compile-time stubs for LOTR classes referenced by handler signatures (not packaged).
stub_src = [str(p) for p in (HERE / "stubs").rglob("*.java")]
stub_out = HERE / "stub_classes"
shutil.rmtree(stub_out, ignore_errors=True)
if stub_src and javac and Path(javac).exists():
    stub_out.mkdir()
    if subprocess.run([javac, "--release", "21", "-proc:none", "-nowarn", "-cp", os.pathsep.join(cp), "-d", str(stub_out)] + stub_src).returncode:
        sys.exit("stub compilation failed")
    cp.append(str(stub_out))
if javac and Path(javac).exists():
    cmd = [javac, "--release", "21", "-proc:none", "-nowarn", "-cp", os.pathsep.join(cp), "-d", str(classes)] + src
elif os.environ.get("ECJ"):
    cmd = [str(JRE), "-jar", os.environ["ECJ"], "--release", "21", "-proc:none", "-nowarn", "-cp", os.pathsep.join(cp), "-d", str(classes)] + src
else:
    sys.exit("No Java compiler found: install a JDK 21 or set ECJ=<path to ecj.jar>")
print(" ".join(cmd[:3]), "...")
r = subprocess.run(cmd)
sys.exit(r.returncode)
