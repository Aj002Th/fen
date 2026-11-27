package app.fen.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fen.BuildConfig
import app.fen.FenApp
import app.fen.autobook.capture.BatchItem
import app.fen.autobook.capture.BatchSelection
import app.fen.autobook.capture.ImageImportState
import app.fen.autobook.classify.DuplicateDetector
import app.fen.autobook.model.ParsedBill
import app.fen.autobook.ui.BatchConfirmSheet
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.ui.screens.AddTransactionScreen
import app.fen.ui.screens.AboutScreen
import app.fen.ui.screens.BackupScreen
import app.fen.ui.screens.BillsScreen
import app.fen.ui.screens.CategoryManagerScreen
import app.fen.ui.screens.ExportScreen
import app.fen.ui.screens.ImportHistoryScreen
import app.fen.ui.screens.ImportScreen
import app.fen.ui.screens.McpLogScreen
import app.fen.ui.screens.McpScreen
import app.fen.ui.screens.SearchScreen
import app.fen.ui.screens.SettingsScreen
import app.fen.ui.screens.StatsBoardSettingsScreen
import app.fen.ui.screens.StatsScreen
import app.fen.ui.screens.UpdateSheet
import app.fen.ui.theme.Fen
import app.fen.ui.theme.FenMotion
import app.fen.ui.theme.FenTheme
import app.fen.update.UpdateViewModel
import app.fen.update.hasAvailableUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private data class DraftSave(
    val type: TxType,
    val amountMinor: Long,
    val categoryId: String?,
    val note: String,
    val occurredAt: Long,
)

private fun tabIndex(entry: NavBackStackEntry?): Int? = when (entry?.destination?.route) {
    "bills" -> 0
    "stats" -> 1
    else -> null
}

private fun tabForward(initial: NavBackStackEntry, target: NavBackStackEntry): Boolean {
    val from = tabIndex(initial)
    val to = tabIndex(target)
    return if (from != null && to != null) to > from else true
}

