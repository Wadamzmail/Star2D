package com.star4droid.star2d.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.evo.R;

/** Quick in-app guide for beginners. */
public final class Help {
    private Help() {}

    public static void show(Context c) {
        boolean ar = Ui.isRtl(c);
        // {key, english title, english text, arabic title, arabic text}
        String[][] all = {
                {"add", "➕ Add elements", "Tap the blue + button and pick a type (box, circle, text, joystick...).", "➕ أضف عنصراً", "اضغط زر + الأزرق واختر نوع العنصر (صندوق، دائرة، نص، عصا تحكم...)."},
                {"tools", "✋ Tools", "Hand = select & pan the camera, then move, resize and rotate. The XY button locks dragging to one axis.", "✋ الأدوات", "أداة اليد لتحريك الكاميرا والتحديد، ثم التحريك، تغيير الحجم، والتدوير. زر XY يقفل الحركة على محور واحد."},
                {"props", "⚙ Properties", "Open Properties from the side rail to change name, image, type (static/dynamic), physics and more. Number boxes open a number pad.", "⚙ الخصائص", "من الشريط الجانبي افتح الخصائص لتعديل الاسم، الصورة، النوع (ثابت/متحرك)، الفيزياء والمزيد. حقول الأرقام تفتح لوحة أرقام."},
                {"events", "⚡ Events", "Select an element, open Events (e.g. On Click) and drag blocks into it to build logic without code.", "⚡ الأحداث", "اختر عنصراً ثم افتح الأحداث، مثل «عند النقر». اسحب البلوكات داخل الحدث لبناء المنطق بدون كتابة كود."},
                {"blocks", "🧩 Blocks", "Hold a block in the palette, then drag it under another one (or just tap it). Tap a field to type a value or put a value block in it; value blocks can go inside each other. Long-press a block for its options, (!) explains it.", "🧩 البلوكات", "اضغط مطولاً على البلوكة في القائمة ثم اسحبها تحت أخرى (أو انقرها فقط). انقر حقلاً لكتابة قيمة أو وضع بلوكة قيمة فيه، ويمكن وضع بلوكات القيم داخل بعضها. اضغط مطولاً على بلوكة لخياراتها، و(!) يشرحها."},
                {"params", "🔹 Event parameters", "The first block of an event shows its parameters: current (this element), body2 (what it hit), event (the touch). Long-press one and drag a copy into any field.", "🔹 مدخلات الحدث", "البلوكة الأولى في الحدث تعرض مدخلاته: current (هذا العنصر)، body2 (ما اصطدم به)، event (اللمسة). اضغط مطولاً على أحدها واسحب نسخة إلى أي حقل."},
                {"numbers", "🔢 Numbers & files", "Number fields open a keypad (⌫ deletes, C clears). Fields for images, sounds, animations and scenes have a Browse button that opens the right folder.", "🔢 الأرقام والملفات", "حقول الأرقام تفتح لوحة أرقام (⌫ يحذف، C يمسح). حقول الصور والأصوات والحركات والمشاهد فيها زر تصفح يفتح المجلد المناسب."},
                {"anims", "🎞 Animations", "In Files, open (or create) an animation in the anims folder: tap images to add frames, drag frames to reorder, set the frame time and press play. Use it with the setAnimation block.", "🎞 الحركات", "من الملفات افتح (أو أنشئ) حركة في مجلد anims: انقر الصور لإضافة إطارات، اسحب الإطارات لترتيبها، اضبط زمن الإطار واضغط تشغيل. استخدمها ببلوكة setAnimation."},
                {"joints", "🔗 Joints", "Joints connect two bodies physically: wheels, doors, ropes...", "🔗 الروابط", "الروابط (Joints) تربط عنصرين فيزيائياً: عجلة سيارة، باب، حبل..."},
                {"vars", "𝑥 Variables", "Create variables like score or lives and use them in blocks.", "𝑥 المتغيرات", "أنشئ متغيرات مثل score و lives واستخدمها في البلوكات."},
                {"assets", "🖼 Collections", "Free images (CC0) you can add to your project from Files → Collections.", "🖼 المجموعات", "صور مجانية (CC0) يمكنك إضافتها لمشروعك من الملفات ← المجموعات."},
                {"play", "▶ Play", "The green Play button builds and runs your game. Press Back to stop it. If it fails, tap “Ask AI to fix”.", "▶ التشغيل", "زر التشغيل الأخضر يبني اللعبة ويشغّلها. اضغط رجوع لإيقافها. إن ظهر خطأ اضغط «اطلب من الذكاء إصلاحه»."},
                {"lessons", "🎓 Lessons", "On the home screen, the Learn tab has step-by-step lessons that build real games with you.", "🎓 الدروس", "في الشاشة الرئيسية، تبويب «تعلّم» فيه دروس خطوة بخطوة تبني معك ألعاباً حقيقية."},
                {"ai", "✨ AI assistant", "Describe what you want, e.g. “make the player jump when I tap”, and it writes the code or explains the steps.", "✨ المساعد الذكي", "اكتب ما تريد بالعربية، مثل: «اجعل اللاعب يقفز عند الضغط»، وسيكتب لك الكود أو يشرح الخطوات."},
        };
        String[][] tips = new String[all.length][];
        for (int i = 0; i < all.length; i++)
            tips[i] = new String[]{Tr.t("help_" + all[i][0] + "_t", ar ? all[i][3] : all[i][1]), Tr.t("help_" + all[i][0] + "_d", ar ? all[i][4] : all[i][2])};
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 6), Ui.dp(c, 20), Ui.dp(c, 6));
        for (String[] t : tips) {
            TextView h = Ui.text(c, t[0], 15, Ui.TEXT);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            h.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2));
            box.addView(h);
            box.addView(Ui.text(c, t[1], 13.5f, Ui.TEXT_2));
        }
        ScrollView s = new ScrollView(c);
        s.addView(box);
        new AlertDialog.Builder(c)
                .setTitle(Tr.t("help_title", ar ? "دليل سريع" : "Quick guide"))
                .setIcon(R.drawable.ic_s2_help)
                .setView(s)
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .show();
    }
}
