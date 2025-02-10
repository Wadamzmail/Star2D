package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.items.BoxItem;
import com.star4droid.star2d.editor.items.CircleItem;

public class AddPopup {
    private final Stage stage;
    private final Table contentTable;
    private final Container<Table> container;
    private final Table background;

    public AddPopup(LibgdxEditor editor) {
        this.stage = editor;
        background = new Table();
        background.setFillParent(true);
        background.setBackground(createTransparentDrawable(0.3f));
        background.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dismiss();
            }
        });

        contentTable = new Table();
        //contentTable.setBackground(skin.getDrawable("window"));
        contentTable.pad(10).defaults().pad(5);
        
        container = new Container<>(contentTable);
        container.setTransform(true);
        container.center();
        container.setFillParent(true);
    }

    public void addItem(String text, TextureRegion icon, final Runnable action) {
        Table itemTable = new Table();
        itemTable.align(Align.left);
        
        Image iconImage = new Image(icon);
		BitmapFont font = new BitmapFont(Gdx.files.internal("files/default.fnt"));
		Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        Label textLabel = new Label(text, labelStyle);
        
        itemTable.add(iconImage).size(32).padRight(10);
        itemTable.add(textLabel);
        
        itemTable.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
                dismiss();
            }
        });
        
        contentTable.add(itemTable).fillX().row();
    }

    public void show() {
        stage.addActor(background);
        stage.addActor(container);
        
        container.getActor().setScale(0.8f);
        container.getActor().getColor().a = 0;
        container.getActor().addAction(Actions.parallel(
            Actions.fadeIn(0.2f),
            Actions.scaleTo(1, 1, 0.2f)
        ));
    }

    public void dismiss() {
        container.getActor().addAction(Actions.sequence(
            Actions.parallel(
                Actions.fadeOut(0.2f),
                Actions.scaleTo(0.8f, 0.8f, 0.2f)
            ),
            Actions.removeActor()
        ));
        background.addAction(Actions.sequence(
            Actions.fadeOut(0.2f),
            Actions.removeActor()
        ));
    }

    private TextureRegionDrawable createTransparentDrawable(float alpha) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0, 0, 0, alpha);
        pm.fill();
        Texture texture = new Texture(pm);
        pm.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    // Usage example
    public static void showAddPopup(LibgdxEditor editor) {
        AddPopup popup = new AddPopup(editor);
        
        popup.addItem("Circle Item", new TextureRegion(new Texture("images/circle.png")), () -> {
            CircleItem item = new CircleItem(editor);
            item.setName("circle1");
            item.setDefault();
            item.update();
            editor.selectActor(item);
        });
        
        popup.addItem("Box Item", new TextureRegion(new Texture("images/box.png")), () -> {
            BoxItem item = new BoxItem(editor);
            item.setName("box1");
            item.setDefault();
            item.update();
            editor.selectActor(item);
        });
        
        // Add more items as needed...
        
        popup.show();
    }
}