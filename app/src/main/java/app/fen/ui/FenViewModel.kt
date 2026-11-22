package app.fen.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fen.FenApp
import app.fen.data.FenBackupCodec
import app.fen.data.ImportBatch
import app.fen.data.Snapshot
import app.fen.data.Tx
import app.fen.data.TxType
import app.fen.data.exchange.FenCsv
import app.fen.data.exchange.FenTemplate
import app.fen.data.exchange.FileDecode
import app.fen.data.exchange.ImportOutcome
import app.fen.data.exchange.ImportParseException
import app.fen.data.exchange.ImportPlanner
import app.fen.data.exchange.ImportSession
import app.fen.data.exchange.ImportSource
import app.fen.data.exchange.MappingTarget
import app.fen.data.exchange.TransferPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class FenViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as FenApp).repository
    private val prefs = (app as FenApp).prefs
    val snapshot: StateFlow<Snapshot> = repository.snapshot

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _hiddenSuggestions = MutableStateFlow(prefs.hiddenSuggestions)
    val hiddenSuggestions: StateFlow<Set<String>> = _hiddenSuggestions.asStateFlow()

    private val _penalties = MutableStateFlow(prefs.suggestionPenalties)
    val suggestionPenalties: StateFlow<Map<String, Int>> = _penalties.asStateFlow()

    private val _searchHistory = MutableStateFlow(prefs.searchHistory)
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    fun shiftMonth(months: Long) {
        _month.value = _month.value.plusMonths(months)
    }

    fun jumpTo(target: YearMonth) {
        _month.value = target
    }

    fun setThemeMode(mode: Int) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun hideSuggestion(categoryId: String) {
        prefs.hiddenSuggestions = prefs.hiddenSuggestions + categoryId
        _hiddenSuggestions.value = prefs.hiddenSuggestions
    }

    /** 长按常用分类：降低它在推荐里的权重 */
    fun penalizeSuggestion(categoryId: String) {
        val current = prefs.suggestionPenalties
        prefs.suggestionPenalties = current + (categoryId to ((current[categoryId] ?: 0) + 1))
        _penalties.value = prefs.suggestionPenalties
    }

    fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        prefs.searchHistory = (listOf(q) + prefs.searchHistory.filter { it != q }).take(5)
        _searchHistory.value = prefs.searchHistory
    }

    fun clearSearchHistory() {
        prefs.searchHistory = emptyList()
        _searchHistory.value = emptyList()
    }

    fun addTransaction(
        type: TxType,
        amountMinor: Long,
        categoryId: String?,
        note: String,
        dateTime: LocalDateTime,
    ) {
        repository.addTransaction(
            type = type,
            amountMinor = amountMinor,
            categoryId = categoryId,
            note = note,
            occurredAt = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }

    fun updateTransaction(tx: Tx) = repository.updateTransaction(tx)

    fun delete(tx: Tx) = repository.deleteTransaction(tx.id)

    fun addCategory(name: String, type: TxType, icon: String, color: Long) =
        repository.addCategory(name, type, icon, color)

    fun updateCategory(category: app.fen.data.Category) = repository.updateCategory(category)

    fun deleteCategory(category: app.fen.data.Category) = repository.deleteCategory(category.id)

    fun mergeCategory(from: app.fen.data.Category, into: app.fen.data.Category) =
        repository.mergeCategory(from.id, into.id)

    fun deleteCategoryWithTransactions(category: app.fen.data.Category) =
        repository.deleteCategoryWithTransactions(category.id)

    fun categoryUsage(categoryId: String): Int = repository.transactionCount(categoryId)

    fun seedDemoData(count: Int = 400) {
        repository.seedDemoData(count)
    }

    fun clearTransactions() {
        repository.clearTransactions()
    }

    /* ------------------------------- 导入 / 导出 ------------------------------- */

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _batches = MutableStateFlow<List<ImportBatch>>(emptyList())
    val importBatches: StateFlow<List<ImportBatch>> = _batches.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    @Volatile
    private var pendingHash: String? = null

    init {
        refreshBatches()
    }

    fun refreshBatches() {
        viewModelScope.launch(Dispatchers.IO) {
            _batches.value = repository.importBatches()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** 只记下文件，不解析；等用户选好来源再点「解析」。 */
    fun pickImportFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = displayName(uri) ?: "导入文件"
            val state = _importState.value
            _importState.value = state.copy(
                stage = ImportStage.IDLE,
                pickedUri = uri,
                fileName = name,
                session = null,
                error = null,
                mapping = false,
                outcome = null,
                restored = null,
                undone = false,
            )
        }
    }

    /** 从「数据 → 恢复完整备份」直接进入备份恢复模式。 */
    fun startBackupRestore() {
        _importState.value = ImportUiState(source = ImportSource.FEN_JSON)
    }

    fun setImportSource(source: ImportSource) {
        _importState.value = _importState.value.copy(source = source)
    }

    fun startParse() {
        val state = _importState.value
        val uri = state.pickedUri ?: return
        _importState.value = state.copy(stage = ImportStage.PARSING, error = null, mapping = false)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取文件")
                val hash = sha256(bytes)
                val parsed = FileDecode.parse(state.fileName, bytes, state.source)
                    ?: error("无法识别这个文件，请确认是 xlsx / xls / csv，或 Fen 的 JSON 备份。")
                val session = FileDecode.sessionFrom(state.fileName, parsed, snapshot.value.categories)
                pendingHash = hash
                _importState.value = state.copy(
                    stage = ImportStage.READY,
                    session = session,
                    hashSeen = prefs.importHashes.contains(hash),
                    error = null,
                    mapping = false,
                )
            } catch (e: ImportParseException) {
                _importState.value = state.copy(stage = ImportStage.IDLE, error = e.message)
            } catch (e: Exception) {
                _importState.value = state.copy(
                    stage = ImportStage.IDLE,
                    error = e.message ?: "解析失败",
                )
            }
        }
    }

    /** 从预览/映射/结果回到第一步，保留已选文件和来源。 */
    fun backToSetup() {
        val state = _importState.value
        _importState.value = state.copy(
            stage = ImportStage.IDLE,
            session = null,
            error = null,
            mapping = false,
            outcome = null,
            restored = null,
            undone = false,
        )
    }

    fun saveTemplate(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(FenTemplate.content().toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")
            }.onSuccess {
                _message.value = "模板已保存"
            }.onFailure {
                _message.value = "保存失败：${it.message}"
            }
        }
    }

    fun cancelImport() {
        _importState.value = ImportUiState()
        pendingHash = null
    }

    fun setSkipDuplicates(skip: Boolean) {
        _importState.value = _importState.value.copy(skipDuplicates = skip)
    }

    fun setTransferPolicy(policy: TransferPolicy) {
        _importState.value = _importState.value.copy(transferPolicy = policy)
    }

    /** 概览 ↔ 分类映射 两步之间切换（返回键也走这里） */
    fun setMappingView(show: Boolean) {
        _importState.value = _importState.value.copy(mapping = show)
    }

    fun updateMapping(source: String, type: TxType, target: MappingTarget) {
        val state = _importState.value
        val session = state.session ?: return
        val mappings = session.mappings.map { mapping ->
            if (mapping.source == source && mapping.type == type) mapping.copy(target = target) else mapping
        }
        _importState.value = state.copy(session = session.copy(mappings = mappings))
    }

    fun confirmImport() {
        val state = _importState.value
        val session = state.session ?: return
        if (session.isBackup) return
        _importState.value = state.copy(stage = ImportStage.IMPORTING, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val batchId = UUID.randomUUID().toString()
                val plan = ImportPlanner.build(
                    session = session,
                    existingCategories = snapshot.value.categories,
                    skipDuplicates = state.skipDuplicates,
                    transferPolicy = state.transferPolicy,
                    batchId = batchId,
                    startSortOrder = repository.nextCategoryOrder(),
                )
                if (plan.transactions.isEmpty()) {
                    _importState.value = state.copy(
                        stage = ImportStage.DONE,
                        outcome = ImportOutcome(
                            batchId = null,
                            imported = 0,
                            newCategories = 0,
                            skipped = session.skipped.size,
                            skippedDuplicates = plan.skippedDuplicates,
                        ),
                    )
                    return@launch
                }
                repository.applyImport(
                    plan,
                    ImportBatch(
                        id = batchId,
                        source = session.source,
                        label = session.fileName,
                        createdAt = System.currentTimeMillis(),
                        rows = plan.transactions.size,
                    ),
                )
                val hash = pendingHash
                if (hash != null) prefs.importHashes = prefs.importHashes + hash
                _batches.value = repository.importBatches()
                _importState.value = state.copy(
                    stage = ImportStage.DONE,
                    outcome = ImportOutcome(
                        batchId = batchId,
                        imported = plan.transactions.size,
                        newCategories = plan.newCategories.size,
                        skipped = session.skipped.size + plan.skippedTransfers + plan.skippedDuplicates,
                        skippedDuplicates = plan.skippedDuplicates,
                    ),
                )
            } catch (e: Exception) {
                _importState.value = state.copy(
                    stage = ImportStage.ERROR,
                    error = e.message ?: "写入数据库失败",
                )
            }
        }
    }

    fun restoreBackup() {
        val state = _importState.value
        val backup = state.session?.backup ?: return
        _importState.value = state.copy(stage = ImportStage.IMPORTING, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.restoreBackup(backup)
                _batches.value = repository.importBatches()
                _importState.value = ImportUiState(
                    stage = ImportStage.DONE,
                    fileName = state.fileName,
                    restored = backup.transactions.size,
                )
            } catch (e: Exception) {
                _importState.value = state.copy(
                    stage = ImportStage.ERROR,
                    error = e.message ?: "恢复失败",
                )
            }
        }
    }

    fun undoImport() {
        val batchId = _importState.value.outcome?.batchId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteImportBatch(batchId)
            _batches.value = repository.importBatches()
            _importState.value = ImportUiState(stage = ImportStage.DONE, undone = true)
            _message.value = "已撤销这次导入"
        }
    }

    fun deleteImportBatch(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteImportBatch(id)
            _batches.value = repository.importBatches()
            _message.value = "已删除这次导入"
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val snap = snapshot.value
                val text = FenBackupCodec.encode(snap.categories, snap.transactions, System.currentTimeMillis())
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")
            }.onSuccess {
                _message.value = "备份已导出"
            }.onFailure {
                _message.value = "导出失败：${it.message}"
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val snap = snapshot.value
                val text = FenCsv.encode(snap.categories, snap.transactions)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")
            }.onSuccess {
                _message.value = "CSV 已导出"
            }.onFailure {
                _message.value = "导出失败：${it.message}"
            }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

enum class ImportStage { IDLE, PARSING, READY, IMPORTING, DONE, ERROR }

data class ImportUiState(
    val stage: ImportStage = ImportStage.IDLE,
    val pickedUri: Uri? = null,
    val source: ImportSource = ImportSource.AUTO,
    val fileName: String = "",
    val session: ImportSession? = null,
    val skipDuplicates: Boolean = false,
    val transferPolicy: TransferPolicy = TransferPolicy.SIGN,
    val hashSeen: Boolean = false,
    val error: String? = null,
    val outcome: ImportOutcome? = null,
    val restored: Int? = null,
    val undone: Boolean = false,
    /** true = 正在看分类映射列表；false = 概览 */
    val mapping: Boolean = false,
)
