package com.kingzcheung.xime.relationship.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [PersonEntity::class, MemoryEntity::class, CommitmentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RelationshipDatabase : RoomDatabase() {
    abstract fun relationshipDao(): RelationshipDao

    companion object {
        @Volatile
        private var instance: RelationshipDatabase? = null

        fun getInstance(context: Context): RelationshipDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder<RelationshipDatabase>(
                    context.applicationContext,
                    "relationship.db",
                )
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
                    .also { instance = it }
            }
    }
}
