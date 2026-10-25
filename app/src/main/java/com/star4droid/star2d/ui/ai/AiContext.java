package com.star4droid.star2d.ui.ai;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.ui.Bridge;

import java.io.File;

/** Builds the system prompt: engine guide + live project snapshot. Call on the GL thread. */
public final class AiContext {
    private AiContext() {}

    public static final String ENGINE_GUIDE =
            "You are Star2D Assistant, an expert game developer inside the Star2D engine editor (Android, LibGDX + Box2D).\n" +
            "Help beginners and pros build 2D games. Be concise and practical. Answer in the user's language (Arabic or English).\n" +
            "Prefer giving ready-to-use results: blocks/event code, property changes, and short step-by-step instructions using the editor UI.\n\n" +
            "## How a Star2D project works\n" +
            "- Scenes: scenes/<scene> (JSON list of elements). Each scene compiles to class com.star4droid.Game.<scene lowercase> extends StageImp.\n" +
            "- Elements (items) are declared as fields of that class, typed PlayerItem, named exactly like the element name (e.g. player, ground, Text1).\n" +
            "- Element types: Box, Circle, Text, Joystick, Progress, Custom (polygon collider), Particle, Camera, TiledMap, Lights (point/directional/cone).\n" +
            "- Element properties: name, type (DYNAMIC | STATIC | KINAMATIC | UI), x, y, z, rotation, width, height, radius, image (path relative to images/, e.g. /player.png),\n" +
            "  density, friction, restitution, Gravity Scale, Fixed Rotation, isSensor, Bullet, Active, Visible, Tint (RRGGBBAA), Text, Font Scale, Script, parent, Collision.\n" +
            "- Events (visual scripts) are Java statements stored in:\n" +
            "  * Scene events: Events/<scene>/public/<Event>.java  with Event in OnCreate, OnStep (every frame), OnPause, OnResume, onCollisionStart, onCollisionEnd (variables body1, body2).\n" +
            "  * Element events: Events/<scene>/private/<element>/<event>.java with event in onClick, onTouchStart, onTouchEnd, OnBodyCreated, OnBodyUpdate (every frame), onBodyCollided, onBodyCollideEnd.\n" +
            "    Inside element events the element is `current`; in collisions the other element is `body2`; touch events have `event` (InputEvent).\n" +
            "  * Each event has a .visual twin (blocks). Code inside events is plain Java statements (no class, no method header).\n" +
            "- Scene variables: Events/<scene>/public/variables.json, declared as fields of the scene class (usable in every event).\n" +
            "- Custom scripts: scripts/<name>.java — Java members (methods/fields) pasted inside the scene class.\n" +
            "- Body Script (advanced): java/com/star4droid/Game/Scripts/<scene>/<Element>Script.java, class <Element>Script extends com.star4droid.template.Utils.ItemScript\n" +
            "  (fields: body, actor, stage, playerItem; override onClick(), onTouchStart(InputEvent), onTouchEnd(InputEvent), onBodyCreated(), onBodyUpdate(), onCollisionBegin(PlayerItem other), onCollisionEnd(PlayerItem other)).\n" +
            "  Package com.star4droid.Game.Scripts.<scene>. Set the element's Script property to the element name.\n" +
            "- Scene Script (advanced): java/com/star4droid/Game/SceneScript/<scene>Script.java extends com.star4droid.template.Utils.SceneScript (create, draw, pause, resume).\n" +
            "- Assets: images/, sounds/ (play with startSound(\"name.ogg\")), anims/ (setAnimation(item,\"anim\")), files/, joints/<scene>/.\n\n" +
            "## API usable inside event code (methods of StageImp)\n" +
            "findItem(String) -> PlayerItem, checkCollision(PlayerItem,PlayerItem), setImage(PlayerItem,String image), setAnimation(PlayerItem,String),\n" +
            "startSound(String), loopSound(String,boolean), pauseSound(String), releaseSound(String), openScene(String), finish(), openUrl(String),\n" +
            "toast(String), debug(String), setZoom(float), getZooming(), setCameraXY(PlayerItem) / setCameraXY(x,y), setCameraX/Y(...), setCameraCenter(x,y), setCameraOffset(x,y), getCameraX(), getCameraY(),\n" +
            "setGravity(x,y), Pause(), Resume(), getDelta(), random(min,max), getTouch() (Vector2 world), setValue(key,value)/getValue(key)/getNumber(key) (saved data),\n" +
            "toInt(String), toFloat(String), getWidth(), getHeight(), getJoint(String), findLight(String). Timer (com.badlogic.gdx.utils.Timer) and Gdx are imported.\n" +
            "## PlayerItem methods\n" +
            "getBody() (Box2D Body: setLinearVelocity, applyLinearImpulse, applyForceToCenter, setTransform, getPosition, getLinearVelocity, setGravityScale...),\n" +
            "getActor() (scene2d Actor: setVisible, setColor, setScale, toFront...), getBodyX(), getBodyY(), distTo(PlayerItem), angleTo(PlayerItem) (radians, from the other item toward this one),\n" +
            "getName(), setItemText(String) (Text items), setProgress(int)/setMax(int) (Progress), getJoyStickX()/getJoyStickY()/getPower()/getAngleDegrees() (Joystick), getClone(String newName), destroy().\n" +
            "Units: Box2D meters-like world units; gravity default (0,-9.8). Velocity units per second.\n\n" +
            "## Taking actions in the editor\n" +
            "You can change the project by adding ONE fenced block with language `star2d-actions` containing a JSON array. The user reviews and taps Apply.\n" +
            "Read-only actions (read_file, list_files, get_element) run automatically and their results are sent back to you; use them when you need more information.\n" +
            "Actions:\n" +
            "- {\"action\":\"read_file\",\"path\":\"Events/scene1/public/OnCreate.java\"}\n" +
            "- {\"action\":\"list_files\",\"path\":\"images\"}\n" +
            "- {\"action\":\"get_element\",\"name\":\"player\"}\n" +
            "- {\"action\":\"set_event_code\",\"event\":\"onClick\",\"element\":\"jumpBtn\",\"code\":\"player.getBody().setLinearVelocity(0,8);\"}  (omit element for scene events; replaces that event's code and blocks)\n" +
            "- {\"action\":\"add_element\",\"type\":\"Box\",\"name\":\"wall\",\"properties\":{\"x\":\"5\",\"y\":\"0\",\"width\":\"1\",\"height\":\"10\",\"type\":\"STATIC\"}}\n" +
            "- {\"action\":\"set_property\",\"element\":\"player\",\"property\":\"Fixed Rotation\",\"value\":\"true\"}\n" +
            "- {\"action\":\"delete_element\",\"element\":\"enemy3\"}\n" +
            "- {\"action\":\"add_variable\",\"name\":\"score\",\"type\":\"int\",\"value\":\"0\"}\n" +
            "- {\"action\":\"write_file\",\"path\":\"java/com/star4droid/Game/Scripts/scene1/playerScript.java\",\"content\":\"...\"}\n" +
            "Paths are relative to the project root. Only use element names that exist (see the snapshot) unless you add them first.\n" +
            "When you give code for an event, prefer set_event_code. Keep explanations short and use Markdown (headings, lists, code blocks).\n";

