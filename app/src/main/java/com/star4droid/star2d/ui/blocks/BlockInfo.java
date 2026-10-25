package com.star4droid.star2d.ui.blocks;

import java.util.HashMap;

/**
 * Human labels and (!) explanations for blocks, categories and field names (English / Arabic).
 * The saved block title never changes; these are display-only.
 */
public final class BlockInfo {
    private BlockInfo() {}

    // title -> {label en, label ar, info en, info ar}
    private static final HashMap<String, String[]> B = new HashMap<>();
    private static final HashMap<String, String[]> CAT = new HashMap<>();
    private static final HashMap<String, String[]> FIELD = new HashMap<>();

    private static void b(String t, String le, String la, String ie, String ia) { B.put(t, new String[]{le, la, ie, ia}); }
    private static void c(String t, String e, String a) { CAT.put(t, new String[]{e, a}); }
    private static void f(String t, String e, String a) { FIELD.put(t, new String[]{e, a}); }

    static {
        c("main", "Control", "تحكم أساسي");
        c("Control", "Loops & logic", "التكرار والشروط");
        c("Variables", "Variables & data", "المتغيرات والبيانات");
        c("Motion", "Motion", "الحركة");
        c("Looks", "Looks", "المظهر");
        c("Input", "Input", "الإدخال واللمس");
        c("Physics", "Physics+", "فيزياء إضافية");
        c("Camera", "Camera", "الكاميرا");
        c("Configurations", "Scene", "المشهد");
        c("Debug", "Debug", "رسائل وتصحيح");
        c("Ui Nodes", "UI", "واجهة اللعبة");
        c("BodyModify", "Element", "تعديل العنصر");
        c("bodyControl", "Forces", "القوى والسرعة");
        c("bodyBooleans", "Body options", "خيارات الجسم");
        c("Sounds", "Sounds", "الأصوات");
        c("Joints", "Joints", "الروابط");

        f("body", "element", "العنصر");
        f("body1", "element 1", "العنصر 1");
        f("body2", "element 2", "العنصر 2");
        f("item", "element", "العنصر");
        f("target", "target", "الهدف");
        f("ui", "UI element", "عنصر الواجهة");
        f("text", "text", "النص");
        f("value", "value", "القيمة");
        f("boolean", "condition", "الشرط");
        f("delay", "seconds", "ثوانٍ");
        f("seconds", "seconds", "ثوانٍ");
        f("name", "name", "الاسم");
        f("scene", "scene", "المشهد");
        f("image", "image", "الصورة");
        f("animation", "animation", "الحركة");
        f("Sound", "sound", "الصوت");
        f("speed", "speed", "السرعة");
        f("power", "power", "القوة");
        f("times", "times", "عدد المرات");
        f("variable", "variable", "المتغير");
        f("by", "by", "بمقدار");
        f("message", "message", "الرسالة");
        f("x", "x", "س");
        f("y", "y", "ص");
        f("dx", "dx", "إزاحة س");
        f("dy", "dy", "إزاحة ص");
        f("strength", "strength", "القوة");
        f("joystick", "joystick", "عصا التحكم");
        f("newName", "new name", "الاسم الجديد");
        f("var", "var", "المتغير");
        f("joystick", "joystick", "عصا التحكم");
        f("degrees", "degrees", "درجات");
        f("visibility", "visible", "ظاهر");
        f("wake", "wake", "إيقاظ");
        f("x", "x", "x");
        f("y", "y", "y");
        f("dx", "dx", "إزاحة x");
        f("dy", "dy", "إزاحة y");
        f("zoom", "zoom", "التقريب");
        f("code", "code", "الكود");
        f("color", "color", "اللون");
        f("alpha", "opacity", "الشفافية");
        f("key", "key", "المفتاح");
        f("type", "type", "النوع");
        f("url", "link", "الرابط");

        // root
        b("First >", "When this event runs", "عند تشغيل هذا الحدث", "Every block attached below runs, top to bottom, when the event happens.", "كل البلوكات المتصلة أسفل هذا البلوك تعمل بالترتيب من الأعلى للأسفل عند حدوث الحدث.");
        b("First > ", "When this event runs", "عند تشغيل هذا الحدث", "Every block attached below runs, top to bottom, when the event happens.", "كل البلوكات المتصلة أسفل هذا البلوك تعمل بالترتيب من الأعلى للأسفل عند حدوث الحدث.");
        // main
        b("addSource", "Write code", "اكتب كوداً", "Insert your own Java code here (manual typing). Example: score++;", "أدخل كود جافا الخاص بك يدوياً هنا. مثال: score++;");
        b("try", "Try (ignore errors)", "جرّب (تجاهل الأخطاء)", "Runs the blocks inside; if they crash, the game continues.", "يشغّل البلوكات بداخله، وإذا حدث خطأ تستمر اللعبة.");
        b("void", "Define function", "عرّف دالة", "Creates a function (use in custom scripts). Name example: shoot()", "ينشئ دالة (للسكربتات المخصصة). مثال الاسم: shoot()");
        b("while", "Repeat while", "كرّر طالما", "Repeats the inside blocks while the condition is true. Careful: endless loops freeze the game.", "يكرر البلوكات بداخله طالما الشرط صحيح. احذر: التكرار اللانهائي يجمّد اللعبة.");
        b("if", "If / else", "إذا / وإلا", "If the condition is true run the first part, otherwise the second.", "إذا كان الشرط صحيحاً ينفّذ الجزء الأول، وإلا ينفّذ الجزء الثاني.");
        b("openUrl", "Open link", "افتح رابطاً", "Opens a web page in the browser.", "يفتح صفحة ويب في المتصفح.");
        b("OpenScene", "Go to scene", "انتقل إلى مشهد", "Switches to another scene (level, menu...).", "ينتقل إلى مشهد آخر (مرحلة، قائمة...).");
        b("finish", "Close game", "أغلق اللعبة", "Ends the current scene / game.", "ينهي المشهد الحالي أو اللعبة.");
        b("_DoAfter", "Do after (timer)", "نفّذ بعد (مؤقت)", "Runs the inside blocks after a delay in seconds. Give the timer a name to cancel it.", "ينفّذ البلوكات بداخله بعد عدد من الثواني. أعطِ المؤقت اسماً لتتمكن من إلغائه.");
        b("cancelTimer", "Cancel timer", "ألغِ المؤقت", "Stops a timer created by Do after / Repeat every.", "يوقف مؤقتاً أنشأته بـ نفّذ بعد أو كرّر كل.");
        b("_repeatEvery", "Repeat every (timer)", "كرّر كل (مؤقت)", "Runs the inside blocks every N seconds.", "ينفّذ البلوكات بداخله كل عدد من الثواني.");
        b("writeToFile", "Write file", "اكتب ملفاً", "Saves text to a file.", "يحفظ نصاً في ملف.");
        b("writeInternal", "Write internal file", "اكتب ملفاً داخلياً", "Saves text to the game's private storage.", "يحفظ نصاً في ذاكرة اللعبة الخاصة.");
        // camera
        b("setZoom", "Set zoom", "اضبط التقريب", "Camera zoom: smaller = closer.", "تقريب الكاميرا: أصغر = أقرب.");
        b("cameraFollow", "Camera follow", "الكاميرا تتبع", "Centers the camera on an element (use in OnStep to keep following).", "يوسّط الكاميرا على عنصر (ضعه في OnStep ليتبعه باستمرار).");
        b("setCameraXAt", "Camera X to element", "الكاميرا أفقياً إلى عنصر", "Moves the camera horizontally to an element.", "يحرك الكاميرا أفقياً إلى عنصر.");
        b("setCameraYAt", "Camera Y to element", "الكاميرا رأسياً إلى عنصر", "Moves the camera vertically to an element.", "يحرك الكاميرا رأسياً إلى عنصر.");
        b("setCameraXY", "Camera to x, y", "الكاميرا إلى x, y", "Moves the camera to a point.", "يحرك الكاميرا إلى نقطة.");
        b("setCameraCenter", "Camera center", "مركز الكاميرا", "Sets the camera center point.", "يضبط نقطة مركز الكاميرا.");
        b("setCameraX", "Camera x", "الكاميرا x", "Sets camera x.", "يضبط x للكاميرا.");
        b("setCameraY", "Camera y", "الكاميرا y", "Sets camera y.", "يضبط y للكاميرا.");
        b("setCameraOffset", "Camera offset", "إزاحة الكاميرا", "Offsets the camera from its target.", "يزيح الكاميرا عن هدفها.");
        // configurations
        b("setsplits", "Physics steps", "خطوات الفيزياء", "Physics precision per frame (advanced).", "دقة الفيزياء في كل إطار (متقدم).");
        b("ifCollision", "If touching", "إذا كان يلمس", "Checks if two elements are touching right now.", "يتحقق إذا كان عنصران يتلامسان الآن.");
        b("Pause", "Pause game", "إيقاف مؤقت", "Pauses physics and timers.", "يوقف الفيزياء والمؤقتات مؤقتاً.");
        b("Resume", "Resume game", "استئناف", "Resumes after pause.", "يستأنف بعد الإيقاف المؤقت.");
        b("saveValue", "Save value", "احفظ قيمة", "Saves a value permanently (advanced).", "يحفظ قيمة بشكل دائم (متقدم).");
        b("setGravity", "Set gravity", "اضبط الجاذبية", "World gravity. Normal: x 0, y -9.8", "جاذبية العالم. الطبيعي: x = 0 و y = -9.8");
        // debug
        b("Toast", "Show message", "أظهر رسالة", "Shows a short message. Text needs quotes: \"Hello\"", "يعرض رسالة قصيرة. النص يحتاج علامات تنصيص: \"مرحبا\"");
        b("debug", "Debug log", "سجل التصحيح", "Writes to the debug log.", "يكتب في سجل التصحيح.");
        // ui
        b("setVisibility", "Set visible", "اضبط الظهور", "Shows (true) or hides (false) an element.", "يُظهر (true) أو يخفي (false) عنصراً.");
        b("setText", "Set text", "اضبط النص", "Changes a Text element. Use quotes for text: \"Score\"", "يغيّر نص عنصر النص. استخدم علامات التنصيص: \"النقاط\"");
        b("setAlpha", "Set alpha (old)", "الشفافية (قديم)", "Not implemented; use Set opacity instead.", "غير مكتمل؛ استخدم «اضبط الشفافية» بدلاً منه.");
        b("setProgress", "Set progress", "اضبط التقدم", "Sets a progress bar value.", "يضبط قيمة شريط التقدم.");
        b("setMax", "Set max", "اضبط الحد الأقصى", "Sets a progress bar maximum.", "يضبط الحد الأقصى لشريط التقدم.");
        // body modify
        b("setScaleXY", "Set scale", "اضبط الحجم", "Scales the element's picture.", "يغيّر حجم صورة العنصر.");
        b("setScaleX", "Set scale X", "الحجم أفقياً", "Horizontal scale (-1 flips).", "الحجم الأفقي (-1 يقلب الصورة).");
        b("setScaleY", "Set scale Y", "الحجم رأسياً", "Vertical scale.", "الحجم الرأسي.");
        b("setTransform", "Set position & angle", "اضبط الموضع والزاوية", "Teleports an element (angle in radians).", "ينقل العنصر فوراً (الزاوية بالراديان).");
        b("setAnimation", "Play animation", "شغّل حركة", "Plays an animation from the anims folder.", "يشغّل حركة من مجلد anims.");
        b("removeAnimation", "Stop animation", "أوقف الحركة", "Stops the element's animation.", "يوقف حركة العنصر.");
        b("setX", "Set x", "اضبط x", "Moves an element horizontally.", "ينقل العنصر أفقياً.");
        b("setY", "Set y", "اضبط y", "Moves an element vertically.", "ينقل العنصر رأسياً.");
        b("setAngle", "Set angle", "اضبط الزاوية", "Angle in radians.", "الزاوية بالراديان.");
        b("setImage", "Change image", "غيّر الصورة", "Changes the element's picture.", "يغيّر صورة العنصر.");
        b("copyBody", "Clone element", "انسخ العنصر", "Creates a copy at runtime (bullets, enemies...).", "ينشئ نسخة أثناء اللعب (رصاص، أعداء...).");
        b("destroy", "Destroy", "احذف العنصر", "Removes the element from the game.", "يزيل العنصر من اللعبة.");
        // forces
        b("applyForceToCenter", "Push (force)", "ادفع (قوة)", "Pushes continuously (use in OnStep).", "يدفع باستمرار (ضعه في OnStep).");
        b("applyLinearImpulse", "Kick at point", "دفعة عند نقطة", "Instant push at a point.", "دفعة فورية عند نقطة.");
        b("applyLinearImpulseToCenter", "Kick (impulse)", "دفعة فورية", "Instant push: great for jumps.", "دفعة فورية: ممتازة للقفز.");
        b("applyForce", "Force at point", "قوة عند نقطة", "Pushes at a point (can rotate it).", "يدفع عند نقطة (قد يدوّر الجسم).");
        b("setAngularVelocity", "Spin speed", "سرعة الدوران", "Sets rotation speed.", "يضبط سرعة الدوران.");
        b("applyAngularImpulse", "Spin kick", "دفعة دوران", "Instant spin.", "دوران فوري.");
        b("setGravityScale", "Gravity scale", "مقياس الجاذبية", "0 = floats, 1 = normal.", "0 = يطفو، 1 = طبيعي.");
        b("setLinearVelocity", "Set speed", "اضبط السرعة", "Sets movement speed in x and y.", "يضبط السرعة في x و y.");
        b("applyTorque", "Torque", "عزم دوران", "Rotational force.", "قوة دوران.");
        b("setLinearDamping", "Air friction", "مقاومة الهواء", "Slows the body down over time.", "يبطئ الجسم مع الوقت.");
        b("setAwake", "Wake up", "أيقظ الجسم", "Wakes a sleeping body.", "يوقظ جسماً نائماً.");
        b("setBullet", "Bullet mode", "وضع الرصاصة", "Better collisions for fast bodies.", "تصادم أدق للأجسام السريعة.");
        b("setFixedRotation", "Fixed rotation", "منع الدوران", "true = never rotates (players).", "true = لا يدور أبداً (للاعب).");
        b("setActive", "Active", "نشط", "false = removed from physics.", "false = خارج الفيزياء.");
        b("setSleepingAllowed", "Allow sleep", "السماح بالنوم", "Performance option.", "خيار للأداء.");
        // sounds
        b("loopSound", "Loop sound", "كرّر الصوت", "Plays music in a loop (true/false).", "يشغّل الصوت بشكل متكرر (true/false).");
        b("playSound", "Play sound", "شغّل صوتاً", "Plays a sound from the sounds folder.", "يشغّل صوتاً من مجلد sounds.");
        b("pauseSound", "Pause sound", "أوقف الصوت", "Pauses a sound.", "يوقف الصوت مؤقتاً.");
        b("releaseSound", "Release sound", "حرّر الصوت", "Frees a sound from memory.", "يحرر الصوت من الذاكرة.");
        // joints
        b("setMaxMotorTorque", "Motor torque", "عزم المحرك", "Joint motor strength.", "قوة محرك الرابط.");
        b("setMotorSpeed", "Motor speed", "سرعة المحرك", "Joint motor speed (wheels).", "سرعة محرك الرابط (العجلات).");
        b("setLimits", "Joint limits", "حدود الرابط", "Min/max of the joint.", "الحد الأدنى والأعلى للرابط.");
        b("setSpringDampingRatio", "Spring damping", "تخميد النابض", "Spring bounciness control.", "تحكم في ارتداد النابض.");
        b("setSpringFrequencyHz", "Spring frequency", "تردد النابض", "Spring stiffness.", "صلابة النابض.");
        b("enableMotor", "Enable motor", "شغّل المحرك", "Turns the joint motor on/off.", "يشغّل أو يوقف محرك الرابط.");
        b("enableLimit", "Enable limit", "فعّل الحدود", "Turns joint limits on/off.", "يفعّل أو يلغي حدود الرابط.");
        // new: control
        b("repeatTimes", "Repeat N times", "كرّر N مرة", "Runs the inside blocks a number of times. 'var' is the counter name (0, 1, 2...).", "يكرر البلوكات بداخله عدداً من المرات. var هو اسم العدّاد (0، 1، 2...).");
        b("ifThen", "If", "إذا", "Runs the inside blocks only when the condition is true.", "ينفّذ البلوكات بداخله فقط عندما يكون الشرط صحيحاً.");
        b("breakLoop", "Exit loop", "اخرج من التكرار", "Stops the current loop.", "يوقف التكرار الحالي.");
        b("stopEvent", "Stop this event", "أوقف هذا الحدث", "Skips the rest of the blocks of this event.", "يتجاوز باقي بلوكات هذا الحدث.");
        b("waitSeconds", "Wait then", "انتظر ثم", "Runs the inside blocks after some seconds.", "ينفّذ البلوكات بداخله بعد عدة ثوانٍ.");
        b("everySeconds", "Every N seconds", "كل N ثانية", "Runs the inside blocks repeatedly (spawn enemies, timers...).", "ينفّذ البلوكات بداخله بشكل متكرر (إنتاج أعداء، عدّاد...).");
        b("comment", "Note", "ملاحظة", "A note for you; does nothing.", "ملاحظة لك؛ لا تفعل شيئاً.");
        b("codeLine", "Code line", "سطر كود", "Type any Java line, e.g. score++;", "اكتب أي سطر جافا، مثل: score++;");
        // variables
        b("setVariable", "Set variable", "اضبط متغيراً", "variable = value. Create variables in the Variables panel.", "المتغير = القيمة. أنشئ المتغيرات من لوحة المتغيرات.");
        b("changeVariable", "Change variable by", "غيّر المتغير بمقدار", "Adds to a variable (use -1 to subtract).", "يضيف إلى المتغير (استخدم -1 للطرح).");
        b("newVariable", "Local variable", "متغير محلي", "Creates a temporary variable for this event.", "ينشئ متغيراً مؤقتاً لهذا الحدث.");
        b("saveData", "Save data", "احفظ بيانات", "Saves a value permanently (high score...). Read it with 'saved number'.", "يحفظ قيمة بشكل دائم (أعلى نتيجة...). اقرأها بـ «الرقم المحفوظ».");
        // motion
        b("moveBy", "Move by", "تحرك بمقدار", "Moves an element by dx, dy.", "يحرك العنصر بمقدار dx و dy.");
        b("goTo", "Go to x, y", "اذهب إلى x, y", "Teleports an element.", "ينقل العنصر فوراً.");
        b("goToBody", "Go to element", "اذهب إلى عنصر", "Teleports to another element's position.", "ينتقل إلى موضع عنصر آخر.");
        b("setVelocityX", "Speed X", "السرعة الأفقية", "Horizontal speed (keeps falling speed).", "السرعة الأفقية (يحافظ على سرعة السقوط).");
        b("setVelocityY", "Speed Y", "السرعة الرأسية", "Vertical speed.", "السرعة الرأسية.");
        b("stopMoving", "Stop moving", "توقف عن الحركة", "Sets speed and spin to zero.", "يجعل السرعة والدوران صفراً.");
        b("jump", "Jump", "اقفز", "Gives an upward speed. Use in onClick of a jump button.", "يعطي سرعة للأعلى. ضعه في onClick لزر القفز.");
        b("rotateBy", "Rotate by", "دوّر بمقدار", "Rotates by degrees.", "يدوّر بعدد من الدرجات.");
        b("lookAt", "Look at", "انظر إلى", "Turns toward another element.", "يتجه نحو عنصر آخر.");
        b("moveToward", "Move toward", "تحرك نحو", "Moves toward a target (enemies chasing). Use in OnStep.", "يتحرك نحو هدف (أعداء يطاردون). ضعه في OnStep.");
        b("moveWithJoystick", "Move with joystick", "تحرك بعصا التحكم", "Top-down movement. Put it in OnStep.", "حركة من الأعلى. ضعه في OnStep.");
        b("walkWithJoystick", "Walk with joystick", "امشِ بعصا التحكم", "Platformer movement (left/right only). Put it in OnStep.", "حركة ألعاب المنصات (يمين/يسار فقط). ضعه في OnStep.");
        // looks
        b("show", "Show", "أظهر", "Makes an element visible.", "يُظهر العنصر.");
        b("hide", "Hide", "أخفِ", "Hides an element.", "يخفي العنصر.");
        b("setTint", "Tint color", "لوّن", "Colors the picture (RRGGBBAA).", "يلوّن الصورة (RRGGBBAA).");
        b("setOpacity", "Set opacity", "اضبط الشفافية", "0 = invisible, 1 = solid.", "0 = غير مرئي، 1 = واضح.");
        b("flipX", "Flip horizontally", "اقلب أفقياً", "Mirrors the picture.", "يعكس الصورة.");
        b("bringToFront", "Bring to front", "إلى المقدمة", "Draws on top of others.", "يُرسم فوق الباقي.");
        b("sendToBack", "Send to back", "إلى الخلف", "Draws behind others.", "يُرسم خلف الباقي.");
        b("showText", "Show text", "اعرض نصاً", "Shows a value in a Text element (score...).", "يعرض قيمة في عنصر نص (النقاط...).");
        // input
        b("ifTouching", "If screen touched", "إذا لُمست الشاشة", "True while a finger touches the screen.", "صحيح طالما هناك إصبع على الشاشة.");
        b("ifJoystickMoved", "If joystick moved", "إذا تحركت العصا", "True when the joystick is not centered.", "صحيح عندما لا تكون العصا في المنتصف.");
        // physics
        b("setBodyType", "Set body type", "اضبط نوع الجسم", "DynamicBody, StaticBody or KinematicBody.", "DynamicBody أو StaticBody أو KinematicBody.");
        b("setGravityScaleOf", "Gravity scale", "مقياس الجاذبية", "0 = floats, 1 = normal, 2 = heavy.", "0 = يطفو، 1 = طبيعي، 2 = ثقيل.");
        b("destroyAfter", "Destroy after", "احذف بعد", "Removes the element after some seconds (bullets).", "يحذف العنصر بعد عدة ثوانٍ (الرصاص).");
        b("spawnCopy", "Spawn copy at", "أنشئ نسخة عند", "Clones an element at x, y. newName needs quotes.", "ينسخ العنصر عند x و y. الاسم الجديد يحتاج علامات تنصيص.");
        b("setWorldGravity", "Set gravity", "اضبط الجاذبية", "How fast things fall, in pixels per second² (600 = normal, 0 = space).", "سرعة سقوط الأشياء بالبكسل/ثانية² (600 = عادي، 0 = فضاء).");
        b("toastValue", "Show value", "أظهر قيمة", "Shows any value as a message (no quotes needed).", "يعرض أي قيمة كرسالة (بدون علامات تنصيص).");
        b("logValue", "Log value", "سجّل قيمة", "Writes any value to the debug log.", "يكتب أي قيمة في سجل التصحيح.");
    }

