package com.star4droid.star2d.Helpers;

import android.os.StrictMode;
import android.os.strictmode.Violation;
import android.util.Log;

import com.star4droid.star2d.evo.star2dApp;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class CustomStrictMode {
    public static void enableStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
    }

    private static class CustomLogHandler implements StrictMode.OnThreadViolationListener {
		@Override
		public void onThreadViolation(Violation violation) {
			writeLogToFile("StrictMode","Violation : "+violation);
		}
    }
	
	

    private static void writeLogToFile(String tag, String message) {
		String file = star2dApp.getContext().getExternalFilesDir("logs")+"/strict.txt";
        FileUtil.writeFile(file,FileUtil.readFile(file)+"\n"+"_".repeat(10)+"\n"+message);
    }
}