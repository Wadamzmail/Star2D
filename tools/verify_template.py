#!/usr/bin/env python3
"""
Safety checks for the generated template (run by CI before anything is committed, and by hand after
`make_template.py build`). Exit code 1 + a list of problems when something is wrong.

What is checked
  * template.zip : stored resources.arsc, no manifest / signature, dex + native libs for the 4 ABIs,
                   res/drawable/icon.jpg (the exporter replaces the icon under that exact name),
                   engine assets, every class of game.zip really is inside the dex files
  * manifest.xml : 5 placeholders, and (when java + the encoder jar are available, they are on CI) the manifest is
                   filled with sample values, encoded with the SAME AxmlUtil the editor uses, glued to template.zip
                   and read back with aapt2 (package, version, label, launcher, orientation, icon, theme)
  * game.zip     : classes of the runtime, class files readable by the on-phone compiler (major version <= 52)
  * template.version matches the sources (unless --allow-stale)
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_template as mt  # noqa: E402

ASSETS = mt.DEFAULT_ASSETS
problems = []
notes = []


def bad(msg):
    problems.append(msg)


def check_template_zip(path):
    z = zipfile.ZipFile(path)
    names = z.namelist()
    if "AndroidManifest.xml" in names:
        bad("template.zip must not contain AndroidManifest.xml (the editor adds its own)")
    for n in names:
        if n.startswith("META-INF/") and not n.startswith("META-INF/services/"):
            bad("template.zip has a signature/meta entry: " + n)
    arsc = z.getinfo("resources.arsc") if "resources.arsc" in names else None
    if arsc is None:
        bad("template.zip has no resources.arsc")
    elif arsc.compress_type != zipfile.ZIP_STORED:
        bad("resources.arsc must be STORED (Android 11+ refuses a compressed one when targetSdk >= 30)")
    dexes = [n for n in names if re.fullmatch(r"classes\d*\.dex", n)]
    if "classes.dex" not in dexes:
        bad("template.zip has no classes.dex")
    for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
        for so in ("libgdx.so", "libgdx-box2d.so", "libgdx-freetype.so"):
            if "lib/%s/%s" % (abi, so) not in names:
                bad("missing native library lib/%s/%s" % (abi, so))
    for need in ("res/drawable/icon.jpg", "assets/files/default.fnt", "assets/files/font.fnt", "assets/images/white.png",
                 "assets/images/logo.png"):
        if need not in names:
            bad("template.zip is missing " + need)
    return z, dexes


def check_game_zip(path, tz, dexes):
    z = zipfile.ZipFile(path)
    classes = [n for n in z.namelist() if n.endswith(".class")]
    if len(classes) < 50:
        bad("game.zip has only %d classes" % len(classes))
    must = ["com/star4droid/star2d/Helpers/Pair.class", "com/star4droid/star2d/Helpers/Project.class",
            "com/star4droid/star2d/ElementDefs/GroupDef.class",
            "com/star4droid/template/Items/GroupItem.class", "com/star4droid/template/Items/GroupFollower.class"]
    for m in must:
        if m not in classes:
            bad("game.zip is missing " + m)
    # every source file of ElementDefs must have its class
    for rel, _ in mt.runtime_sources():
        if rel.startswith("com/star4droid/star2d/ElementDefs/") and rel.endswith(".java"):
            if rel[:-5] + ".class" not in classes:
                bad("no class compiled for " + rel)
    dex_bytes = b"".join(tz.read(d) for d in dexes)
    for c in classes:
        major = int.from_bytes(z.read(c)[6:8], "big")
        if major > 52:
            bad("%s has class version %d (> 52) which the on-phone compiler can't read" % (c, major))
        desc = ("L" + c[:-6] + ";").encode()
        if desc not in dex_bytes:
            bad("class %s of game.zip is not inside the template's dex" % c[:-6])
    return len(classes)


def java_tools():
    java, javac = shutil.which("java"), shutil.which("javac")
    jar = os.path.join(mt.ROOT, "app", "libs", "lib_7.jar")
    android = None
    for sdk in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"), "/opt/android-sdk"):
        if sdk:
            c = sorted(glob.glob(os.path.join(sdk, "platforms", "android-*", "android.jar")))
            if c:
                android = c[-1]
                break
    kxml = find_kxml2()
    if java and javac and os.path.exists(jar) and android and kxml:
        return java, javac, jar + os.pathsep + kxml, android
    return None


def find_kxml2():
    """The editor's encoder needs an XmlPull implementation; on the phone Android provides it, here kxml2 does."""
    env = os.environ.get("KXML2_JAR")
    if env and os.path.exists(env):
        return env
    hits = glob.glob(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/net.sf.kxml/kxml2/*/*/kxml2-*.jar"))
    if hits:
        return hits[0]
    dest = os.path.join(tempfile.gettempdir(), "kxml2-2.3.0.jar")
    if not os.path.exists(dest):
        try:
            import urllib.request
            urllib.request.urlretrieve("https://repo1.maven.org/maven2/net/sf/kxml/kxml2/2.3.0/kxml2-2.3.0.jar", dest)
        except Exception:
            return None
    return dest


def check_manifest(manifest_path, template_zip_path):
    text = open(manifest_path, encoding="utf-8").read()
    for i in range(1, 6):
        if "%%%d$s" % i not in text:
            bad("manifest.xml lacks placeholder %%%d$s" % i)
    if "TPL_APP_LABEL" in text:
        bad("manifest.xml still has the dummy label")
    if re.search(r"%(?!\d\$s)", text):
        bad("manifest.xml has a stray %")
    tools = java_tools()
    if not tools:
        notes.append("encode test skipped (java / lib_7.jar / android.jar not found)")
        return
    java, javac, jar, android = tools
    tmp = tempfile.mkdtemp(prefix="tplcheck-")
    try:
        src = os.path.join(tmp, "E.java")
        open(src, "w").write("public class E{public static void main(String[] a)throws Exception{axml.xml.AxmlUtil.encodeFile(a[0],a[1]);}}")
        cp = jar + os.pathsep + android  # jar = encoder + kxml2 (before android.jar, whose XmlPull is a stub)
        r = subprocess.run([javac, "-nowarn", "-cp", cp, "-d", tmp, src], capture_output=True, text=True)
        if r.returncode:
            bad("could not compile the AXML encoder harness: " + r.stderr[-400:])
            return
        for orient in ("0", "1"):
            vals = ("7", "1.7.1", "com.test.game", "My Game", orient)
            filled = re.sub(r"%(\d)\$s", lambda m: vals[int(m.group(1)) - 1], text)
            mf = os.path.join(tmp, "manifest" + orient)
            out = os.path.join(tmp, "AndroidManifest%s.xml" % orient)
            open(mf, "w", encoding="utf-8").write(filled)
            r = subprocess.run([java, "-cp", tmp + os.pathsep + cp, "E", mf, out], capture_output=True, text=True)
            if r.returncode or not os.path.exists(out):
                bad("the editor's AXML encoder rejected manifest.xml (orientation %s): %s" % (orient, (r.stderr or r.stdout)[-600:]))
                return
            apk = os.path.join(tmp, "t%s.apk" % orient)
            with zipfile.ZipFile(template_zip_path) as zin, zipfile.ZipFile(apk, "w") as zout:
                for info in zin.infolist():
                    zi = zipfile.ZipInfo(info.filename, (1980, 1, 1, 0, 0, 0))
                    zi.compress_type = info.compress_type
                    zout.writestr(zi, zin.read(info.filename))
                zout.write(out, "AndroidManifest.xml")
                zout.writestr("assets/project.zip", b"PK\x05\x06" + b"\0" * 18)
            badging = mt.aapt2("dump", "badging", apk)
            tree = mt.aapt2("dump", "xmltree", "--file", "AndroidManifest.xml", apk)
            res = mt.aapt2("dump", "resources", apk)
            exp = {
                "package": r"package: name='com\.test\.game' versionCode='7' versionName='1\.7\.1'",
                "label": r"application-label:'My Game'",
                "launcher": r"launchable-activity: name='com\.star4droid\.template\.LoadingScreen'",
                "sdk": r"targetSdkVersion:'35'",
                "icon": r"application-icon-\d+:'res/drawable/icon\.jpg'",
                "abis": r"native-code: .*arm64-v8a",
            }
            for k, pat in exp.items():
                if not re.search(pat, badging):
                    bad("aapt2 badging (orientation %s): '%s' not as expected" % (orient, k))
            if not re.search(r"screenOrientation\(0x0101001e\)=%s\b" % orient, tree):
                bad("screenOrientation was not encoded as %s" % orient)
            theme = re.search(r"android:theme\(0x01010000\)=@0x([0-9a-f]+)", tree)
            icon = re.search(r"android:icon\(0x01010002\)=@0x([0-9a-f]+)", tree)
            if not theme or ("resource 0x" + theme.group(1) + " style/AppTheme") not in res:
                bad("the theme id in the manifest is not style/AppTheme in resources.arsc")
            if not icon or ("resource 0x" + icon.group(1) + " drawable/icon") not in res:
                bad("the icon id in the manifest is not drawable/icon in resources.arsc")
            if "com.test.game.androidx-startup" not in tree:
                bad("provider authority was not made unique (%3$s)")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    paths = {
        "zip": os.path.join(ASSETS, "files", "template.zip"),
        "manifest": os.path.join(ASSETS, "java", "manifest.xml"),
        "game": os.path.join(ASSETS, "java", "game.zip"),
        "version": os.path.join(ASSETS, "java", "template.version"),
    }
    for k, p in paths.items():
        if not os.path.exists(p):
            bad("missing " + p)
    if not problems:
        tz, dexes = check_template_zip(paths["zip"])
        n = check_game_zip(paths["game"], tz, dexes)
        check_manifest(paths["manifest"], paths["zip"])
        if open(paths["version"]).read().strip() != mt.source_hash():
            if "--allow-stale" in sys.argv:
                notes.append("template.version is older than the sources (allowed: it is rebuilt after the merge)")
            else:
                bad("template.version does not match the sources (run tools/make_template.py build)")
        notes.append("game.zip classes: %d, dex files: %d" % (n, len(dexes)))
    for n in notes:
        print("note:", n)
    if problems:
        print("\nTEMPLATE CHECK FAILED:")
        for p in problems:
            print(" -", p)
        sys.exit(1)
    print("template check OK")


if __name__ == "__main__":
    main()
