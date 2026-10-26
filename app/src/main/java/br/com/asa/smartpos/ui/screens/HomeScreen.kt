package br.com.asa.smartpos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.asa.smartpos.domain.TransactionRecord
import br.com.asa.smartpos.ui.components.*
import br.com.asa.smartpos.ui.theme.*

@Composable
internal fun HomeScreen(
    transactions: List<TransactionRecord>,
    establishmentName: String,
    onSellClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTransactionClick: (String) -> Unit,
    onPrint: (TransactionRecord) -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(AsaCanvas).statusBarsPadding().navigationBarsPadding()
    ) {
        HomeTopBar(onSettingsClick)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("ASA", color = AsaBrown, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("·", color = AsaMuted, style = MaterialTheme.typography.labelLarge)
                    Text(establishmentName, color = AsaMuted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                Text("Pronto para vender?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Inicie uma venda e acompanhe o processamento por aqui.", color = AsaMuted, style = MaterialTheme.typography.bodyLarge)
            }
            item {
                Button(
                    onClick = onSellClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AsaCharcoal, contentColor = Color.White)
                ) { Text("Vender", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            }
            item {
                Text("Transações recentes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            }
            if (transactions.isEmpty()) {
                item { Text("Suas vendas aparecerão aqui.", color = AsaMuted, style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(transactions, key = { it.sale.transactionId }) { record ->
                    TransactionCard(record, onClick = { onTransactionClick(record.sale.transactionId) }, onPrint = { onPrint(record) })
                }
            }
        }
    }
}
