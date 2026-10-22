package com.shawnrodis.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * True when the hosting surface is a Desktop Mode window. ToolScreen then
 * renders only its scrollable content (no Scaffold/TopAppBar): in the source
 * the app header is hidden in desktop mode and the WINDOW titlebar takes
 * its place. Set by the Desktop shell around window bodies.
 */
val LocalInDesktopWindow = compositionLocalOf { false }

/**
 * Screen shell — v1.0.1 (defect #4c): NO Material TopAppBar any more. The
 * HTML has none: the global #app-header (native GlobalHeader) sits above
 * every tab and the Home FAB is the way back, exactly as on the web. This
 * host is now just the scrollable content column in BOTH shells; inside a
 * Desktop Mode window the window titlebar is the frame instead (the
 * source hides #app-header in desktop mode too). [title]/[onBack] are
 * kept in the signature for call-site compatibility; [onBack] is no
 * longer wired to a back arrow.
 */
@Composable
fun ToolScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = scrollContentBottomPadding())
            .verticalScroll(rememberScrollState()),
        content = content
    )
}

/**
 * v1.0.2 (defect D) — bottom clearance for scrollable tool/home content
 * in the MOBILE shell: the floating Home button (52dp + its 16dp
 * Scaffold margin) overlays the bottom-end of the content, and in
 * v1.0.1 the last card could not scroll clear of it (Currency's
 * EXCHANGE RATE status text ran underneath the FAB). The mobile value
 * is FAB height + margin + a breathing gap; inside a Desktop Mode
 * window there is no FAB, so the plain 8dp stands. Padding sits OUTSIDE
 * the scroll modifier (as ToolScreen already had it), so the scroll
 * viewport itself ends above the FAB and the last card can always
 * scroll fully into the clear.
 */
internal const val FAB_CLEARANCE_DP = 88f

@Composable
fun scrollContentBottomPadding(): Dp =
    if (LocalInDesktopWindow.current) 8.dp else FAB_CLEARANCE_DP.dp

/** PCM tone synthesis for toolboxBeep — a tiny Web Audio oscillator port. */
object SynthTone {
    private const val SAMPLE_RATE = 44100

    fun play(freq: Int, durMs: Int, type: String, vol: Double) {
        try {
            val frames = (SAMPLE_RATE * durMs / 1000).coerceAtLeast(1)
            val buffer = ShortArray(frames)
            val startGain = vol.coerceIn(0.0001, 1.0)
            // Exponential ramp startGain -> 0.0001 over the full duration,
            // exactly the source's exponentialRampToValueAtTime curve.
            val ratio = ln(0.0001 / startGain)
            for (i in 0 until frames) {
                val tSec = i.toDouble() / SAMPLE_RATE
                val phase = (freq * tSec) % 1.0
                val wave = when (type) {
                    "square" -> if (phase < 0.5) 1.0 else -1.0
                    "triangle" -> if (phase < 0.5) (phase * 4 - 1) else (3 - phase * 4)
                    "sawtooth" -> phase * 2 - 1
                    else -> sin(2 * PI * freq * tSec)
                }
                val gain = startGain * exp(ratio * i / frames)
                buffer[i] = (wave * gain * Short.MAX_VALUE).toInt()
                    .coerceIn(-32768, 32767).toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(durMs.toLong() + 30)
            try { track.stop() } catch (e: Exception) { }
            track.release()
        } catch (e: Exception) {
            // Sound is decorative in the source too (wrapped in try/catch there).
        }
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    // Source field label: small semibold slate label ABOVE the field.
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun NumField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    // v1.0.1 (defect #5): the source puts a small label ABOVE each field,
    // not a Material floating label inside it.
    Column(modifier = modifier) {
        FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == '-' }) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Simple exposed-dropdown picker used in place of the HTML <select> elements.
 *
 * v1.0.2 (defect B): the anchor is no longer a multi-line OutlinedTextField
 * (v1.0.1 never set singleLine, so a long value like "USD (United States)"
 * wrapped inside the field and the box grew five lines tall — an HTML
 * <select> is single-line). The anchor is now a fixed-height
 * ([OutlinedTextFieldDefaults.MinHeight] = 56dp) outlined container drawn
 * by OutlinedTextFieldDefaults.Container — the same border/shape/colours
 * as the real field, focus highlight included (a Focus interaction is
 * emitted while the menu is open) — holding a single-line Text with
 * ellipsis, so the value truncates ("USD (United St…") and the chevron
 * keeps its place, at the standard single-line height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerField(
    label: String?,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        // Null label = the field sits in a paired row whose shared label is
        // rendered by the caller (source: one label over a field pair).
        if (label != null) FieldLabel(label)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            var focusInteraction by remember { mutableStateOf<FocusInteraction.Focus?>(null) }
            LaunchedEffect(expanded) {
                if (expanded) {
                    val f = FocusInteraction.Focus()
                    focusInteraction = f
                    interactionSource.emit(f)
                } else {
                    focusInteraction?.let { interactionSource.emit(FocusInteraction.Unfocus(it)) }
                    focusInteraction = null
                }
            }
            Box(
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
                    .height(OutlinedTextFieldDefaults.MinHeight)
            ) {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxSize(),
                    colors = OutlinedTextFieldDefaults.colors(),
                    shape = OutlinedTextFieldDefaults.shape,
                    focusedBorderThickness = OutlinedTextFieldDefaults.FocusedBorderThickness,
                    unfocusedBorderThickness = OutlinedTextFieldDefaults.UnfocusedBorderThickness
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 12.dp)
                ) {
                    Text(
                        selected,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onSelect(opt); expanded = false }
                    )
                }
            }
        }
    }
}

