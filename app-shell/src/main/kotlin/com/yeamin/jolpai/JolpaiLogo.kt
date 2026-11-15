package com.yeamin.jolpai

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Jolpai's mark (shared with Jolpai Type and the launcher icon): a keyboard with the keys cut
 * out, an olive (জলপাই) as the lit key and a leaf, on a 32-unit grid. When [animate] is on it
 * pops in, then keeps a gentle loop: keys tap in a typing rhythm, the olive key is pressed, the
 * leaf sways and the mark bobs. Still when the system's animations are turned off.
 */
@Composable
fun JolpaiLogo(modifier: Modifier = Modifier, ink: Color = LogoInk, animate: Boolean = true) {
    val context = LocalContext.current
    val motion = animate && remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val board = remember { PathParser().parsePathString(BOARD).toPath().apply { fillType = PathFillType.EvenOdd } }
    val olive = remember { PathParser().parsePathString(OLIVE).toPath() }
    val leaf = remember { PathParser().parsePathString(LEAF).toPath() }

    val intro = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) intro.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    val t = if (motion) {
        rememberInfiniteTransition(label = "logo").animateFloat(
            initialValue = 0f, targetValue = LOOP_MS,
            animationSpec = infiniteRepeatable(tween(LOOP_MS.toInt(), easing = LinearEasing), RepeatMode.Restart),
            label = "t",
        ).value
    } else 0f

    Canvas(modifier) {
        val unit = size.minDimension / 32f
        val wave = sin(2 * PI * t / LOOP_MS).toFloat()
        scale(intro.value, pivot = center) {
            translate((size.width - 32 * unit) / 2, (size.height - 32 * unit) / 2) {
                scale(unit, pivot = Offset.Zero) {
                    rotate(if (motion) wave * 2f else 0f, pivot = Offset(16f, 16f)) {
                        translate(top = if (motion) -0.5f * wave else 0f) {
                            drawPath(board, ink)
                            if (motion) TAPS.forEach { (x, y, w, h, k) ->
                                val a = tapAlpha(t, k.toInt())
                                if (a > 0f) drawRoundRect(ink.copy(alpha = a), Offset(x, y), Size(w, h), CornerRadius(0.6f))
                            }
                            scale(if (motion) olivePress(t) else 1f, pivot = Offset(16f, 19f)) { drawPath(olive, ink) }
                            rotate(if (motion) -4f + 9f * wave else 0f, pivot = Offset(21.4f, 9.8f)) { drawPath(leaf, ink) }
                        }
                    }
                }
            }
        }
    }
}

/** The mark's dark ink, for drawing on the olive tile. */
val LogoInk = Color(0xFF1E2118)
/** The olive tile behind the mark (the launcher icon's background). */
val LogoTile = Color(0xFFA3B84A)

private const val LOOP_MS = 2400f

/** Each key lights for a moment, one after another (by its typing order k). */
private fun tapAlpha(t: Float, k: Int): Float {
    val p = ((t - k * 200f) % 2000f + 2000f) % 2000f / 2000f
    return when {
        p < 0.04f -> p / 0.04f * 0.9f
        p < 0.14f -> (0.14f - p) / 0.10f * 0.9f
        else -> 0f
    }
}

/** The olive key is pressed once a loop: in, a small bounce past full size, back. */
private fun olivePress(t: Float): Float {
    val p = (t - 1700f) / 500f
    return when {
        p < 0f || p > 1f -> 1f
        p < 0.3f -> 1f - 0.45f * (p / 0.3f)
        p < 0.55f -> 0.55f + 0.6f * ((p - 0.3f) / 0.25f)
        else -> 1.15f - 0.15f * ((p - 0.55f) / 0.45f)
    }
}

