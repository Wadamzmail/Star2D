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
        String[][] tips = ar ? new String[][]{
                {"➕ أضف عنصراً", "اضغط زر + الأزرق واختر نوع العنصر (صندوق، دائرة، نص، عصا تحكم...)."},
                {"✋ الأدوات", "أداة اليد لتحريك الكاميرا والتحديد، ثم التحريك، تغيير الحجم، والتدوير. زر XY يقفل الحركة على محور واحد."},
                {"⚙ الخصائص", "من الشريط الجانبي افتح الخصائص لتعديل الاسم، الصورة، النوع (ثابت/متحرك)، الفيزياء والمزيد."},
                {"⚡ الأحداث", "اختر عنصراً ثم افتح الأحداث، مثل «عند النقر». اسحب البلوكات داخل الحدث لبناء المنطق بدون كتابة كود."},
                {"🔗 الروابط", "الروابط (Joints) تربط عنصرين فيزيائياً: عجلة سيارة، باب، حبل..."},
                {"𝑥 المتغيرات", "أنشئ متغيرات مثل score و lives واستخدمها في البلوكات."},
                {"▶ التشغيل", "زر التشغيل الأخضر يبني اللعبة ويشغّلها. اضغط رجوع لإيقافها. إن ظهر خطأ اضغط «اطلب من الذكاء إصلاحه»."},
                {"✨ المساعد الذكي", "اكتب ما تريد بالعربية، مثل: «اجعل اللاعب يقفز عند الضغط»، وسيكتب لك الكود أو يشرح الخطوات."},
        } : new String[][]{
                {"➕ Add elements", "Tap the blue + button and pick a type (box, circle, text, joystick...)."},
                {"✋ Tools", "Hand = select & pan the camera, then move, resize and rotate. The XY button locks dragging to one axis."},
                {"⚙ Properties", "Open Properties from the side rail to change name, image, type (static/dynamic), physics and more."},
                {"⚡ Events", "Select an element, open Events (e.g. On Click) and drag blocks into it to build logic without code."},
                {"🔗 Joints", "Joints connect two bodies physically: wheels, doors, ropes..."},
                {"𝑥 Variables", "Create variables like score or lives and use them in blocks."},
                {"▶ Play", "The green Play button builds and runs your game. Press Back to stop it. If it fails, tap “Ask AI to fix”."},
                {"✨ AI assistant", "Describe what you want, e.g. “make the player jump when I tap”, and it writes the code or explains the steps."},
        };
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
                .setTitle(ar ? "دليل سريع" : "Quick guide")
                .setIcon(R.drawable.ic_s2_help)
                .setView(s)
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .show();
    }
}
