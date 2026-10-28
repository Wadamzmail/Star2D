# Java API (use only when blocks cannot do it)

Blocks first. Raw Java is for: key polling, text alignment, arrays/lists, string work, anything with `final` copies, reading files. Three places for Java:

1. A `codeLine` block or `"java"` in `events` (statements inside an event, no method header).
2. `scripts/<scene>/<anything>.java`: members (fields and methods) pasted into the scene class. Write them with `files`.
3. Real classes under `java/` (compiled with the game): element scripts and scene scripts (below), plus your own helper classes.

**Java source level is 7**: no lambdas, no method references, no `var`, no streams. Anonymous classes and `Timer.Task` may only use `final` locals. libGDX and Box2D classes can be used by full name.

## Names, files and packages

| What | Path | Package / class |
|---|---|---|
| Element script | `java/com/star4droid/Game/Scripts/<scene>/<Script>Script.java` | `package com.star4droid.Game.Scripts.<scene lowercase>;` `public class <Script>Script extends ItemScript` |
| Scene script | `java/com/star4droid/Game/SceneScript/<scenelowercase>Script.java` | `package com.star4droid.game.SceneScript;` (lowercase `game`) `public class <scenelowercase>Script extends SceneScript` |
| Scene members | `scripts/<scene>/<name>.java` (or `.code`) | no package/class: just members, e.g. `int hits = 0; void boom(){ toast("boom"); }` |
| Helpers | `java/<any/package>/Name.java` | normal Java |

- `<Script>` is the element's `Script` property (default = the element name). The script class is attached automatically when its file exists, via `new ...Script().setItem(element).setStage(this)`; it needs a public no-arg constructor (the default one). Keep scene names lowercase so folder and package agree.
- Imports usually needed: `com.star4droid.template.Utils.ItemScript`, `com.star4droid.template.Utils.PlayerItem`, `com.star4droid.template.Utils.SceneScript`, `com.star4droid.template.Items.*`, `com.badlogic.gdx.math.Vector2`, `com.badlogic.gdx.Gdx`, `com.badlogic.gdx.utils.Timer`.
- Scripts and events of the same element both run. After writing Java with `files`, call `build` to see the compile errors.

### ItemScript (element script)

Fields: `playerItem` (PlayerItem), `body` (Box2D `Body`, null for UI), `actor` (scene2d `Actor`), `stage` (the StageImp / scene). Override any of: `onClick()`, `onTouchStart(InputEvent e)`, `onTouchEnd(InputEvent e)`, `onBodyCreated()`, `onBodyUpdate()` (every frame), `onCollisionBegin(PlayerItem other)`, `onCollisionEnd(PlayerItem other)`. Helpers: `findItem(String name)`, `getJoint(String)`, `findLight(String)`, `setImage(String image)` (this element), `getStage()`, `getPlayerItem()`.

```java
package com.star4droid.Game.Scripts.scene1;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
public class enemyScript extends ItemScript {
    public void onCollisionBegin(PlayerItem other){
        if(other.getName().equals("player")) stage.toast("hit!");
    }
}
```
A scripted element receives its own collisions correctly (this avoids the element-event collision problem in `events`).

### SceneScript

```java
package com.star4droid.game.SceneScript;
import com.star4droid.template.Utils.SceneScript;
public class scene1Script extends SceneScript {
    public void create(){ getStage().setSteps(1); getStage().setGravity(0f, -200f); }
    public void draw(){}   // every frame
    public void pause(){}
    public void resume(){}
}
```
`getStage()` returns the StageImp. `linkTo(OtherScript.class)` chains another script. It is attached when the file exists.

## Inside event code: what you can call (scene class = StageImp)

Everything below is available by plain name in events, `scripts/` members and scene scripts (`getStage().x()`).

**Scene and flow**: `openScene(String)`, `finish()`, `Pause()` / `Resume()` (physics only), `isPlaying()`, `openUrl(String)`, `toast(String)`, `toast(String,int seconds)`, `debug(String)`, `getDelta()` (seconds per frame), `random(int min,int max)` (inclusive), `toInt(String)`, `toFloat(String)`.

