package br.com.asa.smartpos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.asa.smartpos.domain.TransactionRecord
import br.com.asa.smartpos.ui.PaymentViewModel
import br.com.asa.smartpos.ui.ReceiptCopies
import br.com.asa.smartpos.ui.screens.HomeScreen
import br.com.asa.smartpos.ui.screens.ReceiptOptionsScreen
import br.com.asa.smartpos.ui.screens.ReceiptPreviewScreen
import br.com.asa.smartpos.ui.screens.SaleScreenContent
import br.com.asa.smartpos.ui.screens.SettingsScreen
import br.com.asa.smartpos.ui.screens.TransactionResultScreen
import br.com.asa.smartpos.ui.settings.SalesSettings

@Composable
internal fun SalesNavigation(
    paymentViewModel: PaymentViewModel,
    transactions: List<TransactionRecord>,
    settings: SalesSettings,
    showMockOptions: Boolean,
    onSaveSettings: (SalesSettings) -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = SalesRoutes.HOME,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(SalesRoutes.HOME) {
            HomeScreen(
                transactions = transactions,
                establishmentName = settings.establishmentName,
                onSellClick = { navController.navigate(SalesRoutes.SALE) },
                onSettingsClick = { navController.navigate(SalesRoutes.SETTINGS) },
                onTransactionClick = { transactionId ->
                    navController.navigate(SalesRoutes.result(transactionId, autoOpenReceipt = false))
                },
                onPrint = { record ->
                    navController.navigate(
                        SalesRoutes.receiptOptions(record.sale.transactionId, openedFromHome = true)
                    )
                }
            )
        }

        composable(SalesRoutes.SALE) {
            SaleScreenContent(
                paymentViewModel = paymentViewModel,
                minimumCents = settings.minimumCents,
                maximumCents = settings.maximumCents,
                autoPrint = settings.autoPrint,
                onBack = { navController.popBackStack() },
                onTransaction = { transactionId ->
                    navController.navigate(SalesRoutes.result(transactionId, autoOpenReceipt = true)) {
                        popUpTo(SalesRoutes.SALE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(SalesRoutes.SETTINGS) {
            SettingsScreen(
                establishmentName = settings.establishmentName,
                minimumCents = settings.minimumCents,
                maximumCents = settings.maximumCents,
                autoPrint = settings.autoPrint,
                mockOutcome = settings.mockOutcome,
                mockResponseDelaySeconds = settings.mockResponseDelaySeconds,
                showMockOptions = showMockOptions,
                onBack = { navController.popBackStack() },
                onSave = { name, minimum, maximum, autoPrint, mockOutcome, delaySeconds ->
                    onSaveSettings(
                        SalesSettings(
                            establishmentName = name,
                            minimumCents = minimum,
                            maximumCents = maximum,
                            autoPrint = autoPrint,
                            mockOutcome = mockOutcome,
                            mockResponseDelaySeconds = delaySeconds
                        )
                    )
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = SalesRoutes.RESULT_PATTERN,
            arguments = listOf(
                navArgument(SalesRoutes.TRANSACTION_ID) { type = NavType.StringType },
                navArgument(SalesRoutes.AUTO_OPEN_RECEIPT) { type = NavType.BoolType }
            )
        ) { entry ->
            val transactionId = entry.arguments?.getString(SalesRoutes.TRANSACTION_ID)
            val autoOpenReceipt = entry.arguments?.getBoolean(SalesRoutes.AUTO_OPEN_RECEIPT) ?: false
            val record = transactions.firstOrNull { it.sale.transactionId == transactionId }

            if (record != null) {
                TransactionResultScreen(
                    record = record,
                    keepProcessingScreen = record.autoPrint && autoOpenReceipt,
                    autoOpenReceipt = autoOpenReceipt,
                    onHome = { navController.navigateHome() },
                    onPrint = {
                        navController.navigate(
                            SalesRoutes.receiptOptions(record.sale.transactionId, openedFromHome = false)
                        )
                    }
                )
            } else {
                RedirectToHomeWhenMissingTransaction(navController)
            }
        }

        composable(
            route = SalesRoutes.RECEIPT_OPTIONS_PATTERN,
            arguments = listOf(
                navArgument(SalesRoutes.TRANSACTION_ID) { type = NavType.StringType },
                navArgument(SalesRoutes.OPENED_FROM_HOME) { type = NavType.BoolType }
            )
        ) { entry ->
            val transactionId = entry.arguments?.getString(SalesRoutes.TRANSACTION_ID)
            val openedFromHome = entry.arguments?.getBoolean(SalesRoutes.OPENED_FROM_HOME) ?: false
            val record = transactions.firstOrNull { it.sale.transactionId == transactionId }

            if (record != null) {
                ReceiptOptionsScreen(
                    record = record,
                    showHomeButton = openedFromHome,
                    onFinish = { navController.navigateHome() },
                    onSelectCopies = { copies ->
                        paymentViewModel.printReceipt(record.sale.transactionId, copies.targets)
                        navController.navigate(
                            SalesRoutes.receiptPreview(record.sale.transactionId, copies.name)
                        ) {
                            popUpTo(SalesRoutes.receiptOptions(transactionId.orEmpty(), openedFromHome)) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            } else {
                RedirectToHomeWhenMissingTransaction(navController)
            }
        }

        composable(
            route = SalesRoutes.RECEIPT_PREVIEW_PATTERN,
            arguments = listOf(
                navArgument(SalesRoutes.TRANSACTION_ID) { type = NavType.StringType },
                navArgument(SalesRoutes.RECEIPT_COPIES) { type = NavType.StringType }
            )
        ) { entry ->
            val transactionId = entry.arguments?.getString(SalesRoutes.TRANSACTION_ID)
            val copies = entry.arguments?.getString(SalesRoutes.RECEIPT_COPIES)
                ?.let { value -> runCatching { ReceiptCopies.valueOf(value) }.getOrNull() }
            val record = transactions.firstOrNull { it.sale.transactionId == transactionId }

            if (record != null && copies != null) {
                ReceiptPreviewScreen(
                    record = record,
                    copies = copies,
                    establishmentName = settings.establishmentName,
                    onFinish = { navController.navigateHome() }
                )
            } else {
                RedirectToHomeWhenMissingTransaction(navController)
            }
        }
    }
}

@Composable
private fun RedirectToHomeWhenMissingTransaction(navController: NavHostController) {
    LaunchedEffect(navController) {
        navController.navigateHome()
    }
}

private fun NavHostController.navigateHome() {
    if (!popBackStack(SalesRoutes.HOME, inclusive = false)) {
        navigate(SalesRoutes.HOME) {
            launchSingleTop = true
        }
    }
}