    /** Translation override from the language files (fr, pt, ru, es…), or null. */
    private static String tr(String key) {
        return com.star4droid.star2d.ui.Tr.t(key, null);
    }

    static String key(String s) {
        return s.replaceAll("[^A-Za-z0-9]+", "_");
    }

    public static String label(String title, boolean ar) {
        String t = tr("blk_l_" + key(title));
        if (t != null) return t;
        String[] v = B.get(title);
        return v == null ? title : v[ar ? 1 : 0];
    }

    public static String info(String title, boolean ar) {
        String t = tr("blk_i_" + key(title));
        if (t != null) return t;
        String[] v = B.get(title);
        return v == null ? null : v[ar ? 3 : 2];
    }

    public static String category(String cat, boolean ar) {
        String t = tr("blk_c_" + key(cat));
        if (t != null) return t;
        String[] v = CAT.get(cat);
        return v == null ? cat : v[ar ? 1 : 0];
    }

    public static String field(String label, boolean ar) {
        String t = tr("blk_f_" + key(label));
        if (t != null) return t;
        String[] v = FIELD.get(label);
        return v == null ? label : v[ar ? 1 : 0];
    }

    /** Value block text ("x of ___"), translated when the language file has it. Keeps the ___ slots. */
    public static String reporter(String display) {
        String t = tr("blk_r_" + key(display));
        if (t == null) return display;
        // a translation must keep the same number of slots
        if (BlockModel.Param.count(t, "___") != BlockModel.Param.count(display, "___")) return display;
        return t;
    }

    /** All built-in labels as properties lines (for translators). */
    public static String dump() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String[]> e : CAT.entrySet()) sb.append("blk_c_").append(key(e.getKey())).append('=').append(e.getValue()[0]).append('\n');
        for (java.util.Map.Entry<String, String[]> e : FIELD.entrySet()) sb.append("blk_f_").append(key(e.getKey())).append('=').append(e.getValue()[0]).append('\n');
        for (java.util.Map.Entry<String, String[]> e : B.entrySet()) {
            sb.append("blk_l_").append(key(e.getKey())).append('=').append(e.getValue()[0]).append('\n');
            sb.append("blk_i_").append(key(e.getKey())).append('=').append(e.getValue()[2]).append('\n');
        }
        return sb.toString();
    }
}
