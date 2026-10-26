package br.com.asa.smartpos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.asa.smartpos.R
import br.com.asa.smartpos.domain.TransactionRecord
import br.com.asa.smartpos.domain.TransactionStatus
import br.com.asa.smartpos.ui.ReceiptCopies
import br.com.asa.smartpos.ui.components.*
import br.com.asa.smartpos.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ReceiptOptionsScreen(
    record: TransactionRecord,
    showHomeButton: Boolean,
    onFinish: () -> Unit,
    onSelectCopies: (ReceiptCopies) -> Unit
) {
    BackHandler {
        if (!record.autoPrint) onFinish()
    }
    val amount = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(record.sale.amount)
    Column(Modifier.fillMaxSize().background(AsaCanvas).statusBarsPadding().navigationBarsPadding()) {
        AsaTopBar(title = "Comprovante")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TransactionStatusBadge(record.status)
                Spacer(Modifier.height(18.dp))
                Text(
                    record.status.label,
                    color = AsaCharcoal,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Text(amount, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))
                Text("Selecione quais vias deseja visualizar", color = AsaMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                ReceiptCopies.values().forEach { copies ->
                    Button(
                        onClick = { onSelectCopies(copies) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AsaCharcoal),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(copies.label, fontWeight = FontWeight.Medium) }
                    Spacer(Modifier.height(12.dp))
                }
                Text("A prévia será exibida na tela. Nenhuma impressão será enviada.", color = AsaMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
        if (showHomeButton) {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AsaCharcoal)
            ) { Text("Voltar ao início") }
        }
    }
}

@Composable
internal fun ReceiptPreviewScreen(
    record: TransactionRecord,
    copies: ReceiptCopies,
    establishmentName: String,
    onFinish: () -> Unit
) {
    BackHandler {
        if (!record.autoPrint) onFinish()
    }
    val requestedCopies = when (copies) {
        ReceiptCopies.BOTH -> listOf("Via do estabelecimento", "Via do cliente")
        ReceiptCopies.MERCHANT -> listOf("Via do estabelecimento")
        ReceiptCopies.CUSTOMER -> listOf("Via do cliente")
    }
    Column(Modifier.fillMaxSize().background(AsaCanvas).statusBarsPadding().navigationBarsPadding()) {
        AsaTopBar(title = "Comprovantes")
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            requestedCopies.forEach { copyLabel -> ReceiptCopyCard(record, copyLabel, establishmentName) }
            Text("Prévia digital do comprovante", color = AsaMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp).height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AsaCharcoal)
        ) { Text("Concluir") }
    }
}

@Composable
private fun ReceiptCopyCard(record: TransactionRecord, copyLabel: String, establishmentName: String) {
    val amount = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(record.sale.amount)
    val date = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale("pt", "BR")).format(Date(record.createdAtMillis))
    val (statusColor, statusBackground) = when (record.status) {
        TransactionStatus.APPROVED -> Color(0xFF2E7D32) to Color(0xFFE7F3E8)
        TransactionStatus.REJECTED, TransactionStatus.ERROR -> Color(0xFFB3261E) to Color(0xFFFCE8E6)
        TransactionStatus.UNDONE -> Color(0xFF9A6A00) to Color(0xFFF5EBD3)
        else -> AsaCharcoal to AsaGoldTint
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AsaSurface),
        border = BorderStroke(1.dp, AsaBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = AsaCharcoal
                ) {
                    Image(
                        painter = painterResource(R.drawable.asa_logo),
                        contentDescription = "Logo ASA",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(5.dp)
                    )
                }
                Column {
                    Text("ASA PAY", color = AsaCharcoal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Comprovante da transação", color = AsaMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    copyLabel.uppercase(Locale.ROOT),
                    color = AsaBrown,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = statusBackground, shape = RoundedCornerShape(6.dp)) {
                    val compactStatus = if (record.status == TransactionStatus.ERROR) "ERRO" else record.status.label
                    Text(
                        compactStatus,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(AsaBorder))
            ReceiptDetailRow("Data", date)
            ReceiptDetailRow("Transação", record.sale.transactionId)
            ReceiptDetailRow("Pagamento", record.sale.paymentType.label)
            ReceiptDetailRow("Estabelecimento", establishmentName)
            ReceiptDetailRow("Terminal", record.sale.terminalId)
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(AsaBorder))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("VALOR", color = AsaMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    amount,
                    color = AsaText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    textDecoration = if (record.status == TransactionStatus.UNDONE) TextDecoration.LineThrough else TextDecoration.None
                )
            }
        }
    }
}

@Composable
private fun ReceiptDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = AsaMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
        Text(value, color = AsaText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1.5f))
    }
}
