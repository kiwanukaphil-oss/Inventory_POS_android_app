package com.kline.inventorypos.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kline.inventorypos.core.designsystem.PosCard
import com.kline.inventorypos.core.designsystem.Primary50
import com.kline.inventorypos.core.designsystem.Primary700
import com.kline.inventorypos.core.designsystem.Primary800
import com.kline.inventorypos.core.designsystem.Primary950
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.designsystem.SquareActionIcon
import com.kline.inventorypos.core.session.PosSession

private data class MenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val permission: String? = null,
    val alternativePermission: String? = null,
)

@Composable
fun MoreScreen(
    session: PosSession,
    onChangeBranch: () -> Unit,
    onLogout: () -> Unit,
    onMessage: (String) -> Unit,
    onCustomers: () -> Unit,
    onGiftVouchers: () -> Unit,
    onCash: () -> Unit,
    onReconciliation: () -> Unit,
    onExpenses: () -> Unit,
    onApprovals: () -> Unit,
    onDocuments: () -> Unit,
    onAdministration: () -> Unit,
) {
    val groups = listOf(
        "Customers & growth" to listOf(
            MenuItem("Customers", "Loyalty, credit and prepaid accounts", Icons.Outlined.People, "customers.view"),
            MenuItem("Gift vouchers", "Issue, verify and manage liability", Icons.Outlined.CardGiftcard, "gift_vouchers.view"),
        ),
        "Operations" to listOf(
            MenuItem("Products & catalog", "Products, variants, brands and categories", Icons.Outlined.Inventory2, "products.view"),
            MenuItem("Cash book", "Movements, handover and Z report", Icons.Outlined.Payments, "cash.view", "sales.create"),
            MenuItem("End of day", "Channel counts, sign-off and daily close", Icons.Outlined.Assessment, "cash.view", "cash.reconcile"),
            MenuItem("Expenses", "Operating costs and payment references", Icons.AutoMirrored.Outlined.ReceiptLong, "expenses.view"),
            MenuItem("Approvals", "Review high-risk exception requests", Icons.Outlined.Approval, "sales.approve"),
            MenuItem("Business documents", "Quotes, invoices and receipts", Icons.Outlined.BusinessCenter, "documents.view"),
        ),
        "Insights & administration" to listOf(
            MenuItem("Reports", "Daily sales and payment performance", Icons.Outlined.Assessment, "reports.sales"),
            MenuItem("Settings & team", "Branches, users, tax and printers", Icons.Outlined.Settings, "settings.view", "users.view"),
        ),
    ).map { (title, items) -> title to items.filter {
        it.permission == null || session.user.hasPermission(it.permission) ||
            it.alternativePermission?.let(session.user::hasPermission) == true
    } }
        .filter { it.second.isNotEmpty() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ProfileBand(session) }
        groups.forEach { (title, items) ->
            item {
                PosCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column {
                        Text(
                            title.uppercase(),
                            modifier = Modifier.padding(start = 13.dp, top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500,
                        )
                        items.forEachIndexed { index, item ->
                            MenuRow(item) {
                                if (item.title == "Customers") onCustomers()
                                else if (item.title == "Gift vouchers") onGiftVouchers()
                                else if (item.title == "Cash book") onCash()
                                else if (item.title == "End of day" || item.title == "Reports") onReconciliation()
                                else if (item.title == "Expenses") onExpenses()
                                else if (item.title == "Approvals") onApprovals()
                                else if (item.title == "Business documents") onDocuments()
                                else if (item.title == "Settings & team") onAdministration()
                                else onMessage("${item.title} opens as a permission-aware workspace")
                            }
                            if (index != items.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 56.dp).background(Slate100).size(height = 1.dp, width = 340.dp))
                        }
                    }
                }
            }
        }
        item {
            PosCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    Text(
                        "ACCOUNT",
                        modifier = Modifier.padding(start = 13.dp, top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500,
                    )
                    MenuRow(MenuItem("Switch branch", "Currently ${session.branch.name}", Icons.Outlined.SwapHoriz), onChangeBranch)
                    Box(Modifier.fillMaxWidth().padding(start = 56.dp).background(Slate100).size(height = 1.dp, width = 340.dp))
                    MenuRow(MenuItem("Sign out", "Securely end this device session", Icons.AutoMirrored.Outlined.Logout), onLogout)
                }
            }
        }
    }
}

@Composable
private fun ProfileBand(session: PosSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Primary950, Primary800)))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Text(session.user.initials, color = Primary950, style = MaterialTheme.typography.titleSmall)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(session.user.fullName, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                "${session.user.roleName} · ${session.branch.name}${if (session.isDemo) " · Demo" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = .65f),
            )
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareActionIcon(item.icon, backgroundColor = Primary50, contentColor = Primary800)
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = Slate500)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Slate500)
    }
}
