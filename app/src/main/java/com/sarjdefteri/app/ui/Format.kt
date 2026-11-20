package com.sarjdefteri.app.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    val tr: Locale = Locale("tr", "TR")
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun nf(min: Int, max: Int) = NumberFormat.getNumberInstance(tr).apply {
        minimumFractionDigits = min; maximumFractionDigits = max
    }

    fun tl(v: Double?): String = v?.let { nf(2, 2).format(it) + " ₺" } ?: "—"
    fun tlShort(v: Double?): String = v?.let { nf(0, 0).format(it) + " ₺" } ?: "—"
    fun kwh(v: Double?): String = v?.let { nf(0, 1).format(it) + " kWh" } ?: "—"
    fun km(v: Double?): String = v?.let { nf(0, 0).format(it) + " km" } ?: "—"
    fun pct(v: Double?): String = v?.let { "%" + nf(0, 0).format(it) } ?: "—"
    fun unit(v: Double?): String = v?.let { nf(2, 2).format(it) + " ₺/kWh" } ?: "—"
    fun perKm(v: Double?): String = v?.let { nf(2, 2).format(it) + " ₺/km" } ?: "—"
    fun consumption(v: Double?): String = v?.let { nf(1, 1).format(it) + " kWh/100km" } ?: "—"
    fun num(v: Double?, maxDigits: Int = 2): String = v?.let { nf(0, maxDigits).format(it) } ?: "—"

    /** Form alanları için: gruplama yok, virgüllü ondalık. */
    fun input(v: Double?): String = v?.let {
        NumberFormat.getNumberInstance(tr).apply { isGroupingUsed = false; maximumFractionDigits = 4 }.format(it)
    } ?: ""

    /** "1.234,5" / "1234.5" / "1234,5" girişlerini çözer. */
    fun parse(s: String): Double? {
        val t = s.trim().replace(" ", "").replace("₺", "").replace("%", "")
        if (t.isEmpty()) return null
        val normalized = when {
            t.contains(',') && t.contains('.') ->
                if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            t.contains(',') -> t.replace(',', '.')
            else -> t
        }
        return normalized.toDoubleOrNull()
    }

    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", tr)
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", tr)
    private val time = DateTimeFormatter.ofPattern("HH:mm", tr)

    fun dateTime(millis: Long?): String = millis?.let { Instant.ofEpochMilli(it).atZone(zone).format(dateTime) } ?: "—"
    fun date(millis: Long?): String = millis?.let { Instant.ofEpochMilli(it).atZone(zone).format(date) } ?: "—"
    fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(time)
    fun month(m: YearMonth): String = m.month.getDisplayName(TextStyle.SHORT, tr).replaceFirstChar { it.uppercase(tr) }

    fun relativeDays(millis: Long?): String {
        millis ?: return "—"
        val d = LocalDate.now(zone).toEpochDay() - Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
        return when {
            d <= 0L -> "Bugün"
            d == 1L -> "Dün"
            else -> "$d gün önce"
        }
    }
}
