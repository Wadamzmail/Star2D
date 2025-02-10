package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisScrollPane;

public class ControlLayer extends Table {
    private Table topContainer;
    private Table bottomContainer;
    private VisScrollPane topScrollPane;
    private VisScrollPane bottomScrollPane;
    
    private float iconSize = 50f;
    private ImageButton.ImageButtonStyle buttonStyle;

    public ControlLayer() {
        super(VisUI.getSkin());
        setupStyles();
        createLayout();
    }

    private void setupStyles() {
        if (!VisUI.isLoaded()) VisUI.load();
        buttonStyle = new ImageButton.ImageButtonStyle(VisUI.getSkin().get(ImageButton.ImageButtonStyle.class));
    }

    private void createLayout() {
        // Top section
        topContainer = new Table();
        topScrollPane = new VisScrollPane(topContainer);
        topScrollPane.setScrollingDisabled(true, false); // Vertical scroll disabled
        topScrollPane.setFadeScrollBars(false);
		topScrollPane.getStyle().background = VisUI.getSkin().getDrawable("window-bg");
        
        // Bottom section
        bottomContainer = new Table();
        bottomScrollPane = new VisScrollPane(bottomContainer);
        bottomScrollPane.setScrollingDisabled(true, false);
        bottomScrollPane.setFadeScrollBars(false);
		bottomScrollPane.getStyle().background = VisUI.getSkin().getDrawable("window-bg");

        // Main layout
        setFillParent(true);
        defaults().growX();
        
        add(topScrollPane).height(iconSize + 10).padBottom(5).row();
        add().growY().row(); // Spacer
        add(bottomScrollPane).height(iconSize + 10).padTop(5);
    }

    public void addIconToTop(String name, Drawable drawable, Runnable action) {
        addIcon(topContainer, name, drawable, action);
    }

    public void addIconToBottom(String name, Drawable drawable, Runnable action) {
        addIcon(bottomContainer, name, drawable, action);
    }

    private void addIcon(Table container, String name, Drawable drawable, Runnable action) {
        VisImageButton button = new VisImageButton(drawable);
		//button.setStyle(buttonStyle);
        button.setName(name);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
            }
        });
        
        container.add(button).size(iconSize).pad(2);
        container.invalidateHierarchy();
    }

    // Optional configuration methods
    public void setIconSize(float size) {
        this.iconSize = size;
        refreshIconSizes();
    }

    private void refreshIconSizes() {
        refreshContainerSizes(topContainer);
        refreshContainerSizes(bottomContainer);
    }

    private void refreshContainerSizes(Table container) {
        for (Cell<?> cell : container.getCells()) {
            cell.size(iconSize);
        }
        container.invalidateHierarchy();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate(); // Ensure layout is updated
        super.draw(batch, parentAlpha);
    }
}