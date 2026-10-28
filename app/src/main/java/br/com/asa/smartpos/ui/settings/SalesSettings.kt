package br.com.asa.smartpos.ui.settings

import android.content.Context
import br.com.asa.smartpos.data.MockOutcome

internal data class SalesSettings(
    val establishmentName: String = "Padaria pão bom",
    val minimumCents: Long = 1L,
    val maximumCents: Long = 10_000_000L,
    val autoPrint: Boolean = false,
    val mockOutcome: MockOutcome = MockOutcome.APPROVED,
    val mockResponseDelaySeconds: Int = 0
)

internal class SalesSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("asa_settings", Context.MODE_PRIVATE)

    fun load(): SalesSettings = SalesSettings(
        establishmentName = preferences.getString("establishment_name", null)
            ?.takeUnless { it == "Padaria Pão Bom" }
            ?: "Padaria pão bom",
        minimumCents = preferences.getLong("minimum_cents", 1L),
        maximumCents = preferences.getLong("maximum_cents", 10_000_000L),
        autoPrint = preferences.getBoolean("auto_print", false),
        mockOutcome = runCatching {
            MockOutcome.valueOf(preferences.getString("mock_outcome", null) ?: "APPROVED")
        }.getOrDefault(MockOutcome.APPROVED),
        mockResponseDelaySeconds = preferences
            .getInt("mock_response_delay_seconds", 0)
            .coerceIn(0, 13)
    )

    fun save(settings: SalesSettings) {
        preferences.edit()
            .putString("establishment_name", settings.establishmentName)
            .putLong("minimum_cents", settings.minimumCents)
            .putLong("maximum_cents", settings.maximumCents)
            .putBoolean("auto_print", settings.autoPrint)
            .putString("mock_outcome", settings.mockOutcome.name)
            .putInt("mock_response_delay_seconds", settings.mockResponseDelaySeconds)
            .apply()
    }
}
