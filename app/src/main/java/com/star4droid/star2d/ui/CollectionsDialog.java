package com.star4droid.star2d.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.evo.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Free asset packs shipped with the app (assets/collections). Each pack lists its license;
 * "Add to project" copies the images into the project's images folder.
 */
public final class CollectionsDialog {
    private CollectionsDialog() {}

    public static class Pack {
        public String id, title, titleAr, author, license;
        public final ArrayList<String> files = new ArrayList<>();
    }

    public static ArrayList<Pack> packs(Context c) {
        ArrayList<Pack> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open("collections/index.txt")) {
            String idx = new String(readAll(in), "UTF-8");
            for (String id : idx.split("\n")) {
                id = id.trim();
                if (id.isEmpty()) continue;
                try (InputStream pj = c.getAssets().open("collections/" + id + "/pack.json")) {
                    JsonObject o = JsonParser.parseString(new String(readAll(pj), "UTF-8")).getAsJsonObject();
                    Pack p = new Pack();
                    p.id = id;
                    p.title = o.get("title").getAsString();
                    p.titleAr = o.has("title_ar") ? o.get("title_ar").getAsString() : p.title;
                    p.author = o.has("author") ? o.get("author").getAsString() : "";
                    p.license = o.has("license") ? o.get("license").getAsString() : "";
                    JsonArray fs = o.getAsJsonArray("files");
                    for (int i = 0; i < fs.size(); i++) p.files.add(fs.get(i).getAsString());
                    out.add(p);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Copy "pack/file.png" from the collections into a folder (keeps existing files). */
    public static boolean copy(Context c, String packFile, File dir) {
        File target = new File(dir, packFile.substring(packFile.lastIndexOf('/') + 1));
        if (target.exists()) return true;
        dir.mkdirs();
        try (InputStream in = c.getAssets().open("collections/" + packFile); OutputStream out = new FileOutputStream(target)) {
            byte[] b = new byte[16 * 1024];
            int r;
            while ((r = in.read(b)) != -1) out.write(b, 0, r);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void show(Activity a, File project, Runnable done) {
        boolean ar = Ui.isRtl(a);
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 8));
        TextView hint = Ui.text(a, Tr.t("ui_collections_hint", "Free images you can use in your games. They are copied to the images folder of this project."), 12.5f, Ui.TEXT_3);
        col.addView(hint);
        AlertDialog[] dlg = new AlertDialog[1];
        for (Pack p : packs(a)) {
            LinearLayout card = Ui.column(a);
            card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(a, 12), Ui.BORDER, 1));
            card.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
            LinearLayout head = Ui.row(a);
            head.addView(Ui.smallIcon(a, R.drawable.ic_s2_collections, 0xFF81C784, 20));
            LinearLayout texts = Ui.column(a);
            texts.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 6), 0);
            TextView t = Ui.text(a, ar ? p.titleAr : p.title, 15, Ui.TEXT);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            texts.addView(t);
            texts.addView(Ui.text(a, p.files.size() + " " + Tr.t("ui_images", "images") + " · " + p.author + " · " + p.license, 11.5f, Ui.TEXT_3));
            head.addView(texts, Ui.weight(1));
            TextView lic = Ui.text(a, "ⓘ", 16, Ui.TEXT_2);
            lic.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4));
            lic.setBackground(Ui.ripple(0, Ui.dp(a, 14)));
            lic.setOnClickListener(v -> showLicense(a, p));
            head.addView(lic);
            card.addView(head, Ui.matchWrap());
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout thumbs = Ui.row(a);
            thumbs.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
            for (String f : p.files) {
                ImageView iv = new ImageView(a);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setBackground(Ui.rounded(0xFF121417, Ui.dp(a, 8)));
                int pad = Ui.dp(a, 4);
                iv.setPadding(pad, pad, pad, pad);
                try (InputStream in = a.getAssets().open("collections/" + p.id + "/" + f)) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 4;
                    Bitmap b = BitmapFactory.decodeStream(in, null, o);
                    iv.setImageBitmap(b);
                } catch (Exception ignored) {}
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56));
                lp.setMarginEnd(Ui.dp(a, 6));
                thumbs.addView(iv, lp);
            }
            hs.addView(thumbs);
            card.addView(hs, Ui.matchWrap());
            LinearLayout addBtn = Ui.button(a, R.drawable.ic_s2_add, Tr.t("ui_add_to_project", "Add to project"), true, v -> {
                File images = new File(project, "images");
                int ok = 0;
                for (String f : p.files) if (copy(a, p.id + "/" + f, images)) ok++;
                Snack.show(Tr.t("ui_added", "Added") + ": " + ok + " " + Tr.t("ui_images", "images") + " → images/");
                if (done != null) done.run();
            });
            card.addView(addBtn, Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(a, 36)), a, 0, 2, 0, 0));
            col.addView(card, Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 0));
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        dlg[0] = new AlertDialog.Builder(a)
                .setTitle(Tr.t("ui_collections", "Collections (free assets)"))
                .setView(sv)
                .setPositiveButton(Tr.t("close", "Close"), null)
                .show();
    }

    private static void showLicense(Activity a, Pack p) {
        String text = p.license;
        try (InputStream in = a.getAssets().open("collections/" + p.id + "/LICENSE.txt")) {
            text = new String(readAll(in), "UTF-8");
        } catch (Exception ignored) {}
        TextView t = Ui.text(a, text, 13, Ui.TEXT);
        t.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), 0);
        t.setTextIsSelectable(true);
        t.setGravity(Gravity.START);
        new AlertDialog.Builder(a).setTitle(Tr.t("ui_license", "License")).setView(t).setPositiveButton(Tr.t("ok", "OK"), null).show();
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int r;
        while ((r = in.read(b)) != -1) bo.write(b, 0, r);
        return bo.toByteArray();
    }

    static View unused() { return null; }
}
