package br.com.asa.smartpos.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.asa.smartpos.data.MockOutcome
import br.com.asa.smartpos.ui.components.*
import br.com.asa.smartpos.ui.theme.*

@Composable
internal fun SettingsScreen(
    establishmentName: String,
    minimumCents: Long,
    maximumCents: Long,
    autoPrint: Boolean,
    mockOutcome: MockOutcome,
    mockResponseDelaySeconds: Int,
    showMockOptions: Boolean,
    onBack: () -> Unit,
    onSave: (String, Long, Long, Boolean, MockOutcome, Int) -> Unit
) {
    var name by remember(establishmentName) { mutableStateOf(establishmentName) }
    var minimum by remember(minimumCents) { mutableStateOf(formatCents(minimumCents)) }
    var maximum by remember(maximumCents) { mutableStateOf(formatCents(maximumCents)) }
    var printEnabled by remember(autoPrint) { mutableStateOf(autoPrint) }
    var selectedMockOutcome by remember(mockOutcome) { mutableStateOf(mockOutcome) }
    var mockResponseDelay by remember(mockResponseDelaySeconds) { mutableStateOf(mockResponseDelaySeconds.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val settingsScrollState = rememberScrollState()
    Column(Modifier.fillMaxSize().background(AsaCanvas).statusBarsPadding().navigationBarsPadding()) {
        AsaTopBar(title = "Configurações", onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(settingsScrollState).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            if (showMockOptions) {
                Text("Simulação de desenvolvimento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Configure o comportamento da API simulada para as próximas vendas.", color = AsaMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = mockResponseDelay,
                    onValueChange = { mockResponseDelay = it.filter(Char::isDigit); error = null },
                    label = { Text("Atraso da resposta da API (segundos)") },
                    supportingText = { Text("De 0 a 13 segundos. O timeout da transação continua em 15 segundos.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MockOutcome.values().forEach { outcome ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selectedMockOutcome = outcome }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedMockOutcome == outcome, onClick = { selectedMockOutcome = outcome })
                        Column {
                            Text(outcome.label, fontWeight = FontWeight.Medium)
                            if (outcome == MockOutcome.TIMEOUT_UNDO) {
                                Text("Aguarda o limite de 15 segundos e solicita desfazimento.", color = AsaMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (selectedMockOutcome == MockOutcome.TIMEOUT_UNDO) {
                    Text("Neste cenário o mock não responde; o atraso configurado não se aplica.", color = AsaMuted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
            }
            Text("Estabelecimento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("Nome do estabelecimento") },
                isError = name.isBlank(),
                supportingText = {
                    if (name.isBlank()) {
                        Text("Este campo não pode ficar vazio.", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Exibido na tela inicial e nos comprovantes.")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Limites da transação", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = minimum, onValueChange = { minimum = it.filter { c -> c.isDigit() || c == ',' || c == '.' }; error = null }, label = { Text("Valor mínimo (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = maximum, onValueChange = { maximum = it.filter { c -> c.isDigit() || c == ',' || c == '.' }; error = null }, label = { Text("Valor máximo (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Comprovante", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exibir comprovante automaticamente", fontWeight = FontWeight.Medium)
                    Text("Mantém esta tela durante o processamento e abre as opções de vias 3 segundos após o resultado.", color = AsaMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = printEnabled, onCheckedChange = { printEnabled = it })
            }
            Text("Com a opção desligada, você pode voltar ao início durante o processamento. Se permanecer na tela de resultado, as opções de vias abrem após 3 segundos.", color = AsaMuted, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (settingsScrollState.maxValue > 0 && settingsScrollState.value < settingsScrollState.maxValue) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.45f to AsaCanvas.copy(alpha = 0.92f),
                                    1f to AsaCanvas
                                )
                            )
                        )
                        .padding(top = 20.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Canvas(Modifier.width(18.dp).height(10.dp)) {
                        val strokeWidth = 2.dp.toPx()
                        drawLine(
                            color = AsaBrown,
                            start = Offset(size.width * 0.2f, size.height * 0.25f),
                            end = Offset(size.width * 0.5f, size.height * 0.7f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = AsaBrown,
                            start = Offset(size.width * 0.5f, size.height * 0.7f),
                            end = Offset(size.width * 0.8f, size.height * 0.25f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    Text("Role para ver mais", color = AsaMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Button(onClick = {
            val min = parseCents(minimum)
            val max = parseCents(maximum)
            val delaySeconds = mockResponseDelay.toIntOrNull()
            if (name.isBlank()) error = "Informe o nome do estabelecimento."
            else if (min == null || max == null || min <= 0 || max < min) error = "Informe limites válidos: mínimo maior que zero e máximo igual ou maior que o mínimo."
            else if (showMockOptions && (delaySeconds == null || delaySeconds !in 0..13)) error = "O atraso simulado deve estar entre 0 e 13 segundos."
            else onSave(name.trim(), min, max, printEnabled, selectedMockOutcome, delaySeconds?.coerceIn(0, 13) ?: mockResponseDelaySeconds)
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = AsaCharcoal)) { Text("Salvar configurações") }
    }
}

internal fun parseCents(value: String): Long? = runCatching {
    val trimmed = value.trim()
    val normalized = if (',' in trimmed) trimmed.replace(".", "").replace(',', '.') else trimmed
    val amount = java.math.BigDecimal(normalized).setScale(2, java.math.RoundingMode.HALF_UP)
    amount.movePointRight(2).longValueExact()
}.getOrNull()
