package com.star4droid.star2d.remote;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Map;

/** Procedural PNGs so the AI needs no uploads: pixel sprites, shapes, gradients, tiles. */
final class ImageGen {
    private ImageGen() {}

    private static final int MAX_SIDE = 2048;
    private static final long MAX_PIXELS = 4_000_000L;

    static int color(String s, int def) {
        if (s == null) return def;
        String t = s.trim();
        if (t.isEmpty()) return def;
        if (t.equalsIgnoreCase("transparent") || t.equalsIgnoreCase("none")) return Color.TRANSPARENT;
        try {
            return Color.parseColor(t.startsWith("#") || Character.isLetter(t.charAt(0)) && !isHex(t) ? t : "#" + t);
        } catch (Exception e) {
            throw new Gl.ToolError("bad color '" + s + "' (use #RRGGBB, #AARRGGBB, a name like red, or transparent)");
        }
    }

    private static boolean isHex(String t) {
        return t.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?");
    }

    static byte[] png(JsonObject a) {
        String kind = CoreTools.s(a, "kind");
        Bitmap bmp;
        switch (kind) {
            case "pixels": bmp = pixels(a); break;
            case "tiles": bmp = tiles(a); break;
            case "shape": bmp = shape(a); break;
            case "gradient": bmp = gradient(a); break;
            default: throw new Gl.ToolError("kind must be pixels|shape|gradient|tiles");
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bo);
        bmp.recycle();
        return bo.toByteArray();
    }

    private static Bitmap make(int w, int h) {
        if (w < 1 || h < 1 || w > MAX_SIDE || h > MAX_SIDE || (long) w * h > MAX_PIXELS)
            throw new Gl.ToolError("image size " + w + "x" + h + " is out of range (1.." + MAX_SIDE + " per side, max 4M pixels)");
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    }

    private static ArrayList<String> rowsOf(JsonObject a) {
        ArrayList<String> rows = new ArrayList<>();
        if (a.has("rows") && a.get("rows").isJsonArray()) for (JsonElement e : a.getAsJsonArray("rows")) rows.add(e.getAsString());
        if (rows.isEmpty()) throw new Gl.ToolError("rows is required: an array of strings, one per pixel row");
        return rows;
    }

