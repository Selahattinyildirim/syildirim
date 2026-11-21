package com.sarjdefteri.core

/** Yaklaşık kullanılabilir batarya kapasitesiyle araç ön ayarı. Kullanıcı kapasiteyi düzenleyebilir. */
data class VehiclePreset(
    val id: String,
    val brand: String,
    val model: String,
    val variant: String,
    val usableKwh: Double,
) {
    val displayName: String get() = listOf(brand, model, variant).filter { it.isNotBlank() }.joinToString(" ")
}

/** Türkiye'de yaygın elektrikli araçlar. Kapasiteler yaklaşık değerlerdir (üretici/yıl/varyanta göre değişir). */
object VehicleCatalog {
    val all: List<VehiclePreset> = listOf(
        VehiclePreset("togg-t10x-std", "Togg", "T10X", "Standart Menzil", 52.4),
        VehiclePreset("togg-t10x-long", "Togg", "T10X", "Uzun Menzil", 88.5),
        VehiclePreset("togg-t10f-std", "Togg", "T10F", "Standart Menzil", 52.4),
        VehiclePreset("togg-t10f-long", "Togg", "T10F", "Uzun Menzil", 88.5),
        VehiclePreset("tesla-my-rwd", "Tesla", "Model Y", "RWD", 60.0),
        VehiclePreset("tesla-my-lr", "Tesla", "Model Y", "Long Range", 75.0),
        VehiclePreset("tesla-m3-rwd", "Tesla", "Model 3", "RWD", 57.5),
        VehiclePreset("tesla-m3-lr", "Tesla", "Model 3", "Long Range", 75.0),
        VehiclePreset("byd-atto3", "BYD", "Atto 3", "", 60.5),
        VehiclePreset("byd-dolphin", "BYD", "Dolphin", "", 60.4),
        VehiclePreset("byd-seal", "BYD", "Seal", "", 82.5),
        VehiclePreset("byd-sealu", "BYD", "Seal U", "", 71.8),
        VehiclePreset("renault-megane", "Renault", "Megane E-Tech", "", 60.0),
        VehiclePreset("renault-zoe", "Renault", "Zoe", "", 52.0),
        VehiclePreset("renault-r5", "Renault", "5 E-Tech", "", 52.0),
        VehiclePreset("hyundai-kona", "Hyundai", "Kona Electric", "", 64.8),
        VehiclePreset("hyundai-ioniq5", "Hyundai", "IONIQ 5", "", 77.4),
        VehiclePreset("kia-ev6", "Kia", "EV6", "", 77.4),
        VehiclePreset("kia-niro", "Kia", "Niro EV", "", 64.8),
        VehiclePreset("vw-id3", "Volkswagen", "ID.3", "", 58.0),
        VehiclePreset("vw-id4", "Volkswagen", "ID.4", "", 77.0),
        VehiclePreset("mg-mg4", "MG", "MG4", "", 64.0),
        VehiclePreset("mg-zs", "MG", "ZS EV", "", 51.0),
        VehiclePreset("volvo-ex30", "Volvo", "EX30", "", 64.0),
        VehiclePreset("bmw-ix1", "BMW", "iX1", "", 64.7),
        VehiclePreset("bmw-i4", "BMW", "i4", "eDrive40", 81.3),
        VehiclePreset("mercedes-eqa", "Mercedes-Benz", "EQA", "", 66.5),
        VehiclePreset("peugeot-e2008", "Peugeot", "e-2008", "", 51.0),
        VehiclePreset("fiat-500e", "Fiat", "500e", "", 42.0),
    )

    val brands: List<String> get() = all.map { it.brand }.distinct()

    fun byId(id: String?): VehiclePreset? = all.firstOrNull { it.id == id }

    fun search(query: String): List<VehiclePreset> {
        val q = TextNorm.normalize(query)
        if (q.isEmpty()) return all
        val terms = q.split(' ').filter { it.isNotEmpty() }
        return all.filter { p -> val n = TextNorm.normalize(p.displayName); terms.all { n.contains(it) } }
    }
}

/** Türkçe karakter ve büyük/küçük harf duyarsız metin. */
object TextNorm {
    fun normalize(s: String): String = s.trim().lowercase(java.util.Locale("tr", "TR"))
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        .replace(Regex("\\s+"), " ")
}
