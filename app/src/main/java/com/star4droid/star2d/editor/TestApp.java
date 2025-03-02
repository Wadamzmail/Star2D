package com.star4droid.star2d.editor;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.ToastManager;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.ui.ControlLayer;
import com.star4droid.star2d.editor.ui.FileBrowser;
import com.star4droid.star2d.editor.ui.FilePicker;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ProjectAssetLoader;

public class TestApp implements ApplicationListener {
	LibgdxEditor editor;
	Project project;
	ToastManager toastManager;
	boolean landscape = true;
	Runnable whenEditorReady;
	ProjectAssetLoader projectAssetLoader;
	StageImp stageImp;
	int width=-1,height = -1;
	Stage UiStage;
	FilePicker filePicker;
	ControlLayer controlLayer;
	InputMultiplexer multiplexer;
	FileBrowser fileBrowser;
	
	public TestApp(Project project){
		this.project = project;
	}
	
	@Override
	public void create() {
		//Gdx.files.external("logs/testapp.txt").writeString("test app created\n"+"_".repeat(10)+"\n",true);
		if(!VisUI.isLoaded())
			VisUI.load(VisUI.SkinScale.X2);
		
		controlLayer = new ControlLayer(this);
		
		ScreenViewport screenViewport = new ScreenViewport();
		UiStage = new Stage(screenViewport);
		
		UiStage.addActor(controlLayer);
		
		//screenViewport.setUnitsPerPixel(1 / 1.5f);
		toastManager = new ToastManager(UiStage);
		filePicker = new FilePicker().setShowImageIcons(true);
		filePicker.setVisible(false);
		
		projectAssetLoader = new ProjectAssetLoader(new com.star4droid.star2d.Helpers.Project(project.getPath()));
		projectAssetLoader.setAssetsLoadListener(()->{
			editor = new LibgdxEditor(project,"scene1",projectAssetLoader);
        	editor.loadFromPath();
			editor.setToastManager(toastManager);
			editor.setFilePicker(filePicker);
			editor.setControlLayer(controlLayer);
			editor.setUiStage(UiStage);
			fileBrowser = new FileBrowser(project.getPath(),projectAssetLoader).setToastManager(toastManager);
			fileBrowser.setVisible(false);
			UiStage.addActor(fileBrowser);
			try {
				landscape = editor.getConfig().getString("or").equals("");
			} catch(Exception e){}
			//no need to call this method if the landscape mode doesn't change
			if(!landscape)
				editor.setLandscape(landscape);
			if(whenEditorReady!=null)
				whenEditorReady.run();
			multiplexer = new InputMultiplexer();
			multiplexer.addProcessor(UiStage);
			multiplexer.addProcessor(editor);
			Gdx.input.setInputProcessor(multiplexer);
			editor.getFilePicker(false);
			if(width != -1){
				resize(width,height);
			}
		});
		projectAssetLoader.finishLoading();
	}
	
	public void setEdtitorReadyAction(Runnable runnable){
		whenEditorReady = runnable;
		//already ready, call directly
		if(editor!=null && whenEditorReady!=null)
			whenEditorReady.run();
	}

	@Override
	public void resize(int width, int height) {
		this.width = width;
		this.height = height;
		UiStage.getViewport().update(width,height);
		try {
			editor.getViewport().update(width,height);
		} catch(Exception e){}
		if(stageImp!=null)
			stageImp.resize(width,height);
	}
	
	public LibgdxEditor getEditor(){
		return editor;
	}
	
	public FileBrowser getFileBrowser(){
		return fileBrowser;
	}
	
	public ToastManager getToastManager(){
		return toastManager;
	}
	
	public void toast(String message){
		//toastManager.show(message,2);
	}
	
	public boolean isPlaying(){
		return stageImp!=null;
	}
	
	public void play(String path,String scene){
		//TODO : Show error if something wrong happen...
		play(StageImp.getFromDex(path,scene,null,null));
	}
	
	public void play(StageImp stageImp){
		Gdx.app.postRunnable(()->{
			if(stageImp!=null){
				stageImp.create();
				Gdx.input.setInputProcessor(stageImp.multiplexer);
				if(width!=-1 && height!=-1)
					stageImp.resize(width,height);
			} else {
			    if(this.stageImp!=null){
			        this.stageImp.pause();
					//TODO : Fix audio playing after game exit...
					this.stageImp.dispose();
				}
				Gdx.input.setCatchKey(4,false);
				Gdx.input.setInputProcessor(multiplexer);
			}
			this.stageImp = stageImp;
		});
	}
	
	public void setLandscape(boolean isLandscape){
		if(editor == null){
			landscape = isLandscape;
		} else editor.setLandscape(isLandscape);
	}

	@Override
	public void render() {
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		Gdx.gl.glClearColor(0,0,0,0);
		if(stageImp!=null){
			try {
				stageImp.render();
			} catch(Exception e){
				Gdx.files.external("logs/render.error.txt").writeString(Utils.getStackTraceString(e)+"\n"+"_".repeat(10)+"\n",false);
			}
			return;
		} else {
			if(editor!=null){
				editor.act();
				editor.draw();
			}
			
			if(UiStage!=null){
				UiStage.act();
				UiStage.draw();
			}
			
			if(!projectAssetLoader.isFinished()){
				//TODO : Loading animation or progress
			}
		}
	}

	@Override
	public void pause() {}

	@Override
	public void resume() {}

	@Override
	public void dispose() {
		if(VisUI.isLoaded())
			VisUI.dispose();
		fileBrowser.dispose();
		//filePicker.dispose();
		if(editor!=null)
			editor.dispose();
	}
	
}