package com.star4droid.star2d.ui.tutorial;

import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.star2d.ui.blocks.BlockCatalog;
import com.star4droid.star2d.ui.blocks.BlockEditorActivity;
import com.star4droid.star2d.ui.blocks.BlockInfo;

import java.util.ArrayList;

/**
 * A lesson: a list of steps plus how its project starts (orientation, images, prebuilt elements).
 * The builder methods below create the common step groups (add an element, set a property,
 * open an event, drag a block, fill a field…), so each lesson reads like a script.
 */
public class Lesson {
    public final String id, title, tag, desc;
    public boolean landscape;
    /** Collection files copied into images/ when the project is created ("pack/file.png"). */
    public String[] startImages = {};
    /** Prebuilt scene elements (JSON array) or null. */
    public String sceneJson;
    public final ArrayList<Step> steps = new ArrayList<>();

    public Lesson(String id, String title, String tag, String desc) {
        this.id = id;
        this.title = title;
        this.tag = tag;
        this.desc = desc;
    }

    interface P { com.star4droid.star2d.ui.PanelHost.Panel get(); }

    static String t(String key, String en) { return Tr.t("tut_" + key, en); }

    static String f(String key, String en, Object... args) {
        try {
            return String.format(t(key, en), args);
        } catch (Exception e) {
            return String.format(en, args);
        }
    }

    static String blockName(String title) {
        return BlockInfo.label(title, Ui.isRtl(Tutorial.activity() == null ? com.star4droid.star2d.evo.star2dApp.getContext() : Tutorial.activity()));
    }

    static String catName(String cat) {
        return BlockInfo.category(cat, Ui.isRtl(Tutorial.activity() == null ? com.star4droid.star2d.evo.star2dApp.getContext() : Tutorial.activity()));
    }

    Lesson add(Step s) {
        steps.add(s);
        return this;
    }

    // ================================================================ generic

    Lesson info(String key, String en) {
        return add(new Step(t(key, en)));
    }

    Lesson infoAt(String key, String en, String tag) {
        return add(new Step(t(key, en)).at(tag));
    }

    // ================================================================ editor

    Lesson openPanel(String railTag, P panel, String key, String en) {
        return add(new Step(t(key, en)).at(railTag)
                .until(() -> L.panelIs(panel.get()))
                .auto(() -> L.show(panel.get())));
    }

    Lesson closePanel() {
        return add(new Step(t("close_panel", "Close this panel with ✕ to see the scene again.")).at("panel:close")
                .until(L::noPanel).auto(L::hidePanel));
    }

    /** Open the add sheet and add an element type; the new element is selected. */
    Lesson addElement(String typeTag, String typeName, int pos, String key, String en) {
        add(new Step(t("add_open", "Tap the blue + button to add a new element.")).at("tool:add")
                .until(() -> L.shell() != null && L.panelIs(L.shell().addItem))
                .auto(() -> L.show(L.shell().addItem)));
        final String cnt = "count_" + typeName + "_" + steps.size();
        return add(new Step(t(key, en)).at("add:" + typeTag)
                .onEnter(() -> Tutorial.vars.put(cnt, L.count(typeName)))
                .until(() -> L.count(typeName) > (Integer) Tutorial.vars.get(cnt))
                .auto(() -> L.addBody(pos)));
    }

    Lesson openProperties() {
        return add(new Step(t("open_props", "Open the Properties panel: everything about the selected element is here."))
                .at("rail:properties")
                .until(() -> L.shell() != null && L.panelIs(L.shell().properties))
                .auto(() -> L.show(L.shell().properties)));
    }

    /** Rename the selected element. */
    Lesson rename(String name) {
        return add(new Step(f("rename", "Tap the name box, write %1$s and press Done ✓ on the keyboard. Blocks use this name.", name))
                .at("propedit:name")
                .until(() -> L.elem(name) != null)
                .auto(() -> L.renameSelected(name)));
    }

    Lesson image(String name, String file) {
        return add(new Step(f("image", "Tap the image box, then pick %1$s.", file))
                .at(Targets.first(Targets.view("pick:" + file), Targets.view("prop:image")))
                .until(() -> L.propIs(name, "image", "/" + file))
                .auto(() -> L.setProp(name, "image", "/" + file)));
    }

    Lesson type(String name, String type, String key, String en) {
        return add(new Step(t(key, en))
                .at(Targets.first(Targets.view("menu:" + type), Targets.view("propedit:type")))
                .until(() -> L.propIs(name, "type", type))
                .auto(() -> L.setProp(name, "type", type)));
    }

    Lesson number(String name, String prop, String value, String key, String en) {
        return add(new Step(f(key, en, value)).at("propedit:" + prop)
                .until(() -> L.propIs(name, prop, value))
                .auto(() -> L.setProp(name, prop, value)));
    }

    Lesson check(String name, String prop, String key, String en) {
        return add(new Step(t(key, en)).at("prop:" + prop)
                .until(() -> L.propIs(name, prop, "true"))
                .auto(() -> L.setProp(name, prop, "true")));
    }

    Lesson selectFromList(String name) {
        openPanel("rail:elements", () -> L.shell().elements, "open_elements", "Open the Elements list: all the elements of the scene.");
        return add(new Step(f("select_elem", "Tap %1$s to select it.", name)).at("elem:" + name)
                .until(() -> name.equals(L.selected()))
                .auto(() -> L.select(name)));
    }

