package com.kline.inventorypos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CatalogVariantEntity::class,
        CategoryEntity::class,
        CatalogSyncEntity::class,
        CartLineEntity::class,
        HeldCartEntity::class,
        CheckoutAttemptEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class InventoryPosDatabase : RoomDatabase() {
    abstract fun dao(): InventoryPosDao

    companion object {
        fun create(context: Context): InventoryPosDatabase = Room.databaseBuilder(
            context.applicationContext,
            InventoryPosDatabase::class.java,
            "inventory_pos.db",
        ).addMigrations(Migration1To2).build()

        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `checkout_attempts` (
                       `attempt_id` TEXT NOT NULL,
                       `session_key` TEXT NOT NULL,
                       `request_json` TEXT NOT NULL,
                       `status` TEXT NOT NULL,
                       `created_at` INTEGER NOT NULL,
                       `receipt_json` TEXT,
                       `message` TEXT,
                       PRIMARY KEY(`attempt_id`))""",
                )
            }
        }
    }
}
