# Star2D documentation index

Star2D is a 2D game-engine editor that runs on an Android phone (LibGDX + Box2D). You build games through tools; the user sees everything live in the editor. Read this index, then only the topics you need (`read_docs` with `topic`).

## Golden rules

1. **The engine runs ONLY on the phone.** Never try to compile, run or test the game anywhere else. Build, run and test with the `build`, `run_game`, `run_test`, `screenshot`, `game_state` tools. The Star2D app must stay open and visible on screen.
2. **Blocks first.** Write game logic as visual blocks (`events` with `blocks`), because that is what the user can later open and edit. Use raw Java (`java` in `events`, or files in `java/`) only when no block can express it. A `codeLine` block is the in-between: one Java statement inside a block sequence.
3. **Batch tools.** One `elements` call can add 30 elements; one `events` call can set 10 events; one `run_test` call can play a whole scenario. Every call costs quota.
4. **Units are editor pixels.** Positions and sizes you give in `elements` are pixels of the scene's logic screen (see `scenes`). The origin is the BOTTOM-LEFT, y goes UP, `x`/`y` is the element's bottom-left corner. Block "px" fields use the same pixels. Raw Box2D blocks and Java use world units (= px * 0.25). The `physics` topic explains this in full.
5. **Always verify by running.** After building, `run_game`/`run_test`, look at `screenshot`, `game_state` and `game_logs`. Never say it works without checking. A compile error appears in the `build` result with file and line.
6. **Names:** elements, scenes, variables and joints are named with letters, digits and `_`, not starting with a digit. Use lowercase scene names (`scene1` is the main scene and cannot be renamed or deleted).
7. **Physics:** the first block of every physical scene's `OnCreate` is `setWorldGravity` (about 800 for platformers, 0 for top-down); keep speeds at or below about 450 px/s. Use collision events of the SCENE (`onCollisionStart`), `isSensor` for pickups, and `hide` + the `destroyLater` helper (see `blocks`) to remove things.
8. **Plan, then work:** `set_plan` after reading the docs, `update_task` as you go, `finish_work` at the end.

## Topics

- `overview`: project folders on disk, how scenes/events/code become a game, what `build` does. Read first on a new project.
- `scenes`: scene files and config (orientation, logic size, colors), several scenes, `openScene`. Read before creating scenes.
- `elements`: every element type with EVERY property, default and allowed values (generated tables) plus their meaning. Read before adding or editing elements.
- `physics`: body types, density/friction/restitution, sensors, collision filtering, gravity, speeds, the units trap, typical numbers. Read before anything that moves.
- `events`: every scene and element event, exact names, when it fires, which variables exist. Read before writing logic.
- `blocks`: the block JSON format for `events`, the rules for field values, `define_blocks`. Read before the first `events` call.
- `blocks-examples`, `blocks-examples-2`: 15 verified worked block sequences (movement, jump, coins, shooting, timers, game over, scenes, saving, camera, animation). Copy from here.
- `blocks-catalog`: the live list of every block and value block (also available through the `block_catalog` tool).
- `variables`: scene variables (`variables` tool), types, value syntax, saving data between scenes and runs.
- `ui-input`: TEXT/PROGRESS/JOYSTICK, HUD layout, touch, keyboard, camera follow.
- `assets`: images, sprite animations, sounds, particles, maps, fonts; `create_image` / `create_sound` recipes.
- `joints`: Box2D joints through the `joints` tool, which types work, which properties are safe.
- `java-api`: the Java API (StageImp, PlayerItem, ItemScript, SceneScript), file locations and package rules for `files` + `java/`.
- `recipes`, `recipes-2`: complete small games as ordered lists of tool calls with concrete JSON (platformer, top-down shooter, clicker, breakout, runner, ...).
- `testing`: `run_test` and `game_input` examples, how to assert, real-time limits.
- `pitfalls`: the mistakes that cost the most time. Skim it before your first build.

## Minimum path for a new small game

`status` -> `read_docs` index -> read `overview`, `elements`, `physics`, `blocks` (+ `blocks-examples`, `recipes`) -> `set_plan` -> `scenes` configure -> `create_image` for sprites -> `elements` (batch) -> `variables` -> `events` (batch) -> `run_test` -> fix -> `finish_work`.
