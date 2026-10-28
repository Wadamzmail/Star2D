# Groups and parents: containers, followers, UI vs world

## Groups and parents

- **GROUP** is an empty element: it draws nothing, has no physics, never catches touches. It is a container: elements whose `parent` is the group move, rotate, hide and are destroyed with it.
- Any element can be a parent (a Box, a Text...), not only a GROUP. A light can't be a parent or a child.
- Create: `elements` op `add` with `type: "GROUP"`, props `x y width height` (the size is only the editor frame and the pivot), `type: "STATIC"` (world) or `"UI"` (fixed on screen). Put an element inside with the prop `parent: "GroupName"` on the child (`set`). Empty string = no parent.
- Positions stay WORLD pixels (never relative to the parent). Setting `parent` does not move the element. Setting `parent` on yourself or on a descendant is refused (no cycles).
- In the editor, changing `x`, `y` or `rotation` of a parent moves/rotates all its descendants (rotation turns them around the parent's center, no scaling). Deleting a parent deletes everything inside (one undo). Duplicating copies the whole subtree with new names. Hiding (`Visible false`) or locking a parent applies to the subtree visually; the children's own flags are untouched.

## What follows what (in the running game)

The child keeps the place it had inside the parent when the scene starts (or when the link is made later), then follows every frame: same movement, same rotation, rotation around the parent's center.

| Child | Parent | Result |
|---|---|---|
| world, no body / STATIC / KINEMATIC body | world (any body type or GROUP) | follows position and rotation (bodies are teleported with `setTransform`; a KINEMATIC child gets velocity 0) |
| world, DYNAMIC body | anything | NOT driven (physics owns it). Use a KINEMATIC or STATIC child, or a joint |
| UI (Text, Progress, UI Box, UI GROUP) | world | **world-anchored UI**: sticks to the parent's place on the screen (health bar over an enemy), no rotation, follows the camera |
| UI | UI | follows position and rotation |
| world | UI | NOT driven (logged once) |

Visibility: a child is shown only when its own `Visible` is true AND the parent is shown. Hiding the parent hides the subtree (bodies of hidden children are deactivated and come back when shown). Destroying a parent destroys its children first, then itself (twice is safe).

## UI vs world

- UI elements live on a separate fixed layer above the world; world elements move with the camera. A text can follow a physics parent: make the text `type UI`, the parent a world element, set `parent`. It then stays glued to the parent on the screen even when the camera moves.
- The offset is measured when the scene starts: place the child where you want it relative to the parent in the editor (e.g. the bar 40 px above the enemy) and leave it.
- UI children do not rotate with a world parent and do not scale with the camera zoom.
- A UI GROUP is a HUD panel: put all HUD elements inside it, hide or move the group as one.

## Rules and limits

- GROUP has no body: blocks that use the body (`goTo`, `moveBy`, `setVelocity...`, `jump`) do NOT work on it. Move it with a `codeLine`: `Group1.getActor().moveBy(dx, dy);` (a world group: dx, dy in WORLD units = px * 0.25; a UI group: px). Rotate: `Group1.getActor().rotateBy(-2f);` (positive = counter-clockwise on the actor; the editor property `rotation` is clockwise).
- `hide` / `show` on a group hide / show everything inside.
- A DYNAMIC child never follows. A KINEMATIC/STATIC child follows but does not carry things standing on it (velocity 0): to carry riders make the PLATFORM a KINEMATIC box that is the parent, moved with velocity.
- Runtime Java (codeLine): `child.setParent(parent);` (keeps the world position), `child.detachFromParent();`, `child.getParentItem()`, `parent.getChild("Name")`. `getClone` of a group does not clone its children.
- Do not parent an element to something it should collide-sync with by joints; use one or the other.
- If the follower ever throws, it logs once (`logs/group_follower.txt` on the phone) and switches itself off; the game keeps running.
- Elements created at runtime (`spawnCopy`) are not linked: call `copy.setParent(parent)` yourself.
- Many levels of nesting are fine (parents are processed first), keep it under about 5.

## Recipes

**Enemy with a health bar** (blocks: nothing special)
1. `Enemy1` Box, DYNAMIC, with the physics and script.
2. `Bar1` Progress, `type UI`, `parent: "Enemy1"`, placed 40 px above the enemy in the editor.
3. In the enemy's collision event lower the bar with `setProgress` on `Bar1`; `destroy` of `Enemy1` removes the bar too.

**Player with a sensor child** (feet / vision)
1. `Player1` Box DYNAMIC.
2. `FootSensor` Box, `type KINAMATIC`, `isSensor true`, small, under the feet, `parent: "Player1"`.
3. Use the collision events of `FootSensor` to know when the player stands on something. It rotates with the player; set `Fixed Rotation` on the player to keep it upright.

**HUD panel group**
1. `Hud` GROUP `type UI`, at the top of the screen.
2. Score Text, Health Progress, Pause button: all `type UI`, `parent: "Hud"`.
3. Show / hide the whole panel with one `hide` / `show` block on `Hud`.

**Rotating platform**
1. `Spinner` GROUP `type STATIC` at the center of rotation; `PlatformA`, `PlatformB` STATIC boxes with `parent: "Spinner"`.
2. In `OnBodyUpdate` of any element (or a scene event): `Spinner.getActor().rotateBy(-1f);` rotates both platforms around the group center.
3. For platforms that must carry the player use a KINEMATIC box as the parent with `setAngularVelocity` instead.
