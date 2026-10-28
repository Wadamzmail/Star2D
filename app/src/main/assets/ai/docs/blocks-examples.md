# Block examples (1 of 2): movement, jump, pickups, score

Every JSON array below is a complete value for `blocks` in an `events` op. Titles, field labels and reporter texts are the real ones from the catalog. More examples (shooting, spawning, game over, saving, animation, camera) are in `blocks-examples-2`.

Assumed scene (1280 x 720): BOX `player` (DYNAMIC, Fixed Rotation, restitution 0), BOX `ground` and `ground2` (STATIC), BOX `coin1`..`coin5` (STATIC, isSensor), JOYSTICK `joy` (UI), BOX `jumpBtn` (UI), TEXT `scoreText` (UI). Variables (`variables` tool): `score` int = 0, `groundContacts` int = 0.

## 1. Scene `OnCreate`: gravity, first text

```json
[
 {"block":"setWorldGravity","fields":{"strength":1500}},
 {"block":"setVariable","fields":{"variable":"score","value":0}},
 {"block":"setText","fields":{"ui":"scoreText","text":"\"Score: 0\""}}
]
```
The text field is raw Java, so the literal has quotes inside the JSON string.

## 2. Scene `OnStep`: walk with joystick and with the keyboard

```json
[
 {"block":"walkWithJoystick","fields":{"body":"player","joystick":"joy","speed":300}},
 {"block":"ifThen","fields":{"boolean":"Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.LEFT)"},
  "do":[{"block":"setVelocityX","fields":{"body":"player","speed":-300}}]},
 {"block":"ifThen","fields":{"boolean":"Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.RIGHT)"},
  "do":[{"block":"setVelocityX","fields":{"body":"player","speed":300}}]}
]
```
`walkWithJoystick` sets horizontal speed from the stick every frame (0 when idle) and keeps the vertical speed. `moveWithJoystick` (both axes) is for top-down games.

## 3. Element `onTouchStart` of `jumpBtn`: jump only when standing

```json
[
 {"block":"ifThen","fields":{"boolean":{"r":"___ > ___","args":["groundContacts",0]}},
  "do":[
   {"block":"jump","fields":{"body":"player","power":720}},
   {"block":"playSound","fields":{"Sound":"jump.wav"}}
  ]}
]
```
`jump` sets the vertical speed in px/s (needs `setWorldGravity` in OnCreate). Use `onTouchStart` for buttons that must feel instant; `onClick` fires on release.

## 4. Scene `onCollisionStart`: count ground contacts, pick up coins

```json
[
 {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().startsWith(\"ground\")) || (body2.getName().equals(\"player\") && body1.getName().startsWith(\"ground\"))"},
  "do":[{"block":"changeVariable","fields":{"variable":"groundContacts","by":1}}]},
 {"block":"ifThen","fields":{"boolean":"body1.getName().equals(\"player\") && body2.getName().startsWith(\"coin\")"},
  "do":[
   {"block":"codeLine","fields":{"code":"final PlayerItem hit = body2;"}},
   {"block":"changeVariable","fields":{"variable":"score","by":1}},
   {"block":"setText","fields":{"ui":"scoreText","text":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["Score: "]},"score"]}}},
   {"block":"playSound","fields":{"Sound":"coin.wav"}},
   {"block":"hide","fields":{"body":"hit"}},
   {"block":"destroyAfter","fields":{"body":"hit","seconds":0.05}}
  ]},
 {"block":"ifThen","fields":{"boolean":"body2.getName().equals(\"player\") && body1.getName().startsWith(\"coin\")"},
  "do":[
   {"block":"codeLine","fields":{"code":"final PlayerItem hit = body1;"}},
   {"block":"changeVariable","fields":{"variable":"score","by":1}},
   {"block":"setText","fields":{"ui":"scoreText","text":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["Score: "]},"score"]}}},
   {"block":"playSound","fields":{"Sound":"coin.wav"}},
   {"block":"hide","fields":{"body":"hit"}},
   {"block":"destroyAfter","fields":{"body":"hit","seconds":0.05}}
  ]}
]
```
Why it looks like this: (a) `body1`/`body2` come in either order, so both are tested; (b) names with a common prefix (`coin1`, `coin2`...) let one test cover all coins; (c) `destroyAfter` runs on a timer, and `body1`/`body2` are not `final`, so the first line copies the touched coin to a `final` local (`hit`); (d) the body is never destroyed inside the callback itself.

## 5. Scene `onCollisionEnd`: leave the ground

```json
[
 {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().startsWith(\"ground\")) || (body2.getName().equals(\"player\") && body1.getName().startsWith(\"ground\"))"},
  "do":[{"block":"changeVariable","fields":{"variable":"groundContacts","by":-1}}]}
]
```

## 6. Simple one-pair check in `OnStep` with the reporter `___ touches ___`

```json
[
 {"block":"if","fields":{"boolean":{"r":"___ touches ___","args":["player","flag"]}},
  "do":[{"block":"OpenScene","fields":{"scene":"level2"}}]}
]
```
Fine for a single pair with fixed names (a goal flag, a lava pit). The scene opens once per frame while touching, so for anything repeated use a variable guard or a collision event.

## 7. Patrolling enemy in `OnStep` (variable `enemyDir` int = 1)

```json
[
 {"block":"if","fields":{"boolean":{"r":"___ > ___","args":[{"r":"x of ___","args":["enemy1"]},900]}},
  "do":[{"block":"setVariable","fields":{"variable":"enemyDir","value":-1}}]},
 {"block":"if","fields":{"boolean":{"r":"___ < ___","args":[{"r":"x of ___","args":["enemy1"]},500]}},
  "do":[{"block":"setVariable","fields":{"variable":"enemyDir","value":1}}]},
 {"block":"setVelocityX","fields":{"body":"enemy1","speed":{"r":"___ * ___","args":["enemyDir",120]}}}
]
```
`x of ___` is the element's center in px. The enemy walks between x 500 and 900 at 120 px/s.

## 8. Top-down chase and aim in `OnStep`

```json
[
 {"block":"moveToward","fields":{"body":"enemy","target":"player","speed":140}},
 {"block":"lookAt","fields":{"body":"enemy","target":"player"}}
]
```
Use `setWorldGravity` strength 0 in OnCreate for top-down games. `lookAt` rotates the sprite toward the target; the sprite image should point right (east) at rotation 0.

## Reading the examples: checklist

- Every `fields` key is the catalog label (`ui`, `text`, `body`, `speed`, `boolean`, `variable`, `by`, `value`, `scene`, `Sound`).
- Raw-Java fields with text contain quotes; typed fields (`scene`, `Sound`) do not.
- Numbers may be JSON numbers; they are written into the Java as typed.
- Reporters without slots (`touch x`, `screen is touched`, `frame time`) are written `{"r":"touch x","args":[]}` (or without `args`).
