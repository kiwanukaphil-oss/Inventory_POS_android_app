package com.kline.inventorypos.feature.inventory

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.kline.inventorypos.core.designsystem.InventoryPosTheme
import com.kline.inventorypos.core.model.SampleProducts
import org.junit.Rule
import org.junit.Test

class ProductCatalogAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogRemainsOperableAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                InventoryPosTheme {
                    ProductCatalogScreen(
                        state = InventoryUiState(products = SampleProducts, canViewProducts = true, canEditPrices = true),
                        onBack = {},
                        onLoad = {},
                        onRefresh = {},
                        onManagePrices = {},
                        onPrintLabels = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Product catalog").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertHasClickAction()
        composeRule.onNodeWithText("Prices", substring = true).assertHasClickAction().assertIsEnabled()
        composeRule.onNodeWithText("Refresh catalog", substring = true).assertHasClickAction()
        composeRule.onNodeWithText("Search product, SKU or barcode").performTextInput("LIN-SKY")
        composeRule.onNodeWithText("Premium Linen Shirt").performScrollTo().assertIsDisplayed()
    }
}
