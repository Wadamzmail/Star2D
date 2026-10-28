package com.star4droid.star2d.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.blocks.BlockModel;
import com.star4droid.star2d.ui.tutorial.Tut;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

/**
 * Animation editor and player (Android). An animation is a file in anims/ holding a JSON list
 * of frames: [{"name": "folder/image.png", "dur": "300"}, …] (image paths relative to images/,
 * duration in milliseconds) — the same format the engine and the old editor use.
 * Portrait: preview, frames strip, images grid. Landscape: preview and frames on one side,
 * images on the other.
 */
public class AnimationEditorActivity extends AppCompatActivity {

    private static String pendingFile, pendingImages;

    public static void open(Activity a, String animFile, String imagesDir) {
        pendingFile = animFile;
        pendingImages = imagesDir;
        a.startActivity(new Intent(a, AnimationEditorActivity.class));
    }

    private File file, imagesRoot, dir;
    private final ArrayList<HashMap<String, Object>> frames = new ArrayList<>();
    private String original = "[]";
    private int duration = 300;
    private boolean playing = true, changed;
    private int current = 0;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final HashMap<String, Bitmap> cache = new HashMap<>();

    private ImageView preview;
    private TextView counter, durBtn, emptyHint;
    private ImageView playBtn;
    private RecyclerView framesList, imagesGrid;
    private FramesAdapter framesAdapter;
    private final ArrayList<File> images = new ArrayList<>();
    private ImagesAdapter imagesAdapter;

    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (!playing || frames.isEmpty()) return;
            current = (current + 1) % frames.size();
            showFrame();
            h.postDelayed(this, frameDur(current));
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tr.init(this);
        if (pendingFile == null) {
            finish();
            return;
        }
        file = new File(pendingFile);
        imagesRoot = new File(pendingImages);
        imagesRoot.mkdirs();
        dir = imagesRoot;
        load();
        build();
        reloadImages();
        restart();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        build();
        reloadImages();
        restart();
    }

    // ================================================================ data

    private void load() {
        frames.clear();
        try {
            if (file.exists()) {
                original = BlockModel.read(file);
                ArrayList<HashMap<String, Object>> l = new Gson().fromJson(original, new TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType());
                if (l != null) frames.addAll(l);
            }
        } catch (Exception ignored) {}
        if (original == null || original.trim().isEmpty()) original = "[]";
        if (!frames.isEmpty()) {
            try {
                duration = Math.round(Float.parseFloat(String.valueOf(frames.get(0).get("dur"))));
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        try {
            for (HashMap<String, Object> f : frames) f.put("dur", String.valueOf(duration));
            BlockModel.write(file, new Gson().toJson(frames));
            original = new Gson().toJson(frames);
            changed = false;
            Snack.show(Tr.t("saved", "Saved") + " ✓");
        } catch (Exception e) {
            Snack.show(e.toString());
        }
    }

    private int frameDur(int i) {
        try {
            int d = Math.round(Float.parseFloat(String.valueOf(frames.get(i).get("dur"))));
            return Math.max(16, d);
        } catch (Exception e) {
            return Math.max(16, duration);
        }
    }

    private File imageOf(HashMap<String, Object> f) {
        String n = String.valueOf(f.get("name")).replace('\\', '/');
        while (n.startsWith("/")) n = n.substring(1);
        return new File(imagesRoot, n);
    }

    private Bitmap bitmap(File f) {
        String k = f.getAbsolutePath();
        Bitmap b = cache.get(k);
        if (b != null) return b;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(k, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= 512 && o.outHeight / (s * 2) >= 512) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            b = BitmapFactory.decodeFile(k, o);
            if (b != null) cache.put(k, b);
        } catch (Throwable ignored) {}
        return b;
    }

    private String relName(File f) {
        String rel = f.getAbsolutePath().substring(imagesRoot.getAbsolutePath().length());
        while (rel.startsWith("/")) rel = rel.substring(1);
        return rel;
    }

    // ================================================================ layout

    private void build() {
        Context c = this;
        boolean land = Ui.isLandscape(c);
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Ui.BG);
        root.setLayoutDirection(Ui.isRtl(c) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout top = Ui.row(c);
        top.setBackgroundColor(0xFF181A1E);
        top.setPadding(Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3));
        ImageView back = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, Tr.t("close", "Close"), v -> onBackPressed());
        back.setScaleX(Ui.isRtl(c) ? -1 : 1);
        top.addView(back);
        LinearLayout titles = Ui.column(c);
        titles.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        TextView t = Ui.text(c, file.getName(), 15, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(1);
        titles.addView(t);
        titles.addView(Ui.text(c, Tr.t("animationEditor", "Animation editor"), 11.5f, Ui.TEXT_3));
        top.addView(titles, Ui.weight(1));
        ImageView help = Ui.iconButton(c, R.drawable.ic_s2_help, Tr.t("ui_help", "Help"), v -> Dialogs.longText(c, Tr.t("animationEditor", "Animation editor"),
                Tr.t("anim_help", "Tap images on the list to add them as frames. Drag a frame to change the order, ✕ removes it, tap a frame to see it. \"Frame time\" sets how long each frame is shown (milliseconds). Use the animation in blocks with setAnimation and its name.")));
        top.addView(help);
        ImageView save = Ui.iconButton(c, R.drawable.ic_s2_save, Tr.t("save", "Save"), v -> save());
        save.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        Tut.tag(save, "anim:save");
        top.addView(save);
        root.addView(top, Ui.matchWrap());

        // preview + controls
        LinearLayout player = Ui.column(c);
        FrameLayout stage = new FrameLayout(c);
        stage.setBackground(new Checker(Ui.dp(c, 10)));
        preview = new ImageView(c);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.dp(c, 12);
        preview.setPadding(pad, pad, pad, pad);
        stage.addView(preview, Ui.match());
        emptyHint = Ui.text(c, Tr.t("anim_empty", "No frames yet.\nTap images below to add them."), 13.5f, Ui.TEXT_2);
        emptyHint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(-2, -2);
        el.gravity = Gravity.CENTER;
        stage.addView(emptyHint, el);
        counter = Ui.text(c, "", 12, 0xFFFFFFFF);
        counter.setPadding(Ui.dp(c, 8), Ui.dp(c, 3), Ui.dp(c, 8), Ui.dp(c, 3));
        counter.setBackground(Ui.rounded(0x99000000, Ui.dp(c, 10)));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2);
        cl.gravity = Gravity.TOP | Gravity.END;
        cl.setMargins(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), 0);
        stage.addView(counter, cl);
        player.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout controls = Ui.row(c);
        controls.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4));
        controls.setGravity(Gravity.CENTER_VERTICAL);
        ImageView prev = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, Tr.t("anim_prev", "Previous frame"), v -> stepBy(-1));
        prev.setScaleX(Ui.isRtl(c) ? -1 : 1);
        controls.addView(prev);
        playBtn = Ui.iconButton(c, playing ? R.drawable.ic_s2_pause : R.drawable.ic_s2_play, Tr.t("ui_play", "Play"), v -> togglePlay());
        playBtn.setBackground(Ui.ripple(Ui.SUCCESS, Ui.dp(c, 10)));
        controls.addView(playBtn);
        ImageView next = Ui.iconButton(c, R.drawable.ic_s2_arrow_forward, Tr.t("anim_next", "Next frame"), v -> stepBy(1));
        next.setScaleX(Ui.isRtl(c) ? -1 : 1);
        controls.addView(next);
        controls.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        durBtn = Ui.text(c, "", 13, Ui.TEXT);
        durBtn.setTypeface(Typeface.DEFAULT_BOLD);
        durBtn.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        durBtn.setBackground(Ui.ripple(Ui.SURFACE_3, Ui.dp(c, 16)));
        durBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_s2_history, 0, 0, 0);
        durBtn.setCompoundDrawablePadding(Ui.dp(c, 6));
        durBtn.setCompoundDrawableTintList(ColorStateList.valueOf(Ui.TEXT_2));
        durBtn.setOnClickListener(v -> NumberPad.show(c, Tr.t("anim_frame_time", "Frame time (ms)"), String.valueOf(duration), val -> {
            try {
                duration = Math.max(16, Math.round(Float.parseFloat(val)));
            } catch (Exception ignored) {}
            for (HashMap<String, Object> f : frames) f.put("dur", String.valueOf(duration));
            changed = true;
            refreshInfo();
            restart();
        }, null));
        controls.addView(durBtn);
        player.addView(controls, Ui.matchWrap());

        // frames
        framesList = new RecyclerView(c);
        framesList.setLayoutManager(new LinearLayoutManager(c, land ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL, false));
        framesAdapter = new FramesAdapter(land);
        framesList.setAdapter(framesAdapter);
        framesList.setBackgroundColor(0xFF1C1F24);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                land ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder a, @NonNull RecyclerView.ViewHolder b) {
                int from = a.getAdapterPosition(), to = b.getAdapterPosition();
                if (from < 0 || to < 0) return false;
                Collections.swap(frames, from, to);
                framesAdapter.notifyItemMoved(from, to);
                changed = true;
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
            @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                framesAdapter.notifyDataSetChanged();
                restart();
            }
        }).attachToRecyclerView(framesList);

        // images
        LinearLayout picker = Ui.column(c);
        LinearLayout ph = Ui.row(c);
        ph.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 2));
        TextView pt = Ui.text(c, Tr.t("anim_add_frames", "Images: tap to add a frame"), 12.5f, Ui.TEXT_2);
        pt.setTypeface(Typeface.DEFAULT_BOLD);
        ph.addView(pt, Ui.weight(1));
        TextView all = Ui.text(c, "+ " + Tr.t("anim_add_all", "Add all"), 12.5f, Ui.ACCENT);
        all.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
        all.setBackground(Ui.ripple(0, Ui.dp(c, 14)));
        all.setOnClickListener(v -> {
            for (File f : images) if (f != null && f.isFile()) addFrame(f);
        });
        ph.addView(all);
        picker.addView(ph, Ui.matchWrap());
        imagesGrid = new RecyclerView(c);
        int span = land ? 4 : (getResources().getDisplayMetrics().widthPixels / Ui.dp(c, 84));
        imagesGrid.setLayoutManager(new GridLayoutManager(c, Math.max(3, span)));
        imagesAdapter = new ImagesAdapter();
        imagesGrid.setAdapter(imagesAdapter);
        picker.addView(imagesGrid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (land) {
            LinearLayout body = Ui.row(c);
            LinearLayout left = Ui.column(c);
            left.addView(player, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            body.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.1f));
            body.addView(framesList, new LinearLayout.LayoutParams(Ui.dp(c, 112), ViewGroup.LayoutParams.MATCH_PARENT));
            body.addView(picker, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        } else {
            root.addView(player, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            root.addView(framesList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 104)));
            root.addView(picker, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
        setContentView(root);
        refreshInfo();
    }

    private void refreshInfo() {
        durBtn.setText(Tr.t("anim_frame_time", "Frame time (ms)") + ": " + duration);
        emptyHint.setVisibility(frames.isEmpty() ? View.VISIBLE : View.GONE);
        float fps = 1000f / Math.max(16, duration);
        counter.setText(frames.isEmpty() ? "" : String.format(Locale.ENGLISH, "%d/%d · %.1f fps", current + 1, frames.size(), fps));
        counter.setVisibility(frames.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showFrame() {
        if (frames.isEmpty()) {
            preview.setImageDrawable(null);
            refreshInfo();
            return;
        }
        if (current >= frames.size()) current = 0;
        Bitmap b = bitmap(imageOf(frames.get(current)));
        if (b != null) preview.setImageBitmap(b);
        else preview.setImageResource(R.drawable.ic_s2_image);
        refreshInfo();
        if (framesAdapter != null) framesAdapter.notifyDataSetChanged();
    }

    private void restart() {
        h.removeCallbacks(step);
        if (current >= frames.size()) current = 0;
        showFrame();
        if (playing && frames.size() > 1) h.postDelayed(step, frameDur(current));
    }

    private void togglePlay() {
        playing = !playing;
        playBtn.setImageResource(playing ? R.drawable.ic_s2_pause : R.drawable.ic_s2_play);
        restart();
    }

    private void stepBy(int d) {
        if (frames.isEmpty()) return;
        playing = false;
        playBtn.setImageResource(R.drawable.ic_s2_play);
        current = (current + d + frames.size()) % frames.size();
        restart();
    }

    private void addFrame(File f) {
        HashMap<String, Object> m = new HashMap<>();
        m.put("name", relName(f));
        m.put("dur", String.valueOf(duration));
        frames.add(m);
        changed = true;
        framesAdapter.notifyDataSetChanged();
        framesList.scrollToPosition(frames.size() - 1);
        restart();
    }

    private void reloadImages() {
        images.clear();
        if (!dir.getAbsolutePath().equals(imagesRoot.getAbsolutePath())) images.add(null);
        File[] l = dir.listFiles();
        if (l != null) {
            Arrays.sort(l, (a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(b.getName()));
            for (File f : l) if (f.isDirectory() || FilePickerDialog.isImage(f.getName())) images.add(f);
        }
        if (imagesAdapter != null) imagesAdapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (!changed) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(Tr.t("ui_unsaved", "Unsaved changes"))
                .setMessage(Tr.t("anim_save_q", "Save this animation before closing?"))
                .setPositiveButton(Tr.t("save", "Save"), (d, w) -> { save(); finish(); })
                .setNegativeButton(Tr.t("ui_discard", "Discard"), (d, w) -> finish())
                .setNeutralButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    // ================================================================ adapters

    private class FramesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final boolean vertical;

        FramesAdapter(boolean vertical) { this.vertical = vertical; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            Context c = p.getContext();
            FrameLayout cell = new FrameLayout(c);
            int s = Ui.dp(c, 88);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(vertical ? ViewGroup.LayoutParams.MATCH_PARENT : s, s);
            lp.setMargins(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6));
            cell.setLayoutParams(lp);
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pd = Ui.dp(c, 8);
            iv.setPadding(pd, pd, pd, pd);
            cell.addView(iv, Ui.match());
            TextView n = Ui.text(c, "", 10.5f, 0xFFFFFFFF);
            n.setPadding(Ui.dp(c, 5), Ui.dp(c, 1), Ui.dp(c, 5), Ui.dp(c, 1));
            n.setBackground(Ui.rounded(0xAA000000, Ui.dp(c, 8)));
            FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2);
            nl.gravity = Gravity.BOTTOM | Gravity.START;
            nl.setMargins(Ui.dp(c, 4), 0, 0, Ui.dp(c, 4));
            cell.addView(n, nl);
            ImageView del = Ui.smallIcon(c, R.drawable.ic_s2_close, 0xFFFF8A80, 16);
            del.setBackground(Ui.rounded(0xCC000000, Ui.dp(c, 12)));
            int dp = Ui.dp(c, 4);
            del.setPadding(dp, dp, dp, dp);
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
            dl.gravity = Gravity.TOP | Gravity.END;
            cell.addView(del, dl);
            return new RecyclerView.ViewHolder(cell) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int pos) {
            FrameLayout cell = (FrameLayout) vh.itemView;
            Context c = cell.getContext();
            ImageView iv = (ImageView) cell.getChildAt(0);
            TextView n = (TextView) cell.getChildAt(1);
            View del = cell.getChildAt(2);
            File f = imageOf(frames.get(pos));
            iv.setImageResource(R.drawable.ic_s2_image);
            if (f.isFile()) FilePickerDialog.thumb(iv, f, Ui.dp(c, 72));
            n.setText(String.valueOf(pos + 1));
            cell.setBackground(pos == current ? Ui.rounded(Ui.ACCENT_SOFT, Ui.dp(c, 10), Ui.ACCENT, Ui.dp(c, 2))
                    : Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, 1));
            cell.setOnClickListener(v -> {
                int p = vh.getAdapterPosition();
                if (p < 0) return;
                playing = false;
                playBtn.setImageResource(R.drawable.ic_s2_play);
                current = p;
                restart();
            });
            del.setOnClickListener(v -> {
                int p = vh.getAdapterPosition();
                if (p < 0 || p >= frames.size()) return;
                frames.remove(p);
                changed = true;
                if (current >= frames.size()) current = Math.max(0, frames.size() - 1);
                notifyDataSetChanged();
                restart();
            });
        }

        @Override public int getItemCount() { return frames.size(); }
    }

    private class ImagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            Context c = p.getContext();
            LinearLayout cell = Ui.column(c);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6));
            cell.setBackground(Ui.ripple(0, Ui.dp(c, 8)));
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.addView(iv, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));
            TextView tv = Ui.text(c, "", 10.5f, Ui.TEXT_2);
            tv.setMaxLines(1);
            tv.setGravity(Gravity.CENTER);
            cell.addView(tv, new LinearLayout.LayoutParams(-1, -2));
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(cell) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int pos) {
            File f = images.get(pos);
            LinearLayout cell = (LinearLayout) vh.itemView;
            Context c = cell.getContext();
            ImageView iv = (ImageView) cell.getChildAt(0);
            TextView tv = (TextView) cell.getChildAt(1);
            iv.setTag(null);
            if (f == null || f.isDirectory()) {
                Ui.setDirIcon(iv, f == null ? R.drawable.ic_s2_arrow_back : R.drawable.ic_s2_folder);
                iv.setImageTintList(ColorStateList.valueOf(0xFFFFCA28));
                tv.setText(f == null ? ".." : f.getName());
            } else {
                iv.setImageResource(R.drawable.ic_s2_image);
                iv.setImageTintList(ColorStateList.valueOf(Ui.TEXT_3));
                FilePickerDialog.thumb(iv, f, Ui.dp(c, 56));
                tv.setText(f.getName());
            }
            Tut.tag(cell, f == null ? "animimg:.." : "animimg:" + f.getName());
            cell.setOnClickListener(v -> {
                if (f == null) {
                    dir = dir.getParentFile();
                    reloadImages();
                } else if (f.isDirectory()) {
                    dir = f;
                    reloadImages();
                } else addFrame(f);
            });
        }

        @Override public int getItemCount() { return images.size(); }
    }

    /** Checkerboard behind the preview (shows transparency). */
    static final class Checker extends Drawable {
        final Paint a = new Paint(), b = new Paint();
        final int s;

        Checker(int s) {
            this.s = Math.max(4, s);
            a.setColor(0xFF2A2E35);
            b.setColor(0xFF22262C);
        }

        @Override
        public void draw(@NonNull Canvas c) {
            Rect r = getBounds();
            c.drawRect(r, a);
            for (int y = r.top, row = 0; y < r.bottom; y += s, row++)
                for (int x = r.left + (row % 2) * s; x < r.right; x += 2 * s) c.drawRect(x, y, Math.min(x + s, r.right), Math.min(y + s, r.bottom), b);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
