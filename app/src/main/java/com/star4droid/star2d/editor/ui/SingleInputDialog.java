package com.star4droid.star2d.editor.ui;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;

import com.kotcrab.vis.ui.widget.VisDialog;

public class SingleInputDialog extends VisDialog {
	public SingleInputDialog(String title, String message, String initialText, InputListener listener) {
		super(title);
		VisTextField textField = new VisTextField(initialText);
		VisTextButton okButton = new VisTextButton("OK");
		VisTextButton cancelButton = new VisTextButton("Cancel");
		
		getContentTable().add(message).row();
		getContentTable().add(textField).growX().row();
		
		VisTable buttons = new VisTable();
		buttons.add(okButton).padRight(10);
		buttons.add(cancelButton);
		
		getContentTable().add(buttons);
		
		okButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				listener.onInput(textField.getText());
				fadeOut();
			}
		});
		
		cancelButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				fadeOut();
			}
		});
		
		setSize(300, 150);
		centerWindow();
	}
	
	interface InputListener {
		void onInput(String result);
	}
}