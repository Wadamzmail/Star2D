# Joints (Box2D)

Joints connect two dynamic bodies: hinges, wheels, springs, bridges, doors, pendulums. They are created once when the scene starts (before `OnCreate`). Manage them with the `joints` tool on the open scene (batch):

```json
{"ops":[
 {"op":"add","name":"hinge1","type":"RevoluteJoint","bodyA":"door","bodyB":"frame",
  "props":{"anchor":"120,300","enableLimit":true,"lowerAngle":-1.2,"upperAngle":0}},
 {"op":"set","name":"hinge1","props":{"upperAngle":0.3}},
 {"op":"list"},
 {"op":"delete","name":"hinge1"}
]}
```

- `type`: `RevoluteJoint`, `PrismaticJoint`, `DistanceJoint`, `WheelJoint`, `WeldJoint`, `FrictionJoint`, `PulleyJoint`, `MotorJoint` (the word `Joint` is added if you omit it). **Do not use `RopeJoint`, `MouseJoint` or `GearJoint`: the code generated for them does not compile in this version.**
- `bodyA`, `bodyB`: names of existing elements (they need bodies: BOX, CIRCLE, CUSTOM not of type UI). The element variable is the joint name itself, e.g. `hinge1`, available in events with the Java type of the joint.
- Names: letters, digits, `_`. `add` fails if the name exists; `set` changes only the listed props and keeps the rest.
- Joints are stored in `joints/<scene>/<name>-<Type>`. The `list` op shows each joint's props as you would set them.

## Value formats in `props`

| Kind | Format |
|---|---|
| anchor / point (vector) | `"x,y"` in editor PIXELS (e.g. `"120,300"`), or the NAME of an element (its center is used). |
| axis (Prismatic, Wheel) | `"x,y"` direction, e.g. `"0,1"` vertical, `"1,0"` horizontal. |
| number | a number (raw: NOT scaled). Angles are in radians, `motorSpeed` in radians/second, translations in WORLD units (px * 0.25). |
| boolean | `true` / `false` |

Unknown property names are rejected with the list of valid ones.

## Constructor parameters per type (all set by the tool: pass them in `props`, bodyA/bodyB at top level)

| Type | Required props |
|---|---|
| RevoluteJoint | `anchor` (the hinge point) |
| PrismaticJoint | `anchor`, `axis` |
| DistanceJoint | `anchorA`, `anchorB` (the rest length is the distance between them at start) |
| WheelJoint | `anchor`, `axis` |
| WeldJoint | `anchor` |
| FrictionJoint | `anchor` |
| PulleyJoint | `groundAnchorA`, `groundAnchorB`, `anchorA`, `anchorB`, `ratio` |
| MotorJoint | (none) |

## Safe tuning properties (applied before the joint is initialised, so these are NOT overwritten)

- Revolute: `enableLimit`, `lowerAngle`, `upperAngle`, `enableMotor`, `motorSpeed`, `maxMotorTorque`
- Prismatic: `enableLimit`, `lowerTranslation`, `upperTranslation` (world units), `enableMotor`, `motorSpeed`, `maxMotorForce`
- Distance: `frequencyHz` (0 = rigid, 2 to 6 = springy), `dampingRatio` (0 to 1)
- Wheel: `enableMotor`, `motorSpeed`, `maxMotorTorque`, `frequencyHz`, `dampingRatio`
- Weld: `frequencyHz`, `dampingRatio` (0 = stiff weld)
- Friction: `maxForce`, `maxTorque`
- Motor: `linearOffset`, `angularOffset`, `maxForce`, `maxTorque`, `correctionFactor`
- All: `collideConnected` (true = the two bodies still collide with each other; default false)

Properties like `localAnchorA/B`, `referenceAngle` and a Distance `length` are listed by the tool but are overwritten by the joint's own initialisation from the anchors, so setting them has no effect.

## Examples

**Hinged door** (door DYNAMIC, frame STATIC, anchor at the frame edge): the Revolute example above.

**Car with wheels** (chassis `car` and circles `wheelF`, `wheelB`, all DYNAMIC; wheels `friction` 1):
```json
{"ops":[
 {"op":"add","name":"axleF","type":"WheelJoint","bodyA":"car","bodyB":"wheelF",
  "props":{"anchor":"wheelF","axis":"0,1","frequencyHz":4,"dampingRatio":0.7,"enableMotor":true,"maxMotorTorque":60000,"motorSpeed":0}},
 {"op":"add","name":"axleB","type":"WheelJoint","bodyA":"car","bodyB":"wheelB",
  "props":{"anchor":"wheelB","axis":"0,1","frequencyHz":4,"dampingRatio":0.7,"enableMotor":true,"maxMotorTorque":60000,"motorSpeed":0}}
]}
```
Drive it with blocks in `OnStep` from the joystick or a button: `{"block":"setMotorSpeed","fields":{"joint":"axleB","speed":-15}}` (negative = clockwise on screen = driving right). Torque needed grows with mass (`density * (w*0.25) * (h*0.25)`); start high (10^4 to 10^5), lower it if the car flips, verify with `game_state`.

**Spring platform**: `DistanceJoint` with `frequencyHz` 3 and `dampingRatio` 0.3 between a STATIC anchor box and a DYNAMIC box.

**Slider / elevator**: `PrismaticJoint` with `axis` `"0,1"`, `enableLimit` true, `lowerTranslation` 0, `upperTranslation` 50 (world units = 200 px), `enableMotor` true, `maxMotorForce` large, `motorSpeed` 5.

**Pendulum / swing**: `RevoluteJoint` between a STATIC pivot and a DYNAMIC weight, `anchor` = the pivot's name.

## Controlling joints from events

Joint blocks (category Joints): `setMotorSpeed(joint, speed)`, `enableMotor(joint, enable)`, `setMaxMotorTorque(joint, torque)`, `setLimits(joint, x, y)`, `enableLimit(joint, enable)`, `setSpringFrequencyHz`, `setSpringDampingRatio`. The `joint` field is the joint's name as a plain Java expression (no quotes). The blocks call the method of the same name on the joint, so they only compile for joints that have it: `setMotorSpeed`, `enableMotor` (Revolute, Prismatic, Wheel), `setMaxMotorTorque` (Revolute, Wheel), `setLimits`, `enableLimit` (Revolute, Prismatic), `setSpringFrequencyHz` / `setSpringDampingRatio` (WheelJoint only). For anything else use a `codeLine`, e.g. `((PrismaticJoint)lift).setMaxMotorForce(5000f);`. Decimals need the `f` suffix in these raw fields (`0.5f`). Java: `getJoint("axleB")` returns a `Joint` you can cast.
