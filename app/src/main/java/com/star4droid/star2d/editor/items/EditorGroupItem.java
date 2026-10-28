package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;

/**
 * The GROUP element: an empty container. In the editor it is a dashed rectangle with a cross in the
 * center and its name; it can be selected, moved, rotated and resized, and its children move with it.
 * It is only hit on its outline and on the center cross, so a big group never steals the taps of its children.
 */
public class EditorGroupItem extends Image implements EditorItem {
	PropertySet<String,Object> propertySet;
	LibgdxEditor editor;
	ShapeRenderer shapeRenderer;
	private static BitmapFont font;
	private static TextureRegionDrawable icon;
	
	private static TextureRegionDrawable icon(){
		if(icon == null)
			icon = new TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(new Texture(Gdx.files.internal("images/bodies/group.png"))));
		return icon;
	}
	private final Vector2 tmp = new Vector2();
	
	public EditorGroupItem(final LibgdxEditor libgdxEditor){
		super(icon());
		this.editor = libgdxEditor;
		this.shapeRenderer = libgdxEditor.getShapeRenderer();
		setSize(100,100);
		libgdxEditor.addActor(this);
		addListener(new ItemClickListener(this,editor));
	}
	
	@Override
	public PropertySet<String, Object> getPropertySet() {
	    return propertySet;
	}
	
	@Override
	public void update() {
		if(getStage()==null || propertySet==null) return;
		float w = propertySet.getFloat("width"), h = propertySet.getFloat("height");
		if(w <= 0) w = 100;
		if(h <= 0) h = 100;
		setSize(w,h);
		setOrigin(w*0.5f,h*0.5f);
		String name = propertySet.getString("name");
		if(!name.equals(""))
			setName(name);
		setPosition(propertySet.getFloat("x"),propertySet.getFloat("y"));
		setZIndex(propertySet.getInt("z"));
		setRotation(-propertySet.getFloat("rotation"));
		setVisible(!propertySet.getString("Visible").equals("false"));
	}
	
	@Override
	public Body getBody() {
		return null;
	}
	
	public LibgdxEditor getEditor(){
		return editor;
	}
	
	public EditorGroupItem setDefault() {
		propertySet = PropertySet.getDefualt(this,"group.json");
		return this;
	}
	
	@Override
	protected void sizeChanged() {
		super.sizeChanged();
		setOrigin(getWidth()*0.5f,getHeight()*0.5f);
	}
	
	@Override
	public void setProperties(PropertySet<String, Object> propertySet) {
	    this.propertySet = propertySet;
		if(propertySet==null) return;
		// add the missing keys; keys we do not know (prefab / pid / pbase ...) are kept
		PropertySet<String,Object> temp = PropertySet.getDefualt(this,"group.json");
		for(String s:temp.keySet()){
			if(!propertySet.containsKey(s))
				propertySet.put(s,temp.get(s));
		}
		update();
	}
	
	/** Only the outline and the center handle can be hit (a group is mostly empty space). */
	@Override
	public Actor hit(float x, float y, boolean touchable) {
		if(touchable && getTouchable() != Touchable.enabled) return null;
		if(!isVisible()) return null;
		float zoom = 1f;
		try { zoom = ((com.badlogic.gdx.graphics.OrthographicCamera)getStage().getCamera()).zoom; } catch(Throwable ignored){}
		float band = Math.max(10f, 14f * zoom);
		float w = getWidth(), h = getHeight();
		if(x < -band || y < -band || x > w + band || y > h + band) return null;
		boolean nearEdge = x < band || y < band || x > w - band || y > h - band;
		boolean nearCenter = Math.abs(x - w*0.5f) < band*1.6f && Math.abs(y - h*0.5f) < band*1.6f;
		return (nearEdge || nearCenter) ? this : null;
	}
	
	private void dashed(float x1, float y1, float x2, float y2, float dash){
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float)Math.sqrt(dx*dx + dy*dy);
		if(len < 0.01f) return;
		int n = Math.max(1, (int)(len / dash));
		if(n > 400) n = 400;
		for(int i = 0; i < n; i += 2){
			float a = i / (float)n, b = Math.min((i + 1) / (float)n, 1f);
			shapeRenderer.line(x1 + dx*a, y1 + dy*a, x1 + dx*b, y1 + dy*b);
		}
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha) {
		if(getStage()==null || propertySet==null) return;
		float zoom = 1f;
		try { zoom = ((com.badlogic.gdx.graphics.OrthographicCamera)getStage().getCamera()).zoom; } catch(Throwable ignored){}
		batch.end();
		shapeRenderer.setProjectionMatrix(getStage().getCamera().combined);
		Gdx.gl.glLineWidth(2f);
		shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
		Color c = editor.getSelectedActor() == this ? Color.ORANGE : new Color(1f, 0.76f, 0.03f, 0.9f);
		shapeRenderer.setColor(c);
		float cx = getX() + getWidth()*0.5f, cy = getY() + getHeight()*0.5f;
		float hw = getWidth()*0.5f, hh = getHeight()*0.5f;
		float rad = getRotation() * MathUtils.degreesToRadians;
		float cos = MathUtils.cos(rad), sin = MathUtils.sin(rad);
		float[] px = new float[4], py = new float[4];
		float[][] corners = {{-hw,-hh},{hw,-hh},{hw,hh},{-hw,hh}};
		for(int i = 0; i < 4; i++){
			px[i] = cx + corners[i][0]*cos - corners[i][1]*sin;
			py[i] = cy + corners[i][0]*sin + corners[i][1]*cos;
		}
		float dash = Math.max(8f, 8f * zoom);
		for(int i = 0; i < 4; i++){
			int j = (i + 1) % 4;
			dashed(px[i], py[i], px[j], py[j], dash);
		}
		// center cross
		float arm = Math.max(8f, 10f * zoom);
		shapeRenderer.line(cx - arm*cos, cy - arm*sin, cx + arm*cos, cy + arm*sin);
		shapeRenderer.line(cx + arm*sin, cy - arm*cos, cx - arm*sin, cy + arm*cos);
		shapeRenderer.end();
		Gdx.gl.glLineWidth(1f);
		batch.begin();
		// name
		try {
			if(font == null) font = new BitmapFont();
			font.getData().setScale(Math.max(0.5f, zoom));
			font.setColor(c);
			String n = propertySet.getString("name");
			float topY = Math.max(Math.max(py[0], py[1]), Math.max(py[2], py[3]));
			float leftX = Math.min(Math.min(px[0], px[1]), Math.min(px[2], px[3]));
			font.draw(batch, n, leftX, topY + 16f * Math.max(0.5f, zoom));
		} catch(Throwable ignored){}
	}

	@Override
	public String getTypeName() {
	    return "Group";
	}
	
}
