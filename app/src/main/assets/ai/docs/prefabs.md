# Prefabs: reusable groups of elements

## What a prefab is

A prefab is a saved GROUP with elements under it (and their events and scripts), stored as a file in `prefabs/` (subfolders allowed: `enemies/Slime` = `prefabs/enemies/Slime.prefab`). Add it to any scene as many times as you want; each copy is an INSTANCE that follows the prefab. Change the prefab and every instance updates, except what that instance changed on purpose (an override).

- The root of a prefab is always a GROUP (fixed, cannot be deleted). Everything else sits under it (`parent` chain).
- An instance named `Slime` gets children named `Slime_<node>` (`Slime_body`, `Slime_eye`), numbered when the name is taken (`Slime2`, `Slime2_body`).
- Instance elements carry three extra properties you can ignore: `prefab` (root only: the prefab path), `pid` (node id), `pbase` (snapshot used to detect overrides). Never edit them by hand.
- Prefabs are design time: they are expanded into normal elements in the scene file. Runtime spawning from code is NOT supported; to create things while the game runs use `clone` (see `events`/`blocks`).

## Create / add / edit / apply

The `prefabs` tool takes `ops`, a batch like the other tools; `scene` defaults to the open scene.

```json
{"ops":[{"op":"list"}]}
{"ops":[{"op":"create","path":"enemies/Slime","from_element":"slime"}]}
{"ops":[{"op":"create","path":"fx/Spark"}]}
{"ops":[{"op":"add","path":"enemies/Slime","x":400,"y":120,"name":"slimeA"}]}
{"ops":[{"op":"info","element":"slimeA_body"}]}
{"ops":[{"op":"apply","element":"slimeA"}]}
{"ops":[{"op":"revert","element":"slimeA"}]}
{"ops":[{"op":"unpack","element":"slimeA"}]}
{"ops":[{"op":"link","element":"someGroup","path":"enemies/Slime"}]}
{"ops":[{"op":"rename","path":"enemies/Slime","to":"mobs/Slime"},{"op":"duplicate","path":"mobs/Slime","to":"mobs/Slime2"},{"op":"delete","path":"mobs/Old"},{"op":"mkdir","path":"mobs"}]}
```

- `create` + `from_element`: turns that element (and everything under it) into a prefab; if it is not a GROUP it is wrapped in a new GROUP at its place. The element becomes the first instance.
- `create` without `from_element`: an empty prefab (just the root). Build it like this: `add` it to the scene, create elements with `elements` using `parent:'<instance name>'` (or a child of it), position them, then `apply` on the instance root.
- `add` returns `root` (the instance name) and `created` (all new element names). Children are moved with the root because they have `parent`.
- Editing an existing prefab: change the elements of one instance (properties, events, new children), then `apply` on its root. Every other instance in every scene is updated; their overrides stay.
- `apply` also takes in elements you added under the instance (they become part of the prefab) and drops prefab nodes you deleted.
- `revert` throws the instance's overrides and local additions away (it becomes exactly the prefab again; positions and sizes you set stay).
- `unpack`: the elements become normal ones, no longer linked.
- `link`: replaces a group and everything under it with an instance of an existing prefab, at the group's position.
- `delete` a prefab: its instances stay in scenes as unlinked copies.
- `info` on any element of an instance returns the prefab, the instance root, `overrides` (count) and `modified` (names such as `Slime_body.image`, `Slime_body.event:onClick`, `Slime_body.script`, `+name` = added, `-name` = removed).

## Overrides

Rule: a property follows the prefab unless the instance has a different value than it had at the last sync. Then the instance value wins and counts as an override.

- Never counted as overrides, so freely changed per instance: `x`, `y`, `z`, `rotation`, `width`, `height`, `radius`, `lock`, `name`, `parent`, the collider size. Position/size/rotation of an element still follow the prefab while the instance has not changed them.
- Everything else (image, tint, type of body, friction, `Visible`, ...) is tracked.
- To make an instance different on purpose, just `elements set` it. To make all instances different, change one and `apply`.

## Scripts

- Events and the body script of every element are saved in the prefab, and written again for every instance under that instance element's own name in the scene it lives in (`Events/<scene>/private/<element>/...`, `Script` = the element's own name). So an instance in another scene never points to a script of a scene that does not have it.
- When you make a prefab, an element whose `Script` pointed at another element or another scene gets its own copy of that code (the prefab never shares scripts).
- Event code is merged like properties: a script or event you edited on one instance stays; the rest follows the prefab. Edit the event on any instance and `apply` to push it to all.
- Event code is plain per-element code: refer to other elements of the instance by their real names inside the instance (use `parent` relations, not hard-coded names of elements outside the prefab).

## Limits

- No prefab inside a prefab: an instance of another prefab placed under an instance keeps its own link and is left alone by the outer prefab (not saved into it, not counted as an override).
- Instance children (nodes of the prefab) cannot be deleted in the editor; `unpack` first, or edit the prefab and `apply`.
- Prefab edit mode in the editor opens a temporary scene named `__prefab_...`; it is hidden from `scenes list` and removed on save/cancel. Do not use it from tools; use `add` + `apply`.
- Joints are not part of prefabs.
- Changing a prefab file by hand (`files`) is possible but unneeded; call `apply`/`add` instead.