    private static java.util.HashMap<Character, Integer> paletteOf(JsonObject a) {
        java.util.HashMap<Character, Integer> pal = new java.util.HashMap<>();
        if (a.has("palette") && a.get("palette").isJsonObject())
            for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("palette").entrySet()) {
                if (e.getKey().length() != 1) throw new Gl.ToolError("palette keys must be single characters, got '" + e.getKey() + "'");
                pal.put(e.getKey().charAt(0), color(e.getValue().getAsString(), Color.TRANSPARENT));
            }
        if (!pal.containsKey('.')) pal.put('.', Color.TRANSPARENT);
        if (!pal.containsKey(' ')) pal.put(' ', Color.TRANSPARENT);
        return pal;
    }

    private static Bitmap pixels(JsonObject a) {
        ArrayList<String> rows = rowsOf(a);
        java.util.HashMap<Character, Integer> pal = paletteOf(a);
        int w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        int h = rows.size();
        int scale = Math.max(1, CoreTools.i(a, "scale", 1));
        Bitmap small = make(w, h);
        for (int y = 0; y < h; y++) {
            String r = rows.get(y);
            for (int x = 0; x < w; x++) {
                char c = x < r.length() ? r.charAt(x) : '.';
                Integer col = pal.get(c);
                if (col == null) throw new Gl.ToolError("row " + y + " uses '" + c + "' which is not in the palette");
                small.setPixel(x, y, col);
            }
        }
        if (scale == 1) return small;
        Bitmap big = make(w * scale, h * scale);
        Canvas cv = new Canvas(big);
        Paint p = new Paint();
        p.setFilterBitmap(false);
        p.setAntiAlias(false);
        cv.drawBitmap(small, null, new RectF(0, 0, w * scale, h * scale), p);
        small.recycle();
        return big;
    }

    private static Bitmap tiles(JsonObject a) {
        Bitmap tile = pixels(a);
        int w = Math.max(1, CoreTools.i(a, "width", tile.getWidth())), h = Math.max(1, CoreTools.i(a, "height", tile.getHeight()));
        Bitmap out = make(w, h);
        Canvas cv = new Canvas(out);
        Paint p = new Paint();
        p.setFilterBitmap(false);
        for (int y = 0; y < h; y += tile.getHeight()) for (int x = 0; x < w; x += tile.getWidth()) cv.drawBitmap(tile, x, y, p);
        tile.recycle();
        return out;
    }

    /** "#RRGGBB", "linear:#a,#b[,angle]" or "radial:#a,#b". */
    private static Paint fillPaint(String fill, int w, int h, boolean hasFill) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        if (fill == null || fill.isEmpty()) {
            p.setColor(hasFill ? Color.WHITE : Color.TRANSPARENT);
            return p;
        }
        if (fill.startsWith("linear:") || fill.startsWith("radial:")) {
            boolean lin = fill.startsWith("linear:");
            String[] parts = fill.substring(7).split(",");
            ArrayList<Integer> cols = new ArrayList<>();
            float angle = 90;
            for (String s : parts) {
                String t = s.trim();
                if (t.matches("-?[0-9.]+") && lin && cols.size() >= 2) angle = Float.parseFloat(t);
                else if (!t.isEmpty()) cols.add(color(t, Color.WHITE));
            }
            if (cols.size() < 2) throw new Gl.ToolError("a gradient fill needs at least two colors");
            int[] cs = new int[cols.size()];
            for (int i = 0; i < cs.length; i++) cs[i] = cols.get(i);
            if (lin) {
                double r = Math.toRadians(angle);
                float cx = w / 2f, cy = h / 2f, dx = (float) Math.cos(r) * w / 2f, dy = (float) Math.sin(r) * h / 2f;
                p.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, cs, null, Shader.TileMode.CLAMP));
            } else {
                p.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) / 2f, cs, null, Shader.TileMode.CLAMP));
            }
            return p;
        }
        p.setColor(color(fill, Color.WHITE));
        return p;
    }

    private static Bitmap gradient(JsonObject a) {
        int w = CoreTools.i(a, "width", 64), h = CoreTools.i(a, "height", 64);
        Bitmap bmp = make(w, h);
        ArrayList<String> cols = new ArrayList<>();
        if (a.has("colors") && a.get("colors").isJsonArray()) for (JsonElement e : a.getAsJsonArray("colors")) cols.add(e.getAsString());
        if (cols.size() < 2) throw new Gl.ToolError("colors needs at least two colors");
        double angle = a.has("angle") ? a.get("angle").getAsDouble() : 90;
        StringBuilder f = new StringBuilder("linear:");
        for (String c : cols) f.append(c).append(',');
        f.append(angle);
        Paint p = fillPaint(f.toString(), w, h, true);
        new Canvas(bmp).drawRect(0, 0, w, h, p);
        return bmp;
    }

    private static Bitmap shape(JsonObject a) {
        int w = CoreTools.i(a, "width", 64), h = CoreTools.i(a, "height", 64);
        Bitmap bmp = make(w, h);
        Canvas cv = new Canvas(bmp);
        String shape = CoreTools.s(a, "shape");
        if (shape.isEmpty()) shape = "rect";
        float sw = a.has("strokeWidth") ? (float) a.get("strokeWidth").getAsDouble() : (CoreTools.s(a, "stroke").isEmpty() ? 0 : 2);
        float inset = sw / 2f;
        RectF r = new RectF(inset, inset, w - inset, h - inset);
        Paint fill = fillPaint(CoreTools.s(a, "fill"), w, h, CoreTools.s(a, "fill").isEmpty() && CoreTools.s(a, "stroke").isEmpty());
        Paint stroke = null;
        if (!CoreTools.s(a, "stroke").isEmpty() && sw > 0) {
            stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(sw);
            stroke.setColor(color(CoreTools.s(a, "stroke"), Color.BLACK));
        }
        Path path = new Path();
        switch (shape) {
            case "rect": path.addRect(r, Path.Direction.CW); break;
            case "rounded": {
                float rad = a.has("radius") ? (float) a.get("radius").getAsDouble() : Math.min(w, h) / 5f;
                path.addRoundRect(r, rad, rad, Path.Direction.CW);
                break;
            }
            case "circle": {
                float d = Math.min(r.width(), r.height());
                path.addCircle(w / 2f, h / 2f, d / 2f, Path.Direction.CW);
                break;
            }
            case "ellipse": path.addOval(r, Path.Direction.CW); break;
            case "triangle":
                path.moveTo(w / 2f, r.top);
                path.lineTo(r.right, r.bottom);
                path.lineTo(r.left, r.bottom);
                path.close();
                break;
            case "star": {
                float cx = w / 2f, cy = h / 2f, ro = Math.min(r.width(), r.height()) / 2f, ri = ro * 0.42f;
                for (int i = 0; i < 10; i++) {
                    double ang = -Math.PI / 2 + i * Math.PI / 5;
                    float rad = i % 2 == 0 ? ro : ri;
                    float x = cx + (float) Math.cos(ang) * rad, y = cy + (float) Math.sin(ang) * rad;
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                break;
            }
            default: throw new Gl.ToolError("shape must be rect|circle|ellipse|triangle|star|rounded");
        }
        if (fill.getShader() != null || fill.getColor() != Color.TRANSPARENT) cv.drawPath(path, fill);
        if (stroke != null) cv.drawPath(path, stroke);
        return bmp;
    }
}
