package com.example.smsspend.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TxnEntity::class, MerchantRuleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun merchantRuleDao(): MerchantRuleDao

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
