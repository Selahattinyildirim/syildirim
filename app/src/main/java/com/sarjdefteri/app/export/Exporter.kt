package com.sarjdefteri.app.export

import com.sarjdefteri.core.UsageCalculator
import com.sarjdefteri.app.data.db.ChargeSessionEntity
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Şarj kayıtlarını CSV (Türkçe Excel uyumlu: ; ayraç, virgüllü ondalık, UTF-8 BOM) ve XLSX olarak yazar. */
class Exporter(private val zone: ZoneId = ZoneId.systemDefault()) {

    private sealed interface Cell {
        data class Text(val v: String?) : Cell
        data class Num(val v: Double?) : Cell
        data class Date(val millis: Long?) : Cell
    }

    private val headers = listOf(
        "Tarih", "Bitiş", "İstasyon", "Konum", "Toplam Tutar (TL)", "Enerji (kWh)", "Birim Fiyat (TL/kWh)",
        "Ödenen Birim (TL/kWh)", "İndirim (%)", "İndirim (TL)", "Kampanya", "Banka", "Ödeme Yöntemi",
        "Km Öncesi", "Km Sonrası", "Batarya Öncesi (%)", "Batarya Sonrası (%)",
        "Önceki Şarjdan Km", "Tüketim (kWh/100km)", "Km Başına (TL)", "Tüketimden Hariç", "Notlar", "Kaynak",
    )

    private fun rows(sessions: List<ChargeSessionEntity>): List<List<Cell>> {
        val sorted = sessions.sortedBy { it.startedAt }
        val segs = UsageCalculator.segments(sorted.map { it.toRecord() }).associateBy { it.chargeId }
        return sorted.map { s ->
            val seg = segs[s.id]
            val rec = s.toRecord()
            listOf(
                Cell.Date(s.startedAt), Cell.Date(s.endedAt), Cell.Text(s.station), Cell.Text(s.location),
                Cell.Num(rec.effectiveCost), Cell.Num(s.energyKwh), Cell.Num(s.unitPrice),
                Cell.Num(rec.effectiveCost?.let { c -> s.energyKwh?.takeIf { it > 0 }?.let { c / it } }),
                Cell.Num(s.discountRate), Cell.Num(s.discountAmount), Cell.Text(s.campaign), Cell.Text(s.bank),
                Cell.Text(s.paymentMethod), Cell.Num(s.odometerBefore), Cell.Num(s.odometerAfter),
                Cell.Num(s.batteryBefore), Cell.Num(s.batteryAfter), Cell.Num(seg?.distanceKm),
                Cell.Num(seg?.consumptionPer100), Cell.Num(seg?.costPerKm),
                Cell.Text(if (s.excludeFromConsumption) "Evet" else "Hayır"), Cell.Text(s.notes), Cell.Text(s.source.name),
            )
        }
    }

    fun writeCsv(sessions: List<ChargeSessionEntity>, out: OutputStream) {
        val fmt = DecimalFormat("0.####", DecimalFormatSymbols(Locale("tr", "TR")))
        val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        fun esc(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val sb = StringBuilder("﻿")
        sb.append(headers.joinToString(";") { esc(it) }).append("\r\n")
        rows(sessions).forEach { row ->
            sb.append(row.joinToString(";") { c ->
                when (c) {
                    is Cell.Text -> esc(c.v ?: "")
                    is Cell.Num -> c.v?.let { fmt.format(it) } ?: ""
                    is Cell.Date -> c.millis?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).format(dateFmt) } ?: ""
                }
            }).append("\r\n")
        }
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    fun writeXlsx(sessions: List<ChargeSessionEntity>, out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
        entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""")
        entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
        entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Şarjlar" sheetId="1" r:id="rId1"/></sheets>
</workbook>""")
        entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")
        entry("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2"><numFmt numFmtId="164" formatCode="dd.mm.yyyy hh:mm"/><numFmt numFmtId="165" formatCode="#,##0.00"/></numFmts>
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="4">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
</cellXfs>
</styleSheet>""")

        val sheet = StringBuilder()
        sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="2" width="17" customWidth="1"/><col min="3" max="4" width="26" customWidth="1"/><col min="5" max="23" width="14" customWidth="1"/></cols><sheetData>""")
        fun ref(col: Int, row: Int): String {
            var c = col; var s = ""
            while (c >= 0) { s = ('A' + c % 26) + s; c = c / 26 - 1 }
            return "$s$row"
        }
        sheet.append("<row r=\"1\">")
        headers.forEachIndexed { i, h -> sheet.append("<c r=\"${ref(i, 1)}\" t=\"inlineStr\" s=\"1\"><is><t>${xml(h)}</t></is></c>") }
        sheet.append("</row>")
        rows(sessions).forEachIndexed { ri, row ->
            val r = ri + 2
            sheet.append("<row r=\"$r\">")
            row.forEachIndexed { ci, c ->
                when (c) {
                    is Cell.Text -> c.v?.let { sheet.append("<c r=\"${ref(ci, r)}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(it)}</t></is></c>") }
                    is Cell.Num -> c.v?.takeIf { !it.isNaN() && !it.isInfinite() }?.let { sheet.append("<c r=\"${ref(ci, r)}\" s=\"3\"><v>$it</v></c>") }
                    is Cell.Date -> c.millis?.let { sheet.append("<c r=\"${ref(ci, r)}\" s=\"2\"><v>${excelSerial(it)}</v></c>") }
                }
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")
        entry("xl/worksheets/sheet1.xml", sheet.toString())
        zip.finish()
        out.flush()
    }

    private fun excelSerial(millis: Long): Double {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        val base = LocalDateTime.of(1899, 12, 30, 0, 0)
        return ChronoUnit.SECONDS.between(base, ldt) / 86_400.0
    }

    private fun xml(s: String) = buildString {
        s.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;")
                else -> if (ch.code < 0x20 && ch != '\n' && ch != '\t') append(' ') else append(ch)
            }
        }
    }
}
