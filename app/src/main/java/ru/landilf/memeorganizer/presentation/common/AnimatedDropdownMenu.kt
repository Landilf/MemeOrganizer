package ru.landilf.memeorganizer.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

const val DROPDOWN_MENU_ANIMATION_DURATION_MILLIS = 300

@Composable
fun AnimatedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var popupExpanded by remember { mutableStateOf(expanded) }
    var menuVisible by remember { mutableStateOf(expanded) }

    LaunchedEffect(expanded) {
        if (expanded) {
            popupExpanded = true
            menuVisible = false
            withFrameNanos { }
            menuVisible = true
        } else if (popupExpanded) {
            menuVisible = false
            delay(DROPDOWN_MENU_ANIMATION_DURATION_MILLIS.toLong())
            popupExpanded = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (menuVisible) 1f else 0f,
        animationSpec = tween(DROPDOWN_MENU_ANIMATION_DURATION_MILLIS),
        label = "dropdownMenuAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (menuVisible) 1f else 0.92f,
        animationSpec = tween(DROPDOWN_MENU_ANIMATION_DURATION_MILLIS),
        label = "dropdownMenuScale"
    )

    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Surface(
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            },
            shape = MenuDefaults.shape,
            color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation
        ) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}
