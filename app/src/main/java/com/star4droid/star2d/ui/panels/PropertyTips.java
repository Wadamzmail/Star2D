package com.star4droid.star2d.ui.panels;

import java.util.HashMap;

/** Short explanations of property fields for beginners (English / Arabic). */
public final class PropertyTips {
    private PropertyTips() {}

    private static final HashMap<String, String[]> TIPS = new HashMap<>();

    private static void t(String field, String en, String ar) {
        TIPS.put(field, new String[]{en, ar});
    }

    static {
        t("name", "Unique name of the element. Used in blocks and code to find it.", "اسم فريد للعنصر، يُستخدم في البلوكات والكود للوصول إليه.");
        t("type", "DYNAMIC: moves and falls with gravity (players, balls).\nSTATIC: never moves (ground, walls).\nKINAMATIC: moves by code only, not affected by gravity (moving platforms).\nUI: stays fixed on the screen (buttons, score).",
                "DYNAMIC: يتحرك ويسقط بالجاذبية (لاعب، كرة).\nSTATIC: ثابت لا يتحرك (أرض، جدار).\nKINAMATIC: يتحرك بالكود فقط ولا تؤثر عليه الجاذبية (منصة متحركة).\nUI: ثابت على الشاشة (أزرار، النقاط).");
        t("lock", "Locked elements can't be moved by touch in the editor.", "العنصر المقفل لا يمكن تحريكه باللمس في المحرر.");
        t("Script", "Which body script (code) controls this element.", "أي سكربت (كود) يتحكم في هذا العنصر.");
        t("parent", "Attach this element to another one; it follows the parent.", "اربط هذا العنصر بعنصر آخر ليتبعه.");
        t("x", "Horizontal position in the world.", "الموضع الأفقي في العالم.");
        t("y", "Vertical position in the world.", "الموضع الرأسي في العالم.");
        t("z", "Drawing order: higher values are drawn on top.", "ترتيب الرسم: القيمة الأعلى تُرسم فوق غيرها.");
        t("rotation", "Angle in degrees.", "الزاوية بالدرجات.");
        t("width", "Width of the element.", "عرض العنصر.");
        t("height", "Height of the element.", "ارتفاع العنصر.");
        t("radius", "Radius of the circle.", "نصف قطر الدائرة.");
        t("density", "How heavy the body is. Higher = heavier.", "كثافة الجسم: كلما زادت صار أثقل.");
        t("friction", "How much it slides. 0 = ice, 1 = rubber.", "الاحتكاك: 0 = جليد، 1 = مطاط.");
        t("restitution", "Bounciness. 0 = no bounce, 1 = full bounce.", "الارتداد: 0 = لا يرتد، 1 = يرتد بالكامل.");
        t("Gravity Scale", "Multiplier for gravity on this body. 0 = floats.", "مضاعف الجاذبية لهذا الجسم. 0 = يطفو.");
        t("Fixed Rotation", "Prevent the body from rotating (good for players).", "يمنع الجسم من الدوران (مناسب للاعب).");
        t("Bullet", "Better collision for very fast objects.", "تصادم أدق للأجسام السريعة جداً.");
        t("isSensor", "Detects collisions but doesn't block (triggers, coins).", "يكتشف التصادم دون أن يصد الأجسام (مناطق تفعيل، عملات).");
        t("Active", "Inactive bodies don't take part in physics.", "الجسم غير النشط لا يشارك في الفيزياء.");
        t("Visible", "Show or hide the element.", "إظهار أو إخفاء العنصر.");
        t("image", "Picture shown for this element (from the images folder).", "الصورة المعروضة للعنصر (من مجلد images).");
        t("Tint", "Color multiplied over the image.", "لون يُمزج فوق الصورة.");
        t("Collision", "Elements this one should NOT collide with.", "العناصر التي لا يجب أن يتصادم معها هذا العنصر.");
        t("Points", "Custom collider polygon points.", "نقاط شكل التصادم المخصص.");
        t("Text", "Text to show.", "النص المعروض.");
        t("Font Scale", "Size of the text.", "حجم النص.");
        t("tileX", "Repeat the image horizontally.", "تكرار الصورة أفقياً.");
        t("tileY", "Repeat the image vertically.", "تكرار الصورة رأسياً.");
        t("Zoom", "Camera zoom. Smaller = closer.", "تقريب الكاميرا: أصغر = أقرب.");
        t("Duration", "Effect duration in seconds.", "مدة التأثير بالثواني.");
        t("Distance", "How far the light reaches.", "مدى وصول الضوء.");
        t("rays", "Light quality (more rays = smoother, slower).", "جودة الضوء (أشعة أكثر = أنعم وأبطأ).");
        t("Static Light", "Static lights are cheaper (don't move).", "الضوء الثابت أخف على الأداء.");
        t("attach To", "Attach the light to an element so it follows it.", "اربط الضوء بعنصر ليتبعه.");
    }

    public static String tip(String field, boolean arabic) {
        String[] t = TIPS.get(field);
        return t == null ? null : t[arabic ? 1 : 0];
    }
}
