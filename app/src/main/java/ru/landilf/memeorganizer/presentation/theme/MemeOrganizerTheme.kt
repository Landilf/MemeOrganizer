package ru.landilf.memeorganizer.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF65529C), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DFFF), onPrimaryContainer = Color(0xFF24144F),
    secondary = Color(0xFF625B71), background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1D1B20), surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1D1B20), surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8A7F0), onPrimary = Color(0xFF30225A),
    primaryContainer = Color(0xFF493A73), onPrimaryContainer = Color(0xFFE8DFFF),
    secondary = Color(0xFFCCC2DC), background = Color(0xFF232329),
    onBackground = Color(0xFFE7E1E8), surface = Color(0xFF232329),
    onSurface = Color(0xFFE7E1E8), surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0)
)
private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MemeOrganizerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, shapes = AppShapes, content = content)
}
