package com.syildirim.teslacharge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChartPoint(val label: String, val value: Double?)

/** Çevrimdışı, bağımlılıksız çubuk grafik. Son çubuk vurgulanır. */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    color: Color,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Int = 160,
) {
    val measurer = rememberTextMeasurer()
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = onVariant)
    val max = points.maxOfOrNull { it.value ?: 0.0 }?.takeIf { it > 0 } ?: 1.0
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val bottomPad = 18.dp.toPx()
        val topPad = 16.dp.toPx()
        val chartH = size.height - bottomPad - topPad
        val n = points.size.coerceAtLeast(1)
        val slot = size.width / n
        val barW = (slot * 0.56f).coerceAtMost(28.dp.toPx())
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = topPad + chartH * (1 - f)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
                pathEffect = if (f == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        points.forEachIndexed { i, p ->
            val cx = slot * i + slot / 2
            val v = p.value ?: 0.0
            val h = (v / max * chartH).toFloat()
            val isLast = i == points.lastIndex
            if (v > 0) {
                drawRoundRect(
                    color = if (isLast) color else color.copy(alpha = 0.55f),
                    topLeft = Offset(cx - barW / 2, topPad + chartH - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            if (isLast && v > 0) {
                val t = measurer.measure(valueLabel(v), TextStyle(fontSize = 10.sp, color = color))
                drawText(t, topLeft = Offset((cx - t.size.width / 2).coerceIn(0f, size.width - t.size.width), topPad + chartH - h - t.size.height - 2))
            }
            if (n <= 12 || i % 2 == 0) {
                val lt = measurer.measure(p.label, labelStyle)
                drawText(lt, topLeft = Offset(cx - lt.size.width / 2, size.height - lt.size.height))
            }
        }
        val mt = measurer.measure(valueLabel(max), labelStyle)
        drawText(mt, topLeft = Offset(0f, 0f))
    }
}

/** Çizgi grafik (ör. ortalama birim fiyat değişimi). Boş değerler atlanır. */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    color: Color,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Int = 160,
) {
    val measurer = rememberTextMeasurer()
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val labelStyle = TextStyle(fontSize = 10.sp, color = onVariant)
    val values = points.mapNotNull { it.value }
    val maxV = values.maxOrNull() ?: 1.0
    val minV = values.minOrNull() ?: 0.0
    val range = (maxV - minV).takeIf { it > 0.0001 } ?: (maxV.coerceAtLeast(1.0) * 0.2)
    val lo = minV - range * 0.15
    val hi = maxV + range * 0.15
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val bottomPad = 18.dp.toPx()
        val topPad = 16.dp.toPx()
        val chartH = size.height - bottomPad - topPad
        val n = points.size.coerceAtLeast(1)
        val slot = size.width / n
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = topPad + chartH * (1 - f)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        val coords = points.mapIndexedNotNull { i, p ->
            p.value?.let { Offset(slot * i + slot / 2, topPad + chartH * (1 - ((it - lo) / (hi - lo)).toFloat())) }
        }
        if (coords.size >= 2) {
            val path = Path().apply { moveTo(coords[0].x, coords[0].y); coords.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        coords.forEachIndexed { idx, c ->
            drawCircle(surface, radius = 4.5.dp.toPx(), center = c)
            drawCircle(color, radius = 3.dp.toPx(), center = c)
            if (idx == coords.lastIndex) {
                val v = points.mapNotNull { it.value }.last()
                val t = measurer.measure(valueLabel(v), TextStyle(fontSize = 10.sp, color = color))
                drawText(t, topLeft = Offset((c.x - t.size.width / 2).coerceIn(0f, size.width - t.size.width), (c.y - t.size.height - 6).coerceAtLeast(0f)))
            }
        }
        points.forEachIndexed { i, p ->
            if (n <= 12 || i % 2 == 0) {
                val lt = measurer.measure(p.label, labelStyle)
                drawText(lt, topLeft = Offset(slot * i + slot / 2 - lt.size.width / 2, size.height - lt.size.height))
            }
        }
    }
}

/** Yatay çubuk listesi (istasyonlara göre maliyet). */
@Composable
fun HorizontalBars(
    items: List<Pair<String, Double>>,
    color: Color,
    valueLabel: (Double) -> String,
    secondary: (Int) -> String? = { null },
) {
    val max = items.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    Column {
        items.forEachIndexed { i, (label, v) ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(valueLabel(v), style = MaterialTheme.typography.bodyMedium)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((v / max).toFloat().coerceIn(0.02f, 1f)).background(color))
                }
                secondary(i)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
