package app.cameraroll.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF74F8E5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E1),
    onSecondaryContainer = Color(0xFF06201C),
    tertiary = Color(0xFF456179),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDE5FF),
    onTertiaryContainer = Color(0xFF001E30),
    error = Color(0xFFBA1A1A),
    surface = Color(0xFFFAFDFB),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    background = Color(0xFFFAFDFB),
    onBackground = Color(0xFF191C1B),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF55DBCC),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF74F8E5),
    secondary = Color(0xFFB0CCC5),
    onSecondary = Color(0xFF1B3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFA8C9E4),
    onTertiary = Color(0xFF10324A),
    tertiaryContainer = Color(0xFF2A4860),
    onTertiaryContainer = Color(0xFFCDE5FF),
    error = Color(0xFFFFB4AB),
    surface = Color(0xFF191C1B),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    background = Color(0xFF191C1B),
    onBackground = Color(0xFFE0E3E1),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
)

@Composable
fun CameraRollTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
