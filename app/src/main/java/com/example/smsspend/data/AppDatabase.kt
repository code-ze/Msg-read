package com.example.smsspend.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TxnEntity::class, MerchantRuleEntity::class, Holding::class, IpoApplication::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun holdingDao(): HoldingDao
    abstract fun ipoApplicationDao(): IpoApplicationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smsspend.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
