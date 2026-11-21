package app.fen.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** 图标选择器里可选的 key（顺序即展示顺序） */
val CategoryIconKeys = listOf(
    "food", "meal", "transport", "car", "shopping", "clothes",
    "digital", "necessity", "beauty", "entertainment", "movie", "music",
    "housing", "medical", "study", "work", "sport", "pet",
    "child", "social", "utilities", "network", "salary", "bonus",
    "invest", "other_income", "saving",
)

/**
 * 分类 → 线性图标。数据库里存的是 key，不是 emoji，
 * 这样换图标集 / 换风格时只改这一个文件。
 */
fun categoryIcon(key: String?): ImageVector = when (key) {
    "food" -> Icons.Outlined.Restaurant
    "meal" -> Icons.Outlined.LocalCafe
    "snack" -> Icons.Outlined.Cake
    "transport" -> Icons.Outlined.DirectionsBus
    "car" -> Icons.Outlined.DirectionsCar
    "fuel" -> Icons.Outlined.LocalGasStation
    "flight" -> Icons.Outlined.Flight
    "shopping" -> Icons.Outlined.ShoppingCart
    "clothes" -> Icons.Outlined.Checkroom
    "digital" -> Icons.Outlined.PhoneAndroid
    "necessity" -> Icons.Outlined.Inventory2
    "beauty" -> Icons.Outlined.Spa
    "entertainment" -> Icons.Outlined.SportsEsports
    "movie" -> Icons.Outlined.Movie
    "music" -> Icons.Outlined.MusicNote
    "housing" -> Icons.Outlined.Home
    "medical" -> Icons.Outlined.MedicalServices
    "study" -> Icons.Outlined.MenuBook
    "work" -> Icons.Outlined.Work
    "sport" -> Icons.Outlined.FitnessCenter
    "pet" -> Icons.Outlined.Pets
    "child" -> Icons.Outlined.ChildCare
    "social" -> Icons.Outlined.Call
    "utilities" -> Icons.Outlined.Bolt
    "network" -> Icons.Outlined.Wifi
    "salary" -> Icons.Outlined.Payments
    "bonus" -> Icons.Outlined.CardGiftcard
    "invest" -> Icons.Outlined.TrendingUp
    "other_income" -> Icons.Outlined.AttachMoney
    "debt" -> Icons.Outlined.AccountBalance
    "saving" -> Icons.Outlined.AccountBalanceWallet
    else -> Icons.Outlined.Category
}
