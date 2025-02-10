package com.star4droid.star2d.Fragments;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.android.AndroidFragmentApplication;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.editor.TestApp;

public class LibgdxFragment extends AndroidFragmentApplication implements AndroidFragmentApplication.Callbacks {

	private ApplicationListener applicationListener;

    public LibgdxFragment(ApplicationListener applicationListener) {
        this.applicationListener = applicationListener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
		View view = initializeForView(applicationListener);
		//FileUtil.writeFile("logs/check.txt","listener : "+(applicationListener!=null)+", view : "+(view!=null));
		return view; // Pass the ApplicationListener
    }

    @Override
    public void exit() {
        // Handle exit
		if(applicationListener instanceof TestApp){
			TestApp app = ((TestApp)applicationListener);
			if(app.isPlaying())
				app.play(null);
		}
    }
}