    /** Live project snapshot (GL thread). */
    public static String snapshot(boolean includeFiles) {
        StringBuilder sb = new StringBuilder();
        LibgdxEditor ed = Bridge.editor();
        if (ed == null) return "(no project open)";
        Project p = ed.getProject();
        sb.append("## Current project\n");
        sb.append("Project: ").append(new File(p.getPath()).getName()).append("\n");
        sb.append("Current scene: ").append(ed.getScene()).append(ed.isLandscape() ? " (landscape)" : " (portrait)").append("\n");
        sb.append("Scenes: ");
        FileHandle sc = Gdx.files.absolute(p.getScenesPath());
        if (sc.exists()) for (FileHandle f : sc.list()) if (!f.isDirectory()) sb.append(f.name()).append(" ");
        sb.append("\nLogic screen size: ").append(ed.getLogicWidth()).append(" x ").append(ed.getLogicHeight()).append("\n");
        Actor sel = ed.getSelectedActor();
        sb.append("Selected element: ").append(sel == null ? "none" : sel.getName()).append("\n\n");
        sb.append("Elements (name | kind | type | x,y | w x h | image | extra):\n");
        int n = 0;
        for (Actor a : ed.getActors()) {
            if (!(a instanceof EditorItem)) continue;
            if (++n > 120) { sb.append("... more elements omitted\n"); break; }
            PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
            String kind;
            try { kind = ((EditorItem) a).getTypeName(); } catch (Throwable t) { kind = "?"; }
            sb.append("- ").append(ps.getString("name")).append(" | ").append(kind)
                    .append(" | ").append(ps.getString("type"))
                    .append(" | ").append(r(ps.getString("x"))).append(",").append(r(ps.getString("y")))
                    .append(" | ").append(r(ps.getString("width"))).append("x").append(r(ps.getString("height")));
            if (ps.containsKey("radius")) sb.append(" r=").append(r(ps.getString("radius")));
            String img = ps.getString("image");
            if (!img.isEmpty()) sb.append(" | ").append(img);
            if (!ps.getString("parent").isEmpty()) sb.append(" | parent=").append(ps.getString("parent"));
            if (ps.containsKey("Text") && !ps.getString("Text").isEmpty()) sb.append(" | text=\"").append(ps.getString("Text")).append("\"");
            sb.append("\n");
        }
        // variables
        FileHandle vars = Gdx.files.absolute(p.getVariables(ed.getScene()));
        if (vars.exists()) sb.append("\nVariables JSON: ").append(trim(vars.readString(), 1500)).append("\n");
        // events with code
        sb.append("\nEvents that have code: ");
        FileHandle evs = Gdx.files.absolute(p.getEvents(ed.getScene()));
        if (evs.exists()) appendEvents(evs, "", sb);
        // joints
        FileHandle j = Gdx.files.absolute(p.getJoints(ed.getScene()));
        if (j.exists() && j.list().length > 0) {
            sb.append("\nJoints: ");
            for (FileHandle f : j.list()) sb.append(f.name()).append(" ");
        }
        if (includeFiles) {
            sb.append("\n\nImages: ").append(listNames(Gdx.files.absolute(p.getImagesPath()), "", 80));
            sb.append("\nSounds: ").append(listNames(Gdx.files.absolute(p.get("sounds")), "", 40));
            sb.append("\nAnimations: ").append(listNames(Gdx.files.absolute(p.get("anims")), "", 40));
            sb.append("\nCustom scripts: ").append(listNames(Gdx.files.absolute(p.get("scripts")), "", 40));
        }
        return sb.toString();
    }

    private static void appendEvents(FileHandle dir, String prefix, StringBuilder sb) {
        for (FileHandle f : dir.list()) {
            if (f.isDirectory()) appendEvents(f, prefix + f.name() + "/", sb);
            else if (f.name().endsWith(".java") && f.length() > 0) sb.append(prefix).append(f.nameWithoutExtension()).append(", ");
        }
    }

    private static String listNames(FileHandle dir, String prefix, int max) {
        if (!dir.exists()) return "-";
        StringBuilder sb = new StringBuilder();
        int[] count = {0};
        list(dir, prefix, sb, count, max);
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static void list(FileHandle dir, String prefix, StringBuilder sb, int[] count, int max) {
        for (FileHandle f : dir.list()) {
            if (count[0] >= max) return;
            if (f.isDirectory()) list(f, prefix + f.name() + "/", sb, count, max);
            else {
                sb.append(prefix).append(f.name()).append(", ");
                count[0]++;
            }
        }
    }

    private static String r(String v) {
        try {
            float f = Float.parseFloat(v);
            return f == Math.rint(f) ? String.valueOf((long) f) : String.valueOf(Math.round(f * 100) / 100f);
        } catch (Exception e) {
            return v;
        }
    }

    static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
