package com.kingzcheung.xime.relationship.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationshipDao {
    @Query("SELECT * FROM relationship_people ORDER BY updatedAt DESC")
    fun observePeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM relationship_people ORDER BY updatedAt DESC")
    suspend fun getPeople(): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(person: PersonEntity)

    @Query("DELETE FROM relationship_people WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM relationship_memories WHERE personId = :personId ORDER BY updatedAt DESC")
    fun observeMemories(personId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM relationship_memories ORDER BY updatedAt DESC")
    suspend fun getMemories(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity)

    @Query("DELETE FROM relationship_memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM relationship_memories WHERE personId = :personId")
    suspend fun deleteMemoriesForPerson(personId: String)

    @Query("SELECT * FROM relationship_commitments WHERE personId = :personId ORDER BY status ASC, dueAt ASC")
    fun observeCommitments(personId: String): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM relationship_commitments ORDER BY dueAt ASC")
    suspend fun getCommitments(): List<CommitmentEntity>

    @Query("SELECT * FROM relationship_commitments WHERE personId = :personId")
    suspend fun getCommitmentsForPerson(personId: String): List<CommitmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommitment(commitment: CommitmentEntity)

    @Query("DELETE FROM relationship_commitments WHERE id = :id")
    suspend fun deleteCommitment(id: String)

    @Query("DELETE FROM relationship_commitments WHERE personId = :personId")
    suspend fun deleteCommitmentsForPerson(personId: String)

    @Query("DELETE FROM relationship_memories")
    suspend fun deleteAllMemories()

    @Query("DELETE FROM relationship_commitments")
    suspend fun deleteAllCommitments()

    @Query("DELETE FROM relationship_people")
    suspend fun deleteAllPeople()
}
