package com.kingzcheung.xime.relationship

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kingzcheung.xime.relationship.db.RelationshipDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object RelationshipDataExporter {
    suspend fun createShareIntent(context: Context): Intent = withContext(Dispatchers.IO) {
        val dao = RelationshipDatabase.getInstance(context).relationshipDao()
        val memoryCipher = MemoryExportDecryptor(context)
        val root = JSONObject()
            .put("exportedAt", System.currentTimeMillis())
            .put("people", JSONArray(dao.getPeople().map { person ->
                JSONObject()
                    .put("id", person.id)
                    .put("alias", person.alias)
                    .put("relationshipType", person.relationshipType)
                    .put("relationshipStage", person.relationshipStage)
                    .put("notes", person.notes)
            }))
            .put("memories", JSONArray(memoryCipher.snapshot()))
            .put("commitments", JSONArray(dao.getCommitments().map { commitment ->
                JSONObject()
                    .put("id", commitment.id)
                    .put("personId", commitment.personId)
                    .put("title", commitment.title)
                    .put("dueAt", commitment.dueAt)
                    .put("status", commitment.status)
            }))
        val directory = File(context.cacheDir, "relationship_exports").apply { mkdirs() }
        val file = File(directory, "misaki-relationship-export-${System.currentTimeMillis()}.json")
        file.writeText(root.toString(2))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

private class MemoryExportDecryptor(context: Context) {
    private val dao = RelationshipDatabase.getInstance(context).relationshipDao()
    private val repository = MemoryRepository.getInstance(context)

    suspend fun snapshot(): List<JSONObject> {
        val people = dao.getPeople()
        val output = mutableListOf<JSONObject>()
        people.forEach { person ->
            repository.observe(person.id).firstSnapshot().forEach { memory ->
                output += JSONObject()
                    .put("id", memory.id)
                    .put("personId", memory.personId)
                    .put("kind", memory.kind.id)
                    .put("category", memory.category.id)
                    .put("content", memory.content)
            }
        }
        return output
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<List<T>>.firstSnapshot(): List<T> =
    first()
