package app.fen.data

/** 支出 / 收入 / 转账 */
enum class TxType { EXPENSE, INCOME, TRANSFER }

enum class AccountType { CASH, DEBIT, CREDIT, VIRTUAL }

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val initialBalance: Long,
    val color: Long,
    val sortOrder: Int,
    val archived: Boolean,
)

data class Category(
    val id: String,
    val parentId: String?,
    val name: String,
    val type: TxType,
    val icon: String,
    val color: Long,
    val sortOrder: Int,
) {
    val isParent: Boolean get() = parentId == null
}

data class Tx(
    val id: String,
    val type: TxType,
    val amount: Long,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val note: String,
    val occurredAt: Long,
)

data class Snapshot(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Tx> = emptyList(),
    val budgets: Map<String, Long> = emptyMap(),
)
