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
        BalanceSnapshot::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun holdingDao(): HoldingDao
    abstract fun ipoApplicationDao(): IpoApplicationDao
    abstract fun balanceDao(): BalanceDao

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

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smsspend.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
