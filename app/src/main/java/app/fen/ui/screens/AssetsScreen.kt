package app.fen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.fen.data.Account
import app.fen.data.AccountType
import app.fen.data.Snapshot
import app.fen.data.balance
import app.fen.ui.components.AmountText
import app.fen.ui.components.IconBadge
import app.fen.ui.components.RowDivider
import app.fen.ui.components.SectionLabel
import app.fen.ui.icons.accountIcon
import app.fen.ui.theme.Fen
import app.fen.util.formatMoney

@Composable
fun AssetsScreen(snapshot: Snapshot) {
    val balances = remember(snapshot) {
        snapshot.accounts.associate { it.id to it.balance(snapshot.transactions) }
    }
    val total = balances.values.sum()
    val liabilities = snapshot.accounts
        .filter { it.type == AccountType.CREDIT }
        .sumOf { balances[it.id] ?: 0L }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
    ) {
        item {
            Text("资产", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Fen.colors.ink,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "净资产",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(2.dp))
                    AmountText(
                        amount = total,
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "总资产",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                            Text(
                                formatMoney(total + liabilities),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                color = Color.White,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "总负债",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                            Text(
                                formatMoney(liabilities),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            SectionLabel("账户", modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Fen.colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    snapshot.accounts.forEachIndexed { index, account ->
                        AccountRow(account = account, balance = balances[account.id] ?: 0L)
                        if (index != snapshot.accounts.lastIndex) {
                            RowDivider(startInset = 66.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: Account, balance: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = accountIcon(account.type.name),
            color = Color(account.color),
            size = 38.dp,
            iconSize = 18.dp,
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.titleMedium)
            val typeLabel = when (account.type) {
                AccountType.CASH -> "现金"
                AccountType.DEBIT -> "储蓄卡"
                AccountType.CREDIT -> "信用卡"
                AccountType.VIRTUAL -> "虚拟账户"
            }
            if (typeLabel != account.name) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fen.colors.inkSoft,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatMoney(balance),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = if (balance < 0) Fen.colors.expense else Fen.colors.ink,
        )
    }
}
