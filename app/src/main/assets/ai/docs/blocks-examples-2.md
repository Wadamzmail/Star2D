# Block examples (2 of 2): shooting, spawning, game over, saving, camera, animation

Same conventions as `blocks-examples` (field labels, quotes only in raw-Java fields, `final` copies before timers).

Assumed extra elements: BOX `bulletTemplate` (DYNAMIC, `Gravity Scale` 0, `Bullet` true, `Collision` `(player)`, placed off-screen at x -300), BOX `meteor` (DYNAMIC, off-screen template), BOX `fireBtn` (UI), TEXT `msg` (UI), PROGRESS `hpBar` (UI, Max 100). Variables: `shots` int, `spawned` int, `lives` int = 3, `walking` boolean = false, `enemyDir` int = 1.

## 9. Shooting: element `onTouchStart` of `fireBtn`

```json
[
 {"block":"changeVariable","fields":{"variable":"shots","by":1}},
 {"block":"spawnCopy","fields":{
   "body":"bulletTemplate","var":"b",
   "newName":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["bullet"]},"shots"]},
   "x":{"r":"___ + ___","args":[{"r":"x of ___","args":["player"]},70]},
   "y":{"r":"y of ___","args":["player"]}}},
 {"block":"setVelocityX","fields":{"body":"b","speed":800}},
 {"block":"codeLine","fields":{"code":"final PlayerItem fb = b;"}},
 {"block":"destroyAfter","fields":{"body":"fb","seconds":2}},
 {"block":"playSound","fields":{"Sound":"shoot.wav"}}
]
```
`spawnCopy` clones the template at a pixel position (center) and stores the clone in a new Java variable (`var`). Clone names must be unique (`bullet1`, `bullet2`...), always clone the original template, never a clone. Bullets are cleaned up after 2 s. The `Collision` filter `(player)` on the template stops the bullet from pushing the player.

## 10. Spawning on a timer: scene `OnCreate` (falling meteors)

```json
[
 {"block":"setWorldGravity","fields":{"strength":300}},
 {"block":"everySeconds","fields":{"seconds":1.2},"do":[
   {"block":"changeVariable","fields":{"variable":"spawned","by":1}},
   {"block":"spawnCopy","fields":{
     "body":"meteor","var":"m",
     "newName":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["meteor"]},"spawned"]},
     "x":{"r":"random ___ to ___","args":[60,1220]},
     "y":760}},
   {"block":"codeLine","fields":{"code":"final PlayerItem fm = m;"}},
   {"block":"destroyAfter","fields":{"body":"fm","seconds":8}}
 ]}
]
```
`everySeconds` repeats until the scene ends. Elements and variables of the scene are fine inside the timer; `m` is a local, hence the `final` copy. To stop spawning, wrap the body in `ifThen` with a boolean variable (`running`).

## 11. Damage, lives, health bar, game over: scene `onCollisionStart` part

```json
[
 {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().startsWith(\"meteor\")) || (body2.getName().equals(\"player\") && body1.getName().startsWith(\"meteor\"))"},
  "do":[
   {"block":"changeVariable","fields":{"variable":"lives","by":-1}},
   {"block":"setProgress","fields":{"progress":"hpBar","value":{"r":"___ * ___","args":["lives",33]}}},
   {"block":"if","fields":{"boolean":{"r":"___ <= ___","args":["lives",0]}},
    "do":[
      {"block":"saveData","fields":{"key":"last","value":"score"}},
      {"block":"OpenScene","fields":{"scene":"gameover"}}
    ]}
  ]}
]
```
`setProgress` casts the value to int. The `hpBar` `Max` property should be 100 (3 lives = 99).

## 12. Best score and the `gameover` scene

Before leaving the game scene (in the block above, or any win/lose point):

```json
[
 {"block":"if","fields":{"boolean":{"r":"___ > ___","args":["score",{"r":"saved number ___","args":["best"]}]}},
  "do":[{"block":"saveData","fields":{"key":"best","value":"score"}}]},
 {"block":"saveData","fields":{"key":"last","value":"score"}},
 {"block":"OpenScene","fields":{"scene":"gameover"}}
]
```

