package com.shawnrodis.toolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.shawnrodis.toolbox.R

/**
 * App theme driven by the user's Settings, mirroring the HTML app:
 *  - appearance "light" | "dark" | "black" (source toolbox_dark / setThemeMode)
 *  - accent color presets + custom (source [data-accent] CSS variables);
 *    the accent becomes Material 3's primary color.
 * "black" is the dark scheme with true-black backgrounds, as in the source.
 * "dark" (non-black) uses the source's deep-blue surfaces (#0b1526 body,
 * from the source's html.dark:not(.black) rule).
 *
 * Parity pass — fonts: the source loads Inter (body, weights 300..800) and
 * Fredoka (400/600/700, SpongeBob headings) from Google Fonts; both are now
 * bundled in res/font as variable fonts, so the native app renders with the
 * same families offline. Fredoka is exposed for the meme tool's headings.
 */
val InterFamily: FontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
    Font(R.font.inter, FontWeight.Light)
)

val FredokaFamily: FontFamily = FontFamily(
    Font(R.font.fredoka, FontWeight.Normal),
    Font(R.font.fredoka, FontWeight.SemiBold),
    Font(R.font.fredoka, FontWeight.Bold)
)

private val ToolboxTypography: Typography
    @Composable get() {
        val base = Typography()
        return Typography(
            displayLarge = base.displayLarge.copy(fontFamily = InterFamily),
            displayMedium = base.displayMedium.copy(fontFamily = InterFamily),
            displaySmall = base.displaySmall.copy(fontFamily = InterFamily),
            headlineLarge = base.headlineLarge.copy(fontFamily = InterFamily),
            headlineMedium = base.headlineMedium.copy(fontFamily = InterFamily),
            headlineSmall = base.headlineSmall.copy(fontFamily = InterFamily),
            titleLarge = base.titleLarge.copy(fontFamily = InterFamily),
            titleMedium = base.titleMedium.copy(fontFamily = InterFamily),
            titleSmall = base.titleSmall.copy(fontFamily = InterFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = InterFamily),
            bodyMedium = base.bodyMedium.copy(fontFamily = InterFamily),
            bodySmall = base.bodySmall.copy(fontFamily = InterFamily),
            labelLarge = base.labelLarge.copy(fontFamily = InterFamily),
            labelMedium = base.labelMedium.copy(fontFamily = InterFamily),
            labelSmall = base.labelSmall.copy(fontFamily = InterFamily)
        )
    }

@Composable
fun ToolboxNativeTheme(
    themeMode: String = "black",
    accent: Color = Color(0xFF6366F1),
    content: @Composable () -> Unit
) {
    val dark = themeMode != "light"
    val scheme = when {
        themeMode == "black" -> darkColorScheme(
            primary = accent,
            background = Color.Black,
            surface = Color(0xFF0A0A0B),
            surfaceVariant = Color(0xFF18181B),
            onBackground = Color(0xFFFAFAFA),
            onSurface = Color(0xFFFAFAFA)
        )
        // Source dark theme: deep blue body #0b1526 (html.dark:not(.black)).
        dark -> darkColorScheme(
            primary = accent,
            background = Color(0xFF0B1526),
            surface = Color(0xFF0B1526),
            surfaceVariant = Color(0xFF16233C)
        )
        else -> lightColorScheme(primary = accent)
    }
    MaterialTheme(colorScheme = scheme, typography = ToolboxTypography, content = content)
}
