package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.ToastManager;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.star4droid.template.Utils.ProjectAssetLoader;
import java.io.File;
import java.util.HashSet;

public class FileBrowser extends VisWindow implements Disposable {
    private FileHandle currentDir;
    private Runnable pickFilesRunnable;
    private float uiScale = 1f;
    private boolean gridMode = true;
    private VisScrollPane scrollPane;
    private VisTable contentTable;
    private Texture folderIcon;
    private Texture fileIcon;
	private int depth = 0;
    private HashSet<String> imageExtensions = new HashSet<>();
	private ProjectAssetLoader projectAssetLoader;
	private ToastManager toastManager;

    public FileBrowser(String rootAbsoulutePath,ProjectAssetLoader assetLoader) {
        super("File Browser");
		this.projectAssetLoader = assetLoader;
		setupDefaults();
        setupIcons();
        setupUI(rootAbsoulutePath);
        refreshFileList();
        //addCloseButton();
    }
	
	private void setupDefaults(){
		setSize(600, 400);
        setResizable(false);
        setMovable(false);
        setCenterOnAdd(true);
        padTop(42);
		closeOnEscape();
        defaults().pad(5);
	}

    private void setupIcons() {
        folderIcon = new Texture("images/folder.png");
        fileIcon = new Texture("images/file.png");
        imageExtensions.add("png");
        imageExtensions.add("jpg");
        imageExtensions.add("jpeg");
        imageExtensions.add("bmp");
    }