In scene `gameover`, `OnCreate` (TEXT elements `resultText`, `bestText`):

```json
[
 {"block":"setText","fields":{"ui":"resultText","text":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["Score: "]},{"r":"saved number ___","args":["last"]}]}}},
 {"block":"setText","fields":{"ui":"bestText","text":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["Best: "]},{"r":"saved number ___","args":["best"]}]}}}
]
```

Restart button, element `onClick` of `retryBtn` in scene `gameover`:

```json
[{"block":"OpenScene","fields":{"scene":"scene1"}}]
```

Saved values survive scene changes and app restarts (`saveData` stores text; `saved number` reads an int; an unset key reads 0). Scene variables like `score` are reset when a scene starts, so set `lives` etc. in `OnCreate` or give the variables their start value.

## 13. Camera follow (scrolling levels)

Continuous follow, set once in scene `OnCreate` (the engine then recenters on the element every frame):

```json
[
 {"block":"codeLine","fields":{"code":"cameraFollowX(player); cameraFollowY(player);"}},
 {"block":"setCameraOffset","fields":{"offsetX":0,"offsetY":40}}
]
```

Or with blocks only, in scene `OnStep` (each frame re-centers once):

```json
[{"block":"cameraFollow","fields":{"body":"player"}}]
```

The camera offset is in world units (px * 0.25), so 40 raises the view by 160 px.

Zoom: `{"block":"setZoom","fields":{"zoom":4}}`: 4 is the default full view, 6 is closer, 3 is farther. Camera x/y blocks use world units (px * 0.25). The camera has no limits: make the background big enough or end the level before the edge. HUD elements with `type` UI stay fixed on screen.

## 14. Walk/idle animation without restarting it every frame

Variable `walking` (boolean). Scene `OnStep`:

```json
[
 {"block":"if","fields":{"boolean":{"r":"___ and ___","args":[
     {"r":"___ > ___","args":[{"r":"abs ___","args":[{"r":"speed x of ___","args":["player"]}]},20]},
     {"r":"not ___","args":["walking"]}]}},
  "do":[
    {"block":"setVariable","fields":{"variable":"walking","value":"true"}},
    {"block":"setAnimation","fields":{"body":"player","animation":"walk"}}
  ]},
 {"block":"if","fields":{"boolean":{"r":"___ and ___","args":[
     {"r":"___ <= ___","args":[{"r":"abs ___","args":[{"r":"speed x of ___","args":["player"]}]},20]},
     "walking"]}},
  "do":[
    {"block":"setVariable","fields":{"variable":"walking","value":"false"}},
    {"block":"setImage","fields":{"body":"player","image":"hero_idle.png"}}
  ]}
]
```
`setAnimation` restarts the animation at frame 0 every time it is called, so call it only when the state changes. `setImage` (or `removeAnimation`) stops the animation. Animation files: see `assets`.

## 15. More small patterns

Paddle follows the finger (OnStep, paddle is KINAMATIC):

```json
[{"block":"ifThen","fields":{"boolean":{"r":"screen is touched","args":[]}},
  "do":[{"block":"goTo","fields":{"body":"paddle","x":{"r":"touch x","args":[]},"y":80}}]}]
```

Delayed scene change after a message (`waitSeconds` runs its inner blocks later):

```json
[
 {"block":"showText","fields":{"ui":"msg","text":"\"You win!\""}},
 {"block":"waitSeconds","fields":{"seconds":2},"do":[{"block":"OpenScene","fields":{"scene":"level2"}}]}
]
```

Show / hide a panel and start music (OnCreate):

```json
[
 {"block":"setVisibility","fields":{"item":"helpPanel","visibility":"false"}},
 {"block":"loopSound","fields":{"Sound":"music.mp3","repeat?":"true"}},
 {"block":"playSound","fields":{"Sound":"music.mp3"}}
]
```

Repeat N times (`repeatTimes` declares the loop variable `i`):

```json
[{"block":"repeatTimes","fields":{"times":5,"var":"i"},"do":[
  {"block":"changeVariable","fields":{"variable":"score","by":"i"}}
]}]
```
