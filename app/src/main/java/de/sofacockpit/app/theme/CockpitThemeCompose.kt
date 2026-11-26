package de.sofacockpit.app.theme

import android.graphics.RectF
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalCockpitPack = staticCompositionLocalOf { ThemePack.fallback() }

@Composable
fun CockpitThemeScope(pack: ThemePack, content: @Composable () -> Unit) {
    val t = pack.theme
    val base = Typography()
    val family = when (t.font) {
        "mono" -> FontFamily.Monospace
        "condensed" -> FontFamily.SansSerif
        else -> FontFamily.SansSerif
    }
    fun styled(s: TextStyle) = s.copy(fontFamily = family,
        fontSize = (s.fontSize.value * t.fontScale).sp,
        lineHeight = (s.lineHeight.value * t.fontScale).sp)
    val typography = Typography(
        displayLarge = styled(base.displayLarge), displayMedium = styled(base.displayMedium),
        displaySmall = styled(base.displaySmall), headlineLarge = styled(base.headlineLarge),
        headlineMedium = styled(base.headlineMedium), headlineSmall = styled(base.headlineSmall),
        titleLarge = styled(base.titleLarge), titleMedium = styled(base.titleMedium),
        titleSmall = styled(base.titleSmall), bodyLarge = styled(base.bodyLarge),
        bodyMedium = styled(base.bodyMedium), bodySmall = styled(base.bodySmall),
        labelLarge = styled(base.labelLarge), labelMedium = styled(base.labelMedium), labelSmall = styled(base.labelSmall))
    val shape = if (t.style == "hud") CutCornerShape(t.cornerDp.dp) else RoundedCornerShape(t.cornerDp.dp)
    CompositionLocalProvider(LocalCockpitPack provides pack) {
        MaterialTheme(colorScheme = darkColorScheme(
            primary = Color(t.accent), onPrimary = Color(t.onAccent),
            secondary = Color(t.accent), onSecondary = Color(t.onAccent),
            primaryContainer = Color(t.selected), onPrimaryContainer = Color(t.accent),
            secondaryContainer = Color(t.selected), onSecondaryContainer = Color(t.accent),
            tertiary = Color(t.accent), onTertiary = Color(t.onAccent),
            background = Color(t.background), onBackground = Color(t.text),
            surface = Color(t.panel), onSurface = Color(t.text),
            surfaceVariant = Color(t.control), onSurfaceVariant = Color(t.muted),
            surfaceContainer = Color(t.panel), surfaceContainerHigh = Color(t.control),
            surfaceContainerHighest = Color(t.control), outline = Color(t.border),
            outlineVariant = Color(t.border), error = Color(CockpitTheme.ERROR)),
            typography = typography,
            shapes = Shapes(extraSmall = shape, small = shape, medium = shape, large = shape, extraLarge = shape),
            content = content)
    }
}

/** Uses the exact same panel renderer as Java screens, including optional texture. */
fun Modifier.cockpitPanel(pack: ThemePack, color: Int = pack.theme.panel, selected: Boolean = false): Modifier =
    drawBehind {
        drawIntoCanvas {
            ThemeArt.drawPanel(it.nativeCanvas, RectF(0f, 0f, size.width, size.height),
                pack, color, pack.theme.cornerDp.dp.toPx(), selected)
        }
    }
