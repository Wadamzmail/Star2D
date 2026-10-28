#!/usr/bin/env python3
"""Builds the Star2D desktop templates (Windows + Linux). macOS is intentionally not supported.

Nothing produced here is committed to the repository: the zips go to build/desktop-dist.

Usage:
  python3 tools/make_desktop.py [--os windows|linux|both] [--with-jre] [--no-gradle]
                                [--project project.zip] [--game-jar game.jar]
                                [--out build/desktop-dist] [--name MyGame]

--with-jre   bundle a minimal JRE made with jlink. jlink can only produce a runtime for the OS it runs on,
             so the Windows zip must be built on Windows (the GitHub workflow does this) and the Linux zip on Linux.
             Without it the zip needs Java 17+ on the player's machine.
--project    optional exported project (project.zip) to place in game/ (otherwise game/ is empty, drop it in later)
--game-jar   optional compiled scenes jar (see docs/DESKTOP.md) to place in game/game.jar
"""
import argparse, os, platform, shutil, subprocess, sys, zipfile, stat

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLEW = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
MAIN = "com.star4droid.desktop.DesktopLauncher"
# modules used by LWJGL3 / LibGDX / gson / zip4j; jdk.unsupported is needed by LWJGL (Unsafe)
JRE_MODULES = "java.base,java.desktop,java.logging,java.management,java.naming,java.prefs,java.sql,java.xml,jdk.unsupported,jdk.crypto.ec"

RUN_SH = """#!/bin/sh
# Star2D desktop launcher (Linux)
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -x "$DIR/jre/bin/java" ]; then JAVA="$DIR/jre/bin/java"; else JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"; fi
exec "$JAVA" -Xmx1g -jar "$DIR/lib/star2d-desktop.jar" "$@"
"""

RUN_BAT = """@echo off
rem Star2D desktop launcher (Windows)
setlocal
set "DIR=%~dp0"
if exist "%DIR%jre\\bin\\javaw.exe" (set "JAVA=%DIR%jre\\bin\\javaw.exe") else (set "JAVA=javaw")
start "" "%JAVA%" -Xmx1g -jar "%DIR%lib\\star2d-desktop.jar" %*
"""

README = """Star2D desktop build ({os})
================================
Run:   {run}
Needs: {needs}

Folders
  lib/        the engine (star2d-desktop.jar)
  game/       project.zip (exported game) and game.jar (compiled scenes)
  jre/        bundled Java runtime (only when built with --with-jre)

Options (pass after the launcher):
  --scene NAME            start scene (default: main scene from the project)
  --project PATH          a project.zip or a project folder
  --game-jar PATH         compiled scenes jar
  --fullscreen            start fullscreen
  --demo                  run the built-in demo (no project needed)
  --selftest              check that the engine starts correctly
  --help                  all options

The window shape follows the project: portrait projects open in a tall window, landscape projects in a wide one.
Errors are written to error.txt in the data folder ({data}).
Unsigned build: Windows SmartScreen / Linux file managers may ask for confirmation the first time.
"""


def sh(cmd, **kw):
    print("+", " ".join(cmd), flush=True)
    subprocess.check_call(cmd, cwd=kw.pop("cwd", ROOT), **kw)


def build_jar(target):
    task = {"windows": ":desktop:fatJarWindows", "linux": ":desktop:fatJarLinux"}[target]
    sh(([GRADLEW] if os.name == "nt" else ["sh", GRADLEW]) + [task, "--no-daemon", "-q"])
    jar = os.path.join(ROOT, "desktop", "build", "libs", "star2d-desktop-%s.jar" % target)
    if not os.path.isfile(jar):
        sys.exit("missing " + jar)
    return jar


