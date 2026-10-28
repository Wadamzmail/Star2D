# Recipes (1 of 2): platformer, top-down shooter, tap game

Each recipe is an ordered list of tool calls with concrete JSON for a 1280 x 720 landscape game. Adapt names and numbers. Remember: read `elements`, `physics`, `blocks` first; after the last call do `run_test` (see `testing`) and fix what you see. `{...}` in prose is not JSON.

Common first calls for every recipe (call numbers continue below):
0. `define_blocks` with the `destroyLater` helper from the last section of `blocks` (once per project; recipes 1 and 2 use it).
1. `scenes` `{"action":"configure","name":"scene1","config":{"or":"landscape","logicWidth":1280,"logicHeight":720,"sceneColor":"#87CEEB"}}`
2. `create_image` `{"path":"images/white.png","kind":"shape","shape":"rect","width":16,"height":16,"fill":"#FFFFFF"}` (flat colored shapes via `Tint`). One call per image.

## 1. Platformer "Coin Run": joystick, jump button, coins, patrolling enemy, game over

3. `create_image` `{"path":"images/coin.png","kind":"shape","shape":"circle","width":32,"height":32,"fill":"radial:#FFF59D,#FBC02D","stroke":"#F57F17","strokeWidth":2}`
4. `create_sound` `{"path":"sounds/jump.wav","preset":"jump"}`, `{"path":"sounds/coin.wav","preset":"coin"}`, `{"path":"sounds/hit.wav","preset":"hit"}`
5. `variables`
```json
{"ops":[{"op":"add","name":"score","type":"int","value":"0"},{"op":"add","name":"groundContacts","type":"int","value":"0"},{"op":"add","name":"enemyDir","type":"int","value":"1"}]}
```
6. `elements` (one call). Platform names start with `ground` so one test covers them.
```json
{"ops":[
 {"op":"add","type":"BOX","name":"ground","props":{"x":0,"y":0,"width":1280,"height":60,"type":"STATIC","image":"/white.png","Tint":"#5D4037","friction":0.4,"restitution":0,"z":1}},
 {"op":"add","type":"BOX","name":"ground_p1","props":{"x":300,"y":120,"width":250,"height":30,"type":"STATIC","image":"/white.png","Tint":"#8D6E63","friction":0.4,"restitution":0,"z":1}},
 {"op":"add","type":"BOX","name":"ground_p2","props":{"x":700,"y":210,"width":250,"height":30,"type":"STATIC","image":"/white.png","Tint":"#8D6E63","friction":0.4,"restitution":0,"z":1}},
 {"op":"add","type":"BOX","name":"player","props":{"x":100,"y":80,"width":50,"height":70,"type":"DYNAMIC","image":"/white.png","Tint":"#E53935","Fixed Rotation":true,"restitution":0,"friction":0,"z":5}},
 {"op":"add","type":"BOX","name":"enemy1","props":{"x":900,"y":62,"width":50,"height":50,"type":"DYNAMIC","image":"/white.png","Tint":"#6A1B9A","Fixed Rotation":true,"restitution":0,"friction":0,"z":5}},
 {"op":"add","type":"CIRCLE","name":"coin1","props":{"x":380,"y":180,"radius":16,"type":"STATIC","isSensor":true,"image":"/coin.png","z":3}},
 {"op":"add","type":"CIRCLE","name":"coin2","props":{"x":460,"y":180,"radius":16,"type":"STATIC","isSensor":true,"image":"/coin.png","z":3}},
 {"op":"add","type":"CIRCLE","name":"coin3","props":{"x":780,"y":270,"radius":16,"type":"STATIC","isSensor":true,"image":"/coin.png","z":3}},
 {"op":"add","type":"CIRCLE","name":"coin4","props":{"x":1100,"y":80,"radius":16,"type":"STATIC","isSensor":true,"image":"/coin.png","z":3}},
 {"op":"add","type":"TEXT","name":"scoreText","props":{"x":30,"y":640,"width":500,"height":60,"Text":"Score: 0","Font Scale":4,"z":100}},
 {"op":"add","type":"JOYSTICK","name":"joy","props":{"x":40,"y":40,"width":300,"height":300,"z":100}},
 {"op":"add","type":"BOX","name":"jumpBox","props":{"type":"UI","x":1090,"y":60,"width":150,"height":150,"image":"/white.png","Tint":"#43A047AA","z":100}},
 {"op":"add","type":"TEXT","name":"jumpBtn","props":{"x":1090,"y":60,"width":150,"height":150,"Text":"JUMP","Font Scale":4,"z":101}}
]}
```
7. `events` (one call)
```json
{"ops":[
 {"op":"set","scope":"scene","event":"OnCreate","blocks":[{"block":"setWorldGravity","fields":{"strength":800}}]},
 {"op":"set","scope":"scene","event":"OnStep","blocks":[
   {"block":"walkWithJoystick","fields":{"body":"player","joystick":"joy","speed":300}},
   {"block":"if","fields":{"boolean":{"r":"___ > ___","args":[{"r":"x of ___","args":["enemy1"]},1150]}},"do":[{"block":"setVariable","fields":{"variable":"enemyDir","value":-1}}]},
   {"block":"if","fields":{"boolean":{"r":"___ < ___","args":[{"r":"x of ___","args":["enemy1"]},800]}},"do":[{"block":"setVariable","fields":{"variable":"enemyDir","value":1}}]},
   {"block":"setVelocityX","fields":{"body":"enemy1","speed":{"r":"___ * ___","args":["enemyDir",120]}}}]},
 {"op":"set","scope":"element","element":"jumpBox","event":"onTouchStart","blocks":[
   {"block":"ifThen","fields":{"boolean":{"r":"___ > ___","args":["groundContacts",0]}},"do":[
     {"block":"jump","fields":{"body":"player","power":450}},{"block":"playSound","fields":{"Sound":"jump.wav"}}]}]},
 {"op":"set","scope":"scene","event":"onCollisionStart","blocks":[
   {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().startsWith(\"ground\")) || (body2.getName().equals(\"player\") && body1.getName().startsWith(\"ground\"))"},"do":[{"block":"changeVariable","fields":{"variable":"groundContacts","by":1}}]},
   {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().startsWith(\"coin\")) || (body2.getName().equals(\"player\") && body1.getName().startsWith(\"coin\"))"},"do":[
     {"block":"codeLine","fields":{"code":"PlayerItem hit = body1.getName().startsWith(\"coin\") ? body1 : body2;"}},
     {"block":"changeVariable","fields":{"variable":"score","by":1}},
     {"block":"setText","fields":{"ui":"scoreText","text":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["Score: "]},"score"]}}},
     {"block":"playSound","fields":{"Sound":"coin.wav"}},
     {"block":"hide","fields":{"body":"hit"}},{"block":"destroyLater","fields":{"item":"hit","seconds":0.05}}]},
   {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().equals(\"enemy1\")) || (body2.getName().equals(\"player\") && body1.getName().equals(\"enemy1\"))"},"do":[
     {"block":"playSound","fields":{"Sound":"hit.wav"}},{"block":"OpenScene","fields":{"scene":"gameover"}}]}]},
 {"op":"set","scope":"scene","event":"onCollisionEnd","blocks":[
   {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().startsWith(\"ground\")) || (body2.getName().equals(\"player\") && body1.getName().startsWith(\"ground\"))"},"do":[{"block":"changeVariable","fields":{"variable":"groundContacts","by":-1}}]}]}
]}
```
8. Second scene: `scenes` `{"action":"create","name":"gameover","config":{"or":"landscape","logicWidth":1280,"logicHeight":720,"sceneColor":"#212121"}}`, then
`elements` `{"ops":[{"op":"add","type":"TEXT","name":"title","props":{"x":340,"y":420,"width":600,"height":100,"Text":"GAME OVER","Font Scale":8}},{"op":"add","type":"TEXT","name":"retry","props":{"x":440,"y":250,"width":400,"height":100,"Text":"TAP TO RETRY","Font Scale":5}}]}` and
`events` `{"ops":[{"op":"set","scope":"element","element":"retry","event":"onClick","blocks":[{"block":"OpenScene","fields":{"scene":"scene1"}}]}]}`. Finally `scenes` `{"action":"open","name":"scene1"}`.
9. Test: `run_test` `{"steps":[{"do":"wait","ms":800},{"do":"tap","element":"jumpBox"},{"do":"wait","ms":300},{"do":"assert","element":"player","prop":"vy","op":">","value":0},{"do":"screenshot","label":"jump"}]}` (see `testing`).

