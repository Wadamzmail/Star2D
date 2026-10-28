# Game template (Export APK)

"Export APK" in the editor does not compile the engine. It takes a **prebuilt template** that lives in the
editor's assets, adds the project, a manifest and an icon, and signs the result:

| Asset in `app/src/main/assets` | What it is |
|---|---|
| `files/template.zip` | the template APK without `AndroidManifest.xml` and signature (classes.dex, resources.arsc, native libs, engine assets) |
| `java/manifest.xml` | the template's real manifest as text with placeholders `%1$s` versionCode, `%2$s` versionName, `%3$s` package, `%4$s` app name, `%5$s` orientation (0 landscape / 1 portrait) |
| `java/game.zip` | compiled runtime classes; the on-phone compiler uses it as class path (`addition.jar`) |
| `java/template.version` | hash of everything the template is made of; used to detect a stale template |

**Nobody edits these four files by hand.** They are generated.

## Where the template comes from

```
app/src/main/java/...   (runtime classes shared by the editor and the games, listed in template/runtime-sources.txt)
        │  copied by Gradle task :template:syncRuntimeSources
        ▼
:template  (Android application module, package com.star4droid.template)
        │  ./gradlew :template:assembleRelease      → template-release-unsigned.apk
        ▼
tools/make_template.py build                        → the four assets above
tools/verify_template.py                            → safety checks
```

* `template/runtime-sources.txt` is the single list of shared runtime sources
  (`com/star4droid/template/**`, `ElementDefs/**`, `Helpers/rtl/**`, `Helpers/Pair.java`, gdx `StringBuilder`).
  The editor keeps compiling them as part of `:app`; the template compiles the very same files, so they can't drift apart.
* Files that only the template has live in `template/src/main` (manifest, resources, `Star2dApp`, `DebugActivity`,
  `FileUtil`, `Logger`, and a slim `Helpers/Project`). If a runtime class starts using another `Project` method,
  the template build fails to compile until it is added to the slim class.
* The manifest of `template/src/main/AndroidManifest.xml` is turned into `java/manifest.xml` automatically
  (placeholders, unique permission/provider names per game). The label must stay `TPL_APP_LABEL`.
* `android.enableResourceOptimizations=false` (gradle.properties) keeps real resource paths: the exporter replaces
  the icon at `res/drawable/icon.jpg`.

## Rebuilding

* **Automatic:** when a commit on `master` touches the runtime sources, `template/`, or the tools, the workflow
  **Build game template** builds the template and pushes the four files to `master` (`Rebuild game template [skip ci]`).
  If `template.version` already matches the sources it does nothing.
* **Manual (GitHub):** Actions → *Build game template* → *Run workflow* (tick *force* to rebuild anyway).
* **Manual (local):**
  ```
  ./gradlew :template:assembleRelease
  python3 tools/make_template.py build      # needs ANDROID_HOME for aapt2
  python3 tools/verify_template.py
  ```
  then commit the four files.
* `python3 tools/make_template.py check` tells whether the committed template is older than the sources.
  Pull requests get a warning (not a failure) when it is; the rebuild happens after the merge.

## Safety checks (`tools/verify_template.py`)

* `resources.arsc` is stored (required by Android 11+), no manifest/signature inside, dex + 4 ABIs of the native libs present,
  `res/drawable/icon.jpg` present.
* Every class of `game.zip` exists in the template's dex, class files are readable by the on-phone compiler (version ≤ 52),
  `GroupDef`, `GroupItem`, `Pair`, `Project` are there.
* The manifest is filled with sample values, encoded with the **same AXML encoder the editor uses**, glued to
  `template.zip`, and read back with `aapt2` (package, version, label, launcher activity, orientation 0/1,
  icon and theme ids really exist in `resources.arsc`, unique provider authority).
* `template.version` equals the hash of the sources.

## Changing the template

* New runtime class that games need → put it under one of the patterns in `template/runtime-sources.txt` (or add a pattern).
* New permission/activity for exported games → `template/src/main/AndroidManifest.xml`.
* New engine asset (fonts, images) → `template/src/main/assets/`.
* New dependency (library) → `template/build.gradle`.

Merge to `master`, the workflow does the rest. Exported games keep working with an older editor because the template
is part of the editor build; an editor update brings the new template with it.
