package com.example.slagalica.data

data class RegionInfo(
    val naziv: String,
    val emoji: String,
    val minLat: Double, val maxLat: Double,
    val minLng: Double, val maxLng: Double
) {
    val centarLat: Double get() = (minLat + maxLat) / 2
    val centarLng: Double get() = (minLng + maxLng) / 2
}

object Regioni {

    val SVI = listOf(
        RegionInfo("Vojvodina", "🌾", 45.05, 46.15, 18.85, 21.55),
        RegionInfo("Beograd", "🏙️", 44.65, 44.95, 20.20, 20.65),
        RegionInfo("Šumadija i Zapadna Srbija", "⛰️", 43.30, 44.55, 19.20, 20.95),
        RegionInfo("Južna i Istočna Srbija", "🍇", 42.35, 44.20, 21.05, 23.00)
    )

    const val SRBIJA_LAT = 44.0
    const val SRBIJA_LNG = 20.9
    const val POCETNI_ZOOM = 7.0

    fun zaNaziv(naziv: String): RegionInfo? = SVI.firstOrNull { it.naziv == naziv }

    fun tackaZa(uid: String, region: RegionInfo): Pair<Double, Double> {
        val h = uid.hashCode()
        val fx = (h and 0xFFFF) / 65535.0
        val fy = ((h ushr 16) and 0xFFFF) / 65535.0
        val lat = region.minLat + fx * (region.maxLat - region.minLat)
        val lng = region.minLng + fy * (region.maxLng - region.minLng)
        return lat to lng
    }
}
