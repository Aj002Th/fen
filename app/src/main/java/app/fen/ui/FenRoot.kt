package app.fen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fen.ui.screens.AddTransactionScreen
import app.fen.ui.screens.AssetsScreen
import app.fen.ui.screens.BillsScreen
import app.fen.ui.screens.SettingsScreen
import app.fen.ui.screens.StatsScreen
import app.fen.ui.theme.Fen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("bills", "明细", Icons.Outlined.ReceiptLong),
    Tab("stats", "统计", Icons.Outlined.PieChart),
    Tab("assets", "资产", Icons.Outlined.AccountBalanceWallet),
    Tab("settings", "设置", Icons.Outlined.Settings),
)

@Composable
fun FenRoot(viewModel: FenViewModel = viewModel()) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var showAdd by remember { mutableStateOf(false) }

    BackHandler(enabled = showAdd) { showAdd = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Fen.colors.bg,
            bottomBar = {
                FenBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onAdd = { showAdd = true },
                )
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "bills",
                modifier = Modifier.padding(innerPadding),
            ) {
                composable("bills") {
                    BillsScreen(
                        snapshot = snapshot,
                        month = month,
                        onShiftMonth = viewModel::shiftMonth,
                        onToday = { viewModel.jumpTo(java.time.YearMonth.now()) },
                        onDelete = viewModel::delete,
                    )
                }
                composable("stats") {
                    StatsScreen(
                        snapshot = snapshot,
                        month = month,
                        onShiftMonth = viewModel::shiftMonth,
                    )
                }
                composable("assets") {
                    AssetsScreen(snapshot = snapshot)
                }
                composable("settings") {
                    SettingsScreen()
                }
            }
        }

        AnimatedVisibility(
            visible = showAdd,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            AddTransactionScreen(
                snapshot = snapshot,
                onClose = { showAdd = false },
                onSave = { type, amountMinor, accountId, categoryId, note, date ->
                    viewModel.addTransaction(type, amountMinor, accountId, categoryId, note, date)
                    showAdd = false
                },
            )
        }
    }
}

@Composable
private fun FenBottomBar(currentRoute: String?, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Fen.colors.surface,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Fen.colors.surfaceMuted else Color.Transparent)
                            .clickable { onSelect(tab.route) }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) Fen.colors.ink else Fen.colors.inkSoft,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Fen.colors.ink else Fen.colors.inkSoft,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Fen.colors.ink)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "记一笔",
                tint = if (Fen.colors.isDark) Color(0xFF15171B) else Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
