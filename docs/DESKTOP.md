# Desktop templates (Windows + Linux)

Experimental desktop runner for games exported from Star2D. macOS is **not** supported.
Nothing built here is stored in the repository: the zips are produced on demand and live only as GitHub Action artifacts.

## How it works

* Module `:desktop` (plain Java, LibGDX **LWJGL3** backend). It compiles the **same runtime sources** as the Android
  template (`template/runtime-sources.txt`, minus `MainActivity` / `LoadingScreen`) so a game behaves the same on both.
* Only two Android APIs are needed by that runtime, and both are shimmed in `desktop/src/main/java`:
  `android.util.Log` and `dalvik.system.DexClassLoader` (implemented with a `URLClassLoader` over a normal jar).
* `DesktopLauncher` reads `configs/<scene>.json` (`or`, `logicWidth`, `logicHeight`), computes a window with the same
  portrait/landscape rules as `StageImp` (portrait swaps the two sizes; defaults 720 x 1560), fits it to 85% of the
  monitor and keeps the aspect ratio with letterboxing (`FitViewport`). Portrait projects open in a tall window,
  landscape ones in a wide window.

## Layout of a build

```
game-<os>/
  run.sh | run.bat
  lib/star2d-desktop.jar     engine + natives for that OS only
  game/project.zip           exported project (same encrypted zip the Android template uses)
  game/game.jar              compiled scenes (classes com.star4droid.Game.<scene>) as a normal jar
  jre/                       optional bundled runtime (--with-jre)
  README.txt
```

`game.jar` contract: the Android export compiles scenes into `dex/scenes.dex`; the desktop runtime needs the same
classes as a **plain jar** (`.class` files, not dex). The loader looks for `game/game.jar`, `<project>/dex/scenes.jar`,
`<project>/dex/game.jar` or a `game.jar` next to the project. Producing that jar from the phone-side export is the
remaining integration step (see "Limits").

## Building

* GitHub: Actions → **Build desktop templates (manual)** → Run workflow (choose windows / linux / both, JRE yes/no).
  Download the artifacts `star2d-desktop-windows` / `star2d-desktop-linux`. The workflow never commits or pushes.
* Locally: `python3 tools/make_desktop.py --os linux [--with-jre] [--project project.zip --game-jar game.jar]`
  → `build/desktop-dist/`. `jlink` only builds a JRE for the OS it runs on, so the Windows zip with a bundled JRE must
  be built on Windows (the workflow does this); a local Linux run produces the Windows zip without JRE (needs Java 17+).
* Checks: `./gradlew :desktop:windowSizingTest`, `./gradlew :desktop:selfTest` (or `java -jar ...jar --selftest`).
  `--demo [--demo-orientation portrait|landscape]` shows a small scene without any project.

## Limits

* Unsigned binaries; no installer. SmartScreen / file managers may ask for confirmation.
* ESC is not mapped to the Android BACK key; no accelerometer; touch = mouse; audio needs an output device.
* User scripts or blocks that call `android.*` APIs (other than `Log`) will not run on desktop.
* Only verified so far: class loading, natives (gdx / box2d / freetype), window sizing, demo scene under Xvfb on Linux.
  **Not yet verified:** a real exported game on desktop, and anything on Windows.