/** 返回 null 时交给 NavHost 的横滑；根页互切仍然瞬间切换。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootEnterOrNone(
    isPop: Boolean,
): EnterTransition? =
    if (
        RootTabs.slidesWhenEntering(
            initialState.destination.route,
            targetState.destination.route,
            isPop,
        )
    ) {
        null
    } else {
        EnterTransition.None
    }

private val tabs = listOf(
    Tab("bills", "明细", Icons.Outlined.ReceiptLong),
    Tab("stats", "统计", Icons.Outlined.PieChart),
)

@Composable
fun FenRoot(
    openMcpRequest: Boolean = false,
    onMcpOpened: () -> Unit = {},
    viewModel: FenViewModel = viewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    FenTheme(darkTheme = darkTheme) {
        FenContent(viewModel, openMcpRequest, onMcpOpened)
    }
}

@Composable
private fun FenContent(
    viewModel: FenViewModel,
    openMcpRequest: Boolean,
    onMcpOpened: () -> Unit,
) {
    val fenSlide = tween<IntOffset>(FenMotion.navSlideMillis, easing = FastOutSlowInEasing)
    val fenFadeIn = tween<Float>(FenMotion.navFadeInMillis, easing = FastOutSlowInEasing)
    val fenFadeOut = tween<Float>(FenMotion.navFadeOutMillis, easing = FastOutSlowInEasing)
    val fenFloat = tween<Float>(FenMotion.durationMillis, easing = FastOutSlowInEasing)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val hiddenSuggestions by viewModel.hiddenSuggestions.collectAsStateWithLifecycle()
    val penalties by viewModel.suggestionPenalties.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val statsMode by viewModel.statsMode.collectAsStateWithLifecycle()
    val statsAnchor by viewModel.statsAnchor.collectAsStateWithLifecycle()
    val statsCustomRange by viewModel.statsCustomRange.collectAsStateWithLifecycle()
    val statsCategoryType by viewModel.statsCategoryType.collectAsStateWithLifecycle()
    val statsBoardConfig by viewModel.statsBoardConfig.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val batches by viewModel.importBatches.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updateViewModel: UpdateViewModel = viewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || RootTabs.isRoot(currentRoute)

    LaunchedEffect(openMcpRequest) {
        if (openMcpRequest) {
            navController.navigate("mcp") { launchSingleTop = true }
            onMcpOpened()
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Tx?>(null) }
    var draftBill by remember { mutableStateOf<ParsedBill?>(null) }
    var draftOpenedAt by remember { mutableStateOf(0L) }
    var batchItems by remember { mutableStateOf<List<BatchItem>>(emptyList()) }
    var batchSelected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var batchEditIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDraftSave by remember { mutableStateOf<DraftSave?>(null) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var devUnlocked by remember { mutableStateOf(false) }
    val addOffset = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val smartImport = remember(context) { (context.applicationContext as FenApp).imageImport }
    val smartImportState by smartImport.state.collectAsStateWithLifecycle()
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.size == 1) {
            smartImport.submit(uris.first())
        } else if (uris.size > 1) {
            smartImport.submitAll(uris)
        }
    }

    LaunchedEffect(smartImportState) {
        when (val current = smartImportState) {
            is ImageImportState.Error -> {
                android.util.Log.w("SmartImport", "state error=${current.message}")
                Toast.makeText(context, current.message, Toast.LENGTH_SHORT).show()
                smartImport.consume()
            }

            is ImageImportState.Ready -> {
                android.util.Log.d(
                    "SmartImport",
                    "ready amount=${current.bill.amountMinor} merchant=${current.bill.merchant}",
                )
                batchItems = emptyList()
                batchSelected = emptySet()
                batchEditIndex = null
                draftBill = current.bill
                draftOpenedAt = System.currentTimeMillis()
                smartImport.consume()
            }

            is ImageImportState.ReadyBatch -> {
                draftBill = null
                batchItems = current.items
                batchSelected = BatchSelection.initialSelection(
                    current.items,
                    snapshot.transactions,
                )
                batchEditIndex = null
            }

            else -> Unit
        }
    }

    fun saveDraft(save: DraftSave, andContinue: Boolean = false) {
        viewModel.addTransactionAt(
            type = save.type,
            amountMinor = save.amountMinor,
            categoryId = save.categoryId,
            note = save.note,
            occurredAtMillis = save.occurredAt,
        )
        if (save.categoryId != null) {
            viewModel.rememberMerchant(save.note, save.type, save.categoryId)
        }
        pendingDraftSave = null
        showDuplicateDialog = false
        // 「再记一笔」保留当前确认页继续录，与手工记账一致；正常保存才关闭。
        if (!andContinue) {
            scope.launch {
                addOffset.animateTo(1f, fenFloat)
                draftBill = null
            }
        }
    }

    fun dismissDraft() {
        scope.launch {
            addOffset.animateTo(1f, fenFloat)
            draftBill = null
        }
    }

    fun dismissBatchEditor() {
        scope.launch {
            addOffset.animateTo(1f, fenFloat)
            batchEditIndex = null
        }
    }

    fun closeBatch() {
        smartImport.consume()
        batchItems = emptyList()
        batchSelected = emptySet()
        batchEditIndex = null
    }

    suspend fun dismissAdd() {
        addOffset.animateTo(1f, fenFloat)
        showAdd = false
    }

    PredictiveBackHandler(enabled = showAdd) { progress ->
        try {
            progress.collect { event -> addOffset.snapTo(event.progress) }
            dismissAdd()
        } catch (_: CancellationException) {
            addOffset.animateTo(0f, fenFloat)
        }
    }

    BackHandler(enabled = draftBill != null) {
        val age = System.currentTimeMillis() - draftOpenedAt
        android.util.Log.d("SmartImport", "back draftAge=$age")
        // 相册/分享返回时可能补发一次 BACK，刚打开时忽略，避免把 draft 立刻清掉。
        if (age >= 800) {
            dismissDraft()
        }
    }

    BackHandler(enabled = batchEditIndex != null) {
        dismissBatchEditor()
    }

    LaunchedEffect(showAdd, draftBill, batchEditIndex) {
        if (showAdd || draftBill != null || batchEditIndex != null) {
            addOffset.animateTo(0f, fenFloat)
        }
    }

    LaunchedEffect(Unit) {
        updateViewModel.checkOnStart()
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Fen.colors.bg,
            bottomBar = {
                if (showBottomBar) {
                    FenBottomBar(
                        currentRoute = currentRoute,
                        onSelect = { route ->
                            val current = backStackEntry
                            if (currentRoute != route && RootTabs.isRoot(currentRoute) && current != null) {
                                navController.navigate(route) {
                                    popUpTo(current.destination.id) {
                                        inclusive = true
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onAdd = {
                            editingTx = null
                            showAdd = true
                        },                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "bills",
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    val dir = if (tabForward(initialState, targetState)) 1 else -1
                    slideInHorizontally(fenSlide) { dir * it / 5 } + fadeIn(fenFadeIn)
                },
                exitTransition = { fadeOut(fenFadeOut) },
                popEnterTransition = {
                    slideInHorizontally(fenSlide) { -it / 6 } + fadeIn(fenFadeIn)
                },
                popExitTransition = {
                    slideOutHorizontally(fenSlide) { it / 5 } + fadeOut(fenFadeOut)
                },
            ) {
                composable(
                    "bills",
                    enterTransition = { rootEnterOrNone(isPop = false) },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { rootEnterOrNone(isPop = true) },
                    popExitTransition = { ExitTransition.None },
                ) {
                    FenPage {
                        BillsScreen(
                            snapshot = snapshot,
                            month = month,
                            onShiftMonth = viewModel::shiftMonth,
                            onToday = { viewModel.jumpTo(java.time.YearMonth.now()) },
                            onDelete = viewModel::delete,
                            onEdit = { tx ->
                                editingTx = tx
                                showAdd = true
                            },
                            onOpenSearch = { navController.navigate("search") },
                            onOpenSettings = { navController.navigate("settings") },
                        )
                    }
                }
                composable(
                    "stats",
                    enterTransition = { rootEnterOrNone(isPop = false) },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { rootEnterOrNone(isPop = true) },
                    popExitTransition = { ExitTransition.None },
                ) {
                    FenPage {
                        StatsScreen(
                            snapshot = snapshot,
                            statsMode = statsMode,
                            statsAnchor = statsAnchor,
                            customRange = statsCustomRange,
                            categoryType = statsCategoryType,
                            boardConfig = statsBoardConfig,
                            onSetMode = viewModel::setStatsMode,
                            onShift = viewModel::shiftStatsPeriod,
                            onSetCustomRange = viewModel::setStatsCustomRange,
                            onSetCategoryType = viewModel::setStatsCategoryType,
                            shouldPromptForRange = viewModel::shouldPromptForStatsRange,
                            onTouchRange = viewModel::touchStatsRange,
                            onOpenSettings = { navController.navigate("settings") },
                        )
                    }
                }
                composable("settings") {
                    FenPage {
                        SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = viewModel::setThemeMode,
                            onBack = { navController.popBackStack() },
                            onOpenCategories = { navController.navigate("categories") },
                            onOpenMcp = { navController.navigate("mcp") },
                            onOpenStatsBoard = { navController.navigate("stats-board") },
                            onOpenImport = { navController.navigate("import") },
                            onOpenExport = { navController.navigate("export") },
                            onOpenBackup = { navController.navigate("backup") },
                            onOpenImageImport = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            devUnlocked = devUnlocked,
                            hasUpdate = hasAvailableUpdate(updateState),
                            onOpenAbout = { navController.navigate("about") },
                            showDebug = BuildConfig.DEBUG,
                            onSeedDemo = {
                                scope.launch(Dispatchers.IO) { viewModel.seedDemoData(400) }
                            },
                            onClearData = {
                                scope.launch(Dispatchers.IO) { viewModel.clearTransactions() }
                            },
                        )
                    }
                }
                composable("search") {
                    FenPage {
                        SearchScreen(
                            snapshot = snapshot,
                            history = searchHistory,
                            onSaveHistory = viewModel::addSearchHistory,
                            onClearHistory = viewModel::clearSearchHistory,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable("stats-board") {
                    FenPage {
                        StatsBoardSettingsScreen(
                            config = statsBoardConfig,
                            onBack = { navController.popBackStack() },
                            onToggle = viewModel::setStatsSectionVisible,
                            onMove = viewModel::moveStatsSection,
                            onRankingLimit = viewModel::setStatsRankingLimit,
                            onSummaryShowEmpty = viewModel::setStatsSummaryShowEmpty,
                            onReset = viewModel::resetStatsBoard,
                        )
                    }
                }
                composable("categories") {
                    FenPage {
                        CategoryManagerScreen(
                            snapshot = snapshot,
                            onBack = { navController.popBackStack() },
                            onAdd = { name, type, icon, color ->
                                viewModel.addCategory(name, type, icon, color)
                            },
                            onUpdate = { category -> viewModel.updateCategory(category) },
                            onReorder = viewModel::reorderCategories,
                            onDelete = { category -> viewModel.deleteCategory(category) },
                            onMerge = { from, into -> viewModel.mergeCategory(from, into) },
                            onDeleteWithTransactions = { category ->
                                viewModel.deleteCategoryWithTransactions(category)
                            },
                            countFor = { id -> viewModel.categoryUsage(id) },
                        )
                    }
                }
                composable("export") {
                    FenPage {
                        ExportScreen(
                            snapshot = snapshot,
                            onBack = { navController.popBackStack() },
                            onShare = viewModel::shareCsv,
                        )
                    }
                }
                composable("backup") {
                    FenPage {
                        BackupScreen(
                            onBack = { navController.popBackStack() },
                            onExportBackup = viewModel::exportBackup,
                            onRestore = {
                                viewModel.startBackupRestore()
                                navController.navigate("import")
                            },
                        )
                    }
                }
                composable("import") {
                    FenPage {
                        ImportScreen(
                            state = importState,
                            categories = snapshot.categories,
                            batchCount = batches.size,
                            onPickFile = viewModel::pickImportFile,
                            onShareTemplate = viewModel::shareTemplate,
                            onSetSource = viewModel::setImportSource,
                            onStartParse = viewModel::startParse,
                            onExit = {
                                viewModel.cancelImport()
                                navController.popBackStack()
                            },
                            onBackToSetup = viewModel::backToSetup,
                            onOpenHistory = { navController.navigate("import-history") },
                            onSetSkipDuplicates = viewModel::setSkipDuplicates,
                            onSetTransferPolicy = viewModel::setTransferPolicy,
                            onUpdateMapping = viewModel::updateMapping,
                            onCreateUncategorized = viewModel::createUncategorizedCategories,
                            onSetMappingView = viewModel::setMappingView,
                            onConfirm = viewModel::confirmImport,
                            onRestore = viewModel::restoreBackup,
                            onUndo = viewModel::undoImport,
                            onDone = {
                                viewModel.cancelImport()
                                navController.popBackStack()
                            },
                        )
                    }
                }
                composable("import-history") {
                    FenPage {
                        ImportHistoryScreen(
                            batches = batches,
                            onBack = { navController.popBackStack() },
                            onDelete = viewModel::deleteImportBatch,
                        )
                    }
                }
                composable("mcp") {
                    FenPage {
                        McpScreen(
                            viewModel = viewModel(),
                            onBack = { navController.popBackStack() },
                            onOpenLog = { navController.navigate("mcp/log") },
                        )
                    }
                }
                composable("mcp/log") {
                    FenPage {
                        McpLogScreen(
                            viewModel = viewModel(),
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable("about") {
                    FenPage {
                        AboutScreen(
                            versionName = BuildConfig.VERSION_NAME,
                            updateState = updateState,
                            onBack = { navController.popBackStack() },
                            onCheckUpdate = updateViewModel::openAndCheck,
                            onUnlockDev = {
                                devUnlocked = true
                            },
                        )
                    }
                }
            }
        }

        val batchEdit = batchEditIndex?.let { index ->
            batchItems.getOrNull(index)?.bill?.let { bill -> index to bill }
        }
        val editingBatchBill = batchEdit?.second
        if (showAdd || draftBill != null || editingBatchBill != null || addOffset.value < 1f) {
            AddTransactionScreen(
                snapshot = snapshot,
                initial = editingTx,
                draft = draftBill ?: editingBatchBill,
                allowContinue = editingBatchBill == null,
                showManageCategories = editingBatchBill == null,
                suggestionPenalties = penalties,
                onPenalizeSuggestion = viewModel::penalizeSuggestion,
                modifier = Modifier.graphicsLayer {
                    translationY = addOffset.value * size.height
                },
                onClose = {
                    when {
                        editingBatchBill != null -> dismissBatchEditor()
                        draftBill != null -> dismissDraft()
                        else -> scope.launch { dismissAdd() }
                    }
                },
                onManageCategories = {
                    scope.launch {
                        dismissAdd()
                        draftBill = null
                        batchEditIndex = null
                        navController.navigate("categories")
                    }
                },
                onSave = { type, amountMinor, categoryId, note, dateTime, andContinue ->
                    if (batchEdit != null) {
                        val (index, bill) = batchEdit
                        val occurredAt = dateTime
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        val updated = bill.copy(
                            type = type,
                            amountMinor = amountMinor,
                            categoryId = categoryId,
                            merchant = note.trim(),
                            occurredAt = occurredAt,
                        )
                        val next = batchItems.toMutableList()
                        next[index] = BatchItem(updated, null)
                        batchItems = next
                        dismissBatchEditor()
                    } else if (draftBill != null) {
                        val occurredAt = dateTime
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        val candidate = draftBill!!.copy(
                            type = type,
                            amountMinor = amountMinor,
                            categoryId = categoryId,
                            merchant = note.trim(),
                            occurredAt = occurredAt,
                        )
                        val save = DraftSave(
                            type = type,
                            amountMinor = amountMinor,
                            categoryId = categoryId,
                            note = note.trim(),
                            occurredAt = occurredAt,
                        )
                        if (DuplicateDetector.find(candidate, snapshot.transactions) != null) {
                            pendingDraftSave = save
                            showDuplicateDialog = true
                        } else {
                            saveDraft(save, andContinue)
                        }
                    } else {
                        val editing = editingTx
                        if (editing == null) {
                            viewModel.addTransaction(type, amountMinor, categoryId, note, dateTime)
                        } else {
                            viewModel.updateTransaction(
                                editing.copy(
                                    type = type,
                                    amount = amountMinor,
                                    categoryId = categoryId,
                                    note = note.trim(),
                                    occurredAt = dateTime
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli(),
                                )
                            )
                        }
                        if (!andContinue) scope.launch { dismissAdd() }
                    }
                },
            )
        }

        if (showDuplicateDialog && pendingDraftSave != null) {
            AlertDialog(
                onDismissRequest = {
                    showDuplicateDialog = false
                    pendingDraftSave = null
                },
                title = { Text("可能重复") },
                text = { Text("已有一笔金额和时间相近的记录，仍要保存吗？") },
                confirmButton = {
                    TextButton(
                        onClick = { pendingDraftSave?.let { saveDraft(it) } }
                    ) { Text("继续保存") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDuplicateDialog = false
                            pendingDraftSave = null
                        }
                    ) { Text("取消") }
                },
            )
        }

        val readyBatch = smartImportState as? ImageImportState.ReadyBatch
        if (readyBatch != null && batchEditIndex == null && batchItems.isNotEmpty()) {
            BatchConfirmSheet(
                items = batchItems,
                categories = snapshot.categories,
                existing = snapshot.transactions,
                selected = batchSelected,
                onSelectedChange = { batchSelected = it },
                onEdit = { index ->
                    batchSelected = batchSelected + index
                    batchEditIndex = index
                },
                onSaveAll = { bills ->
                    bills.forEach { bill ->
                        val type = bill.type ?: TxType.EXPENSE
                        val categoryId = bill.categoryId ?: return@forEach
                        viewModel.addTransactionAt(
                            type = type,
                            amountMinor = bill.amountMinor,
                            categoryId = categoryId,
                            note = bill.merchant,
                            occurredAtMillis = bill.occurredAt,
                        )
                        viewModel.rememberMerchant(bill.merchant, type, categoryId)
                    }
                    closeBatch()
                },
                onDismiss = { closeBatch() },
            )
        }

        val loading = smartImportState as? ImageImportState.Loading
        if (loading != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = RoundedCornerShape(18.dp), color = Fen.colors.surface) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (loading.total > 0) {
                                "识别中 ${loading.completed}/${loading.total}"
                            } else {
                                "识别中…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (updateState.sheetVisible) {
            UpdateSheet(
                state = updateState,
                isDevBuild = updateViewModel.isDevBuild,
                onDismiss = updateViewModel::closeSheet,
                onDownload = updateViewModel::downloadAndInstall,
                onCancel = updateViewModel::cancelDownload,
                onRetry = updateViewModel::retry,
                onOpenPermission = updateViewModel::openInstallPermission,
                onResumeAfterPermission = updateViewModel::resumeAfterPermission,
                onRecheck = updateViewModel::openAndCheck,
            )
        }
    }
}

@Composable
private fun FenPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Fen.colors.bg)) {
        content()
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
            val gap = 2.dp
            BoxWithConstraints(
                Modifier
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .height(52.dp),
            ) {
                val tabWidth = (maxWidth - gap * (tabs.size - 1)) / tabs.size
                val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
                val slidingIndex by animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    label = "tabIndicator",
                )
                val accent = Fen.colors.link
                val pillColor = accent.copy(alpha = if (Fen.colors.isDark) 0.22f else 0.12f)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(((tabWidth + gap) * slidingIndex).roundToPx(), 0) }
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(pillColor),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        val tint by animateColorAsState(
                            targetValue = if (selected) accent else Fen.colors.inkSoft,
                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                            label = "tabTint",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(
                                    interactionSource = remember(tab.route) { MutableInteractionSource() },
                                    indication = null,
                                ) { onSelect(tab.route) }
                                .padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(21.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = tint,
                            )
                        }
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

