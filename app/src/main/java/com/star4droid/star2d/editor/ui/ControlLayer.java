package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Timer;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.kotcrab.vis.ui.widget.color.ColorPickerListener;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.CompileThread;
import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.*;

public class ControlLayer extends Table {
    private Table topContainer;
    private Table bottomContainer;
    private VisScrollPane topScrollPane;
    private VisScrollPane bottomScrollPane;
    
    private float iconSize = 70;
    private ImageButton.ImageButtonStyle buttonStyle;
	private TestApp app;
	private ColorPicker colorPicker;
	private Color sceneColorBeforePick = Color.BLACK;
	VisImageButton gridBtn,moveBtn,rotateBtn,lockBtn,scaleBtn,undoBtn,redoBtn;
	/*
		for me : don't add this to be built-in in the LibgdxEditor
		because it's not a good way if we implement Tabs...
	*/

    public ControlLayer(TestApp testApp) {
        super(VisUI.getSkin());
		this.app = testApp;
        setupStyles();
        createLayout();
		init();
    }

    private void setupStyles() {
        if (!VisUI.isLoaded()) VisUI.load();
        buttonStyle = new ImageButton.ImageButtonStyle(VisUI.getSkin().get(ImageButton.ImageButtonStyle.class));
    }
	
	private void init(){
		colorPicker = new ColorPicker("Choose Scene Color");
		final VisDialog compileDialog = new VisDialog("Compiler");
		final VisLabel compileLabel = new VisLabel("Compiling...");
		ScrollPane scrollPane = new ScrollPane(compileLabel);
		scrollPane.setFillParent(true);
		scrollPane.setFadeScrollBars(false);
		scrollPane.setScrollingDisabled(false,false);
		compileDialog.row();
		compileDialog.add(scrollPane).pad(4).maxHeight(500).minHeight(300).growX();
		compileDialog.row();
		VisTextButton cancelBtn = new VisTextButton("Cancel");
		VisTextButton copyBtn = new VisTextButton("Copy");
		
		cancelBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                compileDialog.hide();
            }
        });
		
		copyBtn.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				Gdx.app.getClipboard().setContents(compileLabel.getText().toString());
			}
		});
		
		compileDialog.add(cancelBtn).padRight(10).padBottom(5);
		compileDialog.add(copyBtn).padBottom(5);
		
		PopupMenu bodiesMenu = createBodiesMenu(),
					lightsMenu = createLightMenu();
		addIconToTop("rotate",drawable("rotate-dev.png"),(btn)->app.getEditor().setLandscape(!app.getEditor().isLandscape()));
		addIconToTop("add-body",drawable("add.png"),(btn)->{
			bodiesMenu.showMenu(getStage(),btn);
		});
		addIconToTop("add-light",drawable("add-light.png"),(btn)->{
			lightsMenu.showMenu(getStage(),btn);
		});
		addIconToTop("copy-scene",drawable("copy.png"),(btn)->{});
		addIconToTop("save",drawable("save.png"),(btn)->{
			if(app.getEditor()==null) {
				app.toast("editor returns null!!");
				return;
			}
			app.getEditor().getProject().save(app.getEditor());
			//btn.setScale(0);
			//btn.addAction(Actions.scaleTo(1,1,300));
			app.toast("Saved...");
		});
		addIconToTop("files",drawable("file-white.png"),(btn)->{
			this.app.getFileBrowser().setVisible(!this.app.getFileBrowser().isVisible());
		});
		addIconToTop("scene-color",drawable("color-pal.png"),(btn)->{
			if(app.getEditor()==null) return;
			if(colorPicker.getStage()!=null && colorPicker.getStage()==getStage()){
				colorPicker.remove();
				return;
			}
			//app.getEditor().updateConfig();
			sceneColorBeforePick = app.getEditor().backgroundColor;//app.getEditor().getConfig().containsKey("sceneColor")?app.getEditor().getConfig().getString("sceneColor"):"#263238";
			getStage().addActor(colorPicker);
		});
		colorPicker.setListener(new ColorPickerListener(){
			@Override
			public void canceled(Color oldColor) {
				if(app.getEditor()==null) return;
				Timer.schedule(new Timer.Task(){
					@Override
					public void run() {
						app.getEditor().setSceneColor(sceneColorBeforePick.toString().toUpperCase());
					}
					
				},800);
			}

			@Override
			public void changed(Color color) {
				if(app.getEditor()==null) return;
				//String hex = String.format("#%06X", (0xFFFFFF & color.toIntBits()));
				String hex = color.toString().toUpperCase();
				app.getEditor().setSceneColor(hex);
			}

			@Override
			public void reset(Color arg0, Color arg1) {}

			@Override
			public void finished(Color color) {
				if(app.getEditor()==null) return;
				//String hex = String.format("#%06X", (0xFFFFFF & color.toIntBits()));
				//String hex = color.toString().toUpperCase();
				//app.getEditor().setSceneColor(hex);
			}
			
		});
		lockBtn = addIconToTop("lock-item",drawable("lock.png"),(btn)->{
			if(app.getEditor()==null) return;
			if (app.getEditor().getSelectedActor() != null) {
                PropertySet<String, Object> propertySet = PropertySet.getPropertySet(app.getEditor().getSelectedActor());
                boolean isLock = propertySet.getString("lock").equals("true") ? false : true;
                btn.getStyle().imageUp = drawable(isLock?"lock.png":"unlock.png");
                propertySet.put("lock", String.valueOf(isLock));
            }
		});
		
		//bottom buttons
		addIconToBottom("play",drawable("play.png"),(btn)->{
			btn.setDisabled(true);
			cancelBtn.setDisabled(true);
			copyBtn.setDisabled(true);
			compileDialog.show(getStage());
			final InputProcessor inputProcessor = Gdx.input.getInputProcessor();
			//disable all inputs...
			Gdx.input.setInputProcessor(null);
			CodeGenerator.generateFor(app.getEditor(),(code)->{
				Gdx.files.absolute(app.getEditor().getProject().getCodesPath(app.getEditor().getScene())).writeString(code,false);
				CompileThread compileThread = new CompileThread(app.getEditor().getProject().get("java"),false);
				compileThread.setOnChangeStatus(new CompileThread.OnStatusChanged(){
					@Override
					public void onStatus(String s) {
						compileLabel.setText(s);
						scrollPane.pack();
					}

					@Override
					public void onEnd(String message) {
						Gdx.input.setInputProcessor(inputProcessor);
						cancelBtn.setDisabled(false);
						copyBtn.setDisabled(false);
					}

					@Override
					public void onError(String error) {
						compileLabel.setText(error);
						scrollPane.pack();
					}

					@Override
					public void onSuccess(String message) {
						compileDialog.hide();
						FileHandle fileHandle = new FileHandle(app.getEditor().getProject().getDex());
						
						if(fileHandle.exists())
							fileHandle.file().setWritable(true);
						fileHandle.writeString("", false);
						Gdx.files.absolute(app.getEditor().getProject().getPath()+"/java/classes.dex").moveTo(fileHandle);
						
						app.play(app.getEditor().getProject().getPath(),app.getEditor().getScene());
					}
					
				});
				compileThread.start();
			});
		});
		
		gridBtn = addIconToBottom("grid",drawable("grid-icon.png"),(btn)->{
			gridBtn.setColor(Color.YELLOW);
			moveBtn.setColor(Color.WHITE);
			scaleBtn.setColor(Color.WHITE);
			rotateBtn.setColor(Color.WHITE);
			app.getEditor().setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.GRID);
		});
		gridBtn.setColor(Color.YELLOW);
		
		moveBtn = addIconToBottom("move",drawable("move-icon.png"),(btn)->{
			if(app.getEditor().setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.MOVE)){
				moveBtn.setColor(Color.YELLOW);
				gridBtn.setColor(Color.WHITE);
				scaleBtn.setColor(Color.WHITE);
				rotateBtn.setColor(Color.WHITE);
			}
		});
		
		scaleBtn = addIconToBottom("scale",drawable("scale-icon.png"),(btn)->{
			if(app.getEditor().setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.SCALE)){
				scaleBtn.setColor(Color.YELLOW);
				gridBtn.setColor(Color.WHITE);
				moveBtn.setColor(Color.WHITE);
				rotateBtn.setColor(Color.WHITE);
			}
		});
		
		rotateBtn = addIconToBottom("rotate",drawable("rotate-icon.png"),(btn)->{
			if(app.getEditor().setTouchMode(com.star4droid.star2d.editor.LibgdxEditor.TOUCHMODE.ROTATE)){
				rotateBtn.setColor(Color.YELLOW);
				gridBtn.setColor(Color.WHITE);
				scaleBtn.setColor(Color.WHITE);
				moveBtn.setColor(Color.WHITE);
			}
		});
		
		addIconToBottom("delete",drawable("delete.png"),(btn)->{
			if(app.getEditor().getSelectedActor()==null) return;
			app.getEditor().getProject().deleteBody(PropertySet.getPropertySet(app.getEditor().getSelectedActor()).get("name").toString(), app.getEditor().getScene());
            app.getEditor().getSelectedActor().remove();
            app.getEditor().selectActor(null);
		});
		
		undoBtn = addIconToBottom("undo",drawable("undo.png"),(btn)->{
			if(app.getEditor().canUndo())
				app.getEditor().undo();
		});
		
		redoBtn = addIconToBottom("redo",drawable("redo.png"),(btn)->{
			if(app.getEditor().canRedo())
				app.getEditor().redo();
		});
		
		addIconToBottom("center-camera",drawable("center-camera.png"),(btn)->{
			app.getEditor().centerCamera();
		});
		
	}
	
	public void updateUndoRedo(){
		undoBtn.setDisabled(!app.getEditor().canUndo());
		undoBtn.setColor(app.getEditor().canUndo()?Color.YELLOW:Color.WHITE);
		
		redoBtn.setDisabled(!app.getEditor().canRedo());
		redoBtn.setColor(app.getEditor().canRedo()?Color.YELLOW:Color.WHITE);
	}
	
	public void bodySelected(){
		if (app.getEditor().getSelectedActor() != null) {
            String isLock = PropertySet.getPropertySet(app.getEditor().getSelectedActor()).getString("lock");
            lockBtn.getStyle().imageUp = (drawable(isLock.equals("true") ? "lock.png" : "unlock.png"));
        }
	}
	
	private Drawable drawable(String name){
		return new TextureRegionDrawable(new Texture(Gdx.files.internal("images/"+name)));
	}
	
	private PopupMenu createBodiesMenu(){
		PopupMenu menu = new PopupMenu();
		String[] names={"Box Body","Circle Body","Text Item","Joystick Item","Progress Bar","Custom Body","Particle Effect"};
		for(int x = 0; x < names.length; x++){
			final String name = names[x];
			final int pos = x;
			MenuItem item = new MenuItem(name,new ChangeListener() {
				@Override
				public void changed (ChangeEvent event, Actor actor) {
					EditorItem item = getItem(pos);
                	app.getEditor().addActor((Actor)item);
                	((Actor)item).setName(app.getEditor().getName((Actor)item));
                	try {
						java.lang.reflect.Method method = item.getClass().getMethod("setDefault");
						Object[] objs = null;
						method.invoke(item,objs);
					} catch(Exception e){}
                	item.getPropertySet().put("z",app.getEditor().getActors().size);
                	app.getEditor().selectActor((Actor)item);
                	item.update();
				}
			});
			item.pad(5);
			menu.addItem(item);
		}
		
		return menu;
	}
	
	private EditorItem getItem(int pos){
		switch(pos){
			case 0: return new BoxItem(app.getEditor());
			case 1: return new CircleItem(app.getEditor());
			case 2: return new EditorTextItem(app.getEditor());
			case 3: return new JoyStickItem(app.getEditor());
			case 4: return new EditorProgressItem(app.getEditor());
			case 5: return new CustomItem(app.getEditor());
			case 6: return new ParticleItem(app.getEditor());
		}
		return null;
	}
	
	private PopupMenu createLightMenu(){
		PopupMenu menu = new PopupMenu();
		String[] list = {"Point Light","Directional Light","Cone Light","Light Settings"};
		for(int x = 0; x < 4; x++){
			final int pos = x;
			menu.addItem(new MenuItem(list[x],new ChangeListener() {
				@Override
				public void changed (ChangeEvent event, Actor actor) {
					if(pos == 3){
						LightSettingsDialog.showFor(new Project(app.getEditor().getProject().getPath()),app.getEditor().getScene());
					} else {
						LightItem lightItem = new LightItem(app.getEditor());
            			app.getEditor().addActor(lightItem);
            			lightItem.setName(app.getEditor().getName(lightItem));
            			lightItem.setDefault(getLightType(pos));
            			lightItem.getPropertySet().put("z",app.getEditor().getActors().size);
            			app.getEditor().selectActor(lightItem);
            			lightItem.update();
					}
				}
			}));
		}
		return menu;
	}
	
	private String getLightType(int pos){
		if(pos==0)
	        return "point";
	    else if(pos==1)
	        return "directional";
	    else return "cone";
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

    public VisImageButton addIconToTop(String name, Drawable drawable, IconListener iconListener) {
        return addIcon(topContainer, name, drawable, iconListener);
    }

    public VisImageButton addIconToBottom(String name, Drawable drawable, IconListener iconListener) {
        return addIcon(bottomContainer, name, drawable, iconListener);
    }

    private VisImageButton addIcon(Table container, String name, Drawable drawable, IconListener iconListener) {
        VisImageButton button = new VisImageButton(drawable);
		//button.setStyle(buttonStyle);
        button.setName(name);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                iconListener.click(button);
            }
        });
        
        container.add(button).size(iconSize).pad(2);
        container.invalidateHierarchy();
		return button;
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
	
	interface IconListener {
		public void click(VisImageButton visImageButton);
	}
}