**Finding things**: `findItem(String name)` (PlayerItem or null; also finds clones by their name), `getJoint(String)`, `findLight(String)`, `checkCollision(PlayerItem,PlayerItem)`.

**Physics world**: `world` (Box2D World), `setGravity(float x,float y)` (world units), `setSteps(int)` (physics steps per frame, default 6; `setWorldGravity` block uses 1), `playing`, `debugBox2d` (true draws collider outlines: handy for debugging).

**Camera and view**: `setZoom(float)`, `getZooming()`, `setCameraXY(float,float)`, `setCameraXY(PlayerItem)`, `setCameraX/Y(float|PlayerItem)`, `setCameraCenter(float,float)`, `cameraFollowX(PlayerItem)`, `cameraFollowY(PlayerItem)` (follow every frame), `setCameraOffset(float,float)`, `getCameraX()`, `getCameraY()`, `getCamera()`, `getTouch()` (Vector2 of the touch in world coordinates; divide by `StageImp.WORLD_SCALE` for px), `getWidth()`, `getHeight()` (logic size in px), `setBackgroundColor(String hex)`.

**Assets and data**: `setImage(PlayerItem,String)`, `setAnimation(PlayerItem,String)`, `startSound(String)`, `loopSound(String,boolean)`, `pauseSound(String)`, `releaseSound(String)`, `setValue(String key,String)`, `getValue(String)`, `getNumber(String)` (int), `getProject()` (`getPath()` = project folder), `read(String absolutePath)`, `write(String absolutePath,String text)`, `getFont(String path)`. `StageImp.WORLD_SCALE` = 0.25f (static).

`getUiStage()` currently returns the game stage (a known bug). Elements by name are fields: `player`, `coin1`.

## PlayerItem (every element; also `current`, `body1`, `body2`)

- Identity: `getName()`, `getParentName()` (clones return the template's name), `getChild(String)`, `addChild(PlayerItem)`, `setParent(PlayerItem)`, `getProperties()` (the element's definition object, fields like `Tint`, `Text`, ...).
- Objects: `getBody()` (Box2D Body or null), `getActor()` (scene2d Actor: `getX/getY/getWidth/getHeight/setVisible/setColor/getColor/setScale/setRotation/toFront/toBack`), `getScript()`, `getElementEvents()`.
- Position: `getBodyX()`, `getBodyY()` (body center, WORLD units), `getActorX()`, `getActorY()`, `getActorWidth()`, `getActorHeight()` (world units for world elements), `setActorX(float)`; teleport with `getBody().setTransform(x*0.25f, y*0.25f, angleRadians)`.
- Distances: `distTo(PlayerItem)`, `distToPoint(x,y)` (world units), `angleTo(PlayerItem)` (radians, direction from the argument toward this), `angleDegreesTo`, `angleToPoint(x,y)`.
- Looks: `setTint(String hex | Color)`, `setImage(Texture|FileHandle)`, `setAnimation(String name)`, `removeAnimation()`, `setItemText(String)` (TEXT), `setProgress(int)`, `setMax(int)`, `getProgress()` (PROGRESS), `getJoyStickX()`, `getJoyStickY()` (JOYSTICK, -1..1), `getParticleEffect()`, `scaleEffect(sx,sy)`.
- Life cycle: `getClone(String newName)` (a new element with the same properties; clone from the original), `destroy()` (removes actor and body: never inside a collision callback; wrap in a `Timer`).

Common recipes:

```java
// center text
((com.badlogic.gdx.scenes.scene2d.ui.Label) scoreText.getActor()).setAlignment(com.badlogic.gdx.utils.Align.center);
// key press
if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) player.getBody().setLinearVelocity(player.getBody().getLinearVelocity().x, 175f);
// delayed action (local must be final)
final PlayerItem target = body2;
Timer.schedule(new Timer.Task(){ public void run(){ target.destroy(); } }, 0.1f);
// all clones named bullet*
for (com.badlogic.gdx.scenes.scene2d.Actor a : GameStage.getActors()) if (a.getName() != null && a.getName().startsWith("bullet")) { /* ... */ }
```
Remember units: Box2D velocities are world units (px * 0.25), so 700 px/s is `175f`.
