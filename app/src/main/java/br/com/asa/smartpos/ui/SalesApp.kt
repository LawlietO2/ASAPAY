package br.com.asa.smartpos.ui

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.asa.smartpos.BuildConfig
import br.com.asa.smartpos.data.MockOutcome
import br.com.asa.smartpos.ui.navigation.SalesNavigation
import br.com.asa.smartpos.ui.screens.StartupSplashScreen
import br.com.asa.smartpos.ui.settings.SalesSettings
import br.com.asa.smartpos.ui.settings.SalesSettingsStore
import br.com.asa.smartpos.ui.theme.AsaPayTheme
import kotlinx.coroutines.delay

@Composable
internal fun SalesApp(paymentViewModel: PaymentViewModel = viewModel()) {
    val context = LocalContext.current
    val settingsStore = remember(context) { SalesSettingsStore(context) }
    var settings by remember(settingsStore) { mutableStateOf(settingsStore.load()) }
    val transactions by paymentViewModel.transactions.collectAsState()
    var showingSplash by remember { mutableStateOf(true) }
    val activity = context as? Activity

    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !showingSplash
                isAppearanceLightNavigationBars = !showingSplash
            }
        }
    }

    LaunchedEffect(settings.mockOutcome, settings.mockResponseDelaySeconds) {
        paymentViewModel.setMockOutcome(settings.mockOutcome)
        paymentViewModel.setMockResponseDelaySeconds(settings.mockResponseDelaySeconds)
    }

    LaunchedEffect(Unit) {
        delay(2_500)
        showingSplash = false
    }

    AsaPayTheme {
        Crossfade(
            targetState = showingSplash,
            animationSpec = tween(durationMillis = 450),
            label = "splash-home-transition"
        ) { splashVisible ->
            if (splashVisible) {
                StartupSplashScreen()
            } else {
                SalesNavigation(
                    paymentViewModel = paymentViewModel,
                    transactions = transactions,
                    settings = settings,
                    showMockOptions = BuildConfig.USE_MOCK_API,
                    onSaveSettings = { updatedSettings ->
                        settings = updatedSettings
                        settingsStore.save(updatedSettings)
                    }
                )
            }
        }
    }
}
