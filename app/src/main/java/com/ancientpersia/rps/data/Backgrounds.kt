package com.ancientpersia.rps.data

/**
 * پس‌زمینه‌های فروشگاه؛ پس‌زمینه خریداری‌شده در تمام صفحات بازی اعمال می‌شود.
 */
data class BackgroundItem(
    val id: String,
    val name: String,
    val price: Int,
    val desc: String
)

object BackgroundRepo {

    val all: List<BackgroundItem> = listOf(
        BackgroundItem("persepolis", "تخت جمشید", 0, "شکوه پارسه"),
        BackgroundItem("azadi", "برج آزادی", 200, "دروازه بلند تهران"),
        BackgroundItem("desert", "کویر و کاروان", 300, "شب‌های کویر"),
        BackgroundItem("yazd", "بادگیرهای یزد", 350, "شهر بادگیرها"),
        BackgroundItem("hafezieh", "حافظیه شیراز", 380, "سروهای شب حافظ"),
        BackgroundItem("persepolis_night", "پارسه در شب", 400, "مشعل‌های شبانه"),
        BackgroundItem("khajoo", "پل خواجو", 420, "آوای شب اصفهان"),
        BackgroundItem("ziggurat", "چغازنبیل", 450, "معبد عجیب ایلام"),
        BackgroundItem("naqshjahan", "میدان نقش جهان", 500, "گنبد فیروزه‌ای اصفهان"),
        BackgroundItem("pasargad", "آرامگاه کوروش", 800, "گران‌بهای آرامگاه شاهان"),
    )

    fun byId(id: String): BackgroundItem = all.firstOrNull { it.id == id } ?: all[0]

    fun drawableName(id: String): String = "bg_$id"

    fun thumbName(id: String): String = "bg_${id}_thumb"
}
