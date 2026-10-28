package com.star4droid.star2d.Helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.File;

/** Asks Android to install an APK file (through the app's FileProvider). */
public final class ApkInstaller {
    private ApkInstaller() {}

    public static void installApplication(Context context, Uri target) {
        Uri uri = target;
        if ("file".equals(target.getScheme()) && target.getPath() != null) {
            File apk = new File(target.getPath());
            uri = Build.VERSION.SDK_INT >= 24
                    ? FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apk)
                    : Uri.fromFile(apk);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
