package cl.reabill.monitor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.reabill.monitor.ui.fmtInt
import cl.reabill.monitor.ui.theme.Acta
import cl.reabill.monitor.ui.theme.ActaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Acta.Hairline, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

@Composable
fun ActivityRule(busy: Boolean, fault: Boolean, modifier: Modifier = Modifier) {
    val base = if (fault) Acta.Error else Acta.Ink
    if (busy && !fault) {
        val x by rememberInfiniteTransition(label = "actividad").animateFloat(
            initialValue = -0.35f, targetValue = 1f, label = "x",
            animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart)
        )
        Canvas(modifier.fillMaxWidth().height(3.dp).clipToBounds()) {
            drawRect(base)
            drawRect(Acta.Accent, Offset(size.width * x, 0f), Size(size.width * 0.35f, size.height))
        }
    } else {
        Box(modifier.fillMaxWidth().height(3.dp).background(base))
    }
}

enum class MarkKind { Off, Run, Ok, Error }

@Composable
fun StatusMark(kind: MarkKind, modifier: Modifier = Modifier) {
    val color = when (kind) {
        MarkKind.Off -> Acta.Hairline
        MarkKind.Run -> Acta.Ink
        MarkKind.Ok -> Acta.Accent
        MarkKind.Error -> Acta.Error
    }
    val pulse = if (kind == MarkKind.Run) {
        val a by rememberInfiniteTransition(label = "latido").animateFloat(
            1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a"
        )
        a
    } else 1f
    Box(
        modifier.size(8.dp).alpha(pulse).background(
            color, if (kind == MarkKind.Error) RectangleShape else CircleShape
        )
    )
}

@Composable
fun ResolveText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    if (text.isBlank() || text == "—") {
        Text("—", style = style, color = Acta.Hairline, modifier = modifier)
        return
    }
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            key(text, i) {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(i * 30L); shown = true }
                val a by animateFloatAsState(
                    if (shown) 1f else 0f, tween(280, easing = FastOutSlowInEasing), label = "car"
                )
                Text(
                    ch.toString(), style = style,
                    modifier = Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 8f }
                )
            }
        }
    }
}

@Composable
fun CountText(
    target: Int, from: Int?, style: TextStyle,
    modifier: Modifier = Modifier, textAlign: TextAlign? = null
) {
    val anim = remember { Animatable((from ?: target).toFloat()) }
    LaunchedEffect(target) {
        anim.animateTo(target.toFloat(), tween(700, easing = FastOutSlowInEasing))
    }
    Text(fmtInt(anim.value.roundToInt()), style = style, modifier = modifier, textAlign = textAlign)
}

@Composable
fun RateScale(rate: Double?, modifier: Modifier = Modifier) {
    val target = if (rate == null) 0f
    else ((rate - Acta.RATE_MIN) / (100.0 - Acta.RATE_MIN)).toFloat().coerceIn(0f, 1f)
    val frac by animateFloatAsState(target, tween(800, easing = FastOutSlowInEasing), label = "tasa")
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(Acta.Hairline)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Acta.Accent))
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("80", "85", "90", "95", "100 %").forEach { Text(it, style = ActaType.Tiny) }
        }
    }
}

@Composable
fun LinkText(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    color: Color = Acta.Accent, enabled: Boolean = true
) {
    Text(
        text,
        style = ActaType.Link.copy(color = color, textDecoration = TextDecoration.Underline),
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp)
    )
}

private val ButtonShape = RoundedCornerShape(12.dp)

@Composable
fun ActaButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    filled: Boolean = false, enabled: Boolean = true, danger: Boolean = false, height: Dp = 52.dp,
    textSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    val edge = if (danger) Acta.Error else Acta.Ink
    Box(
        modifier.heightIn(min = height).alpha(if (enabled) 1f else 0.4f)
            .clip(ButtonShape)
            .background(if (filled) Acta.Ink else Acta.Paper)
            .border(1.dp, edge, ButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, style = ActaType.Button.copy(
                fontSize = textSize,
                color = when { filled -> Acta.Paper; danger -> Acta.Error; else -> Acta.Ink }
            ),
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ActaChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.heightIn(min = 40.dp).clip(shape)
            .background(if (selected) Acta.Ink else Acta.Paper)
            .border(1.dp, if (selected) Acta.Ink else Acta.Hairline, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = ActaType.Button.copy(fontSize = 13.sp, color = if (selected) Acta.Paper else Acta.Ink))
    }
}

@Composable
fun GearButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = Acta.Ink2
    Box(
        modifier.size(44.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Preferencias", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.width / 2
            drawCircle(color, radius = r * 0.62f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.32f))
            for (i in 0 until 8) {
                rotate(i * 45f, c) {
                    drawRect(color, Offset(c.x - r * 0.14f, c.y - r), Size(r * 0.28f, r * 0.34f))
                }
            }
        }
    }
}

@Composable
fun HoldButton(
    text: String, hint: String, onArmed: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.heightIn(min = 52.dp).alpha(if (enabled) 1f else 0.4f)
            .clip(ButtonShape)
            .border(1.dp, Acta.Ink, ButtonShape)
            .drawBehind {
                drawRect(
                    Acta.Accent.copy(alpha = 0.22f),
                    size = Size(size.width * progress.value, size.height)
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.animateTo(1f, tween(900, easing = LinearEasing))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onArmed()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    if (job.isActive) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(180)) }
                    }
                })
            }
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
                onClick(label = text) { if (enabled) onArmed(); enabled }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = ActaType.Button)
            Text(hint, style = ActaType.Label.copy(fontSize = 10.sp))
        }
    }
}
