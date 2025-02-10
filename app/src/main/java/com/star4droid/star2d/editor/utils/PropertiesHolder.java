package com.star4droid.star2d.editor.utils;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import com.badlogic.gdx.Gdx;
import com.star4droid.star2d.Adapters.Section;
import android.widget.LinearLayout;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Items.Editor;

public class PropertiesHolder {
	LinearLayout propertiesLinear;
	Editor editor;
	public PropertiesHolder(LinearLayout linearLayout,Editor editor){
		this.propertiesLinear = linearLayout;
		this.editor = editor;
		if(propertiesLinear == null || editor == null)
			throw new RuntimeException("editor and properties cant be null!!");
	}
	
	public void setProperties(LinearLayout linearLayout){
		this.propertiesLinear = linearLayout;
	}
	
	public void updateProperties(){
		new Handler(Looper.getMainLooper()).post(()->updateInternal());
	}
	
	private void updateInternal(){
		//Gdx.files.external("logs/props.txt").writeString("\nprops linear : "+(propertiesLinear!=null)+", view : "+(editor.getSelectedView()!=null),true);
		if (propertiesLinear == null || editor.getSelectedView() == null)
			return;
		PropertySet<String,Object> itemPropertySet = PropertySet.getPropertySet(editor.getSelectedView());
		for (int x = 0; x < propertiesLinear.getChildCount(); x++) {
			View v = propertiesLinear.getChildAt(x);
			if (v.getTag() == null)
				continue;
			if (v.getTag() instanceof Section) {
				((Section) (v.getTag())).update(itemPropertySet);
			}
		}
	}
}