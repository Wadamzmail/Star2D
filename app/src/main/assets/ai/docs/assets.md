# Assets: images, animations, sounds, particles, maps

The AI needs no uploads: make sprites with `create_image` and sounds with `create_sound`. `import_asset` (https URL or base64) exists for real files (max about 25 MB). All asset paths below are relative to the project root; element and block fields use paths relative to `images/` or `sounds/`.

## Images (`images/`)

- Formats: PNG, JPG. Referenced from elements as `/name.png` (relative to `images/`, leading slash; subfolders `/tiles/grass.png`). Blocks `setImage` accept `name.png` or `/name.png`.
- The engine draws the picture stretched to the element's `width x height` with nearest-neighbor filtering. A missing or misspelled image shows the Star2D logo instead.
- `create_image` returns `use_as`: the exact string to put in the element's `image`.

### `create_image` kinds

**pixels**: pixel art from rows of characters and a palette. `.` and space are transparent unless you define them. `scale` enlarges every pixel by an integer factor (keeps it crisp).

```json
{"path":"images/hero.png","kind":"pixels","scale":6,
 "palette":{"o":"#222222","s":"#FFCC99","r":"#E53935","b":"#1E88E5","w":"#FFFFFF","k":"#000000"},
 "rows":["..oooooo..",".orrrrrro.","orrrrrrrro","osswsswsso","osskssksso","ossssssso.","..obbbbo..",".obbbbbbo.",".obo..obo.",".ooo..ooo."]}
```
Every character used in `rows` must be in the palette, otherwise the tool reports the row.

**shape**: `shape` = `rect|rounded|circle|ellipse|triangle|star`; `width`, `height`, `fill`, `stroke`, `strokeWidth`, `radius` (rounded). Fill can be a gradient: `linear:#FF0000,#0000FF,90` (angle in degrees) or `radial:#FFFFFF,#000000`. With no `fill` and no `stroke` the shape is white.

```json
{"path":"images/ball.png","kind":"shape","shape":"circle","width":64,"height":64,"fill":"radial:#FFEB3B,#F57F17"}
{"path":"images/white.png","kind":"shape","shape":"rect","width":16,"height":16,"fill":"#FFFFFF"}
{"path":"images/button.png","kind":"shape","shape":"rounded","width":300,"height":100,"radius":24,"fill":"#43A047","stroke":"#1B5E20","strokeWidth":4}
```

**gradient**: `colors` (2+), `angle`, `width`, `height`. Sky backgrounds: `{"path":"images/sky.png","kind":"gradient","width":64,"height":256,"colors":["#87CEEB","#E0F7FA"],"angle":90}`.

**tiles**: like `pixels` (a small tile from `rows` + `palette`), repeated to fill `width x height`. Ground and wall textures.

Colors in `create_image` are Android colors: `#RRGGBB`, **`#AARRGGBB` (alpha FIRST)**, names (`red`), or `transparent`. This differs from element `Tint` (`#RRGGBBAA`, alpha last).

Tips: sprite sizes of 16 to 32 cells with `scale` 4 to 8 look like pixel art; draw facing right. A white `white.png` + element `Tint` gives any flat colored rectangle without more images. Call `create_image` once per file (not batched), make all needed images before adding elements.

## Sprite animations (`anims/`)

An animation is a JSON file `anims/<name>` (no extension) with the frames in order:

```json
[{"dur":"150","name":"hero_walk1.png"},{"dur":"150","name":"hero_walk2.png"},{"dur":"150","name":"hero_walk3.png"}]
```
Write it with the `files` tool: `{"ops":[{"op":"write","path":"anims/walk","content":"[{\"dur\":\"150\",\"name\":\"hero_walk1.png\"},...]"}]}`.

- `name`: image file relative to `images/` WITHOUT a leading slash.
- `dur` in milliseconds is read from the FIRST entry and used for all frames. The animation always loops.
- Play it with block `setAnimation(body, animation)` (field value = the file name `walk`). It restarts from frame 0 on each call, so call it only when the state changes (see `blocks-examples-2`, example 14). `setImage`/`removeAnimation` stops it. An unknown animation name is silently ignored. Frames are stretched to the element size.
- Make the frames with `create_image` (kind `pixels`, same size).

## Sounds (`sounds/`)

- `create_sound` writes a WAV: `{"path":"sounds/jump.wav","preset":"jump"}`. Presets: `jump`, `coin`, `hit`, `explosion`, `shoot`, `powerup`, `click`, `laser`, `blip`. Optional `duration` (seconds, max 5), `volume` 0..1, `attack`, `decay`. Custom: `{"path":"sounds/beep.wav","wave":"square","freq":440,"freqEnd":880,"duration":0.3}` with `wave` = `sine|square|saw|triangle|noise`.
- Play with block `playSound(Sound)` (value `jump.wav`), `loopSound(Sound, repeat?)` for music, `pauseSound`, `releaseSound` (stops). Java: `startSound("jump.wav")`.
- Sounds are played as streams: keep effects short, and expect a sound that is still playing not to overlap with itself. WAV, MP3 and OGG files work.
- Music: import an mp3/ogg with `import_asset` (url), then `loopSound` with `repeat?` `true` and `playSound` in `OnCreate`.

## Particles, maps, fonts

- PARTICLE element: `file` is a libGDX `.p` effect under `files/`; leave it empty for the default smoke. Effects must be imported (`import_asset` to `files/`).
- MAP element: a Tiled `.tmx` and its tileset image under `images/`, set as `Map` (`/level.tmx`). Visual only; add STATIC boxes for collisions.
- Fonts: an `.s2df` font file (the app's font editor wraps a TTF); set it as a TEXT's `font` (project-relative path). The default font is ASCII only.

## Size and performance

Keep textures small (under 1024 px per side for sprites, 2048 for backgrounds). Prefer repeating a small tile (`tileX`/`tileY` on the element, or a `tiles` image) over one huge picture.