private const val BOARD = "M7.2 9H24.8A5.2 5.2 0 0 1 30 14.2V23.8A5.2 5.2 0 0 1 24.8 29H7.2A5.2 5.2 0 0 1 2 23.8V14.2A5.2 5.2 0 0 1 7.2 9ZM6.2 12.6H7.4A1 1 0 0 1 8.4 13.6V14.8A1 1 0 0 1 7.4 15.8H6.2A1 1 0 0 1 5.2 14.8V13.6A1 1 0 0 1 6.2 12.6ZM10.8 12.6H12A1 1 0 0 1 13 13.6V14.8A1 1 0 0 1 12 15.8H10.8A1 1 0 0 1 9.8 14.8V13.6A1 1 0 0 1 10.8 12.6ZM15.4 12.6H16.6A1 1 0 0 1 17.6 13.6V14.8A1 1 0 0 1 16.6 15.8H15.4A1 1 0 0 1 14.4 14.8V13.6A1 1 0 0 1 15.4 12.6ZM20 12.6H21.2A1 1 0 0 1 22.2 13.6V14.8A1 1 0 0 1 21.2 15.8H20A1 1 0 0 1 19 14.8V13.6A1 1 0 0 1 20 12.6ZM24.6 12.6H25.8A1 1 0 0 1 26.8 13.6V14.8A1 1 0 0 1 25.8 15.8H24.6A1 1 0 0 1 23.6 14.8V13.6A1 1 0 0 1 24.6 12.6ZM6.2 17.4H7.4A1 1 0 0 1 8.4 18.4V19.6A1 1 0 0 1 7.4 20.6H6.2A1 1 0 0 1 5.2 19.6V18.4A1 1 0 0 1 6.2 17.4ZM10.8 17.4H12A1 1 0 0 1 13 18.4V19.6A1 1 0 0 1 12 20.6H10.8A1 1 0 0 1 9.8 19.6V18.4A1 1 0 0 1 10.8 17.4ZM20 17.4H21.2A1 1 0 0 1 22.2 18.4V19.6A1 1 0 0 1 21.2 20.6H20A1 1 0 0 1 19 19.6V18.4A1 1 0 0 1 20 17.4ZM24.6 17.4H25.8A1 1 0 0 1 26.8 18.4V19.6A1 1 0 0 1 25.8 20.6H24.6A1 1 0 0 1 23.6 19.6V18.4A1 1 0 0 1 24.6 17.4ZM10.6 22.2H21.4A1 1 0 0 1 22.4 23.2V24.2A1 1 0 0 1 21.4 25.2H10.6A1 1 0 0 1 9.6 24.2V23.2A1 1 0 0 1 10.6 22.2ZM13.6 19A2.4 2.4 0 1 0 18.4 19A2.4 2.4 0 1 0 13.6 19Z"
private const val OLIVE = "M14.7 19A1.3 1.6 0 1 0 17.3 19A1.3 1.6 0 1 0 14.7 19Z"
private const val LEAF = "M21.4 9.8C21.2 5 24.6 2 29.6 1.9C29.7 6.7 26.4 9.8 21.4 9.8Z"

/** Key holes as x, y, width, height and typing order. */
private val TAPS = listOf(
    floatArrayOf(5.7f, 13.1f, 2.2f, 2.2f, 7f), floatArrayOf(10.3f, 13.1f, 2.2f, 2.2f, 0f),
    floatArrayOf(14.9f, 13.1f, 2.2f, 2.2f, 9f), floatArrayOf(19.5f, 13.1f, 2.2f, 2.2f, 2f),
    floatArrayOf(24.1f, 13.1f, 2.2f, 2.2f, 5f), floatArrayOf(5.7f, 17.9f, 2.2f, 2.2f, 4f),
    floatArrayOf(10.3f, 17.9f, 2.2f, 2.2f, 6f), floatArrayOf(19.5f, 17.9f, 2.2f, 2.2f, 1f),
    floatArrayOf(24.1f, 17.9f, 2.2f, 2.2f, 8f), floatArrayOf(10.1f, 22.7f, 11.8f, 2f, 3f),
)
