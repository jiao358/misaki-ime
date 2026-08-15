package com.kingzcheung.xime.relationship

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.relationship.db.PersonEntity
import com.kingzcheung.xime.relationship.db.RelationshipDatabase
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RelationshipViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = RelationshipDatabase.getInstance(application).relationshipDao()

    val people: StateFlow<List<PersonEntity>> = dao.observePeople().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val currentPerson: StateFlow<CurrentPersonSelection?> = CurrentPersonStore
        .observe(application)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CurrentPersonStore.current(application),
        )

    fun save(
        existing: PersonEntity?,
        alias: String,
        relationshipType: String,
        relationshipStage: String,
        notes: String,
    ) {
        val normalizedAlias = alias.trim()
        if (normalizedAlias.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val person = PersonEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                alias = normalizedAlias,
                relationshipType = relationshipType.trim(),
                relationshipStage = relationshipStage.trim(),
                notes = notes.trim(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            dao.upsert(person)
            if (currentPerson.value?.id == person.id) {
                CurrentPersonStore.select(getApplication(), person.id, person.alias)
            }
        }
    }

    fun select(person: PersonEntity) {
        CurrentPersonStore.select(getApplication(), person.id, person.alias)
    }

    fun clearSelection() {
        CurrentPersonStore.clear(getApplication())
    }

    fun delete(person: PersonEntity) {
        viewModelScope.launch {
            dao.getCommitmentsForPerson(person.id).forEach {
                RelationshipReminderScheduler.cancel(getApplication(), it.id)
            }
            dao.deleteMemoriesForPerson(person.id)
            dao.deleteCommitmentsForPerson(person.id)
            dao.deleteById(person.id)
            if (currentPerson.value?.id == person.id) {
                CurrentPersonStore.clear(getApplication())
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            dao.getCommitments().forEach {
                RelationshipReminderScheduler.cancel(getApplication(), it.id)
            }
            dao.deleteAllCommitments()
            dao.deleteAllMemories()
            dao.deleteAllPeople()
            CurrentPersonStore.clear(getApplication())
        }
    }
}
