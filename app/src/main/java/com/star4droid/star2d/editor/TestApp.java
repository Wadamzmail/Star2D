package com.star4droid.star2d.editor;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.kotcrab.vis.ui.VisUI;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.ui.ControlLayer;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ProjectAssetLoader;

public class TestApp implements ApplicationListener {
	LibgdxEditor editor;
	Project project;
	boolean landscape = true;
	Runnable whenEditorReady;
	ProjectAssetLoader projectAssetLoader;
	StageImp stageImp;
	int width=-1,height = -1;
	//Stage stage;
	//ControlLayer layer;
	
	public TestApp(Project project){
		this.project = project;
	}
	
	@Override
	public void create() {
		//com.star4droid.star2d.Helpers.FileUtil.writeFile(com.star4droid.star2d.Helpers.FileUtil.getPackageDataDir(getContext())+"/logs/libgdx created","works 1");
		Gdx.files.external("logs/testapp.txt").writeString("test app created\n"+"_".repeat(10)+"\n",true);
		if(!VisUI.isLoaded())
			VisUI.load();
		//stage = new Stage();
		//layer = new ControlLayer();
		//layer.addIconToTop("test",VisUI.getSkin().getDrawable("icon-restore"),()->{});
		//layer.addIconToBottom("test2",VisUI.getSkin().getDrawable("icon-refresh"),()->{});
		projectAssetLoader = new ProjectAssetLoader(new com.star4droid.star2d.Helpers.Project(project.getPath()));
		projectAssetLoader.setAssetsLoadListener(()->{
			editor = new LibgdxEditor(project,"scene1",projectAssetLoader);
        	editor.loadFromPath();
			try {
				landscape = editor.getConfig().getString("or").equals("");
			} catch(Exception e){}
			//no need to call this method if the landscape mode doesn't change
			if(!landscape)
				editor.setLandscape(landscape);
			if(whenEditorReady!=null)
				whenEditorReady.run();
			//InputMultiplexer multiplexer = new InputMultiplexer();
			//multiplexer.addProcessor(stage);
			//multiplexer.addProcessor(editor);
			Gdx.input.setInputProcessor(editor);
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
		//stage.getViewport().update(width,height);
		try {
			editor.getViewport().update(width,height);
		} catch(Exception e){}
		if(stageImp!=null)
			stageImp.resize(width,height);
	}
	
	public LibgdxEditor getEditor(){
		return editor;
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
				Gdx.input.setInputProcessor(editor);
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
				Gdx.files.external("logs/render.txt").writeString(Utils.getStackTraceString(e)+"\n"+"_".repeat(10)+"\n",true);
			}
			return;
		} else {
			if(editor!=null){
				editor.act();
				editor.draw();
			}
			/*
			if(stage!=null){
				stage.act();
				stage.draw();
			}
			*/
			if(!projectAssetLoader.isFinished()){
				//TODO : Loading animation or progress
			}
		}
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}

	@Override
	public void dispose() {
		if(editor!=null)
			editor.dispose();
		if(VisUI.isLoaded())
			VisUI.dispose();
	}
	
}
