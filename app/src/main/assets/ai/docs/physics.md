# Physics (Box2D): bodies, units, collisions, typical numbers

## The one unit rule

- **Everything you type in `elements`, and every "px" block field, is in editor pixels** of the scene's logic screen.
- The engine simulates in **world units = pixels * 0.25** (`WORLD_SCALE = 0.25`). You only meet world units in: the raw Box2D blocks (`setLinearVelocity`, `setTransform`, `setX`, `setY`, `applyForce*`, `applyLinearImpulse*`, `setCameraXY`/`setCameraX`/`setCameraY`/`setCameraCenter`/`setCameraOffset`), the reporters `___ .getX()`, `___ .getY()`, `width of ___`, `height of ___`, and the numbers returned by `game_state` for world elements (x, y, width, height are world units there; multiply by 4 for pixels; `vx`, `vy` are raw Box2D velocities).
- Pixel-based blocks (use these): `moveBy`, `goTo`, `goToBody`, `setVelocityX`, `setVelocityY`, `jump`, `moveToward`, `moveWithJoystick`, `walkWithJoystick`, `rotateBy`, `spawnCopy`, and the reporters `x of ___`, `y of ___` (the element's CENTER), `speed x of ___`, `speed y of ___`, `distance ___ to ___`, `touch x`, `touch y`, `camera x`, `camera y`.
- y is up. An element at `y: 0` sits on the bottom edge of the screen.

## Step 1 of every physical scene: set gravity with `setWorldGravity`

By default the engine runs the physics 6 times per frame (6x real-time speed), so speeds in px/s would be about 6x too fast and the default gravity is about 1400 px/s2. The block **`setWorldGravity`** sets "steps per frame = 1" and gravity to `strength` px/s2. After it, all px/s numbers in blocks are true pixels per real second. Put it FIRST in `OnCreate` of every scene that has dynamic bodies:

```json
{"block":"setWorldGravity","fields":{"strength":800}}
```

Platformer/side view: strength 700 to 1000 (see the speed limit below). Top-down or space game: strength 0 (nothing falls). Floaty moon: 500. Do not use the `setGravity` block unless you need a sideways gravity (it takes raw world units like `0f`, `-9.8f`, and keeps the 6x steps).

## Typical numbers (logic screen 1280 x 720, player about 64 x 96 px)

| What | Value |
|---|---|
| Gravity | 800 px/s2 |
| Walk / run speed | 250 to 350 px/s (`setVelocityX`) |
| Jump | `jump` power 400 to 450 px/s (height = power^2 / (2*gravity): 450 gives 126 px with gravity 800) |
| Bullet | 400 to 450 px/s |
| Enemy walker | 80 to 150 px/s |
| Top-down player | 300 px/s |
| Falling/air time of a jump | 2 * power / gravity seconds |

**Speed limit**: Box2D moves a body at most 2 world units (8 px) per physics step, so with one step per frame the speed is capped at about 480 px/s at 60 fps (less when the frame rate drops). A faster `jump` power or velocity is silently cut down. Keep every speed (also falling speed and bullets) at or below about 450 px/s, make walls at least 20 px thick, and set `Bullet` true on fast small bodies. To get a higher jump raise the power only up to 450 and LOWER the gravity instead. Always confirm real speeds with `game_state` (`vx`, `vy` are world units per second: px/s = value * 4).

## Body types (`type` property)

- `DYNAMIC`: simulated. Player, enemies, bullets, balls, crates.
- `STATIC`: fixed forever. Ground, walls, platforms. Cheap; use for everything that never moves.
- `KINAMATIC`: moves only because YOU set its velocity (`setVelocityX/Y`, `setLinearVelocity`), not pushed by others, not affected by gravity. Moving platforms, elevators, rotating bars (`setAngularVelocity`).
- `UI`: no body at all.

## Material properties

- `density` (default 1): heavier bodies push lighter ones. Make crates 0.5 to 2, a pushed ball 1.
- `friction` (default 0 = ice): 0.2 to 0.6 for ground and crates. Characters often keep 0 so they do not stick to walls; friction is mixed between the two touching bodies.
- `restitution` (default **0.5**, bouncy): 0 for player, enemies, ground, walls; 0.8 to 1 for balls and breakout balls.
- `Fixed Rotation: true` for characters so they stay upright.
- `Gravity Scale`: 0 for things that must float (pickups that move, bullets flying straight). The block `setGravityScale` takes raw Java (`0` or `0.5f`).
- Damping: block `setLinearDamping` (value like 2 to 8) slows bodies down like drag; good for top-down friction.

## Moving things (prefer velocities, avoid forces)

- Run: `setVelocityX` (keeps vertical speed). Stop: `setVelocityX` with 0. Jump: `jump` (sets vertical speed). Top-down: `setVelocityX` + `setVelocityY`, or `moveWithJoystick`.
- Chase: `moveToward(body, target, speed)` sets the velocity toward another element.
- Teleport: `goTo(body, x, y)` (pixel CENTER position) or `moveBy`. Teleporting keeps the velocity: follow with `stopMoving` if needed.
- `rotateBy(body, degrees)` rotates instantly.
- Forces and impulses (`applyForce*`, `applyLinearImpulse*`) are in raw Box2D units and depend on mass: avoid them. If you must, start with small numbers and check with `game_state`.
- Do not move a DYNAMIC body every frame with `goTo` while it should collide (it will tunnel). Use velocities.

## Collisions

Two bodies collide (push each other) unless one of them is a sensor or the `Collision` filter excludes the pair.

- **Sensor (`isSensor: true`)**: overlap is detected, nothing is pushed or blocked. Ideal for coins, goals, damage zones, pickups, checkpoints. Sensors still fire collision events and count for the reporter `___ touches ___`. A sensor DYNAMIC body still falls with gravity: use `STATIC` sensors, or `Gravity Scale: 0`.
- **`Collision` property**: a text of names, e.g. `(enemy)(coin)`. Pairs where either body's `Collision` text contains the other's name do not collide AND fire no events. Use it for "player passes through enemy bullets" style filtering, not for pickups.
- **Events**: use the SCENE event `onCollisionStart` (variables `body1`, `body2`, both `PlayerItem`) and `onCollisionEnd`. Test who collided with the reporter `___ is named ___` (args: an element variable, the plain name) or with a raw Java condition such as `body2.getName().startsWith("coin")` in an `ifThen`. The element events `onBodyCollided`/`onBodyCollideEnd` exist, but the engine currently runs the first body's handler twice and never the second body's own handler, so they are unreliable: prefer the scene events (see `events`).
- `___ touches ___` (reporter) / `ifCollision` (block): true while the two named elements are in contact.
- Never create, destroy or deactivate bodies inside a collision event (the physics world is locked and a new body may be null). To remove something that was touched use `hide` plus the helper `destroyLater(item, 0.05)` (defined in `blocks`; it waits a moment on a timer and is safe to call twice). `hide` alone is safe, but a hidden sensor still triggers. The built-in `destroy`/`destroyAfter` blocks are NOT safe to run twice on the same element (a second destroy can crash the game).

## Grounded check for jumping

Option A (simple): jump only when the vertical speed is about zero: `ifThen` with `{"r":"abs ___","args":[{"r":"speed y of ___","args":["player"]}]} < 20`. Works while standing; allows a jump at the top of an arc.
Option B (accurate): count ground contacts with a variable `ground` (int): in `onCollisionStart` add 1 when the pair is player and an element whose name starts with `ground`; subtract 1 in `onCollisionEnd`. Jump only if `ground > 0`. See `blocks-examples`.

## Clones and bullets

`spawnCopy(body, var, newName, x, y)` clones an element at a pixel position (center) while the game runs. Clone from the original template element (keep a hidden or off-screen template, or the original itself), give each clone a unique name (join a counter), and clean up with `destroyLater`. The clone keeps the template's properties and events. A clone created inside a collision event must be avoided.

## Quick recipes of physics properties

- Player: `type DYNAMIC, Fixed Rotation true, restitution 0, friction 0`.
- Ground/wall: `type STATIC, restitution 0, friction 0.4`.
- Coin: `type STATIC, isSensor true`.
- Enemy walker: `type DYNAMIC, Fixed Rotation true, restitution 0`, velocity set each time it turns.
- Bullet: `type DYNAMIC, Gravity Scale 0, Bullet true, isSensor true` (or normal) and `destroyLater` 2 seconds.
- Ball (breakout/pong): `CIRCLE, DYNAMIC, restitution 1, friction 0, Gravity Scale 0`, paddle `KINAMATIC` or `STATIC` moved by `goTo` / velocity, walls `STATIC`.