    private void setupUI(String path) {
        // Header
        VisTextButton importBtn = new VisTextButton("Import");
        VisTextButton modeToggle = new VisTextButton("Switch View");
		VisTextButton backBtn = new VisTextButton("Back");
		VisTextButton createBtn = new VisTextButton("Create");
		PopupMenu createMenu = new PopupMenu();
		MenuItem fileItem = new MenuItem("File",new ChangeListener(){
			@Override
			public void changed(ChangeEvent arg0, Actor arg1) {
				createNew(false);
			}
		});
		MenuItem folderItem = new MenuItem("Folder",new ChangeListener(){
			@Override
			public void changed(ChangeEvent arg0, Actor arg1) {
				createNew(true);
			}
		});
		fileItem.pad(10);
		folderItem.pad(10);
		createMenu.addItem(fileItem);
		createMenu.addItem(folderItem);
        
        importBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if(pickFilesRunnable != null) pickFilesRunnable.run();
            }
        });
        
        modeToggle.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                gridMode = !gridMode;
                refreshFileList();
            }
        });
		
		backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                try {
					if(depth>0){
						setCurrentDirectory(currentDir.parent());
						depth--;
					}
				} catch(Exception e){}
            }
        });
		
		createBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                createMenu.showMenu(getStage(),createBtn);
            }
        });

        VisTable header = new VisTable();
		header.add(createBtn).padRight(10);
        header.add(importBtn).padRight(10);
        header.add(modeToggle).padRight(20);
		header.add(backBtn);
        add(header).growX().row();

        // Content area
        contentTable = new VisTable();
        scrollPane = new VisScrollPane(contentTable);
        scrollPane.setFadeScrollBars(false);
		scrollPane.setScrollingDisabled(false,false);
        add(scrollPane).grow().row();

        setCurrentDirectory(path); // Get root directory
    }
	
	private void createNew(boolean isDir){
		new SingleInputDialog("Create New","Enter "+(isDir?"Directory":"File")+" name :","",(input)->{
			FileHandle newFile = Gdx.files.absolute(currentDir.file().getAbsolutePath()+input);
			if(newFile.exists()){
				toast("already exists !");
			} else {
				try {
					if(isDir)
						newFile.mkdirs();
					else newFile.writeString("",false);
				} catch(Exception e){
					toast(e.toString());
				}
			}
		}).show(getStage());
	}
	
	public void toast(String message){
		if(toastManager!=null)
			toastManager.show(message,2);
	}
	
	public FileHandle getCurrentDir(){
		return currentDir;
	}
	
	public FileBrowser setToastManager(ToastManager toastManager){
		this.toastManager = toastManager;
		return this;
	}
	
    public void refreshFileList() {
        contentTable.clear();
        contentTable.defaults().space(5);

        FileHandle[] files = currentDir.list();
		int r = 0;
		if(files!=null)
        for(FileHandle file : files) {
            Actor item = createFileItem(file);
            if(gridMode) {
                contentTable.add(item).size(150 * uiScale, 150 * uiScale);
                if(r >= 4) {
					contentTable.row();
					r = 0;
				} else r++;
            } else {
                contentTable.add(item).growX().size(500,50 * uiScale).row();
            }
        }
        
        contentTable.pack();
    }

    private Actor createFileItem(FileHandle file) {
        VisImageButton btn = new VisImageButton(new TextureRegionDrawable(getTextureFor(file)));
        //btn.setStyle(createButtonStyle(file));
        
        VisLabel label = new VisLabel(file.name());
        label.setAlignment(Align.center);
        
        Stack stack = new Stack();
		
        stack.add(btn);
        stack.add(label);
        
        stack.addListener(createFileClickListener(file, stack));
        return stack;
    }
	
	public Texture getTextureFor(FileHandle file){
		boolean isImage = (!file.isDirectory()) && imageExtensions.contains(file.extension().toLowerCase());
		if(isImage){
			if(projectAssetLoader.contains(file.toString())){
				return projectAssetLoader.get(file.toString());
			} else {
				projectAssetLoader.loadFile(file.toString(),Texture.class);
				projectAssetLoader.finishLoading();
			}
		}
		return file.isDirectory() ? folderIcon : 
                        isImage ? getAsset(file) : fileIcon;
	}

    private VisImageButton.ButtonStyle createButtonStyle(FileHandle file) {
        Texture texture = getTextureFor(file);
        
        return new VisImageButton.ButtonStyle(
                new TextureRegionDrawable(new TextureRegion(texture)),
                null,
                null
        );
    }
	
	private Texture getAsset(FileHandle asset){
		return (projectAssetLoader!=null&&projectAssetLoader.contains(asset.toString()))?projectAssetLoader.get(asset.toString()):new Texture(asset);
	}
	
	/*private void centerOnScreen(){
		//setPosition(100,100);
		//if(getStage()!=null)
			//setPosition((getStage().getWidth() - getWidth())*0.5f,(getStage().getWidth() - getHeight())*0.5f);
	}*/
	
	public void setRootDir(String aboulutePath){
		setRootDir(Gdx.files.absolute(aboulutePath));
	}
	
	public void setRootDir(FileHandle fileHandle){
		depth=0;
		setCurrentDirectory(fileHandle);
	}
	
    private ClickListener createFileClickListener(FileHandle file, Actor item) {
        return new ClickListener() {
            long pressStartTime;
			boolean exit = false;
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                pressStartTime = System.currentTimeMillis();
				exit = false;
                return true;
            }
			
			

			/*@Override
			public void exit(InputEvent arg0, float arg1, float arg2, int arg3, Actor arg4) {
				super.exit(arg0, arg1, arg2, arg3, arg4);
				exit = true;
			}*/

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
				if(item.hit(x,y,true)==null){
					return;
				}
				if(System.currentTimeMillis() - pressStartTime > 500) {
                    showContextMenu(file, item);
                } else {
                    if(file.isDirectory()) {
						depth++;
                        setCurrentDirectory(file);
                    } else {
                        // Handle file open
						if(depth == 1 && file.parent().name().toLowerCase().equals("anims")){
							if(animationOpen!=null)
								animationOpen.open(file.path());
						}
                    }
                }
            }
        };
    }
	
	private AnimationOpen animationOpen;
	public void setAnimationOpen(AnimationOpen open){
		this.animationOpen = open;
	}

    private void showContextMenu(FileHandle file, Actor item) {
        PopupMenu menu = new PopupMenu();
        menu.addItem(getMenuItem("Delete", () -> deleteFile(file)));
        menu.addItem(getMenuItem("Rename", () -> showRenameDialog(file)));
        menu.showMenu(getStage(), item);
    }
	
	private MenuItem getMenuItem(String title,Runnable runnable){
		MenuItem menuItem = new MenuItem(title,new ChangeListener() {
			@Override
			public void changed (ChangeEvent event, Actor actor) {
				runnable.run();
			}
		});
		menuItem.pad(5);
		return menuItem;
	}

    private void deleteFile(FileHandle file) {
        if(file.isDirectory())
			file.deleteDirectory();
		else file.delete();
        refreshFileList();
    }

    private void showRenameDialog(FileHandle file) {
        new SingleInputDialog("Rename", "New name:", file.nameWithoutExtension(), result -> {
            file.moveTo(file.parent().child(result));
            refreshFileList();
        }).show(getStage());
    }

    public void setCurrentDirectory(FileHandle dir) {
        currentDir = dir;
        refreshFileList();
        addAction(Actions.sequence(
                Actions.alpha(0.7f),
                Actions.fadeIn(0.3f, Interpolation.pow3)
        ));
    }
	
	public void setCurrentDirectory(String absoultePath){
		setCurrentDirectory(Gdx.files.absolute(absoultePath));
	}

    public void setPickFilesRunnable(Runnable runnable) {
        this.pickFilesRunnable = runnable;
    }

    public void setUiScale(float scale) {
        this.uiScale = scale;
        contentTable.setScale(scale);
        contentTable.invalidateHierarchy();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        scrollPane.invalidate();
    }

    @Override
    public void dispose() {
        folderIcon.dispose();
        fileIcon.dispose();
    }
	
	public interface AnimationOpen {
		public void open(String file);
	}
}