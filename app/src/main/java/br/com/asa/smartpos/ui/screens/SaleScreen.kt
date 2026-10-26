package br.com.asa.smartpos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.asa.smartpos.domain.PaymentType
import br.com.asa.smartpos.ui.PaymentViewModel
import br.com.asa.smartpos.ui.components.*
import br.com.asa.smartpos.ui.theme.*

@Composable
internal fun SaleScreenContent(
    paymentViewModel: PaymentViewModel,
    minimumCents: Long,
    maximumCents: Long,
    autoPrint: Boolean,
    onBack: () -> Unit,
    onTransaction: (String) -> Unit
) {
    var amountInCents by remember { mutableStateOf(0L) }
    var amountInput by remember {
        val initial = formatCents(0L)
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    var paymentType by remember { mutableStateOf<PaymentType?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }

    fun setAmount(newAmountInCents: Long) {
        amountInCents = newAmountInCents
        val formatted = formatCents(newAmountInCents)
        amountInput = TextFieldValue(formatted, selection = TextRange(formatted.length))
        message = null
        amountError = null
    }

    fun enterDigit(digit: Int) {
        if (amountInCents <= (Long.MAX_VALUE - digit) / 10L) {
            setAmount(amountInCents * 10L + digit)
        }
    }

    fun confirmSale() {
        when {
            amountInCents == 0L -> amountError = "Informe um valor acima de 0,00."
            amountInCents < minimumCents -> amountError = "O valor mínimo é R$ ${formatCents(minimumCents)}."
            amountInCents > maximumCents -> amountError = "O valor máximo é R$ ${formatCents(maximumCents)}."
            paymentType == null -> message = "Selecione crédito ou débito."
            else -> {
                val id = paymentViewModel.sell(amountInCents, paymentType!!, autoPrint)
                if (id != null) onTransaction(id)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(AsaCanvas).statusBarsPadding().navigationBarsPadding()
    ) {
        AsaTopBar(title = "Nova venda", onBack = onBack)

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Informe o valor da transação",
                    modifier = Modifier.weight(1f),
                    color = AsaMuted,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amountInput,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                prefix = { Text("R$", color = AsaMuted, fontSize = 22.sp, fontWeight = FontWeight.Medium) },
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    color = if (amountError != null) MaterialTheme.colorScheme.error else AsaText
                ),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AsaGold,
                    unfocusedBorderColor = AsaBorder,
                    cursorColor = AsaBrown,
                    focusedContainerColor = AsaSurface,
                    unfocusedContainerColor = AsaSurface
                )
            )

            amountError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { setAmount(0L) }) {
                    Text("Limpar", color = AsaBrown, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Selecione a forma de pagamento",
                modifier = Modifier.fillMaxWidth(),
                color = AsaText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                PaymentTypeOption(
                    label = "Crédito",
                    selected = paymentType == PaymentType.CREDIT,
                    modifier = Modifier.weight(1f),
                    onClick = { paymentType = PaymentType.CREDIT; message = null }
                )
                PaymentTypeOption(
                    label = "Débito",
                    selected = paymentType == PaymentType.DEBIT,
                    modifier = Modifier.weight(1f),
                    onClick = { paymentType = PaymentType.DEBIT; message = null }
                )
            }

            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(AsaCanvas)
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val keypadRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("Apagar", "0", "Confirmar")
            )
            keypadRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.weight(1f).height(46.dp))
                        } else {
                            KeypadButton(
                                label = key,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (key) {
                                        "Apagar" -> setAmount(amountInCents / 10L)
                                        "Confirmar" -> confirmSale()
                                        else -> enterDigit(key.single().digitToInt())
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun PaymentTypeOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(64.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AsaGoldTint else AsaSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AsaGold else AsaBorder),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label, color = AsaText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun KeypadButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val utilityKey = label == "Apagar"
    val confirmKey = label == "Confirmar"
    Surface(
        modifier = modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = when {
            utilityKey -> AsaGoldTint
            confirmKey -> AsaCharcoal
            else -> AsaSurface
        },
        border = BorderStroke(
            1.dp,
            when {
                utilityKey -> AsaGold.copy(alpha = 0.7f)
                confirmKey -> AsaCharcoal
                else -> AsaBorder
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = when {
                    utilityKey -> AsaBrown
                    confirmKey -> Color.White
                    else -> AsaText
                },
                fontSize = if (utilityKey || confirmKey) 13.sp else 20.sp,
                fontWeight = if (confirmKey) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}
