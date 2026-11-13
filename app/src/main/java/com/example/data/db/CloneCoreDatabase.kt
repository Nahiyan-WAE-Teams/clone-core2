package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CloneInstanceEntity::class,
        ProxyNodeEntity::class,
        KernelAuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CloneCoreDatabase : RoomDatabase() {
    abstract fun cloneDao(): CloneDao
    abstract fun proxyNodeDao(): ProxyNodeDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: CloneCoreDatabase? = null

        fun getInstance(context: Context): CloneCoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CloneCoreDatabase::class.java,
                    "clonecore_sandbox.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
