package com.kingzcheung.xime.relationship.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "relationship_memories",
    indices = [Index(value = ["personId"])],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val kind: String,
    val category: String,
    val ciphertext: ByteArray,
    val initializationVector: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
)
