package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.kotcrab.vis.ui.widget.color.ColorPickerListener;
import com.kotcrab.vis.ui.widget.popup.PopupMenu;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Helpers.PropertySet;

public class LightSettingsDialog {

    public static void showFor(Project project, String scene) {
        if (!VisUI.isLoaded()) {
            throw new IllegalStateException("VisUI must be loaded before using this dialog.");
        }

        Skin skin = VisUI.getSkin();

        String config = FileUtil.readFile(project.getConfig(scene));
        PropertySet<String, Object> propertySet = config.isEmpty() ? new PropertySet<>() : PropertySet.getFrom(config);

        Window dialog = new Window("Light Settings", skin);
        dialog.pad(10).defaults().pad(5);

        Table contentTable = new Table();
        contentTable.defaults().fillX().expandX();

        // Add blur number field
        TextField blurNumField = new TextField(propertySet.containsKey("blur_number") ? propertySet.getString("blur_number") : "1", skin);
        blurNumField.setMessageText("Blur Number");
        contentTable.add(new CheckBox("Enable Blur", skin)).row();
        contentTable.add(blurNumField).row();

        // Add checkboxes for various settings
        addCheckBox(contentTable, "Use Diffuse Light", propertySet, skin);
        addCheckBox(contentTable, "Culling", propertySet, skin);
        addCheckBox(contentTable, "Gamma Correction", propertySet, skin);
        addCheckBox(contentTable, "Shadows", propertySet, skin);

        // Add ambient light color picker
        final String ambientKey = "ambient_light";
        String ambientColorHex = propertySet.containsKey(ambientKey) ? (String) propertySet.get(ambientKey) : "#FFFFFF";
        TextButton ambientButton = new TextButton("Ambient Light (" + ambientColorHex + ")", skin);
        ambientButton.addListener(event -> {
            ColorPicker colorPicker = new ColorPicker("Pick Ambient Color", skin);
            colorPicker.setColor(com.badlogic.gdx.graphics.Color.valueOf(ambientColorHex));
            colorPicker.addListener(new ColorPickerListener() {
                @Override
                public void canceled() {}

                @Override
                public void finished(com.badlogic.gdx.graphics.Color color) {
                    String hex = String.format("#%06X", (0xFFFFFF & color.toIntBits()));
                    propertySet.put(ambientKey, hex);
                    ambientButton.setText("Ambient Light (" + hex + ")");
                }
            });
            PopupMenu popup = new PopupMenu(skin);
            popup.addItem(colorPicker);
            popup.showMenu(dialog.getStage(), ambientButton);
            return false;
        });
        contentTable.add(ambientButton).colspan(2).row();

        // Save button
        TextButton saveButton = new TextButton("Save", skin);
        saveButton.addListener(event -> {
            saveSettings(contentTable, propertySet);
            FileUtil.writeFile(project.getConfig(scene), propertySet.toString());
            Gdx.app.postRunnable(() -> {
                if (LibgdxEditor.getCurrentEditor() != null) {
                    try {
                        LibgdxEditor.getCurrentEditor().updateConfig();
                        LibgdxEditor.getCurrentEditor().setupLight();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            dialog.remove();
            return false;
        });

        contentTable.add(saveButton).colspan(2).center().padTop(10).row();

        dialog.add(contentTable).expand().fill();
        dialog.pack();
        dialog.centerWindow();
        dialog.setMovable(true);
        dialog.setVisible(true);
    }

    private static void addCheckBox(Table table, String label, PropertySet<String, Object> propertySet, Skin skin) {
        CheckBox checkBox = new CheckBox(label, skin);
        checkBox.setChecked(Boolean.parseBoolean(propertySet.getOrDefault(label, "false").toString()));
        table.add(checkBox).colspan(2).row();
        propertySet.put(label, checkBox.isChecked() ? "true" : "false");
    }

    private static void saveSettings(Table table, PropertySet<String, Object> propertySet) {
        for (Actor actor : table.getChildren()) {
            if (actor instanceof CheckBox) {
                CheckBox checkBox = (CheckBox) actor;
                propertySet.put(checkBox.getText(), checkBox.isChecked() ? "true" : "false");
            } else if (actor instanceof TextField) {
                TextField textField = (TextField) actor;
                propertySet.put(textField.getMessageText(), textField.getText());
            }
        }
    }
}