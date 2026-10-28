# Star2D AI docs report

Source: `app/src/main/assets/ai/docs/*.md` (sizes in chars, source form).

## Topics
index 4.6k, overview 4.5k, scenes 4.4k, elements 8.3k (+ generated tables ~14k at runtime), physics 8.3k, events 6.1k, blocks 9.4k, blocks-examples 6.0k (ex. 1-8), blocks-examples-2 7.5k (ex. 9-15), blocks-catalog (placeholder `{{BLOCK_CATALOG}}`, ~9.6k generated), variables 3.5k, ui-input 5.4k, assets 5.4k, joints 5.6k, java-api 7.8k, recipes 14.2k (define_blocks, platformer, top-down shooter, tap game), recipes-2 13.3k (breakout, runner, flappy, menu/levels), testing 6.1k, pitfalls 6.9k.

All block JSON (31 snippets) was machine-validated against the real `BlockCatalog`. The only validator "error" is the template pseudo-JSON in `blocks.md`.

Java: `remote/RemoteDocs.java` (index/topic/topics/elementTables/blockCatalog), `git/AiDocs.java` (short ENGINE.md, STATE.md, docs written to `.star2d/docs/`, new `docPaths(File)`). Both compile with plain javac; neither was run on a device.

## Unverified (not run on a phone)
- Box2D `b2_maxTranslation` speed cap (~480 px/s at 60 fps); docs keep speeds <= 450.
- `OnBodyCreated` reliability for scene-defined elements.
- Portrait runtime viewport (`setPropertySet` does not swap width/height).
- Sound overlap semantics (Music play restarts or ignores).
- Whether clones of TextItem/MapItem keep names.
- A double `destroyBody` crash is inferred from javap (native); the `destroyLater` helper avoids it anyway.
- `Collision` filter behaviour with clones (derived from code).
- `blockCatalog()` output checked only via a reflection harness; `AiDocs` not run (needs a Context).

## Discrepancies: old `AiContext.ENGINE_GUIDE` vs code
- x/y/width were described as "Box2D meters-like world units". Real: editor px, y up, origin bottom-left, element bottom-left corner (world = px * 0.25). The `add_element x:"5"` example used meters.
- Missing: 6 physics steps/frame by default and the `setWorldGravity` fix; Java 7 `final` rule for timers; the element-collision dispatch bug; broken/no-op blocks.
- `setCameraXY(PlayerItem)` was listed as follow, but it centers once. Real follow: `cameraFollowX/Y`.
- tools.ts examples use aliases (onCreate/onUpdate/onCollisionBegin); canonical names are OnCreate/OnStep/onCollisionStart (LogicTools maps aliases).
- tools.ts `elements` example `image:'/images/hero.png'` is wrong; should be `/hero.png`.

## Code bugs found (for the other engineer)
- `StageImp` ~733-775: first body's collision handler runs twice, second never runs.
- `GitPanel` ~622 commits only ENGINE.md/STATE.md; add `AiDocs.docPaths(dir)` to the commit.
- `EventsPanel` creates flat `scripts/<name>.java` (others use `scripts/<scene>/`).
- Rope/Mouse/Gear joints do not compile; `saveValue`/`setsplits` blocks do not compile; `setAlpha`/`moveTowardBody` are no-ops.
- `PlayerItem.destroy` is unguarded; `getUiStage()` returns GameStage; `setActorY` sets X.
