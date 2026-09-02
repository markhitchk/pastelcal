package com.pastelcal.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Very subtle decorative frog pattern used behind PastelCal's main app content.
 * The frogs are drawn in Compose instead of using a large bitmap asset so the
 * pattern stays crisp at every screen size and adds almost no package weight.
 */
@Composable
internal fun FrogPatternBackground(
    modifier: Modifier = Modifier,
    dark: Boolean
) {
    val frogColor = if (dark) Color(0xFFA5CF82) else Color(0xFF83B662)
    val detailColor = if (dark) Color(0xFFDAF1C9) else Color(0xFF426735)
    val frogAlpha = if (dark) 0.060f else 0.050f
    val detailAlpha = if (dark) 0.070f else 0.060f

    Canvas(modifier = modifier) {
        val cellWidth = 92.dp.toPx()
        val cellHeight = 78.dp.toPx()
        val small = 18.dp.toPx()
        val medium = 22.dp.toPx()
        val rotations = floatArrayOf(-8f, 4f, 9f, -4f, 6f, -6f)

        var row = 0
        var y = -cellHeight / 2f
        while (y < size.height + cellHeight) {
            val rowOffset = if (row % 2 == 0) 18.dp.toPx() else 61.dp.toPx()
            var column = 0
            var x = -cellWidth + rowOffset

            while (x < size.width + cellWidth) {
                val index = row * 7 + column
                val frogSize = if (index % 5 == 0) medium else small
                val verticalJitter = when (index % 4) {
                    0 -> -7.dp.toPx()
                    1 -> 4.dp.toPx()
                    2 -> 9.dp.toPx()
                    else -> 0f
                }
                val center = Offset(x, y + verticalJitter)
                val angle = rotations[Math.floorMod(index, rotations.size)]

                rotate(degrees = angle, pivot = center) {
                    drawTinyFrog(
                        center = center,
                        frogSize = frogSize,
                        fill = frogColor.copy(alpha = frogAlpha),
                        detail = detailColor.copy(alpha = detailAlpha)
                    )
                }

                x += cellWidth
                column++
            }

            y += cellHeight
            row++
        }
    }
}

private fun DrawScope.drawTinyFrog(
    center: Offset,
    frogSize: Float,
    fill: Color,
    detail: Color
) {
    val faceWidth = frogSize
    val faceHeight = frogSize * 0.67f
    val eyeRadius = frogSize * 0.17f
    val eyeY = center.y - faceHeight * 0.45f
    val eyeOffsetX = faceWidth * 0.31f
    val faceTop = center.y - faceHeight * 0.34f

    // Slight low-opacity offset gives the frog the soft embossed feel of the reference.
    val shadow = Color.Black.copy(alpha = fill.alpha * 0.22f)
    val shadowOffset = 0.75.dp.toPx()
    drawCircle(shadow, eyeRadius, Offset(center.x - eyeOffsetX, eyeY + shadowOffset))
    drawCircle(shadow, eyeRadius, Offset(center.x + eyeOffsetX, eyeY + shadowOffset))
    drawOval(
        color = shadow,
        topLeft = Offset(center.x - faceWidth / 2f, faceTop + shadowOffset),
        size = Size(faceWidth, faceHeight)
    )

    drawCircle(fill, eyeRadius, Offset(center.x - eyeOffsetX, eyeY))
    drawCircle(fill, eyeRadius, Offset(center.x + eyeOffsetX, eyeY))
    drawOval(
        color = fill,
        topLeft = Offset(center.x - faceWidth / 2f, faceTop),
        size = Size(faceWidth, faceHeight)
    )

    val detailStroke = (frogSize * 0.045f).coerceAtLeast(0.65.dp.toPx())
    drawCircle(
        color = detail,
        radius = eyeRadius * 0.56f,
        center = Offset(center.x - eyeOffsetX, eyeY),
        style = Stroke(width = detailStroke)
    )
    drawCircle(
        color = detail,
        radius = eyeRadius * 0.56f,
        center = Offset(center.x + eyeOffsetX, eyeY),
        style = Stroke(width = detailStroke)
    )

    val noseY = center.y - faceHeight * 0.01f
    val noseRadius = frogSize * 0.025f
    drawCircle(detail, noseRadius, Offset(center.x - frogSize * 0.075f, noseY))
    drawCircle(detail, noseRadius, Offset(center.x + frogSize * 0.075f, noseY))

    drawArc(
        color = detail,
        startAngle = 18f,
        sweepAngle = 144f,
        useCenter = false,
        topLeft = Offset(
            center.x - faceWidth * 0.28f,
            center.y + faceHeight * 0.01f
        ),
        size = Size(faceWidth * 0.56f, faceHeight * 0.34f),
        style = Stroke(width = detailStroke, cap = StrokeCap.Round)
    )
}
