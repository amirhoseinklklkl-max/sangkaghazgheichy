package com.ancientpersia.rps.data

/**
 * پوست‌های (اسکین‌های) دست در فروشگاه.
 * پوست شاهنشاهی فقط از طریق بسته شاهنشاهی در بازار سیاه باز می‌شود.
 */
data class Skin(
    val id: String,
    val name: String,
    val price: Int,
    val desc: String,
    val imperial: Boolean = false
)

object SkinRepo {

    val all: List<Skin> = listOf(
        Skin("default", "پوست پهلوانی", 0, "دست طبیعی پهلوان"),
        Skin("gol", "خطاطی گل", 120, "گل سرخ بر دست پهلوان"),
        Skin("nastaliq", "خطاطی نستعلیق", 350, "قلم نی بر دست استاد"),
        Skin("henna", "دست حنا", 260, "نقش حنای شب عروسی"),
        Skin("iran", "پرچم ایران", 180, "ایران بر دستان توست"),
        Skin("kaman", "تیر و کمان", 220, "کمان آرش کمانگیر"),
        Skin("hakhamaneshi", "سرباز هخامنشی", 250, "نشان سپاه هخامنشی"),
        Skin("damavand", "کوه دماوند", 320, "اژدهای خفته در برف"),
        Skin("bronze", "دست مفرغی", 300, "مفرغی چون سلاح جیوان"),
        Skin("mah", "ماه و ستاره", 280, "ماه نو بر دست شب"),
        Skin("gold", "دست طلایی", 400, "رنگ گنج شایگان"),
        Skin("khatam", "خاتم‌کاری", 450, "هنر دست استادان شیراز"),
        Skin("kashi", "کاشی اصفهان", 500, "کاشی آبی مسجد اصفهان"),
        Skin("anar", "انار سرخ", 240, "دانه‌های انار ایران"),
        Skin("firuze", "فیروزه‌کاری", 340, "نگین فیروزه نیشابور"),
        Skin("shir", "خورشید", 360, "نشان شیر دلیران"),
        Skin("khanjar", "خنجر زرین", 420, "خنجر زرین هخامنشی"),
        Skin("simorgh", "پر سیمرغ", 460, "بال اسطوره سیمرغ"),
        Skin("kaaveh", "درفش کاویانی", 480, "درفش کاوه آهنگر"),
        Skin("imperial", "دست شاهنشاهی", -1, "نگین لاجورد شاهان", imperial = true),
    )

    fun byId(id: String): Skin = all.firstOrNull { it.id == id } ?: all[0]

    /** نام drawable برای پوست و حالت دست، مثل hand_gol_fist */
    fun drawableName(skinId: String, gesture: com.ancientpersia.rps.game.Gesture): String =
        "hand_${skinId}_${gesture.name.lowercase()}"
}
