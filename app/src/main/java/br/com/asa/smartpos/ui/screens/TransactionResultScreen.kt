package br.com.asa.smartpos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.asa.smartpos.domain.TransactionRecord
import br.com.asa.smartpos.domain.TransactionStatus
import br.com.asa.smartpos.ui.components.*
import br.com.asa.smartpos.ui.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun TransactionResultScreen(
    record: TransactionRecord,
    keepProcessingScreen: Boolean,
    autoOpenReceipt: Boolean,
    onHome: () -> Unit,
    onPrint: () -> Unit
) {
    val processing = record.status == TransactionStatus.SENDING || record.status == TransactionStatus.WAITING
    var receiptCountdown by remember(record.sale.transactionId, record.status) { mutableStateOf(3) }
    LaunchedEffect(record.sale.transactionId, record.status, autoOpenReceipt) {
        if (!processing && autoOpenReceipt) {
            for (secondsRemaining in 3 downTo 1) {
                receiptCountdown = secondsRemaining
                delay(1_000)
            }
            onPrint()
        }
    }
    BackHandler {
        if (!keepProcessingScreen) onHome()
    }
    Column(Modifier.fillMaxSize().background(AsaCanvas).statusBarsPadding().navigationBarsPadding()) {
        AsaTopBar(title = "Resultado")
        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(88.dp),
                    color = AsaBrown,
                    strokeWidth = 4.dp
                )
                Spacer(Modifier.height(18.dp))
            } else {
                TransactionStatusBadge(record.status)
                Spacer(Modifier.height(18.dp))
            }
            Text(if (processing) "PROCESSANDO" else record.status.label, color = AsaCharcoal, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(record.sale.amount), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Text(if (processing && !keepProcessingScreen) "Você pode aguardar o processamento nesta tela ou consultar o andamento no histórico da tela inicial." else record.detail ?: "Transação ${record.status.label.lowercase(Locale.ROOT)}.", color = AsaMuted, textAlign = TextAlign.Center)
            if (!processing) {
                Spacer(Modifier.height(18.dp))
                Button(onClick = onPrint, colors = ButtonDefaults.buttonColors(containerColor = AsaCharcoal)) { Text("Imprimir comprovante") }
                if (autoOpenReceipt) {
                    Text("Abrindo opções de comprovante em ${receiptCountdown}s", color = AsaMuted, textAlign = TextAlign.Center)
                }
            }
        }
        if (!keepProcessingScreen) {
            Button(onClick = onHome, modifier = Modifier.fillMaxWidth().padding(24.dp).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = AsaCharcoal)) {
                Text("Ir para o início")
            }
        }
    }
}

@Composable
internal fun TransactionStatusBadge(status: TransactionStatus) {
    val iconColor: Color
    val backgroundColor: Color
    val description: String
    when (status) {
        TransactionStatus.APPROVED -> {
            iconColor = Color(0xFF2E7D32)
            backgroundColor = Color(0xFFE7F3E8)
            description = "Transação aprovada"
        }
        TransactionStatus.REJECTED, TransactionStatus.ERROR -> {
            iconColor = Color(0xFFB3261E)
            backgroundColor = Color(0xFFFCE8E6)
            description = if (status == TransactionStatus.REJECTED) "Transação recusada" else "Erro na transação"
        }
        TransactionStatus.UNDONE -> {
            iconColor = Color(0xFF9A6A00)
            backgroundColor = Color(0xFFF5EBD3)
            description = "Transação desfeita"
        }
        else -> return
    }

    Canvas(
        Modifier.size(88.dp).semantics { contentDescription = description }
    ) {
        drawCircle(color = backgroundColor)
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        val icon = Path()
        when (status) {
            TransactionStatus.APPROVED -> {
                icon.moveTo(size.width * 0.28f, size.height * 0.51f)
                icon.lineTo(size.width * 0.44f, size.height * 0.66f)
                icon.lineTo(size.width * 0.73f, size.height * 0.35f)
                drawPath(icon, color = iconColor, style = stroke)
            }
            TransactionStatus.REJECTED, TransactionStatus.ERROR -> {
                drawLine(iconColor, Offset(size.width * 0.34f, size.height * 0.34f), Offset(size.width * 0.66f, size.height * 0.66f), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(iconColor, Offset(size.width * 0.66f, size.height * 0.34f), Offset(size.width * 0.34f, size.height * 0.66f), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            }
            TransactionStatus.UNDONE -> {
                // Haste de desfazer curvada, terminando numa ponta voltada claramente para a esquerda.
                icon.moveTo(size.width * 0.70f, size.height * 0.72f)
                icon.cubicTo(
                    size.width * 0.73f, size.height * 0.53f,
                    size.width * 0.62f, size.height * 0.39f,
                    size.width * 0.43f, size.height * 0.39f
                )
                icon.lineTo(size.width * 0.29f, size.height * 0.39f)
                icon.moveTo(size.width * 0.29f, size.height * 0.39f)
                icon.lineTo(size.width * 0.44f, size.height * 0.24f)
                icon.moveTo(size.width * 0.29f, size.height * 0.39f)
                icon.lineTo(size.width * 0.44f, size.height * 0.54f)
                drawPath(icon, color = iconColor, style = stroke)
            }
            else -> Unit
        }
    }
}
