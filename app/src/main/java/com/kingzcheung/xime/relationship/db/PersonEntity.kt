package com.kingzcheung.xime.relationship.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "relationship_people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val alias: String,
    val relationshipType: String,
    val relationshipStage: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)
