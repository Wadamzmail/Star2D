# UI, touch, keyboard, camera

## Layout of the HUD

UI elements (`type: "UI"`) are placed in logic pixels, origin bottom-left, and never move with the camera. For a 1280 x 720 landscape game: top-left score `x:30, y:650`, top-right lives `x:1000, y:650`, joystick bottom-left `x:40, y:40`, jump button bottom-right `x:1100, y:60`. In a portrait game the screen is 720 wide and 1280 tall (see `scenes`). Draw order is the `z` value: HUD elements need a higher `z` than the world elements they overlap (give HUD `z` 100+).

## TEXT

```json
{"op":"add","type":"TEXT","name":"scoreText","props":{"x":30,"y":640,"width":400,"height":60,"Text":"Score: 0","Font Scale":4,"Text Color":"#FFFFFF","z":100}}
```

- The default font is small and ASCII only: `Font Scale` 3 to 5 for HUD text on a 1280 x 720 screen, 8+ for titles. A different alphabet needs a `.s2df` font file in `font` (made with the app's font editor; skip it if possible).
- The text is left-aligned and vertically centered in its box and wraps at the box width. To center it, use a block: `{"block":"codeLine","fields":{"code":"((com.badlogic.gdx.scenes.scene2d.ui.Label)scoreText.getActor()).setAlignment(com.badlogic.gdx.utils.Align.center);"}}` in `OnCreate`.

### Text size: how wide and how tall a TEXT must be

The text is drawn with the default font at `Font Scale`. One line is about `Font Scale x (sum of the letter advances)` px wide and `20 x Font Scale` px high. Advances of the default font: capital letters about 10 px (W 17, I 5), small letters about 8, digits 9, space 5, `^` 9. Examples at `Font Scale` 1: `UP` 20, `JUMP` 38, `PLAY` 35, `Score: 0` 56, `Game Over` 83. At Font Scale 4 `UP` needs 80 px.
- If the text is wider than `width` it WRAPS onto a second line (a two-letter label can break between its letters). Fix it with `set`: raise `width` (and `height`), or lower `Font Scale`. The `elements` tool measures every TEXT you add or change and returns a warning with the width it needs; do not ignore it.
- For a label on a button: make the TEXT exactly as big as the button and keep `Font Scale` so that `needed width <= button width - 10`.
- A single symbol (`^`, `<`, `>`, `v`, `+`) never wraps; handy for arrow buttons.
- Any element's `x`, `y`, `width`, `height` (and `radius`) can be changed at any time with `elements` `set`; resizing is the normal way to fix a layout.
- Change it: block `setText` (text is Java: `"\"Lives: \" + lives"` or a `join` reporter) or `showText`. `\n` in `Text` makes a new line.

## Buttons

There is no button element. Make a BOX with `type: "UI"` and an image (or a white `create_image` PNG with `Tint`), plus a TEXT with the SAME `x`, `y`, `width`, `height` and a higher `z`; put the SAME touch event on the TEXT too (it is the topmost element, so it receives the tap; a TEXT without events still swallows the touch and the box below never sees it). The `events` tool does both in one call: `{"op":"set","scope":"element","element":"jumpBox","event":"onTouchStart","blocks":[...],"also_on":["jumpBtn"]}`; `elements` and `events` warn when a TEXT lies on a button. Use `onTouchStart` instead of `onClick` for game controls (instant response), `onClick` for menu buttons.

```json
{"ops":[
 {"op":"add","type":"BOX","name":"playBox","props":{"type":"UI","x":490,"y":300,"width":300,"height":100,"Tint":"#43A047","image":"/white.png","z":50}},
 {"op":"add","type":"TEXT","name":"playBtn","props":{"x":490,"y":300,"width":300,"height":100,"Text":"PLAY","Font Scale":5,"z":51}}
]}
```
Then `events` `{"op":"set","scope":"element","element":"playBtn","event":"onClick","blocks":[{"block":"OpenScene","fields":{"scene":"scene1"}}]}`.

A BOX with `Tint` `#FFFFFF00` (transparent) over the whole screen with `type: "UI"` captures taps anywhere (use its `onTouchStart`, and the reporters `touch x` / `touch y` for the position).

## Touch

- Elements (world and UI) receive `onClick`, `onTouchStart`, `onTouchEnd`; hidden elements (`Visible: false`) do not. If a UI and a world element overlap, the UI one gets the tap when its `z` is not lower.
- Anywhere: reporter `screen is touched` (any finger down), `touch x`, `touch y` (px in world coordinates of the game view, y up). `Gdx.input.getX()`/`getY()` in Java are raw screen pixels; avoid them.
- Drag a piece with the finger: in `OnStep` `ifThen screen is touched` -> `goTo` to `touch x`, `touch y`.
- Swipes and multi-touch are Java only.

## Keyboard (Bluetooth keyboards, emulators)

No block, use a plain Java condition in `ifThen`: `Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SPACE)`, `...Keys.LEFT`, `...Keys.A`; `Gdx.input.isKeyJustPressed(...)` for a single press. The `game_input` and `run_test` tools can send keys (`{"do":"key","key":"SPACE","ms":150}`), so keyboard controls are testable. Phones have no keyboard, so ALWAYS also provide touch controls (joystick, buttons).

## Joystick

```json
{"op":"add","type":"JOYSTICK","name":"joy","props":{"x":40,"y":40,"width":300,"height":300,"z":100}}
```
- Reporters: `joystick x ___` and `joystick y ___` (-1 to 1, y up), `power ___` (squared distance, 0 at rest). Do not use `angle (deg) ___` (it points opposite to the stick).
- Blocks: `walkWithJoystick(body, joystick, speed)` (side view: x only), `moveWithJoystick(body, joystick, speed)` (top-down: x and y), `ifJoystickMoved(joystick)` container (`do` when moved, `else` when idle). Call them in `OnStep`. For a custom mix: `setVelocityX speed = joystick x joy * 300`.
- Test it with `game_input` `{"do":"joystick","element":"joy","jx":1,"jy":0,"ms":800}`.

## Camera

- By default the camera shows the whole logic screen and does not move. The CAMERA element has no effect in this version.
- Follow an element continuously: once in `OnCreate` the Java lines `cameraFollowX(player); cameraFollowY(player);` (via `codeLine`); `setCameraOffset(x, y)` shifts the view (world units). Or the block `cameraFollow(body)` every frame in `OnStep`.
- Fixed position: `setCameraXY(x, y)`, `setCameraCenter(x, y)`: WORLD units (px * 0.25). Example: to center on logic point (640, 360) use 160, 90.
- Zoom: block `setZoom` (4 = whole screen at default, bigger = closer). Reporters `camera x`, `camera y` (px).
- A scrolling level: make the world wider than the screen (ground and walls out to x = 3000, say), follow the player, keep HUD elements `type: "UI"`. A parallax or static background should be a `UI` BOX.
- Shake / screen flash: change `setCameraOffset` for a few frames from `OnStep`, or flash a transparent UI BOX with `setOpacity`.

## Showing and hiding

`show` / `hide` (body), `setVisibility(item, true|false)`, `setOpacity(body, 0..1)`, `setTint(body, RRGGBBAA)`, `bringToFront`, `sendToBack`. Hide a "Game Over" panel (BOX + TEXT, `Visible: false`) and `show` it when needed; hidden elements do not take taps.
