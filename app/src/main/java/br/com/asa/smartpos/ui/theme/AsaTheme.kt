package br.com.asa.smartpos.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

internal val AsaCharcoal = Color(0xFF292B29)

internal val AsaGold = Color(0xFFB99A62)

internal val AsaBrown = Color(0xFF79502D)

internal val AsaCanvas = Color(0xFFF6F4EF)

internal val AsaSurface = Color(0xFFFFFFFF)

internal val AsaText = Color(0xFF282724)

internal val AsaMuted = Color(0xFF77736B)

internal val AsaBorder = Color(0xFFE1DDD4)

internal val AsaGoldTint = Color(0xFFF3EDE0)

@Composable
internal fun AsaPayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AsaCharcoal,
            onPrimary = Color.White,
            secondary = AsaGold,
            onSecondary = AsaText,
            background = AsaCanvas,
            onBackground = AsaText,
            surface = AsaSurface,
            onSurface = AsaText,
            error = Color(0xFFB3261E)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = AsaCanvas) {
            content()
        }
    }
}
