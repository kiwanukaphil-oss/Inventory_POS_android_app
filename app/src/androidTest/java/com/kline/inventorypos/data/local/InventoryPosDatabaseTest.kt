package com.kline.inventorypos.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kline.inventorypos.core.model.SampleProducts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryPosDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "inventory-pos-phase2-test.db"
    private lateinit var database: InventoryPosDatabase

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InventoryPosDatabase::class.java,
    )

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun catalogIsBranchScopedAndCartSurvivesDatabaseReopen() = runBlocking {
        val product = SampleProducts.first()
        database.dao().insertVariants(
            listOf(
                CatalogVariantEntity(
                    branchId = "branch-a",
                    variantId = product.id,
                    productId = product.productId,
                    productName = product.name,
                    sku = product.sku,
                    attributesJson = "{}",
                    price = product.price,
                    stock = product.stock,
                    reorderLevel = product.reorderLevel,
                    barcode = "2000000000015",
                    categoryId = "shirts",
                    categoryName = "Shirts",
                    brandName = "K-Line",
                ),
            ),
        )
        database.dao().putCartLine(
            CartLineEntity(
                sessionKey = "user:branch-a",
                variantId = product.id,
                productId = product.productId,
                productName = product.name,
                sku = product.sku,
                price = product.price,
                stock = product.stock,
                category = product.category,
                categoryId = "shirts",
                variant = product.variant,
                barcode = "2000000000015",
                reorderLevel = product.reorderLevel,
                quantity = 2,
            ),
        )
        database.dao().putCheckoutAttempt(
            CheckoutAttemptEntity(
                attemptId = "attempt-1",
                sessionKey = "user:branch-a",
                requestJson = "{}",
                status = "UNCERTAIN",
                createdAt = 123L,
                receiptJson = null,
                message = "Verify before retrying",
            ),
        )

        assertEquals(1, database.dao().observeCatalog("branch-a", "", null).first().size)
        assertEquals(0, database.dao().observeCatalog("branch-b", "", null).first().size)

        database.close()
        database = openDatabase()

        val restored = database.dao().getCart("user:branch-a")
        assertEquals(1, restored.size)
        assertEquals(2, restored.single().quantity)
        assertEquals(product.id, restored.single().variantId)
        assertEquals("UNCERTAIN", database.dao().getLatestCheckoutAttempt("user:branch-a")?.status)
    }

    @Test
    fun migrationFromOneToTwoPreservesHeldSalesAndCreatesAttemptJournal() {
        val migrationDatabase = "inventory-pos-migration-test.db"
        migrationHelper.createDatabase(migrationDatabase, 1).apply {
            execSQL(
                """INSERT INTO held_carts
                   (id, session_key, items_json, item_count, customer_id, customer_name, notes, held_at, server_id, sync_state)
                   VALUES ('held-1', 'user:branch-a', '[]', 1, NULL, NULL, 'Keep me', 123, NULL, 'local')""",
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            migrationDatabase,
            2,
            true,
            InventoryPosDatabase.Migration1To2,
        ).use { migrated ->
            migrated.query("SELECT notes FROM held_carts WHERE id = 'held-1'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("Keep me", cursor.getString(0))
            }
            migrated.query("SELECT COUNT(*) FROM checkout_attempts").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private fun openDatabase(): InventoryPosDatabase = Room.databaseBuilder(
        context,
        InventoryPosDatabase::class.java,
        databaseName,
    ).build()
}