def make_jre(dest):
    jlink = shutil.which("jlink")
    if not jlink:
        jh = os.environ.get("JAVA_HOME")
        if jh:
            cand = os.path.join(jh, "bin", "jlink.exe" if os.name == "nt" else "jlink")
            jlink = cand if os.path.isfile(cand) else None
    if not jlink:
        sys.exit("jlink not found (need a JDK 17+) - run without --with-jre or set JAVA_HOME")
    if os.path.isdir(dest):
        shutil.rmtree(dest)
    sh([jlink, "--add-modules", JRE_MODULES, "--strip-debug", "--no-header-files", "--no-man-pages",
        "--compress", "2", "--output", dest])


def add_file(z, path, arcname, mode=0o644):
    info = zipfile.ZipInfo.from_file(path, arcname)
    info.external_attr = (stat.S_IFREG | mode) << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    with open(path, "rb") as f:
        z.writestr(info, f.read())


def add_text(z, arcname, text, mode=0o644, crlf=False):
    if crlf:
        text = text.replace("\r\n", "\n").replace("\n", "\r\n")
    info = zipfile.ZipInfo(arcname, date_time=(2024, 1, 1, 0, 0, 0))
    info.external_attr = (stat.S_IFREG | mode) << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    z.writestr(info, text)


def package(target, jar, args):
    name = "game-%s" % target
    os.makedirs(args.out, exist_ok=True)
    out = os.path.join(args.out, "star2d-desktop-%s.zip" % target)
    jre = None
    if args.with_jre:
        native = "windows" if os.name == "nt" else "linux" if sys.platform.startswith("linux") else None
        if native != target:
            print("! skipping JRE for %s (jlink runs on %s only); zip needs Java 17+" % (target, native))
        else:
            jre = os.path.join(args.out, "_jre_" + target)
            make_jre(jre)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        add_file(z, jar, name + "/lib/star2d-desktop.jar")
        if target == "linux":
            add_text(z, name + "/run.sh", RUN_SH, 0o755)
            data = "~/.local/share/star2d/<id>"
            run = "./run.sh   (chmod +x run.sh if needed)"
        else:
            add_text(z, name + "/run.bat", RUN_BAT, 0o755, crlf=True)
            data = "%APPDATA%\\Star2D"
            run = "run.bat"
        needs = "nothing (Java is bundled)" if jre else "Java 17 or newer installed"
        add_text(z, name + "/README.txt", README.format(os=target, run=run, needs=needs, data=data),
                 crlf=(target == "windows"))
        add_text(z, name + "/game/PUT_GAME_HERE.txt",
                 "Place the exported project here as project.zip, and the compiled scenes as game.jar.\n")
        if args.project:
            add_file(z, args.project, name + "/game/project.zip")
        if args.game_jar:
            add_file(z, args.game_jar, name + "/game/game.jar")
        if jre:
            for base, dirs, files in os.walk(jre):
                for fn in files:
                    full = os.path.join(base, fn)
                    rel = os.path.relpath(full, jre).replace(os.sep, "/")
                    exe = os.access(full, os.X_OK) and target == "linux"
                    add_file(z, full, name + "/jre/" + rel, 0o755 if exe else 0o644)
            shutil.rmtree(jre, ignore_errors=True)
    print("OK %s (%.1f MB)" % (out, os.path.getsize(out) / 1048576.0))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--os", default="both", choices=["windows", "linux", "both"])
    ap.add_argument("--with-jre", action="store_true")
    ap.add_argument("--no-gradle", action="store_true", help="use jars already built in desktop/build/libs")
    ap.add_argument("--project")
    ap.add_argument("--game-jar")
    ap.add_argument("--out", default=os.path.join(ROOT, "build", "desktop-dist"))
    args = ap.parse_args()
    targets = ["windows", "linux"] if args.os == "both" else [args.os]
    for t in targets:
        jar = (os.path.join(ROOT, "desktop", "build", "libs", "star2d-desktop-%s.jar" % t)
               if args.no_gradle else build_jar(t))
        package(t, jar, args)


if __name__ == "__main__":
    main()
