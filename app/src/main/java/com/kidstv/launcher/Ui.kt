package com.kidstv.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0x99EBEBF5)
val Glass = Color(0x24FFFFFF)
val GlassFocused = Color(0xFFF5F5F7)

fun parseColor(hex: String, fallback: Color = Color(0xFF5B8DEF)): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

/** Dark backdrop with soft colour glows, drawn as gradients so it costs no blur pass on the box's GPU. */
@Composable
fun Backdrop(tint: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF12131C), Color(0xFF05060A))))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.05f),
                        radius = size.width * 0.6f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF7D5CFF).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.9f),
                        radius = size.width * 0.55f,
                    )
                )
            },
        content = content,
    )
}

/** A focusable surface that lifts and grows when the remote lands on it. */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    focusedScale: Float = 1.1f,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) focusedScale else 1f,
        spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "focus-scale",
    )
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused) 28f else 0f
                this.shape = shape
                clip = true
            }
            .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), shape) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(focused) }
}

/** Pill button in the Apple TV style: translucent at rest, solid white when focused. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    FocusCard(onClick, modifier, RoundedCornerShape(50), 1.06f, focusRequester) { focused ->
        Box(
            Modifier
                .background(if (focused) GlassFocused else Glass)
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
            Text(
                text,
                color = if (focused) Color(0xFF1C1C1E) else TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun Avatar(name: String, color: Color, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Numeric keypad driven by the D-pad or the remote's number keys.
 * [onSubmit] returns an error message to show, or null when the PIN was accepted.
 */
@Composable
fun PinPad(title: String, onSubmit: (String) -> String?) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun press(key: String) {
        error = null
        when (key) {
            "⌫" -> pin = pin.dropLast(1)
            "OK" -> if (pin.isNotEmpty()) {
                error = onSubmit(pin)
                pin = ""
            }
            else -> if (pin.length < 8) pin += key
        }
    }

    Column(
        Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val code = e.key.nativeKeyCode
            val digit = when (code) {
                in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> code - android.view.KeyEvent.KEYCODE_0
                in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> code - android.view.KeyEvent.KEYCODE_NUMPAD_0
                else -> -1
            }
            when {
                digit >= 0 -> { press(digit.toString()); true }
                e.key == Key.Backspace -> { press("⌫"); true }
                else -> false
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) TextPrimary else Glass)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(error ?: " ", color = Color(0xFFFF6961), fontSize = 15.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "⌫", "OK").forEachIndexed { i, key ->
                FocusCard(
                    onClick = { press(key) },
                    modifier = Modifier.size(if (key == "OK") 72.dp else 56.dp, 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    focusedScale = 1.12f,
                    focusRequester = if (i == 0) first else null,
                ) { focused ->
                    Box(
                        Modifier.fillMaxSize().background(if (focused) GlassFocused else Glass),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            key,
                            color = if (focused) Color(0xFF1C1C1E) else TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