## 2. Top-down shooter "Blaster": joystick moves, button fires, enemies spawn

Gravity off. Templates `enemyT` and `bulletT` live off-screen and are cloned. `Collision` uses TEMPLATE names (clones inherit their template's name for this filter).

3. `variables`: `score` int 0, `lives` int 3, `shots` int 0, `spawned` int 0.
4. `create_image` circle ship/enemy/bullet if desired; here plain white boxes with `Tint`. Sounds: `shoot.wav` (shoot), `explosion.wav` (explosion).
5. `elements`
```json
{"ops":[
 {"op":"add","type":"BOX","name":"player","props":{"x":100,"y":330,"width":60,"height":40,"type":"DYNAMIC","image":"/white.png","Tint":"#29B6F6","Fixed Rotation":true,"restitution":0,"z":5}},
 {"op":"add","type":"BOX","name":"bulletT","props":{"x":-300,"y":-300,"width":24,"height":8,"type":"DYNAMIC","image":"/white.png","Tint":"#FFEB3B","Gravity Scale":0,"Bullet":true,"Collision":"(player)(bulletT)","z":4}},
 {"op":"add","type":"BOX","name":"enemyT","props":{"x":-300,"y":-100,"width":50,"height":50,"type":"DYNAMIC","image":"/white.png","Tint":"#EF5350","Gravity Scale":0,"Fixed Rotation":true,"Collision":"(enemyT)","restitution":0,"z":4}},
 {"op":"add","type":"TEXT","name":"scoreText","props":{"x":30,"y":650,"width":500,"height":60,"Text":"Score: 0   Lives: 3","Font Scale":4,"z":100}},
 {"op":"add","type":"JOYSTICK","name":"joy","props":{"x":40,"y":40,"width":300,"height":300,"z":100}},
 {"op":"add","type":"BOX","name":"fireBox","props":{"type":"UI","x":1060,"y":60,"width":180,"height":180,"image":"/white.png","Tint":"#E53935AA","z":100}},
 {"op":"add","type":"TEXT","name":"fireBtn","props":{"x":1060,"y":60,"width":180,"height":180,"Text":"FIRE","Font Scale":5,"z":101}}
]}
```
(`enemyT` sits off-screen at x -300 and never moves: DYNAMIC with `Gravity Scale` 0 and zero gravity.)
6. `events`
```json
{"ops":[
 {"op":"set","scope":"scene","event":"OnCreate","blocks":[
   {"block":"setWorldGravity","fields":{"strength":0}},
   {"block":"everySeconds","fields":{"seconds":1},"do":[
     {"block":"changeVariable","fields":{"variable":"spawned","by":1}},
     {"block":"spawnCopy","fields":{"body":"enemyT","var":"e","newName":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["enemy"]},"spawned"]},"x":1320,"y":{"r":"random ___ to ___","args":[40,680]}}},
     {"block":"setVelocityX","fields":{"body":"e","speed":-220}},
     {"block":"destroyLater","fields":{"item":"e","seconds":8}}]}]},
 {"op":"set","scope":"scene","event":"OnStep","blocks":[
   {"block":"moveWithJoystick","fields":{"body":"player","joystick":"joy","speed":350}}]},
 {"op":"set","scope":"element","element":"fireBox","event":"onTouchStart","blocks":[
   {"block":"changeVariable","fields":{"variable":"shots","by":1}},
   {"block":"spawnCopy","fields":{"body":"bulletT","var":"b","newName":{"r":"join ___ ___","args":[{"r":"\"___\"","args":["bullet"]},"shots"]},"x":{"r":"___ + ___","args":[{"r":"x of ___","args":["player"]},50]},"y":{"r":"y of ___","args":["player"]}}},
   {"block":"setVelocityX","fields":{"body":"b","speed":450}},
   {"block":"destroyLater","fields":{"item":"b","seconds":2}},
   {"block":"playSound","fields":{"Sound":"shoot.wav"}}]},
 {"op":"set","scope":"scene","event":"onCollisionStart","blocks":[
   {"block":"ifThen","fields":{"boolean":"(body1.getName().startsWith(\"bullet\") && body2.getName().startsWith(\"enemy\")) || (body2.getName().startsWith(\"bullet\") && body1.getName().startsWith(\"enemy\"))"},"do":[
     {"block":"hide","fields":{"body":"body1"}},{"block":"hide","fields":{"body":"body2"}},
     {"block":"destroyLater","fields":{"item":"body1","seconds":0.05}},{"block":"destroyLater","fields":{"item":"body2","seconds":0.05}},
     {"block":"changeVariable","fields":{"variable":"score","by":10}},
     {"block":"playSound","fields":{"Sound":"explosion.wav"}}]},
   {"block":"ifThen","fields":{"boolean":"(body1.getName().equals(\"player\") && body2.getName().startsWith(\"enemy\")) || (body2.getName().equals(\"player\") && body1.getName().startsWith(\"enemy\"))"},"do":[
     {"block":"codeLine","fields":{"code":"PlayerItem hurt = body1.getName().startsWith(\"enemy\") ? body1 : body2;"}},
     {"block":"hide","fields":{"body":"hurt"}},{"block":"destroyLater","fields":{"item":"hurt","seconds":0.05}},
     {"block":"changeVariable","fields":{"variable":"lives","by":-1}},
     {"block":"if","fields":{"boolean":{"r":"___ <= ___","args":["lives",0]}},"do":[
       {"block":"saveData","fields":{"key":"last","value":"score"}},{"block":"OpenScene","fields":{"scene":"gameover"}}]}]}]}
]}
```
7. Show score and lives in `OnStep` (add to the OnStep blocks): `{"block":"setText","fields":{"ui":"scoreText","text":"\"Score: \" + score + \"   Lives: \" + lives"}}`. Then create the `gameover` scene like recipe 1 step 8.
8. Test: `run_test` with `{"do":"tap","element":"fireBox"}`, `{"do":"joystick","element":"joy","jx":0,"jy":1,"ms":500}`, `{"do":"read","elements":["player"]}`.

## 3. Tap game "Whack the Target": score by tapping, time limit

1. `variables`: `score` int 0, `timeLeft` int 30, `running` boolean true.
2. `create_image` `{"path":"images/target.png","kind":"shape","shape":"circle","width":128,"height":128,"fill":"radial:#FF8A80,#D50000","stroke":"#FFFFFF","strokeWidth":6}`.
3. `elements`: a TEXT `scoreText` (x 30, y 650, UI), TEXT `timeText` (x 1000, y 650, UI), and a BOX `target` `{"x":600,"y":300,"width":128,"height":128,"type":"STATIC","image":"/target.png","z":5}`.
4. `events`:
```json
{"ops":[
 {"op":"set","scope":"scene","event":"OnCreate","blocks":[
   {"block":"everySeconds","fields":{"seconds":1},"do":[
     {"block":"ifThen","fields":{"boolean":"running"},"do":[
       {"block":"changeVariable","fields":{"variable":"timeLeft","by":-1}},
       {"block":"setText","fields":{"ui":"timeText","text":"\"Time: \" + timeLeft"}},
       {"block":"if","fields":{"boolean":{"r":"___ <= ___","args":["timeLeft",0]}},"do":[
         {"block":"setVariable","fields":{"variable":"running","value":"false"}},
         {"block":"setText","fields":{"ui":"timeText","text":"\"TIME UP\""}}]}]}]}]},
 {"op":"set","scope":"element","element":"target","event":"onClick","blocks":[
   {"block":"ifThen","fields":{"boolean":"running"},"do":[
     {"block":"changeVariable","fields":{"variable":"score","by":1}},
     {"block":"setText","fields":{"ui":"scoreText","text":"\"Score: \" + score"}},
     {"block":"goTo","fields":{"body":"target","x":{"r":"random ___ to ___","args":[100,1180]},"y":{"r":"random ___ to ___","args":[100,560]}}}]}]}
]}
```
The target is STATIC and moved with `goTo` (allowed for non-colliding statics). `running` guards the timer and the taps.
5. Test: `run_test` `{"steps":[{"do":"tap","element":"target"},{"do":"tap","element":"target"},{"do":"assert","prop":"var:score","op":">=","value":1}]}` (the target moves after each tap, so a second tap may miss).
