package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;

public class EditorTextItem extends Label implements EditorItem {
	LibgdxEditor editor;
	PropertySet<String,Object> propertySet;
	public EditorTextItem(LibgdxEditor editor){
		super("TextItem",createDefaultStyle());
		this.editor = editor;
		setStyle(createDefaultStyle());
		setText("TextItem");
		editor.addActor(this);
		addListener(new ClickListener(){
			@Override
			public void clicked (InputEvent event, float x, float y) {
				editor.selectActor(EditorTextItem.this);
			}
		});
		//debug();
		setWrap(true);
	}
	
	public static LabelStyle createDefaultStyle(){
		BitmapFont font = new BitmapFont(Gdx.files.internal("files/default.fnt"));
		LabelStyle labelStyle = new LabelStyle(font, Color.WHITE);
		return labelStyle;
	}
	
	@Override
	public PropertySet<String, Object> getPropertySet() {
	    return propertySet;
	}
	
	public EditorTextItem setDefault() {
		propertySet = PropertySet.getDefualt(this,"text.json");
		//if(propertySet==null) Log.e(Utils.error_tag,"Null PropertySet");
		return this;
	}
	
	@Override
	public void update() {
		setName(propertySet.getString("name"));
		float width = propertySet.getFloat("width"),
			height = propertySet.getFloat("height"),
			x = propertySet.getFloat("x"),
			y = propertySet.getFloat("y");
		setSize(width,height);
		setPosition(x,getStage().getHeight()-height-y);
		//Gdx.files.external("logs/text.txt").writeString("eh : "+getStage().getHeight()+", txh : "+height+", y : "+y+" \n = "+getY()+"\n"+"_".repeat(10)+"\n",true);
		setZIndex(propertySet.getInt("z"));
		setRotation(-propertySet.getFloat("rotation"));
		setVisible(propertySet.getString("Visible").equals("true"));
		setText(propertySet.get("Text").toString());
		getStyle().fontColor= new Color(propertySet.getColor("Text Color"));
	}

	@Override
	public void setProperties(PropertySet<String, Object> propertySet) {
		this.propertySet = propertySet;
		update();
	}

	@Override
	public String getTypeName() {
	    return "Text";
	}
	
}