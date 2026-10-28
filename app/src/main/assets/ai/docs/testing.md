# Testing: build, run, input, state, run_test

The game runs on the user's phone in REAL time. Nothing can be simulated faster. The Star2D app must stay open and visible (split screen next to the chat works): screenshots and input only work while the app is on screen. If a call says the app is not on screen, ask the user to bring it back and call `status`.

## Order of work

1. `build` after every batch of changes. Errors come back as `{"ok":false,"errors":[{"where","file","line","message","block","block_index"}]}`. For errors that come from blocks you get the block title and its index in the sequence. Fix and rebuild until `ok`.
2. `run_game` (builds and starts; the game screen covers the editor until `stop_game`). Returns `running:true` and possibly `runtime_error`.
3. `screenshot` (`target` `game`), `game_state`, `game_logs`, `game_input`, or everything at once with `run_test`.
4. `stop_game` when finished. Running a game blocks editing tools, so stop it before `elements`/`events` calls.

## game_state

```json
{"elements":["player","enemy1"],"variables":true,"logs":true}
```
Returns for each element: `name`, `x`, `y`, `width`, `height`, `rotation`, `visible`, `vx`, `vy` (bodies), `text` (TEXT), `progress`; plus the scene's `int/float/boolean/String` variables and the last log lines.

**Units in `game_state`**: for world elements (`type` not UI) `x`, `y`, `width`, `height` are WORLD units (editor pixels * 0.25; x,y = bottom-left corner), `vx`, `vy` are Box2D velocities in world units per second (px/s = value * 4 once `setWorldGravity` is used). UI elements are in plain pixels. Convert before comparing with what you set: an element placed at `x: 400` shows `x: 100`.

## run_test

One call = start the game (default), play a scenario, report. At most about 45 seconds in total, at most 4 screenshots.

```json
{"scene":"scene1","stop":true,"steps":[
 {"do":"wait","ms":1000},
 {"do":"screenshot","label":"start"},
 {"do":"read","elements":["player"]},
 {"do":"joystick","element":"joy","jx":1,"jy":0,"ms":1000},
 {"do":"assert","element":"player","prop":"x","op":">","value":150},
 {"do":"tap","element":"jumpBtn"},
 {"do":"wait","ms":250},
 {"do":"assert","element":"player","prop":"vy","op":">","value":0},
 {"do":"wait","ms":1200},
 {"do":"assert","prop":"var:score","op":">=","value":1},
 {"do":"assert","element":"scoreText","prop":"text","op":"==","value":"Score: 1"},
 {"do":"screenshot","label":"after"}
]}
```

Steps: any `game_input` action (`tap`, `hold`, `down`, `move`, `up`, `release`, `swipe`, `key`, `joystick`, `wait`), plus `screenshot` (with `label`), `read` (`elements`: values returned), `assert`.

`assert` fields: `element` (not needed for variables), `prop` = `x|y|vx|vy|width|height|rotation|visible|text|progress|var:NAME`, `op` = `==|!=|>|<|>=|<=|near`, `value`, `tol` (for `near`, default 1). Numbers compare numerically, other values as text. The report has `pass`/`actual` for each assert and `all_assertions_passed`. Assert values are in the units of `game_state` (world units for world elements, see above). Prefer relative checks (`x` greater than before, `vy` greater than 0 after a jump, a variable increased) over exact positions.

Include `start:false` to test the game that is already running. Use `scene` to start from another scene.

## Loading and fingers

- `run_game` (and `run_test` with `start`) return only when the game finished loading: the reply says `ready:true`. If you start `game_input` / `screenshot` earlier the tool waits for the loading screen to end by itself, so never guess waiting times after a start.
- A finger pressed with `down` STAYS pressed after the call (even across `wait` steps and separate calls) until you send `up` or `{"do":"release"}`; it is released automatically after 60 s without input. Look at the game with `screenshot` / `game_state` while holding.
- `hold` = `tap` with `ms`: `{"do":"hold","element":"jumpBtn","ms":800}`.
- Tests run in real time on the phone (45 s per call). For a long scenario use several `run_test` calls with `"start":false`; the game keeps running between them.

## game_input

```json
{"actions":[
 {"do":"tap","element":"playBtn"},
 {"do":"tap","x":0.5,"y":0.5},
 {"do":"swipe","x":0.2,"y":0.5,"to":{"x":0.8,"y":0.5},"ms":300},
 {"do":"down","element":"joy","ms":0},{"do":"move","x":0.15,"y":0.8,"ms":400},{"do":"up","x":0.15,"y":0.8},
 {"do":"joystick","element":"joy","jx":-1,"jy":0,"ms":700},
 {"do":"key","key":"SPACE","ms":150},
 {"do":"wait","ms":500}
]}
```
- Positions: `{element:"name"}` (center of that element, best choice), `{x,y}` as 0 to 1 fractions of the whole phone screen (0,0 = top-left; the game may be letterboxed inside it), or `{px,py}` raw screen pixels.
- `tap` holds 70 ms by default (`ms` to hold longer). `key` names: `LEFT RIGHT UP DOWN SPACE ENTER BACK A..Z 0..9`.
- `joystick`: `element` is the JOYSTICK, `jx`/`jy` -1..1, `ms` how long to hold (default 500).
- Total 45 seconds per call; it returns `took_ms`, and `runtime_error` if the game threw an exception.
- Keys are visible to `Gdx.input.isKeyPressed`; touches go through the normal UI, so `onClick`, `onTouchStart` and joysticks react like real fingers.

## How to test what you built (checklist)

1. **Visual**: `screenshot` right after start. Are all elements visible, the right size, not the Star2D logo (= wrong image path), HUD readable, nothing stuck at the bottom-left corner (= position or unit mistake)?
2. **Movement**: joystick/keys for 1 second then `assert` x or `vx`.
3. **Jump**: `tap` the button, wait 250 ms, `assert vy > 0` (the sign: y is up), wait, `assert` back on the ground.
4. **Pickups/score**: move onto the coin (drive the player with the joystick or place the coin next to the start while testing), then `assert var:score`.
5. **Enemies/damage/game over**: let the enemy run into the player; check the scene changed (`screenshot` shows the other scene) or a variable changed.
6. **Spawners and timers**: wait 3 seconds, `game_state` without `elements` shows all clones (the list is capped at 120).
7. **Logs**: `game_logs` at the end. Any `runtime_errors` mean a bug even if asserts passed.

## When something is wrong

- Nothing moves: did `OnCreate` run? (`setWorldGravity` present, no exception in `game_logs`.) Is the element `DYNAMIC` and not `UI`? Is `Active` true?
- Moves far too fast: forgot `setWorldGravity` (the physics runs 6 times per frame).
- Falls through the floor: floor too thin for the speed, or the player has `isSensor` true, or the `Collision` text excludes it.
- Events do not fire: wrong scope/event name (`events list` shows what exists), element `Script` property points elsewhere, element `Visible` false for taps.
- Compile error mentioning `final`: see `events` (timers and event variables).
- Use the blocks `Toast` / `debug` (with a quoted message) or `debug(...)` in Java, then `game_logs`.