fun copyToClipboard(context: Context, text: String, toast: String? = null) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Toolbox", text))
    if (toast != null) toast(context, toast)
}

/**
 * Parity pass — the source's showToast() UI: a dark pill at the TOP-CENTRE of
 * the screen (fixed top-16, -translate-x-1/2, slate-900 pill, rounded-2xl,
 * accent check icon, auto-dismiss after 3000 ms). Implemented as an in-app
 * overlay hosted once at the app root ([InAppToastHost]); the existing
 * toast(context, msg) call sites across all tools route here when a host is
 * registered, and fall back to an Android system Toast only before the host
 * exists (very early startup).
 */
object InAppToast {
    /** (message, monotonic id) — a new id restarts the dismiss timer. */
    val current = mutableStateOf<Pair<String, Long>?>(null)
    private var nextId = 0L
    fun show(message: String) {
        current.value = message to (++nextId)
    }
}

fun toast(context: Context, msg: String) {
    // Prefer the in-app overlay (source showToast). The overlay host lives at
    // the app root, so it is registered for the whole session after the
    // first composition; fall back to a system Toast only if called before.
    try {
        InAppToast.show(msg)
    } catch (e: Exception) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

/** Hosts the in-app toast pill (source #toast markup) at the app root. */
@Composable
fun InAppToastHost() {
    val state = InAppToast.current.value
    // Track visibility per toast id so the dismiss animation state restarts.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state?.second) {
        if (state != null) {
            visible = true
            kotlinx.coroutines.delay(3000)
            visible = false
            kotlinx.coroutines.delay(350)
            if (InAppToast.current.value?.second == state.second) InAppToast.current.value = null
        }
    }
    if (state != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(0.9f)
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        state.first,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Parity pass — EXACT port of the source's toolboxBeep(freq, durMs, type, vol):
 * a Web Audio oscillator at [freq] Hz, waveform [type] ("sine" default,
 * "triangle"/"square"/"sawtooth"), gain starting at [vol] (default 0.12) and
 * exponentially ramping to 0.0001 over [durMs]. Synthesized as PCM through
 * AudioTrack, replacing the Phase 1 ToneGenerator presets. Gated by the
 * source's toolboxSoundOn() ([soundOn]). Source call sites:
 * tally add 880/90, tally +/- 740|520/70, tally remove 330/110 triangle,
 * shopping add 880/70, shopping toggle (done ? 660 : 520)/60.
 */
fun toolboxBeep(soundOn: Boolean, freq: Int, durMs: Int, type: String = "sine", vol: Double = 0.12) {
    if (!soundOn) return
    Thread { SynthTone.play(freq, durMs, type, vol) }.start()
}

/** Legacy Phase 1 helper, now routed to the exact synth (740 high / 520 low). */
fun beep(soundOn: Boolean, high: Boolean = true) {
    toolboxBeep(soundOn, if (high) 740 else 520, 70)
}

/** Source formatting: `$${value.toFixed(2)}` in US locale. */
fun money(v: Double): String = String.format(Locale.US, "$%.2f", v)

/** parseFloat equivalent: invalid input -> 0 (source numVal / `parseFloat(x) || 0`). */
fun numOr0(s: String): Double = s.toDoubleOrNull() ?: 0.0