    /** Events panel -> an element event (opens the block editor). */
    Lesson elementEvent(String name, String event, String key, String en) {
        openPanel("rail:events", () -> L.shell().events, "open_events", "Open the Events panel. Events are moments in the game: a tap, a hit, every frame…");
        return add(new Step(f(key, en, name)).at("event:" + event)
                .until(() -> L.blocksOpen(name, event))
                .auto(() -> L.openEvent(name, event)));
    }

    Lesson sceneEvent(String event, String key, String en) {
        openPanel("rail:events", () -> L.shell().events, "open_events", "Open the Events panel. Events are moments in the game: a tap, a hit, every frame…");
        return add(new Step(t(key, en)).at("scene_event:" + event)
                .until(() -> L.blocksOpen(null, event))
                .auto(() -> L.openSceneEvent(event)));
    }

    // ================================================================ blocks

    Lesson category(String cat) {
        return add(new Step(f("category", "Open the \"%1$s\" category of the palette.", catName(cat))).at("blocks:cat:" + cat)
                .until(() -> L.blocks() != null && cat.equals(L.blocks().currentCategory()))
                .auto(() -> { if (L.blocks() != null) L.blocks().revealPalette(firstOf(cat)); }));
    }

    private static String firstOf(String cat) {
        java.util.ArrayList<BlockCatalog.Def> l = BlockCatalog.CATEGORIES.get(cat);
        return l == null || l.isEmpty() ? "" : l.get(0).title;
    }

    /** Drag (or tap) a palette block under the last block of the event. */
    Lesson block(String title, String key, String en) {
        return add(new Step(f(key, en, blockName(title))).at("blocks:def:" + title).dragTo(Targets.dropAfterRoot())
                .onEnter(() -> { BlockEditorActivity b = L.blocks(); if (b != null) b.revealPalette(title); })
                .until(() -> L.inRoot(title))
                .auto(() -> L.insert(title)));
    }

    /** Drag a palette block into the opening of a C-block. */
    Lesson blockInside(String title, String parent, int slot, String key, String en) {
        return add(new Step(f(key, en, blockName(title), blockName(parent))).at("blocks:def:" + title).dragTo(Targets.mouth(parent, slot))
                .onEnter(() -> { BlockEditorActivity b = L.blocks(); if (b != null) b.revealPalette(title); })
                .until(() -> L.inside(parent, slot, title))
                .auto(() -> L.insertInside(parent, slot, title)));
    }

    /** Tap a field, type a value (or tap a suggestion) and press ✓. */
    Lesson value(String title, int index, String value, String key, String en, int... path) {
        Step.Finder chip = Targets.view("blocks:chip:" + value);
        Step.Finder typing = Targets.unionOf("blocks:value_input", "blocks:value_ok", "blocks:chip:" + value);
        Step.Finder slot = Targets.path(title, index, path);
        return add(new Step(f(key, en, value)).at(() -> L.editing(title, index, path) ? typing.find() : slot.find())
                .until(() -> L.fieldIs(title, index, value, path))
                .auto(() -> L.setField(title, index, value, path)));
    }

    /** Tap a field and choose a value block for it. */
    Lesson valueBlock(String title, int index, String group, String display, String key, String en, int... path) {
        Step.Finder slot = Targets.path(title, index, path);
        Step.Finder grp = Targets.view("blocks:group:" + group);
        Step.Finder item = Targets.view("blocks:value:" + display);
        return add(new Step(f(key, en, BlockInfo.reporter(display).replace("___", "◯"))).at(() -> {
                    if (!L.editing(title, index, path)) return slot.find();
                    BlockEditorActivity b = L.blocks();
                    if (b != null && !group.equals(b.valueGroup())) return grp.find();
                    if (b != null) {
                        Step.Found it = item.find();
                        if (it == null) b.scrollValuesTo(group, display);
                        return it;
                    }
                    return null;
                })
                .until(() -> L.fieldHasBlock(title, index, display, path))
                .auto(() -> L.setBlock(title, index, display, path)));
    }

    Lesson saveBlocks() {
        return add(new Step(t("save_blocks", "Save the blocks with the blue save button.")).at("blocks:save")
                .until(L::saved)
                .auto(() -> { BlockEditorActivity b = L.blocks(); if (b != null) b.saveNow(); }));
    }

    Lesson closeBlocks() {
        return add(new Step(t("close_blocks", "Go back to the scene with the back arrow.")).at("blocks:back").back()
                .until(L::blocksClosed)
                .auto(() -> { BlockEditorActivity b = L.blocks(); if (b != null) b.saveAndClose(); }));
    }

    // ================================================================ play

    Lesson play(String key, String en) {
        add(new Step(t("play", "Tap ▶ to build and play your game. The first build can take a few seconds.")).at("top:play")
                .until(L::playing)
                .auto(() -> { EditorShell s = L.shell(); if (s != null) s.play(); }));
        return add(new Step(t(key, en)).free().back()
                .until(() -> !L.playing())
                .auto(() -> com.star4droid.star2d.ui.Bridge.gl(() -> com.star4droid.star2d.ui.Bridge.app().play(null))));
    }

    Lesson files(String[] names) {
        openPanel("rail:files", () -> L.shell().files, "open_files", "Open Files: your images, sounds and scripts live here.");
        add(new Step(t("images_folder", "This is the images folder. You can bring your own pictures with Import (↑) or take free ones from Collections, but this time I already added the images for this game."))
                .at("files:import")
                .onEnter(() -> { EditorShell s = L.shell(); if (s != null) s.files.openFolder("images"); }));
        return closePanel();
    }
}
