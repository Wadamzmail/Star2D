# Scenes: files, config, orientation, logic size, switching

## What a scene is

A scene is one screen/level: its own elements, events, joints and variables. Files for scene `scene1`:

- `scenes/scene1`: JSON array, one object per element, each with ALL its properties as strings or numbers (see `elements`).
- `configs/scene1.json`: scene settings (below).
- `Events/scene1/...`, `joints/scene1/...`: logic and joints.

`scene1` is the main scene (the game starts there) and cannot be renamed or deleted. Use lowercase names without spaces for other scenes (`level2`, `gameover`): the generated class is `com.star4droid.Game.<name lowercased>`.

## The `scenes` tool

```json
{"action":"list"}
{"action":"create","name":"level2","config":{"or":"landscape","logicWidth":1280,"logicHeight":720,"sceneColor":"#263238"}}
{"action":"open","name":"level2"}
{"action":"configure","name":"scene1","config":{"sceneColor":"#87CEEB","logicWidth":1280,"logicHeight":720,"or":"landscape"}}
{"action":"copy","name":"level1","new_name":"level2"}
{"action":"rename","name":"level2","new_name":"level3"}
{"action":"delete","name":"level3"}
```

`create`, `copy`, `rename`, `open` leave the new scene OPEN; `elements`, `events`, `variables`, `joints` act on the open scene unless you pass `scene`. Unknown config keys are ignored (reported in the answer).

## Config keys (`configs/<scene>.json`)

| Key | Meaning |
|---|---|
| `sceneColor` | Background color, `#RRGGBB` or `#RRGGBBAA`. |
| `Ambient Light` | Color of the ambient light, default `#FFFFFF` (white = lights have no visible effect). Use a DARK color (e.g. `#202030`) when the scene uses LIGHT elements. |
| `or` | `landscape` (also stored as empty string) or `portrait`. |
| `logicWidth` | The LONG side of the logic screen in pixels. |
| `logicHeight` | The SHORT side of the logic screen in pixels. |

Light quality keys (only if you really need them): `Enable Blur`, `Blur Number`, `Enable Culling`, `Gamma Correction`, `Enable Shadows`, `Use Diffuse Light`. They are written by the editor's light settings dialog.

## Orientation and logic size (important)

- The **logic screen** is the coordinate space of your elements, in pixels. The game is scaled to fit the phone screen (letterboxed, aspect ratio kept). Pick ONE size and design everything for it.
- Landscape: game width = `logicWidth`, height = `logicHeight`. Example `1280 x 720`.
- Portrait: width = `logicHeight` (short side), height = `logicWidth` (long side). So a portrait 720 x 1280 game is `{"or":"portrait","logicWidth":1280,"logicHeight":720}`. In other words `logicWidth` is always the long side.
- `configure` always stores the larger number as `logicWidth` (long side). If `status` shows `logic_size_warning` (stored width < height, the frame looks reversed), call `configure` with `{"swap_size":true}`. `logic_size` in `status` is the game screen as the game sees it (already swapped in portrait).
- If the keys are missing the editor uses the phone's screen size. Always set both explicitly.
- Coordinates: origin at the **bottom-left** of the logic screen, x to the right, y UP. Visible area is x in [0, width], y in [0, height] (before any camera movement).
- Check the result with `scene_dump` and a `screenshot` (target `editor`, or `game` while running). The portrait runtime path is less tested than landscape: always verify a portrait game on screen.

## Several scenes

- Each scene is independent: its own physics world, variables, events. Typical games: `scene1` (menu or level), `level2`, `gameover`.
- Switch with the block `OpenScene` (field `scene`, name WITHOUT quotes) or Java `openScene("level2");`. Opening a scene again restarts it from scratch.
- Scene variables are reset when a scene starts. To keep a score or best score across scenes or runs use the block `saveData` and the reporter `saved number ___` (see `variables`).
- `finish` (block) ends the current scene; on the main scene it closes the game.
- The `scenes` tool `open` only changes which scene the EDITOR shows. To start the game from another scene use `run_game` with `scene`.

## A "game over" or menu scene pattern

Create the scene with `scenes create`, then add a TEXT and a BOX button there with `elements` (pass `scene`), and give the button an `onClick` event with the block `OpenScene`. See `recipes`.

## Using UI elements for a background

Elements with `type: "UI"` are drawn on a fixed screen layer (not affected by the camera). A BOX with `type: "UI"`, `x:0, y:0` and the full logic size is a good static background. Use it only when you do not move the camera; otherwise make the background a normal element (`type: "STATIC"`, low `z`) that is big enough.
