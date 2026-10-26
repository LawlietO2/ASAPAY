package br.com.asa.smartpos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.asa.smartpos.R
import br.com.asa.smartpos.domain.TransactionRecord
import br.com.asa.smartpos.domain.TransactionStatus
import br.com.asa.smartpos.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun HomeTopBar(onSettingsClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(10.dp).background(AsaCanvas))
        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp).background(AsaCharcoal)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.asa_logo),
                contentDescription = "Logo ASA",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 44.dp, height = 58.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text("ASA PAY", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                    .semantics { contentDescription = "Configurações" }
            ) { SettingsGearIcon() }
        }
    }
}

@Composable
private fun SettingsGearIcon() {
    Canvas(Modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.34f
        val toothRadius = size.minDimension * 0.47f
        val path = Path().apply { fillType = PathFillType.EvenOdd }
        repeat(6) { tooth ->
            val centerAngle = -90f + tooth * 60f
            val points = listOf(
                (centerAngle - 22f) to baseRadius,
                (centerAngle - 14f) to toothRadius,
                (centerAngle + 14f) to toothRadius,
                (centerAngle + 22f) to baseRadius
            )
            points.forEachIndexed { pointIndex, (angle, radius) ->
                val radians = Math.toRadians(angle.toDouble())
                val point = Offset(
                    x = center.x + kotlin.math.cos(radians).toFloat() * radius,
                    y = center.y + kotlin.math.sin(radians).toFloat() * radius
                )
                if (tooth == 0 && pointIndex == 0) path.moveTo(point.x, point.y)
                else path.lineTo(point.x, point.y)
            }
        }
        path.close()
        val holeRadius = size.minDimension * 0.15f
        path.addOval(
            Rect(
                left = center.x - holeRadius,
                top = center.y - holeRadius,
                right = center.x + holeRadius,
                bottom = center.y + holeRadius
            )
        )
        drawPath(
            path = path,
            color = Color.White
        )
    }
}

@Composable
internal fun AsaTopBar(title: String, onBack: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(10.dp).background(AsaCanvas))
        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp).background(AsaCharcoal)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .semantics { contentDescription = "Voltar à tela inicial" }
                    ) {
                        Canvas(Modifier.size(24.dp)) {
                            val strokeWidth = 2.7.dp.toPx()
                            drawLine(
                                color = Color.White,
                                start = Offset(size.width * 0.70f, size.height * 0.18f),
                                end = Offset(size.width * 0.30f, size.height * 0.50f),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(size.width * 0.30f, size.height * 0.50f),
                                end = Offset(size.width * 0.70f, size.height * 0.82f),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(title, color = Color.White, fontWeight = FontWeight.Medium)
                }
            } else {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.asa_logo),
                contentDescription = "Logo ASA",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 44.dp, height = 58.dp)
            )
        }
    }
}

@Composable
internal fun TransactionCard(record: TransactionRecord, onClick: () -> Unit, onPrint: () -> Unit) {
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(record.sale.amount)
    val statusColor = when (record.status) {
        TransactionStatus.APPROVED -> Color(0xFF2E7D32)
        TransactionStatus.REJECTED -> MaterialTheme.colorScheme.error
        TransactionStatus.UNDONE -> Color(0xFF9A6A00)
        else -> AsaBrown
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AsaSurface),
        border = BorderStroke(1.dp, AsaBorder)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.status.label, color = statusColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    currency,
                    color = AsaText,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (record.status == TransactionStatus.UNDONE) TextDecoration.LineThrough else TextDecoration.None
                )
            }
            Text("${record.sale.paymentType.label} • ${record.sale.transactionId}", color = AsaMuted, style = MaterialTheme.typography.bodySmall)
            record.detail?.let { Text(it, color = AsaMuted, style = MaterialTheme.typography.bodySmall) }
            if (record.status != TransactionStatus.SENDING && record.status != TransactionStatus.WAITING) {
                TextButton(onClick = onPrint) { Text("▤  Imprimir comprovante", color = AsaBrown) }
            }
        }
    }
}

internal fun formatCents(cents: Long): String {
    val whole = cents / 100
    val fraction = (cents % 100).toString().padStart(2, '0')
    return "${whole},$fraction"
}
