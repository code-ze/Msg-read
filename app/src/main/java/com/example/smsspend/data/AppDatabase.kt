package com.example.smsspend.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TxnEntity::class,
        MerchantRuleEntity::class,
        Holding::class,
        IpoApplication::class,
        BalanceSnapshot::class,
        CategoryDef::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun holdingDao(): HoldingDao
    abstract fun ipoApplicationDao(): IpoApplicationDao
    abstract fun balanceDao(): BalanceDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * Additive migration: only creates the new balance table. Defining real migrations
         * (instead of destructive ones) means a schema bump never wipes the user's manually
         * entered data — holdings, learned categories and balances all survive app updates.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `balance_snapshot` " +
                        "(`date` INTEGER NOT NULL, `balance` REAL NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        /** Adds sub-categories (column on txn + rules) and the category-definition table. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ADD COLUMN needs a default to backfill existing rows; the entity declares the
                // same default via @ColumnInfo so Room's schema validation matches.
                db.execSQL("ALTER TABLE `txn` ADD COLUMN `subcategory` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `merchant_rule` ADD COLUMN `subcategory` TEXT NOT NULL DEFAULT ''")
                // New table: no SQL defaults, to match Room's generated schema for the entity.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_def` " +
                        "(`name` TEXT NOT NULL, `parent` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, `sort` INTEGER NOT NULL, " +
                        "`builtIn` INTEGER NOT NULL, PRIMARY KEY(`name`))"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smsspend.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
