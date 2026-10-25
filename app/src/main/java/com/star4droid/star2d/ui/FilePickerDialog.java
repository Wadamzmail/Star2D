package com.star4droid.star2d.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.star4droid.star2d.evo.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pick a file under a root folder (folders navigable, extension filter, image thumbnails).
 * The result path is relative to the root and starts with "/" (same as the LibGDX FilePicker).
 */
public final class FilePickerDialog {
    private FilePickerDialog() {}

    public interface OnPick { void on(File file, String relativePath); }

    private static final LruCache<String, Bitmap> THUMBS = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);

    public static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".webp") || n.endsWith(".gif");
    }

    /** Load a small thumbnail into the view asynchronously (cached). */
    public static void thumb(ImageView iv, File f, int px) {
        String key = f.getAbsolutePath() + "@" + f.lastModified() + ":" + px;
        iv.setTag(key);
        Bitmap b = THUMBS.get(key);
        if (b != null) {
            iv.setImageTintList(null);
            iv.setImageBitmap(b);
            return;
        }
        IO.execute(() -> {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                int s = 1;
                while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = s;
                Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (bm == null) return;
                THUMBS.put(key, bm);
                Ui.ui(() -> {
                    if (key.equals(iv.getTag())) {
                        iv.setImageTintList(null);
                        iv.setImageBitmap(bm);
                    }
                });
            } catch (Throwable ignored) {}
        });
    }

    public static void show(Context c, String title, String rootPath, String[] extensions, boolean images, OnPick onPick) {
        File root = new File(rootPath);
        if (!root.exists()) root.mkdirs();
        final File[] dir = {root};
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), 0);
        TextView path = Ui.text(c, "/", 12.5f, Ui.TEXT_3);
        path.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 6));
        col.addView(path);
        RecyclerView rv = new RecyclerView(c);
        int span = images ? (Ui.isLandscape(c) ? 6 : 4) : 1;
        rv.setLayoutManager(new GridLayoutManager(c, span));
        col.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.55f)));
        TextView empty = Ui.text(c, Tr.t("ui_empty_folder", "Nothing here. Import files from the Files panel."), 13, Ui.TEXT_3);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        col.addView(empty);

        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(col)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();

        final ArrayList<File> items = new ArrayList<>();
        class PickAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
            @NonNull @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
                LinearLayout cell = images ? Ui.column(c) : Ui.row(c);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
                cell.setBackground(Ui.ripple(0, Ui.dp(c, 8)));
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int s = Ui.dp(c, images ? 56 : 26);
                cell.addView(iv, new LinearLayout.LayoutParams(s, s));
                TextView tv = Ui.text(c, "", images ? 11 : 14, Ui.TEXT);
                tv.setMaxLines(images ? 2 : 1);
                tv.setGravity(images ? Gravity.CENTER : Gravity.START);
                tv.setPadding(images ? 0 : Ui.dp(c, 10), images ? Ui.dp(c, 4) : 0, 0, 0);
                cell.addView(tv, images ? new LinearLayout.LayoutParams(-1, -2) : Ui.weight(1));
                cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(cell) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
                File f = items.get(pos);
                LinearLayout cell = (LinearLayout) h.itemView;
                ImageView iv = (ImageView) cell.getChildAt(0);
                TextView tv = (TextView) cell.getChildAt(1);
                boolean up = f == null;
                tv.setText(up ? ".." : f.getName());
                com.star4droid.star2d.ui.tutorial.Tut.tag(cell, up ? "pick:.." : "pick:" + f.getName());
                tv.setTypeface(!up && f.isDirectory() ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                iv.setTag(null);
                iv.setImageTintList(android.content.res.ColorStateList.valueOf(up || f.isDirectory() ? 0xFFFFCA28 : Ui.TEXT_2));
                if (up) iv.setImageResource(R.drawable.ic_s2_arrow_back);
                else if (f.isDirectory()) iv.setImageResource(R.drawable.ic_s2_folder);
                else if (isImage(f.getName())) {
                    iv.setImageResource(R.drawable.ic_s2_image);
                    thumb(iv, f, Ui.dp(c, 56));
                } else iv.setImageResource(R.drawable.ic_s2_file);
                cell.setOnClickListener(v -> {
                    if (up) { dir[0] = dir[0].getParentFile(); reload(); }
                    else if (f.isDirectory()) { dir[0] = f; reload(); }
                    else {
                        String rel = f.getAbsolutePath().substring(root.getAbsolutePath().length());
                        if (!rel.startsWith("/")) rel = "/" + rel;
                        d.dismiss();
                        onPick.on(f, rel);
                    }
                });
            }

            @Override public int getItemCount() { return items.size(); }

            void reload() {
                items.clear();
                if (!dir[0].getAbsolutePath().equals(root.getAbsolutePath())) items.add(null);
                File[] list = dir[0].listFiles();
                if (list != null) {
                    Arrays.sort(list, (a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(b.getName()));
                    for (File f : list) {
                        if (f.isDirectory()) { items.add(f); continue; }
                        if (extensions == null || extensions.length == 0) { items.add(f); continue; }
                        String n = f.getName().toLowerCase(Locale.ROOT);
                        for (String e : extensions)
                            if (e.equals("*") || n.endsWith("." + e.toLowerCase(Locale.ROOT))) { items.add(f); break; }
                    }
                }
                String rel = dir[0].getAbsolutePath().substring(root.getAbsolutePath().length());
                path.setText(rel.isEmpty() ? "/" : rel);
                empty.setVisibility(items.isEmpty() || (items.size() == 1 && items.get(0) == null) ? View.VISIBLE : View.GONE);
                notifyDataSetChanged();
            }
        }
        PickAdapter adapter = new PickAdapter();
        rv.setAdapter(adapter);
        adapter.reload();
        d.show();
    }
}
