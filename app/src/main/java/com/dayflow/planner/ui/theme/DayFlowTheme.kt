package com.dayflow.planner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.dayflow.planner.settings.SettingsStore

private val DayFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Root theme. `mode` lets the user force light/dark independently of the
 * system; `accentIndex` selects one of six free accent palettes.
 */
@Composable
fun DayFlowTheme(
    mode: SettingsStore.ThemeMode = SettingsStore.ThemeMode.SYSTEM,
    accentIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        SettingsStore.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        SettingsStore.ThemeMode.LIGHT -> false
        SettingsStore.ThemeMode.DARK -> true
    }
    val accent = DayFlowPalette.accents.getOrElse(accentIndex) { DayFlowPalette.accents.first() }
    MaterialTheme(
        colorScheme = if (dark) accent.dark else accent.light,
        typography = Typography(),
        shapes = DayFlowShapes,
        content = content,
    )
}
