package com.skypass.skytrack.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glass styling shared by the app screens, matching the web portal: a light page with soft colour glows behind
 * it, and see-through cards and buttons with a glossy top highlight and a bright edge. The glows are already
 * soft, so see-through glass over them reads as frosted without a live backdrop blur (which Android only offers
 * from Android 12 and only through an extra library).
 */
object Glass {
    val PageColor = Color(0xFFFBFBFE)
    private val ShadowTint = Color(0xFF1F2687)
    private val EdgeGrey = Color(0xFF94A3B8)

    /** See-through white, lighter towards the bottom right */
    val Fill = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.34f)),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    /** Slightly denser glass for text inputs, so typing stays easy to read */
    val InputFill = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.55f)),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    /** Bright top-left edge fading to a faint grey bottom-right edge */
    val Edge = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.98f), Color.White.copy(alpha = 0.45f), EdgeGrey.copy(alpha = 0.28f)),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    /** Glossy highlight across the top of a card or button */
    private val Sheen = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.55f),
        0.45f to Color.White.copy(alpha = 0.08f),
        1f to Color.Transparent
    )

    /**
     * A soft shadow drawn only outside the shape. A normal elevation shadow is painted under the whole card and
     * would show through the see-through glass as a grey band.
     */
    fun Modifier.outerShadow(
        shape: Shape,
        blur: Dp = 18.dp,
        offsetY: Dp = 6.dp,
        color: Color = ShadowTint.copy(alpha = 0.10f)
    ): Modifier = this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        fun Path.add(o: Outline) = when (o) {
            is Outline.Rectangle -> addRect(o.rect)
            is Outline.Rounded -> addRoundRect(o.roundRect)
            is Outline.Generic -> addPath(o.path)
        }
        val path = Path().apply { add(outline) }
        val shadowPath = Path().apply { add(outline); translate(Offset(0f, offsetY.toPx())) }
        clipPath(path, clipOp = ClipOp.Difference) {
            drawIntoCanvas { canvas ->
                val paint = Paint()
                paint.asFrameworkPaint().apply {
                    isAntiAlias = true
                    this.color = color.toArgb()
                    maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawPath(shadowPath, paint)
            }
        }
    }

    /** Frosted glass: see-through fill, glossy highlight, bright edge and an outside-only soft shadow */
    fun Modifier.glass(
        shape: Shape,
        elevation: Dp = 8.dp,
        border: BorderStroke? = null,
        fill: Brush = Fill
    ): Modifier = this
        .then(if (elevation > 0.dp) Modifier.outerShadow(shape, blur = elevation * 2, offsetY = elevation * 0.6f) else Modifier)
        .clip(shape)
        .background(fill)
        .drawWithContent {
            drawRect(Sheen, size = size.copy(height = size.height.coerceAtMost(72.dp.toPx())))
            drawContent()
        }
        .then(if (border != null) Modifier.border(border, shape) else Modifier.border(1.dp, Edge, shape))

    /**
     * Tinted glass for coloured (e.g. red) buttons: the colour stays, with the glossy highlight and bright edge.
     */
    fun Modifier.tintedGlass(shape: Shape, color: Color, elevation: Dp = 6.dp): Modifier = this
        .then(
            if (elevation > 0.dp) Modifier.outerShadow(shape, blur = elevation * 2, offsetY = elevation * 0.6f, color = color.copy(alpha = 0.28f))
            else Modifier
        )
        .clip(shape)
        .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.86f), color.copy(alpha = 0.96f))))
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.32f), 0.5f to Color.White.copy(alpha = 0.04f), 1f to Color.Transparent),
                size = size.copy(height = size.height * 0.55f)
            )
        }
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))),
            shape
        )
}

/** The page background: light, with soft rose, violet, amber and sky glows (as on the web portal) */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawBehind {
            drawRect(Glass.PageColor)
            fun glow(color: Color, x: Float, y: Float, radius: Float) = drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    center = Offset(size.width * x, size.height * y),
                    radius = size.width * radius
                ),
                radius = size.width * radius,
                center = Offset(size.width * x, size.height * y)
            )
            glow(Color(0xFFFDA4AF).copy(alpha = 0.45f), 0.10f, 0.04f, 0.90f) // rose
            glow(Color(0xFFC4B5FD).copy(alpha = 0.45f), 0.98f, 0.24f, 0.85f) // violet
            glow(Color(0xFFFDE68A).copy(alpha = 0.50f), 0.35f, 0.56f, 0.90f) // amber
            glow(Color(0xFF7DD3FC).copy(alpha = 0.40f), 0.04f, 0.90f, 0.90f) // sky
            glow(Color(0xFFF9A8D4).copy(alpha = 0.30f), 0.95f, 0.82f, 0.75f) // pink
        }
    )
}

/** A frosted glass card or button; a drop-in for the white `Surface` cards */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 8.dp,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    with(Glass) {
        Box(
            modifier = modifier
                .glass(shape, elevation, border)
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier),
            propagateMinConstraints = true
        ) {
            CompositionLocalProvider(LocalContentColor provides Color(0xFF1E293B)) { content() }
        }
    }
}
