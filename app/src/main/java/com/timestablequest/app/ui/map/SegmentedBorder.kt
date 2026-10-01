package com.timestablequest.app.ui.map

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timestablequest.app.ui.theme.Atlas

enum class Segment { EMPTY, CORRECT, CONFIDENT }

/**
 * Draws a border split into one segment per fact (12 for a row tile). Empty segments are thin and
 * grey, correct-at-least-once segments use the row accent, confident segments are thicker navy.
 * The same information is always given in text next to the tile; colour is never the only cue.
 */
fun Modifier.segmentedBorder(
    segments: List<Segment>,
    accent: Color,
    cornerRadius: Dp = 16.dp,
    width: Dp = 5.dp,
): Modifier = this.drawWithContent {
    drawContent()
    if (segments.isEmpty()) return@drawWithContent
    val stroke = width.toPx()
    val inset = stroke / 2f
    val radius = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(inset, inset, size.width - inset, size.height - inset),
                cornerRadius = CornerRadius(radius, radius),
            ),
        )
    }
    val measure = PathMeasure()
    measure.setPath(path, false)
    val length = measure.length
    if (length <= 0f) return@drawWithContent
    val step = length / segments.size
    val gap = (step * 0.12f).coerceAtMost(6.dp.toPx())
    segments.forEachIndexed { i, seg ->
        val piece = Path()
        val ok = measure.getSegment(i * step + gap / 2f, (i + 1) * step - gap / 2f, piece, true)
        if (!ok) return@forEachIndexed
        val (color, w) = when (seg) {
            Segment.EMPTY -> Atlas.SegmentEmpty to stroke * 0.55f
            Segment.CORRECT -> accent to stroke
            Segment.CONFIDENT -> Atlas.Navy to stroke * 1.15f
        }
        drawPath(piece, color = color, style = Stroke(width = w, cap = StrokeCap.Butt))
    }
}
