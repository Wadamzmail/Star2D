package com.star4droid.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * Minimal built-in scene: shows the detected orientation and the window size inside a FitViewport of the game's
 * logic size, the same way StageImp does. Used to check packaging and window sizing without a project.
 */
final class DemoApp extends ApplicationAdapter {
    private final String orientation;
    private final float vw, vh;
    private FitViewport viewport;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private float time;

    DemoApp(String orientation, float viewportW, float viewportH) {
        this.orientation = orientation == null || orientation.isEmpty() ? "landscape" : orientation;
        this.vw = viewportW;
        this.vh = viewportH;
    }

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(vw, vh, camera);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getData().setScale(Math.max(1f, Math.min(vw, vh) / 360f));
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render() {
        time += Gdx.graphics.getDeltaTime();
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.13f, 0.17f, 0.30f, 1);                 // the game's screen
        shapes.rect(0, 0, vw, vh);
        shapes.setColor(Color.ORANGE);                            // a moving box
        float s = Math.min(vw, vh) * 0.15f;
        shapes.rect((vw - s) * (0.5f + 0.4f * (float) Math.sin(time)), vh * 0.2f, s, s);
        shapes.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.draw(batch, "Star2D desktop demo\norientation: " + orientation + "\nviewport: " + (int) vw + " x " + (int) vh
                + "\nwindow: " + Gdx.graphics.getWidth() + " x " + Gdx.graphics.getHeight(), vw * 0.05f, vh * 0.95f);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
