package dev.ziga.iperf3.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.ziga.iperf3.Direction
import dev.ziga.iperf3.Format
import dev.ziga.iperf3.Sample

private const val MAX_POINTS = 180

/** Throughput over time; a second line is drawn for the reverse leg of a bidir test. */
@Composable
fun ThroughputChart(
    samples: List<Sample>,
    direction: Direction,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 160.dp,
) {
    val points = samples.takeLast(MAX_POINTS)
    val forward = points.map { it.bitsPerSecond }
    val reverse = points.mapNotNull { it.reverseBitsPerSecond }.takeIf { it.size == points.size }
    val peak = ((forward + (reverse ?: emptyList())).maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val ceiling = peak * 1.15

    val forwardColor = MaterialTheme.colorScheme.primary
    val reverseColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("peak ${Format.bitrate(peak)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reverse != null) {
                Text(
                    "■ up  ■ down",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            drawGrid(gridColor)
            if (points.size < 2) return@Canvas
            drawSeries(forward, ceiling, forwardColor, fill = true)
            reverse?.let { drawSeries(it, ceiling, reverseColor, fill = false) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                points.firstOrNull()?.let { Format.seconds(it.time) } ?: "0 s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                points.lastOrNull()?.let { Format.seconds(it.time) } ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.drawGrid(color: Color) {
    repeat(5) { index ->
        val y = size.height * index / 4f
        drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
    }
}

private fun DrawScope.drawSeries(values: List<Double>, ceiling: Double, color: Color, fill: Boolean) {
    val stepX = size.width / (values.size - 1).coerceAtLeast(1)
    fun pointAt(index: Int) = Offset(
        x = index * stepX,
        y = size.height - (values[index] / ceiling).coerceIn(0.0, 1.0).toFloat() * size.height,
    )

    val line = Path().apply {
        moveTo(pointAt(0).x, pointAt(0).y)
        for (index in 1 until values.size) {
            val point = pointAt(index)
            lineTo(point.x, point.y)
        }
    }
    if (fill) {
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))),
        )
    }
    drawPath(path = line, color = color, style = Stroke(width = 3f))
}
