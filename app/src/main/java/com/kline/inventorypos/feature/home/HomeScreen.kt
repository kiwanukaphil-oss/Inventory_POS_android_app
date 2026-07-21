package com.kline.inventorypos.feature.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kline.inventorypos.core.common.formatUgx
import com.kline.inventorypos.core.designsystem.Amber100
import com.kline.inventorypos.core.designsystem.Amber50
import com.kline.inventorypos.core.designsystem.Amber700
import com.kline.inventorypos.core.designsystem.MoneyFontFamily
import com.kline.inventorypos.core.designsystem.MoneyGreen100
import com.kline.inventorypos.core.designsystem.MoneyGreen700
import com.kline.inventorypos.core.designsystem.PosCard
import com.kline.inventorypos.core.designsystem.Primary100
import com.kline.inventorypos.core.designsystem.Primary50
import com.kline.inventorypos.core.designsystem.Primary600
import com.kline.inventorypos.core.designsystem.Primary700
import com.kline.inventorypos.core.designsystem.Primary800
import com.kline.inventorypos.core.designsystem.Primary950
import com.kline.inventorypos.core.designsystem.SectionHeader
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.designsystem.SquareActionIcon
import com.kline.inventorypos.core.designsystem.StatusPill
import com.kline.inventorypos.core.model.SalesPeriodReport
import com.kline.inventorypos.core.session.PosSession

@Composable
fun HomeScreen(
    session: PosSession,
    onNewSale: () -> Unit,
    onStock: () -> Unit,
    onActivity: () -> Unit,
    onMore: () -> Unit,
    onProducts: () -> Unit,
    onCash: () -> Unit,
    onApprovals: () -> Unit,
    sales: SalesPeriodReport?,
    reportLoading: Boolean,
    lowStockCount: Int,
    approvalCount: Int?,
) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item { HomeHeader(session) }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(14.dp))
                RevenueHero(session.register != null, sales, reportLoading)
                SectionHeader("Quick actions", action = "View all", onAction = onMore)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction("New sale", Icons.Outlined.PointOfSale, Modifier.weight(1f), onNewSale)
                    QuickAction("Scan stock", Icons.Outlined.QrCodeScanner, Modifier.weight(1f), onStock)
                    QuickAction("Products", Icons.Outlined.AddBox, Modifier.weight(1f), onProducts)
                    QuickAction("Cash in/out", Icons.Outlined.Payments, Modifier.weight(1f), onCash)
                }
                SectionHeader("Needs attention", action = "Open stock", onAction = onStock)
                AttentionCard(lowStockCount, approvalCount, onStock, onApprovals)
                SectionHeader("Sales pulse", action = "Details", onAction = onActivity)
                SalesPulseCard(sales)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader(session: PosSession) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(Primary950, Primary700)), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("KL", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text("Inventory POS", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Primary600, CircleShape))
                    Text("  ${session.branch.name} · ${if (session.register != null) "Register open" else "No register"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.size(36.dp).background(Primary950, CircleShape), contentAlignment = Alignment.Center) {
                Text(session.user.initials, color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RevenueHero(registerOpen: Boolean, report: SalesPeriodReport?, loading: Boolean) {
    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Primary950, Primary800, Primary700)), RoundedCornerShape(22.dp)).padding(20.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Last 7 days", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .72f))
                StatusPill(if (registerOpen) "Register open" else "Register closed", containerColor = Color.White.copy(alpha = .12f), contentColor = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            if (loading && report == null) CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
            Text(report?.let { formatUgx(it.netRevenue) } ?: "Sales summary restricted", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MoneyFontFamily), color = Color.White, fontWeight = FontWeight.Bold)
            Text(if (report == null) "Report permission is required" else "Net revenue", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .68f))
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .13f)))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroMetric(report?.transactions?.toString() ?: "—", "Transactions", Modifier.weight(1f))
                HeroMetric(report?.let { formatUgx(it.averageSale) } ?: "—", "Average basket", Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun HeroMetric(value: String, label: String, modifier: Modifier) { Column(modifier) { Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Text(label, color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    PosCard(modifier.clickable(onClick = onClick)) { Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { SquareActionIcon(icon); Spacer(Modifier.height(8.dp)); Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2) } }
}

@Composable
private fun AttentionCard(lowStock: Int, approvals: Int?, onStock: () -> Unit, onApprovals: () -> Unit) {
    PosCard(Modifier.fillMaxWidth()) {
        if (lowStock == 0 && (approvals ?: 0) == 0) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { SquareActionIcon(Icons.Outlined.Notifications, backgroundColor = MoneyGreen100, contentColor = MoneyGreen700); Text("No stock or approval exceptions need attention.", Modifier.padding(start = 11.dp), color = MoneyGreen700) }
        } else {
            if (lowStock > 0) AttentionRow(Icons.Outlined.WarningAmber, "Low stock", "$lowStock variants need review", lowStock.toString(), Amber50, Amber700, Amber100, onStock)
            if (lowStock > 0 && approvals != null && approvals > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100))
            if (approvals != null && approvals > 0) AttentionRow(Icons.Outlined.Approval, "Approvals pending", "Manager decision queue", approvals.toString(), Primary50, Primary800, Primary100, onApprovals)
        }
    }
}

@Composable
private fun AttentionRow(icon: ImageVector, title: String, subtitle: String, count: String, iconBackground: Color, iconColor: Color, countBackground: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SquareActionIcon(icon, backgroundColor = iconBackground, contentColor = iconColor)
        Column(Modifier.padding(start = 11.dp).weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slate500) }
        StatusPill(count, containerColor = countBackground, contentColor = iconColor)
    }
}

@Composable
private fun SalesPulseCard(report: SalesPeriodReport?) {
    PosCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Last 7 days", style = MaterialTheme.typography.titleSmall); Text(report?.let { "${formatUgx(it.netRevenue)} total" } ?: "No report data", style = MaterialTheme.typography.bodySmall, color = Slate500) }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(88.dp)) {
                repeat(3) { index -> val y = size.height * (index + 1) / 4; drawLine(Slate100, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f) }
                val values = report?.daily?.map { it.revenue }.orEmpty()
                val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1
                val points = values.map { 1f - (it.toFloat() / maximum.toFloat() * .85f) }
                val path = Path()
                points.forEachIndexed { index, value -> val x = if (points.size <= 1) size.width / 2 else size.width * index / (points.size - 1); val y = size.height * value; if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                if (points.isNotEmpty()) drawPath(path, color = Primary700, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Outlined.TrendingUp, null, Modifier.size(16.dp), tint = if (report == null) Slate500 else MoneyGreen700); Text(if (report == null) "  Report access controls this summary" else "  ${report.transactions} transactions in this period", style = MaterialTheme.typography.labelSmall, color = if (report == null) Slate500 else MoneyGreen700) }
        }
    }
}
