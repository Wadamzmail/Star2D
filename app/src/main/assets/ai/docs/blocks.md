# Blocks: the JSON format for `events`, and `define_blocks`

Logic is a list of visual blocks. You send them as JSON in `events` (`blocks`). The app turns them into Java; the user can open and edit them in the block editor. **Fifteen worked, verified examples are in `blocks-examples` (1 to 8: setup, movement, jump, pickups, patrol, chase) and `blocks-examples-2` (9 to 15: shooting, timers, lives/game over, saving, camera, animation). Copy their shape.** Titles and fields come from `blocks-catalog` (or the `block_catalog` tool, which also lists the user's custom blocks).

## The format

`blocks` is an array of **statement blocks**; **array order = execution order**.

```json
{"block":"<title from the catalog>",
 "fields":{"<field label>": <value>, ...},
 "do":   [ ...blocks... ],     // only for [container] blocks
 "else": [ ...blocks... ]      // only for containers that list ", else" (if, ifCollision, ifTouching, ifJoystickMoved, try=catch part)
}
```

- `<field label>` is the field name WITHOUT its type suffix: the catalog line `setImage(body:Body, image:image)` means `"fields":{"body":"player","image":"hero_jump.png"}`. Labels may also be given as an array in field order: `"fields":["player","hero_jump.png"]`.
- Omitted fields keep the catalog defaults (e.g. `moveBy` defaults: body `current`, dx 10, dy 0).
- `<value>` is either a **string/number/boolean** (exactly what a user would type into the block's field) or a **value block** (reporter): `{"r":"<exact display text>","args":[<value>, ...]}`. `args` fill the `___` slots in order and can nest: any arg can again be a string or a reporter. The number of args must equal the number of `___` in the display text.
- Raw Java fallback: `{"block":"codeLine","fields":{"code":"score += 10;"}}`. Shortcuts accepted by the tool: `{"java":"..."}` or just a string in the array. Use Java only when no block fits.
- `"current"` in an element field means the element that owns the event (element events). In scene events there is no `current`: write the element's name.

## What you type in a field (this decides if it compiles)

Plain fields (no type shown) are **raw Java expressions**, pasted as they are:

| You want | Type |
|---|---|
| a number | `10`, `0.5`, `-300` (floats are cast by the block; no `f` needed unless the catalog says "raw Java") |
| a variable | `score` |
| text | `"Score: "` WITH the quotes, or `"Score: " + score` |
| a condition | `score > 10 && lives > 0` |
| a reporter | `{"r":"x of ___","args":["player"]}` |

Typed fields (shown as `name:Type` in the catalog):

- `body:Body` (also `item`, `ui`, `joystick`, `progress`, `target`): an **element name** (`player`) or `current`. No quotes. Can also be a Java expression returning a PlayerItem (`findItem("coin1")`).
- `x:Boolean`: a Java boolean expression (`true`, `false`, `lives > 0`) or a boolean reporter.
- `image:image`, `sound:sound`, `animation:animation`, `scene:scene`: the **plain file/scene name WITHOUT quotes** (`hero.png`, `jump.wav`, `walk`, `level2`); the block adds the quotes. Images: with or without a leading `/`. Sounds: the file name inside `sounds/`.
- Reporter args that are element slots (`x of ___`) take the plain element name (no quotes). Reporter slots of the text reporter `"___"`, `saved number ___`, `___ is named ___` (second slot) take plain text and add quotes themselves.

Value blocks produce Java types: arithmetic and `x of ___` give numbers; comparisons, `and/or/not`, `___ touches ___`, `___ is named ___` give booleans; `join`, `to string`, `"___"` give Strings. Put a boolean reporter in a `Boolean` field. Number reporters inside a text field need `to string ___` or `join ___ ___` (join converts automatically).

## Containers

`if` (field `boolean`; `do` / `else`), `while`, `repeatTimes` (fields `times`, `var`: the loop counter name), `ifThen` (just `do`), `waitSeconds` (`seconds`, `do` runs after the delay), `everySeconds` (repeats forever), `ifCollision`, `ifTouching`, `ifJoystickMoved`, `_DoAfter`/`_repeatEvery` (named timers). Inside `do`/`else` write blocks exactly like at top level.

```json
[{"block":"if","fields":{"boolean":{"r":"___ > ___","args":[{"r":"x of ___","args":["player"]},"1000"]}},
  "do":[{"block":"OpenScene","fields":{"scene":"win"}}],
  "else":[]}]
```

`stopEvent` leaves the event. `breakLoop` leaves a loop. `comment` adds a note. `newVariable` declares a LOCAL Java variable (`type`, `name`, `value`), visible to the blocks after it in the same sequence (not inside timers unless final). For scene variables use the `variables` tool and `setVariable` / `changeVariable`.

## Rules that avoid compile errors

1. Element names and variable names must exist (scene elements are fields of the scene class).
2. A reporter's arg count must match its `___` count.
3. Inside `waitSeconds` / `everySeconds` / `_DoAfter` / `_repeatEvery` / `destroyAfter` do not use `current`, `body1`, `body2`, `event` (not final in Java 7): copy first with `{"block":"codeLine","fields":{"code":"final PlayerItem me = current;"}}`, then use `me`.
4. Body blocks (`moveBy`, `goTo`, `setVelocityX`, `x of ___`...) crash on UI-type elements (no body). For TEXT/PROGRESS/UI use `setText`, `setProgress`, `setVisibility`, or Java on `getActor()`.
5. Do not use the blocks marked BROKEN / NO-OP in the catalog (`saveValue`, `setsplits`, `setAlpha`, `moveTowardBody`).
6. Java types are checked: assigning a float (`x of ___`, `speed y of ___`, `frame time`, `random` is int) to an `int` variable fails to compile: wrap it in `to int ___`, or declare the variable `float`. Variables are `int`/`float`/`String`/`boolean`; `saved number ___` is an int. Float variables need values like `0.5f` in the `variables` tool.
7. If `events set` says "unknown block", the error lists similar titles; call `block_catalog` with `search`.

## Reading back

`events get` returns the same JSON you would send (`blocks`), so you can edit and send it again. After `set`, `build` shows Java compile errors with the event file; the visual editor shows the same blocks.

## `define_blocks`: your own blocks

Use it when the same Java is needed in many places or no block exists (for example `shakeCamera`, `playTone`). The blocks are saved in the user's block library under the category `★ <category>` and are then available in `events` and in the editor palette.

```json
{"category":"My Game","color":"#8E24AA","blocks":[
 {"title":"killEnemy","kind":"statement","fields":["enemy(Body)","points"],"defaults":["current","10"],
  "code":"%1$s.destroy();\nscore += (int)(%2$s);\n%3$s"},
 {"title":"repeatWhileAlive","kind":"container","fields":["times"],"defaults":["3"],
  "code":"for(int i = 0; i < (int)(%1$s); i++){\n%2$s\n}\n%3$s"},
 {"title":"half","kind":"value","fields":["a"],"display":"half of ___","code":"((%1$s) / 2f)"}
]}
```

Rules:

- `kind: "statement"`: `fields` are the inputs in order; the template uses `%1$s` ... `%N$s` for them and MUST end with `%{N+1}$s` = the next block (forgetting it silently drops everything after your block).
- `kind: "container"`: inner blocks are `%{N+1}$s`, the next block is `%{N+2}$s` (the tool maps this to the engine's internal layout for you). It gets `do` in `events`.
- `kind: "value"`: an expression, no next slot. `display` is the exact text with one `___` per field (this exact text is what you write in `"r"`); the number of `___` must equal the number of fields.
- A field named `name(Body)` is an element picker (use the element name or `current` in `events`); `(Boolean)`, `(image)`, `(sound)`, `(animation)`, `(scene)` work as in the catalog. Field names are letters/digits/`_`; avoid `inside` and `else`.
- A literal percent sign in the Java must be written `%%`.
- `defaults` are in field order; the `title` must be unique (redefining a title replaces it).
- Code runs inside the scene class: elements and variables are directly visible; Java 7 only (no lambdas).
- Using it: `{"block":"killEnemy","fields":{"enemy":"body2","points":20}}`, `{"block":"repeatWhileAlive","fields":{"times":5},"do":[ ... ]}`, and the value block as `{"r":"half of ___","args":["score"]}`.
