package com.kline.inventorypos

import android.os.Bundle
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.kline.inventorypos.app.AppViewModel
import com.kline.inventorypos.app.InventoryPosApp
import com.kline.inventorypos.core.designsystem.InventoryPosTheme
import com.kline.inventorypos.feature.pos.SaleViewModel
import com.kline.inventorypos.feature.inventory.InventoryViewModel
import com.kline.inventorypos.feature.activity.ActivityViewModel
import com.kline.inventorypos.feature.customer.CustomerViewModel
import com.kline.inventorypos.feature.voucher.GiftVoucherViewModel
import com.kline.inventorypos.feature.cash.CashViewModel
import com.kline.inventorypos.feature.reconciliation.ReconciliationViewModel
import com.kline.inventorypos.feature.operations.ApprovalViewModel
import com.kline.inventorypos.feature.operations.ExpenseViewModel
import com.kline.inventorypos.feature.document.DocumentViewModel
import com.kline.inventorypos.feature.administration.AdministrationViewModel
import com.kline.inventorypos.feature.report.ManagementReportViewModel
import com.kline.inventorypos.core.model.ConfirmedReceipt
import com.kline.inventorypos.core.model.LabelPrintItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { (application as InventoryPosApplication).container }
    private val appViewModel by viewModels<AppViewModel> { AppViewModel.Factory(appContainer) }
    private val saleViewModel by viewModels<SaleViewModel> { SaleViewModel.Factory(appContainer) }
    private val inventoryViewModel by viewModels<InventoryViewModel> { InventoryViewModel.Factory(appContainer) }
    private val activityViewModel by viewModels<ActivityViewModel> { ActivityViewModel.Factory(appContainer) }
    private val customerViewModel by viewModels<CustomerViewModel> { CustomerViewModel.Factory(appContainer) }
    private val giftVoucherViewModel by viewModels<GiftVoucherViewModel> { GiftVoucherViewModel.Factory(appContainer) }
    private val cashViewModel by viewModels<CashViewModel> { CashViewModel.Factory(appContainer) }
    private val reconciliationViewModel by viewModels<ReconciliationViewModel> { ReconciliationViewModel.Factory(appContainer) }
    private val expenseViewModel by viewModels<ExpenseViewModel> { ExpenseViewModel.Factory(appContainer) }
    private val approvalViewModel by viewModels<ApprovalViewModel> { ApprovalViewModel.Factory(appContainer) }
    private val documentViewModel by viewModels<DocumentViewModel> { DocumentViewModel.Factory(appContainer) }
    private val administrationViewModel by viewModels<AdministrationViewModel> { AdministrationViewModel.Factory(appContainer) }
    private val managementReportViewModel by viewModels<ManagementReportViewModel> { ManagementReportViewModel.Factory(appContainer) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InventoryPosTheme {
                InventoryPosApp(appViewModel, saleViewModel, inventoryViewModel, activityViewModel, customerViewModel, giftVoucherViewModel, cashViewModel, reconciliationViewModel, expenseViewModel, approvalViewModel, documentViewModel, administrationViewModel, managementReportViewModel)
            }
        }
    }

    fun scanBarcode(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE,
            )
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.takeIf(String::isNotBlank)?.let(onResult)
                    ?: onError("The scanned code was empty.")
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Barcode scanner could not start.")
            }
    }

    fun printReceipt(receipt: ConfirmedReceipt) {
        val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        manager.print(
            "K-Line ${receipt.receiptNumber}",
            ReceiptPrintAdapter(this, receipt),
            attributes,
        )
    }

    fun printLabels(items: List<LabelPrintItem>, onCompleted: () -> Unit) {
        val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        val job = manager.print(
            "K-Line product labels",
            LabelPrintAdapter(this, items),
            attributes,
        )
        lifecycleScope.launch {
            while (!job.isCompleted && !job.isCancelled && !job.isFailed) delay(500)
            if (job.isCompleted) onCompleted()
        }
    }
}
