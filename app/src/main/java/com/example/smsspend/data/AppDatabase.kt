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
        CategoryDef::class,
        CardLimitSnapshot::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun holdingDao(): HoldingDao
    abstract fun ipoApplicationDao(): IpoApplicationDao
    abstract fun balanceDao(): BalanceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun cardLimitDao(): CardLimitDao

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

        /**
         * Credit-card support (v5): remembers how much credit is left on each card, and records
         * the currency a purchase was billed in so foreign amounts are visibly conversions.
         * Purely additive — no existing data is touched.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `card_limit_snapshot` " +
                        "(`date` INTEGER NOT NULL, `cardLast4` TEXT NOT NULL, " +
                        "`availableLimit` REAL NOT NULL, PRIMARY KEY(`date`))"
                )
                db.execSQL("ALTER TABLE `txn` ADD COLUMN `currency` TEXT NOT NULL DEFAULT 'OMR'")
                db.execSQL("ALTER TABLE `txn` ADD COLUMN `originalAmount` REAL NOT NULL DEFAULT 0")
            }
        }

        /**
         * User-editable transactions (v6): [TxnEntity.manual] marks rows the user created, and
         * [TxnEntity.hidden] soft-deletes rows so a re-import cannot bring them back.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `txn` ADD COLUMN `manual` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `txn` ADD COLUMN `hidden` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Records whether an amount came from the bank or from an app-side conversion (v7). */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `txn` ADD COLUMN `amountExact` INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smsspend.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
