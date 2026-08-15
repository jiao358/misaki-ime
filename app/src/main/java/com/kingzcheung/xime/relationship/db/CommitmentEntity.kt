package com.kingzcheung.xime.relationship.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "relationship_commitments",
    indices = [Index(value = ["personId"])],
)
data class CommitmentEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val title: String,
    val dueAt: Long,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)
