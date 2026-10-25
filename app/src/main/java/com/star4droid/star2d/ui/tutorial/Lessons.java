package com.star4droid.star2d.ui.tutorial;

import android.view.View;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Locale;

/**
 * The lessons. Every game is built by the learner, block by block; the prebuilt elements are
 * only scenery (background, ground…) so the lesson can focus on the game logic.
 */
final class Lessons {
    private Lessons() {}

    static String t(String k, String en) { return Lesson.t(k, en); }

    static ArrayList<Lesson> all() {
        ArrayList<Lesson> l = new ArrayList<>();
        l.add(first());
        l.add(flappy());
        l.add(racing());
        l.add(shooter());
        l.add(platformer());
        return l;
    }

    static Lesson find(String id) {
        for (Lesson l : all()) if (l.id.equals(id)) return l;
        return null;
    }

    // ================================================================ prebuilt elements

    static String box(String name, String type, String image, float x, float y, float w, float h, int z, String... extra) {
        StringBuilder sb = new StringBuilder("{");
        String[][] base = {
                {"parent", ""}, {"Gravity Scale", "1"}, {"Shape", "Box"}, {"type", type}, {"Version", "1.0"}, {"Visible", "true"},
                {"lock", "false"}, {"TYPE", "BOX"}, {"Script", name}, {"image", image}, {"Bullet", "false"}, {"density", "1"},
                {"rotation", "0"}, {"Collision", ""}, {"isSensor", "false"}, {"ColliderX", "0"}, {"ColliderY", "0"},
                {"friction", "0"}, {"Active", "true"}, {"restitution", "0"}, {"name", name}, {"Tint", "#FFFFFF"},
                {"Scale X", "1"}, {"Scale Y", "1"}, {"tileX", "1"}, {"tileY", "1"}, {"Fixed Rotation", "false"},
        };
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        for (String[] kv : base) m.put(kv[0], kv[1]);
        for (int i = 0; i + 1 < extra.length; i += 2) m.put(extra[i], extra[i + 1]);
        for (java.util.Map.Entry<String, String> e : m.entrySet())
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue().replace("\"", "\\\"")).append("\",");
        sb.append(String.format(Locale.ENGLISH, "\"x\":%.1f,\"y\":%.1f,\"width\":%.1f,\"height\":%.1f,\"Collider Width\":%.1f,\"Collider Height\":%.1f,\"z\":%d}",
                x, y, w, h, w, h, z));
        return sb.toString();
    }

    static String scene(String... boxes) {
        return "[" + android.text.TextUtils.join(",", boxes) + "]";
    }

    // ================================================================ shared parts

    static void welcomeAndFiles(Lesson l, String key, String en) {
        l.info(key, en);
        l.info("how", "Tap exactly where the hand points; everything else is paused. ▶▶ Auto explain does the steps for you, Skip does just this one. Press Back to pause the lesson.");
        l.files(null);
    }

    /** Variables panel -> add an int variable. */
    static void addVariable(Lesson l, String name) {
        l.openPanel("rail:variables", () -> L.shell().variables, "open_vars", "Open Variables. A variable is a box that keeps a value, like the score.");
        l.add(new Step(t("var_add", "Tap Add variable.")).at("vars:add")
                .until(() -> Tut.find("vardlg:name") != null || L.varExists(name))
                .auto(() -> { View v = Tut.find("vars:add"); if (v != null) v.performClick(); }));
        l.add(new Step(Lesson.f("var_name", "Write %1$s as the name. The type int (a whole number) and the value 0 are already right. Then tap Save.", name))
                .at(Targets.unionOf("vardlg:name", "vardlg:value", "vardlg:save"))
                .until(() -> L.varExists(name))
                .auto(() -> {
                    View n = Tut.find("vardlg:name"), s = Tut.find("vardlg:save");
                    if (n instanceof EditText) ((EditText) n).setText(name);
                    if (s != null) s.performClick();
                }));
        l.closePanel();
    }

    static void props(Lesson l, String name, String image, float x, float y) {
        l.openProperties();
        l.rename(name);
        l.image(name, image);
        l.number(name, "x", fmt(x), "set_x", "Set x to %1$s (pixels from the left of the screen).");
        l.number(name, "y", fmt(y), "set_y", "Set y to %1$s (pixels from the bottom).");
    }

    static String fmt(float v) {
        return v == (int) v ? String.valueOf((int) v) : String.valueOf(v);
    }

    /** "if (x / y of current) < limit" on an OnBodyUpdate, then go back with a random position. */
    static void wrapAround(Lesson l, String axis, String limit, String keyAxis, boolean vertical, String r0, String r1, String other) {
        l.category("main");
        l.block("if", "add_if", "Add the \"%1$s\" block: blocks inside it run only when its condition is true.");
        l.valueBlock("if", 0, "Operators", "___ < ___", "cond_less", "Tap the condition and choose %1$s from Operators.");
        l.valueBlock("if", 0, "Elements", axis + " of ___", "cond_axis", "Tap the first circle and choose %1$s from Elements.", 0);
        l.value("if", 0, "current", "cond_current", "Tap the small circle and choose %1$s (this element).", 0, 0);
        l.value("if", 0, limit, "cond_limit", "Tap the second circle and type %1$s: now it means \"this element left the screen\".", 1);
        l.category("Motion");
        l.blockInside("goTo", "if", 1, "goto_inside", "Drag \"%1$s\" inside \"%2$s\" (the opening under the condition).");
        if (vertical) {
            l.valueBlock("goTo", 1, "Math", "random ___ to ___", "goto_x_random", "Tap x and choose %1$s from Math: a new place each time.");
            l.value("goTo", 1, r0, "random_from", "Type %1$s in the first circle.", 0);
            l.value("goTo", 1, r1, "random_to", "Type %1$s in the second circle.", 1);
            l.value("goTo", 2, other, "goto_y_top", "Set y to %1$s: above the top of the screen.");
        } else {
            l.value("goTo", 1, other, "goto_x_right", "Set x to %1$s: back to the right side.");
            l.valueBlock("goTo", 2, "Math", "random ___ to ___", "goto_y_random", "Tap y and choose %1$s from Math: a new height each time.");
            l.value("goTo", 2, r0, "random_from", "Type %1$s in the first circle.", 0);
            l.value("goTo", 2, r1, "random_to", "Type %1$s in the second circle.", 1);
        }
    }

    static void restartOnHit(Lesson l, String who, String key, String en) {
        l.selectFromList(who);
        l.elementEvent(who, "onBodyCollided", key, en);
        l.category("main");
        l.block("OpenScene", "add_open_scene", "Add \"%1$s\".");
        l.value("OpenScene", 0, "scene1", "open_scene1", "Choose %1$s: opening the same scene again restarts the game.");
        l.saveBlocks();
        l.closeBlocks();
    }

    // ================================================================ 1. first game

    static Lesson first() {
        Lesson l = new Lesson("first", t("first_title", "My first game: Catch the star"), t("first_tag", "Beginner · 10 min"),
                t("first_desc", "Learn the editor: add an element, give it a picture, use a variable and your first blocks. Tap the star to score!"));
        l.startImages = new String[]{"starter/star.png", "starter/night_bg.png"};
        l.sceneJson = scene(box("sky", "UI", "/night_bg.png", 0, 0, 720, 1280, 0, "lock", "true"));
        welcomeAndFiles(l, "first_welcome", "Welcome! You will make your first game: a star appears, you tap it, you get a point and it jumps to another place. I placed a night sky for you.");
        l.addElement("box", "Box", 0, "add_box_star", "Choose Box: an element that can show any picture.");
        props(l, "star", "star.png", 296, 600);
        l.type("star", "STATIC", "type_static", "Our star must not fall. Tap type and choose STATIC: it stays where you put it.");
        l.closePanel();
        addVariable(l, "score");
        l.selectFromList("star");
        l.elementEvent("star", "onClick", "event_click_star", "Tap onClick: these blocks run when the player taps the star.");
        l.info("blocks_intro", "This is the block editor. The yellow block is the start; blocks under it run one after another. Long-press a block for its options, (!) explains any block.");
        l.info("params_intro", "See \"current\" on the yellow block? It is an input of this event: this element. Long-press it and drag it into any field to use it.");
        l.category("Variables");
        l.block("changeVariable", "drag_first", "Hold \"%1$s\" for a moment and drag it under the yellow block (or just tap it).");
        l.value("changeVariable", 0, "score", "field_var", "Tap the variable field and choose %1$s. \"by 1\" means +1 point.");
        l.category("Debug");
        l.block("toastValue", "add_toast", "Add \"%1$s\": it shows a short message on the screen.");
        l.value("toastValue", 0, "score", "toast_score", "Tap its value field and choose %1$s.");
        l.category("Motion");
        l.block("goTo", "add_goto_star", "Add \"%1$s\": it moves an element. \"current\" means this element: the star.");
        l.valueBlock("goTo", 1, "Math", "random ___ to ___", "x_random", "Tap the x field and choose the value block %1$s from Math.");
        l.info("numpad_intro", "Number fields open a number pad: tap the digits, ⌫ deletes the last one, C clears, then OK.");
        l.value("goTo", 1, "60", "random_from", "Type %1$s in the first circle.", 0);
        l.value("goTo", 1, "600", "random_to", "Type %1$s in the second circle.", 1);
        l.valueBlock("goTo", 2, "Math", "random ___ to ___", "y_random", "Same for y: tap it and choose %1$s.");
        l.value("goTo", 2, "150", "random_from", "Type %1$s in the first circle.", 0);
        l.value("goTo", 2, "1150", "random_to", "Type %1$s in the second circle.", 1);
        l.saveBlocks();
        l.closeBlocks();
        l.play("play_first", "Play! Tap the star as fast as you can. Press Back to come back to the editor.");
        l.info("first_end", "You made your first game! 🎉 Try: change the random numbers, add playSound in onClick, or add a second star.");
        return l;
    }

    // ================================================================ 2. flappy

    static Lesson flappy() {
        Lesson l = new Lesson("flappy", t("flappy_title", "Flappy Bird"), t("flappy_tag", "Arcade · 15 min"),
                t("flappy_desc", "Tap to fly, avoid the pipe! Gravity, touch events, moving obstacles and conditions with nested value blocks."));
        l.startImages = new String[]{"sky_flyer/sky_bg.png", "sky_flyer/ground_strip.png", "sky_flyer/pipe.png", "sky_flyer/bird.png"};
        l.sceneJson = scene(
                box("sky", "UI", "/sky_bg.png", 0, 0, 720, 1280, 0, "lock", "true"),
                box("pipe", "KINAMATIC", "/pipe.png", 760, -80, 112, 640, 3),
                box("ground", "STATIC", "/ground_strip.png", 0, 0, 720, 140, 5, "lock", "true"));
        welcomeAndFiles(l, "flappy_welcome", "Let's make Flappy Bird: tap to fly up and don't touch the pipe! I placed the sky, the ground and a pipe for you.");
        l.addElement("box", "Box", 0, "add_box_bird", "Choose Box for our bird.");
        props(l, "bird", "bird.png", 150, 700);
        l.check("bird", "Fixed Rotation", "fixed_rot", "Turn on Fixed Rotation so the bird never spins.");
        l.closePanel();
        l.info("gravity_info", "Gravity pulls bodies down. Let's make it stronger: the game feels faster and more fun.");
        l.sceneEvent("OnCreate", "scene_oncreate", "Tap OnCreate in Scene events: it runs once when the game starts.");
        l.category("Physics");
        l.block("setWorldGravity", "add_gravity", "Add \"%1$s\" under the yellow block.");
        l.value("setWorldGravity", 0, "900", "gravity_val", "Tap strength and type %1$s.");
        l.saveBlocks();
        l.closeBlocks();
        l.info("flap_info", "Now the flap: the sky covers the whole screen, so a touch on the sky makes the bird jump.");
        l.selectFromList("sky");
        l.elementEvent("sky", "onTouchStart", "sky_touch", "Tap onTouchStart: it runs when a finger touches the sky.");
        l.category("Motion");
        l.block("jump", "add_jump", "Add \"%1$s\".");
        l.value("jump", 0, "bird", "jump_bird", "It jumps \"current\" (the sky!). Tap the element field and choose %1$s.");
        l.value("jump", 1, "420", "jump_power", "Set the power to %1$s.");
        l.saveBlocks();
        l.closeBlocks();
        l.info("pipe_info", "The pipe must go to the left, and come back from the right again and again.");
        l.selectFromList("pipe");
        l.elementEvent("pipe", "OnBodyCreated", "pipe_created", "Tap OnBodyCreated: it runs once, when the pipe appears.");
        l.category("Motion");
        l.block("setVelocityX", "add_velx", "Add \"%1$s\".");
        l.value("setVelocityX", 1, "-200", "velx_left", "Set the speed to %1$s (minus = to the left, in pixels per second).");
        l.saveBlocks();
        l.closeBlocks();
        l.elementEvent("pipe", "OnBodyUpdate", "pipe_update", "Now tap OnBodyUpdate: it runs every frame for the pipe.");
        wrapAround(l, "x", "-120", "x", false, "-60", "260", "840");
        l.saveBlocks();
        l.closeBlocks();
        l.info("hit_info", "Last thing: when the bird hits the pipe or the ground, the game starts again.");
        restartOnHit(l, "bird", "bird_hit", "Tap onBodyCollided: it runs when the bird hits another body.");
        l.play("play_flappy", "Play! Tap the screen to fly over the pipe. Press Back to come back.");
        l.info("flappy_end", "Your Flappy Bird works! 🎉 Ideas: a score variable that grows in the pipe's \"if\", a second pipe, or a flap sound.");
        return l;
    }

    // ================================================================ 3. racing

    static Lesson racing() {
        Lesson l = new Lesson("racing", t("racing_title", "Racing"), t("racing_tag", "Racing · 15 min"),
                t("racing_desc", "Drive with a joystick and dodge the other cars. Joystick, zero gravity, speed and random positions."));
        l.startImages = new String[]{"racing/road.png", "racing/car_red.png", "racing/car_blue.png", "racing/car_yellow.png"};
        l.sceneJson = scene(
                box("road", "UI", "/road.png", 0, 0, 720, 1280, 0, "lock", "true"),
                box("rival", "KINAMATIC", "/car_blue.png", 420, 1400, 96, 168, 3));
        welcomeAndFiles(l, "racing_welcome", "A racing game! You drive the red car with a joystick and dodge the blue one. I placed the road and the rival car.");
        l.addElement("box", "Box", 0, "add_box_car", "Choose Box for your car.");
        props(l, "car", "car_red.png", 200, 160);
        l.check("car", "Fixed Rotation", "fixed_rot_car", "Turn on Fixed Rotation so the car stays straight.");
        l.addElement("joystick", "Joystick", 3, "add_joystick", "Now choose Joystick: the player drives with it.");
        l.openProperties();
        l.rename("stick");
        l.number("stick", "x", "480", "set_x", "Set x to %1$s (pixels from the left of the screen).");
        l.number("stick", "y", "60", "set_y", "Set y to %1$s (pixels from the bottom).");
        l.closePanel();
        l.sceneEvent("OnCreate", "scene_oncreate", "Tap OnCreate in Scene events: it runs once when the game starts.");
        l.category("Physics");
        l.block("setWorldGravity", "add_gravity", "Add \"%1$s\" under the yellow block.");
        l.value("setWorldGravity", 0, "0", "gravity_zero", "Seen from above, nothing falls: type %1$s.");
        l.saveBlocks();
        l.closeBlocks();
        l.selectFromList("car");
        l.elementEvent("car", "OnBodyUpdate", "car_update", "Tap OnBodyUpdate: every frame the car follows the joystick.");
        l.category("Motion");
        l.block("moveWithJoystick", "add_joy_move", "Add \"%1$s\".");
        l.value("moveWithJoystick", 1, "stick", "joy_field", "Tap the joystick field and choose %1$s.");
        l.value("moveWithJoystick", 2, "350", "joy_speed", "Set the speed to %1$s.");
        l.saveBlocks();
        l.closeBlocks();
        l.selectFromList("rival");
        l.elementEvent("rival", "OnBodyCreated", "rival_created", "Tap OnBodyCreated for the rival car.");
        l.category("Motion");
        l.block("setVelocityY", "add_vely", "Add \"%1$s\".");
        l.value("setVelocityY", 1, "-450", "vely_down", "Set the speed to %1$s (minus = down).");
        l.saveBlocks();
        l.closeBlocks();
        l.elementEvent("rival", "OnBodyUpdate", "rival_update", "Tap OnBodyUpdate: we check every frame if the car went out at the bottom.");
        wrapAround(l, "y", "-200", "y", true, "140", "560", "1450");
        l.saveBlocks();
        l.closeBlocks();
        restartOnHit(l, "car", "car_hit", "Tap onBodyCollided: it runs when your car hits the rival.");
        l.play("play_racing", "Play! Drive with the joystick and dodge the blue car. Press Back to come back.");
        l.info("racing_end", "Vroom! 🏁 Ideas: use car_yellow.png for a second rival, or count dodged cars with a variable.");
        return l;
    }

    // ================================================================ 4. shooter

    static Lesson shooter() {
        Lesson l = new Lesson("shooter", t("shooter_title", "Space shooter"), t("shooter_tag", "Shooter · 20 min"),
                t("shooter_desc", "Move the ship with your finger, shoot lasers and break the rocks. Copies, if / else, collisions and touch position."));
        l.startImages = new String[]{"space/space_bg.png", "space/ship.png", "space/laser.png", "space/rock.png", "space/btn_fire.png"};
        l.sceneJson = scene(
                box("space", "UI", "/space_bg.png", 0, 0, 720, 1280, 0, "lock", "true"),
                box("laser", "DYNAMIC", "/laser.png", 350, -300, 20, 56, 2, "Bullet", "true", "Fixed Rotation", "true"),
                box("rock", "KINAMATIC", "/rock.png", 300, 1350, 100, 100, 3),
                box("fire", "UI", "/btn_fire.png", 560, 40, 130, 130, 6));
        welcomeAndFiles(l, "shooter_welcome", "Space shooter! Move the ship with your finger and shoot the rocks. I placed space, a rock, a laser (hidden below the screen) and the fire button.");
        l.addElement("box", "Box", 0, "add_box_ship", "Choose Box for the ship.");
        props(l, "ship", "ship.png", 300, 140);
        l.check("ship", "Fixed Rotation", "fixed_rot_ship", "Turn on Fixed Rotation.");
        l.closePanel();
        l.sceneEvent("OnCreate", "scene_oncreate", "Tap OnCreate in Scene events: it runs once when the game starts.");
        l.category("Physics");
        l.block("setWorldGravity", "add_gravity", "Add \"%1$s\" under the yellow block.");
        l.value("setWorldGravity", 0, "0", "gravity_space", "No gravity in space: type %1$s.");
        l.saveBlocks();
        l.closeBlocks();
        l.sceneEvent("OnStep", "scene_onstep", "Tap OnStep: it runs every frame, about 60 times per second.");
        l.category("Input");
        l.block("ifTouching", "add_if_touch", "Add \"%1$s\": its blocks run while a finger is on the screen.");
        l.category("Motion");
        l.blockInside("goTo", "ifTouching", 1, "goto_inside", "Drag \"%1$s\" inside \"%2$s\" (the opening under the condition).");
        l.value("goTo", 0, "ship", "goto_ship", "Set the element to %1$s.");
        l.valueBlock("goTo", 1, "Input", "touch x", "goto_touch", "Tap x and choose %1$s from Input: the ship follows your finger.");
        l.value("goTo", 2, "140", "goto_y_ship", "Set y to %1$s.");
        l.saveBlocks();
        l.closeBlocks();
        l.info("fire_info", "Fire! A tap on the red button copies the laser and shoots it up.");
        l.selectFromList("fire");
        l.elementEvent("fire", "onClick", "fire_click", "Tap onClick of the fire button.");
        l.category("Physics");
        l.block("spawnCopy", "add_spawn", "Add \"%1$s\": it makes a copy of an element. The copy is called bullet (the var field).");
        l.value("spawnCopy", 0, "laser", "spawn_what", "Tap the element field and choose %1$s.");
        l.valueBlock("spawnCopy", 3, "Elements", "x of ___", "spawn_x", "Tap x and choose %1$s from Elements.");
        l.value("spawnCopy", 3, "ship", "spawn_x_ship", "Tap the small circle and choose %1$s.", 0);
        l.value("spawnCopy", 4, "220", "spawn_y", "Set y to %1$s: just above the ship.");
        l.category("Motion");
        l.block("setVelocityY", "add_vely", "Add \"%1$s\".");
        l.value("setVelocityY", 0, "bullet", "vely_bullet", "Type %1$s in the element field: the copy we just made.");
        l.value("setVelocityY", 1, "700", "vely_up", "Set the speed to %1$s (up).");
        l.saveBlocks();
        l.closeBlocks();
        l.selectFromList("rock");
        l.elementEvent("rock", "OnBodyCreated", "rock_created", "Tap OnBodyCreated for the rock.");
        l.category("Motion");
        l.block("setVelocityY", "add_vely", "Add \"%1$s\".");
        l.value("setVelocityY", 1, "-250", "vely_down", "Set the speed to %1$s (minus = down).");
        l.saveBlocks();
        l.closeBlocks();
        l.elementEvent("rock", "OnBodyUpdate", "rock_update", "Tap OnBodyUpdate: when the rock leaves the screen at the bottom, it comes back from the top.");
        wrapAround(l, "y", "-150", "y", true, "80", "620", "1400");
        l.saveBlocks();
        l.closeBlocks();
        l.elementEvent("rock", "onBodyCollided", "rock_hit", "Tap onBodyCollided: something hit the rock. Was it a bullet or the ship?");
        l.category("main");
        l.block("if", "add_if", "Add the \"%1$s\" block: blocks inside it run only when its condition is true.");
        l.valueBlock("if", 0, "Logic", "___ is named ___", "cond_named", "Tap the condition and choose %1$s from Logic.");
        l.param("if", 0, "body2", "drag_body2", "The yellow block shows this event's inputs. Long-press %1$s (the body that hit the rock) and drag it into the first circle.", 0);
        l.value("if", 0, "bullet", "named_bullet", "Type %1$s in the second circle.", 1);
        l.category("BodyModify");
        l.blockInside("destroy", "if", 1, "destroy_inside", "Drag \"%1$s\" inside \"%2$s\".");
        l.value("destroy", 0, "body2", "destroy_body2", "Set it to %1$s: the bullet disappears.");
        l.category("Motion");
        l.blockInside("goTo", "if", 1, "goto_inside", "Drag \"%1$s\" inside \"%2$s\" (the opening under the condition).");
        l.valueBlock("goTo", 1, "Math", "random ___ to ___", "goto_x_random", "Tap x and choose %1$s from Math: a new place each time.");
        l.value("goTo", 1, "80", "random_from", "Type %1$s in the first circle.", 0);
        l.value("goTo", 1, "620", "random_to", "Type %1$s in the second circle.", 1);
        l.value("goTo", 2, "1400", "goto_y_top", "Set y to %1$s: above the top of the screen.");
        l.category("main");
        l.blockInside("OpenScene", "if", 2, "else_restart", "Drag \"%1$s\" into the else opening: anything else that hits the rock is the ship, so the game restarts.");
        l.value("OpenScene", 0, "scene1", "open_scene1", "Choose %1$s: opening the same scene again restarts the game.");
        l.saveBlocks();
        l.closeBlocks();
        l.play("play_shooter", "Play! Drag your finger to move and tap the red button to fire. Press Back to come back.");
        l.info("shooter_end", "Pew pew! 🚀 Ideas: a score variable, more rocks (duplicate the rock), or an explosion sound.");
        return l;
    }

    // ================================================================ 5. platformer

    static Lesson platformer() {
        Lesson l = new Lesson("platformer", t("plat_title", "Platformer"), t("plat_tag", "Platformer · 20 min"),
                t("plat_desc", "Walk, jump on crates and grab the coin. Buttons, touch start / end, camera follow and collisions."));
        l.landscape = true;
        l.startImages = new String[]{"platformer/hills_bg.png", "platformer/grass_ground.png", "platformer/crate.png", "platformer/coin.png",
                "platformer/hero.png", "platformer/btn_left.png", "platformer/btn_right.png", "platformer/btn_jump.png"};
        l.sceneJson = scene(
                box("hills", "UI", "/hills_bg.png", 0, 0, 1280, 720, 0, "lock", "true"),
                box("ground", "STATIC", "/grass_ground.png", -200, 0, 2560, 128, 2, "lock", "true", "tileX", "5"),
                box("crate1", "STATIC", "/crate.png", 700, 128, 100, 100, 3),
                box("crate2", "STATIC", "/crate.png", 1000, 128, 100, 100, 3),
                box("crate3", "STATIC", "/crate.png", 1000, 228, 100, 100, 3),
                box("coin", "STATIC", "/coin.png", 1015, 400, 70, 70, 4, "isSensor", "true"),
                box("left", "UI", "/btn_left.png", 40, 30, 130, 130, 8),
                box("right", "UI", "/btn_right.png", 190, 30, 130, 130, 8),
                box("jump", "UI", "/btn_jump.png", 1110, 30, 130, 130, 8));
        welcomeAndFiles(l, "plat_welcome", "A platformer! Walk with the arrows, jump on the crates and grab the coin. I placed the hills, the ground, crates, a coin and the buttons.");
        l.addElement("box", "Box", 0, "add_box_hero", "Choose Box for our hero.");
        props(l, "hero", "hero.png", 200, 300);
        l.check("hero", "Fixed Rotation", "fixed_rot_hero", "Turn on Fixed Rotation so the hero doesn't fall over.");
        l.closePanel();
        l.sceneEvent("OnCreate", "scene_oncreate", "Tap OnCreate in Scene events: it runs once when the game starts.");
        l.category("Physics");
        l.block("setWorldGravity", "add_gravity", "Add \"%1$s\" under the yellow block.");
        l.value("setWorldGravity", 0, "900", "gravity_val", "Tap strength and type %1$s.");
        l.saveBlocks();
        l.closeBlocks();
        l.sceneEvent("OnStep", "scene_onstep", "Tap OnStep: it runs every frame, about 60 times per second.");
        l.category("Camera");
        l.block("cameraFollow", "add_camera", "Add \"%1$s\": the camera follows an element.");
        l.value("cameraFollow", 0, "hero", "camera_hero", "Choose %1$s.");
        l.saveBlocks();
        l.closeBlocks();
        walkButton(l, "right", "300", "right_start");
        walkButton(l, "left", "-300", "left_start");
        l.selectFromList("jump");
        l.elementEvent("jump", "onClick", "jump_click", "Tap onClick of the jump button.");
        l.category("main");
        l.block("if", "add_if", "Add the \"%1$s\" block: blocks inside it run only when its condition is true.");
        l.valueBlock("if", 0, "Logic", "___ touches ___", "cond_touches", "Tap the condition and choose %1$s from Logic: jump only when standing.");
        l.value("if", 0, "hero", "touch_hero", "Type %1$s in the first circle.", 0);
        l.value("if", 0, "ground", "touch_ground", "Type %1$s in the second circle.", 1);
        l.category("Motion");
        l.blockInside("jump", "if", 1, "jump_inside", "Drag \"%1$s\" inside \"%2$s\".");
        l.value("jump", 0, "hero", "jump_hero", "Choose %1$s.");
        l.value("jump", 1, "470", "jump_power", "Set the power to %1$s.");
        l.saveBlocks();
        l.closeBlocks();
        l.selectFromList("coin");
        l.elementEvent("coin", "onBodyCollided", "coin_hit", "Tap onBodyCollided of the coin.");
        l.category("main");
        l.block("if", "add_if", "Add the \"%1$s\" block: blocks inside it run only when its condition is true.");
        l.valueBlock("if", 0, "Logic", "___ is named ___", "cond_named_hero", "Tap the condition and choose %1$s: was it the hero?");
        l.param("if", 0, "body2", "drag_body2_coin", "Long-press %1$s on the yellow block (the body that touched the coin) and drag it into the first circle.", 0);
        l.value("if", 0, "hero", "named_hero", "Type %1$s in the second circle.", 1);
        l.category("BodyModify");
        l.blockInside("destroy", "if", 1, "destroy_inside", "Drag \"%1$s\" inside \"%2$s\".");
        l.value("destroy", 0, "current", "destroy_current", "Set it to %1$s: the coin disappears.");
        l.category("Debug");
        l.blockInside("toastValue", "if", 1, "toast_inside", "Drag \"%1$s\" inside \"%2$s\" too.");
        l.valueBlock("toastValue", 0, "Text", "\"___\"", "toast_text", "Tap its value and choose the text block %1$s from Text.");
        l.value("toastValue", 0, "Coin!", "toast_coin", "Type %1$s in the circle.", 0);
        l.saveBlocks();
        l.closeBlocks();
        l.play("play_plat", "Play! Use the arrows to walk and the up button to jump. Grab the coin! Press Back to come back.");
        l.info("plat_end", "Great platformer! 🏆 Ideas: more coins and crates (duplicate them), a score, or walk animations.");
        return l;
    }

    static void walkButton(Lesson l, String button, String speed, String key) {
        l.selectFromList(button);
        l.elementEvent(button, "onTouchStart", "btn_start", "Tap onTouchStart: it runs while the finger presses the %1$s button.");
        l.category("Motion");
        l.block("setVelocityX", "add_velx", "Add \"%1$s\".");
        l.value("setVelocityX", 0, "hero", "velx_hero", "Set the element to %1$s.");
        l.value("setVelocityX", 1, speed, "velx_speed", "Set the speed to %1$s.");
        l.saveBlocks();
        l.closeBlocks();
        l.elementEvent(button, "onTouchEnd", "btn_end", "Tap onTouchEnd: when the finger leaves the button, the hero stops.");
        l.category("Motion");
        l.block("setVelocityX", "add_velx", "Add \"%1$s\".");
        l.value("setVelocityX", 0, "hero", "velx_hero", "Set the element to %1$s.");
        l.value("setVelocityX", 1, "0", "velx_stop", "Set the speed to %1$s.");
        l.saveBlocks();
        l.closeBlocks();
    }
}
