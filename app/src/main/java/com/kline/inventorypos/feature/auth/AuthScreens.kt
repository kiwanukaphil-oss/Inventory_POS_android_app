package com.kline.inventorypos.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kline.inventorypos.core.common.AmountGroupingVisualTransformation
import com.kline.inventorypos.core.common.formatUgx
import com.kline.inventorypos.core.common.parseAmountInput
import com.kline.inventorypos.core.common.sanitizeAmountInput
import com.kline.inventorypos.core.designsystem.Error50
import com.kline.inventorypos.core.designsystem.Error700
import com.kline.inventorypos.core.designsystem.MoneyGreen50
import com.kline.inventorypos.core.designsystem.MoneyGreen700
import com.kline.inventorypos.core.designsystem.PosCard
import com.kline.inventorypos.core.designsystem.Primary100
import com.kline.inventorypos.core.designsystem.Primary50
import com.kline.inventorypos.core.designsystem.Primary700
import com.kline.inventorypos.core.designsystem.Primary800
import com.kline.inventorypos.core.designsystem.Primary950
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.designsystem.SquareActionIcon
import com.kline.inventorypos.core.designsystem.StatusPill
import com.kline.inventorypos.core.session.AuthenticatedContext
import com.kline.inventorypos.core.session.PosBranch
import com.kline.inventorypos.R

@Composable
fun RestoringSessionScreen() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark()
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text("Restoring your workspace", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LoginScreen(
    working: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onDemo: () -> Unit,
    onClearError: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    fun submit() = onLogin(username, password)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            BrandMark()
            Spacer(Modifier.height(18.dp))
            Text("Welcome back", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Sign in to open your branch workspace",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))
            PosCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; onClearError() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        singleLine = true,
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; onClearError() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !working,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    if (error != null) ErrorBanner(error)
                    Button(
                        onClick = ::submit,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !working,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (working) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Sign in")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("OR", style = MaterialTheme.typography.labelSmall, color = Slate500)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDemo,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !working,
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Storefront, contentDescription = null)
                Text("  Explore the demo workspace")
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = MoneyGreen700, modifier = Modifier.size(17.dp))
                Text(
                    "  Credentials are encrypted on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                )
            }
        }
    }
}

@Composable
fun BranchSelectionScreen(
    context: AuthenticatedContext,
    working: Boolean,
    error: String?,
    onSelect: (PosBranch) -> Unit,
    onLogout: () -> Unit,
) {
    val initial = context.branches.find { it.id == context.selectedBranchId }
        ?: context.branches.find { it.id == context.defaultBranchId }
        ?: context.branches.first()
    var selectedId by rememberSaveable(context.user.id) { mutableStateOf(initial.id) }
    val selected = context.branches.first { it.id == selectedId }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Primary950, Primary800)))
                    .padding(horizontal = 20.dp, vertical = 26.dp),
            ) {
                StatusPill("SIGNED IN", containerColor = Color.White.copy(alpha = .13f), contentColor = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("Choose a branch", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "${context.user.fullName} · ${context.user.roleName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = .7f),
                )
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Where are you working today?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sales, stock and cash activity will be scoped to this branch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                )
                context.branches.forEach { branch ->
                    BranchCard(
                        branch = branch,
                        selected = branch.id == selectedId,
                        onClick = { if (!working) selectedId = branch.id },
                    )
                }
                if (error != null) ErrorBanner(error)
                Button(
                    onClick = { onSelect(selected) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !working,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (working) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Continue to ${selected.name}")
                }
                TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally), enabled = !working) {
                    Text("Use another account")
                }
            }
        }
    }
}

@Composable
private fun BranchCard(branch: PosBranch, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Primary50 else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) Primary700 else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SquareActionIcon(
                Icons.Outlined.LocationOn,
                backgroundColor = if (selected) Primary100 else Slate100,
                contentColor = if (selected) Primary800 else Slate500,
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(branch.name, style = MaterialTheme.typography.titleSmall)
                    if (branch.isUserDefault || branch.isDefault) {
                        Text("  DEFAULT", style = MaterialTheme.typography.labelSmall, color = Primary700)
                    }
                }
                Text(
                    listOfNotNull(branch.code.takeIf(String::isNotBlank), branch.city).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                )
            }
            if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = Primary700)
            else Box(Modifier.size(22.dp).background(Slate100, CircleShape))
        }
    }
}

@Composable
fun OpenRegisterScreen(
    branch: PosBranch,
    working: Boolean,
    error: String?,
    onOpen: (Long) -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    var amount by rememberSaveable { mutableLongStateOf(0L) }
    var amountText by rememberSaveable { mutableStateOf("") }
    val quickAmounts = listOf(0L, 100_000L, 200_000L, 500_000L)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(compact = true)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(branch.name, style = MaterialTheme.typography.titleMedium)
                    Text("Start-of-shift check", style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Open your register", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Count the cash currently in the drawer before taking the first cash payment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            PosCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SquareActionIcon(Icons.Outlined.Payments, backgroundColor = MoneyGreen50, contentColor = MoneyGreen700)
                        Column(Modifier.padding(start = 11.dp)) {
                            Text("Opening float", style = MaterialTheme.typography.titleSmall)
                            Text("Physical cash in drawer", style = MaterialTheme.typography.bodySmall, color = Slate500)
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input ->
                            amountText = sanitizeAmountInput(input)
                            amount = parseAmountInput(amountText) ?: 0L
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Amount (UGX)") },
                        supportingText = { Text(formatUgx(amount)) },
                        singleLine = true,
                        visualTransformation = AmountGroupingVisualTransformation,
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onOpen(amount) }),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickAmounts.forEach { quick ->
                            FilterChip(
                                selected = amount == quick,
                                onClick = {
                                    amount = quick
                                    amountText = if (quick == 0L) "" else quick.toString()
                                },
                                label = {
                                    Text(
                                        if (quick == 0L) "No float" else "${quick / 1000}k",
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                },
                                modifier = Modifier.weight(if (quick == 0L) 1.35f else 1f),
                                enabled = !working,
                            )
                        }
                    }
                    if (error != null) ErrorBanner(error)
                    Button(
                        onClick = { onOpen(amount) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !working,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (working) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Open register")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = Primary50, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Primary700, modifier = Modifier.size(19.dp))
                    Text(
                        "  This count is recorded in the cash audit trail. Closing counts remain blind until submitted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary950,
                    )
                }
            }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth(), enabled = !working) {
                Text("Continue without a register")
            }
            Text(
                "Cash sales will stay unavailable until a register is open.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !working) {
                Text("Choose a different branch")
            }
        }
    }
}

@Composable
private fun BrandMark(compact: Boolean = false) {
    val bitmap = ImageBitmap.imageResource(R.drawable.kline_logo)
    val crop = (bitmap.height * 0.36f).toInt()
    Image(
        painter = BitmapPainter(
            image = bitmap,
            srcOffset = IntOffset((bitmap.width - crop) / 2, (bitmap.height * 0.12f).toInt()),
            srcSize = IntSize(crop, crop),
        ),
        contentDescription = "K-Line Men",
        modifier = Modifier
            .size(if (compact) 46.dp else 66.dp)
            .background(Color.Black, RoundedCornerShape(if (compact) 14.dp else 20.dp)),
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(color = Error50, shape = RoundedCornerShape(12.dp)) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Error700,
        )
    }
}
