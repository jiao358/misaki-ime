package com.kingzcheung.xime.relationship

import android.content.Context
import com.kingzcheung.xime.relationship.db.CommitmentEntity
import com.kingzcheung.xime.relationship.db.RelationshipDatabase
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

enum class CommitmentStatus { PENDING, COMPLETED }

class CommitmentRepository private constructor(private val context: Context) {
    private val dao = RelationshipDatabase.getInstance(context).relationshipDao()

    fun observe(personId: String): Flow<List<CommitmentEntity>> = dao.observeCommitments(personId)

    suspend fun create(personId: String, title: String, dueAt: Long) = withContext(Dispatchers.IO) {
        val normalized = title.trim().take(160)
        if (normalized.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val entity = CommitmentEntity(
            id = UUID.randomUUID().toString(),
            personId = personId,
            title = normalized,
            dueAt = dueAt,
            status = CommitmentStatus.PENDING.name,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertCommitment(entity)
        RelationshipReminderScheduler.schedule(context, entity)
    }

    suspend fun complete(entity: CommitmentEntity) = withContext(Dispatchers.IO) {
        dao.upsertCommitment(
            entity.copy(status = CommitmentStatus.COMPLETED.name, updatedAt = System.currentTimeMillis()),
        )
        RelationshipReminderScheduler.cancel(context, entity.id)
    }

    suspend fun postponeOneDay(entity: CommitmentEntity) = withContext(Dispatchers.IO) {
        val updated = entity.copy(
            dueAt = maxOf(entity.dueAt, System.currentTimeMillis()) + ONE_DAY_MS,
            status = CommitmentStatus.PENDING.name,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsertCommitment(updated)
        RelationshipReminderScheduler.schedule(context, updated)
    }

    suspend fun delete(entity: CommitmentEntity) = withContext(Dispatchers.IO) {
        dao.deleteCommitment(entity.id)
        RelationshipReminderScheduler.cancel(context, entity.id)
    }

    companion object {
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: CommitmentRepository? = null

        fun getInstance(context: Context): CommitmentRepository =
            instance ?: synchronized(this) {
                instance ?: CommitmentRepository(context.applicationContext).also { instance = it }
            }
    }
}
