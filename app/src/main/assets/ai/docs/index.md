# Star2D docs: map

Star2D is a 2D game-engine editor on an Android phone (LibGDX + Box2D). Do NOT read everything. The rules below are all you need to start; read only the part you are about to use: `read_docs {topic:'blocks'}`, `{topic:'elements', section:'JOYSTICK'}`, `{search:'sensor pickup'}`. A long topic answers with its table of contents first.

## Rules
1. The engine runs ONLY on the phone. Never compile/run/test anywhere else. The editor must stay open on screen.
2. Blocks first: logic = `events` with `blocks` (user-editable). Java only when no block fits.
3. Batch: many ops per call; every call costs shared quota. Check `status.scene_rev` before re-reading a scene. Change code with `files` search / read `from_line,to_line` / edit (and `events` edit), never by rewriting a file.
4. Units: editor px, origin BOTTOM-LEFT, y up, x/y = element's bottom-left corner. Raw Box2D/Java: world = px * 0.25. Names: letters, digits, `_`.
5. Event names. Scene: `OnCreate` `OnStep` `onCollisionStart` `onCollisionEnd` `OnPause` `OnResume`. Element: `onClick` `onTouchStart` `onTouchEnd` `OnBodyCreated` `OnBodyUpdate` `onBodyCollided` `onBodyCollideEnd`.
6. Physics: first block of a physical scene's `OnCreate` is `setWorldGravity` (about 800 platformer, 0 top-down); speeds <= 450 px/s; use scene collision events, `isSensor` pickups, `hide` + `destroyLater` to remove things.
7. Images: `create_image` `hero.png` -> element `image` `/hero.png`.
8. TEXT wraps if narrower than its content (about 10 px per letter x Font Scale): resize with `set`. A TEXT/PROGRESS without touch events is click-through: put the event on the BOX button only.
9. UI: `layout_check`, then `screenshot` `{target:'scene', region:{...}}`, fix with `elements set`.
10. Test: `run_game` returns when loaded (`ready:true`); `down` stays pressed until `up`/`release`; real time, 45 s per call. Verify with `screenshot`, `game_state`, `game_logs` before saying it works.

## Topics (read when)
- `overview`: folders on disk, how scenes/events become a game, what `build` does. First project contact.
- `scenes`: scene files, orientation, logic size, colors, several scenes. Before creating scenes.
- `elements`: EVERY element type with every property, default and allowed values. Use `section:'<TYPE>'`.
- `physics`: body types, friction, sensors, filtering, gravity, speeds, units. Before anything that moves.
- `events`: every event, exact names, when it fires. Before writing logic.
- `blocks`: block JSON format, `define_blocks`. Before the first `events` call.
- `blocks-examples`, `blocks-examples-2`: 15 verified block sequences (movement, jump, coins, shooting, timers, game over, scenes, saving, camera). Copy from here.
- `blocks-catalog`: every block (also `block_catalog` tool, with `search`).
- `variables`: scene variables, types, saving data.
- `ui-input`: TEXT sizing, buttons, designing UI (layout_check + screenshot loop), touch, keyboard, joystick, camera follow.
- `assets`: images, animations, sounds, particles, maps, fonts, paths, `create_image` / `create_sound`.
- `joints`: the `joints` tool, which types are safe.
- `java-api`: Java API, file locations, package rules (only for raw Java).
- `recipes`, `recipes-2`: complete small games as ordered tool calls (platformer, shooter, clicker, breakout, runner, ...). Read ONE recipe section.
- `testing`: `run_test` / `game_input` examples and limits.
- `pitfalls`: the mistakes that cost the most time (skim once).

## Minimal path for a new game
`status` -> `set_plan` -> `scenes` configure -> `create_image` -> `elements` (batch) -> `variables` -> `events` (batch) -> `run_test` -> fix -> `finish_work`. Read `blocks` + one `blocks-examples` entry before the first `events`, `elements` sections for the types you use, `physics` if bodies move.
