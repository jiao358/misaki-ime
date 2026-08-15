package com.kingzcheung.xime.relationship

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kingzcheung.xime.relationship.db.MemoryEntity
import com.kingzcheung.xime.relationship.db.RelationshipDatabase
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

enum class MemoryCategory(val id: String, val label: String) {
    IMPORTANT_EVENT("important_event", "重要事件"),
    CURRENT_CONCERN("current_concern", "近期关注"),
    BOUNDARY("boundary", "边界禁忌"),
    PREFERENCE("preference", "偏好习惯");

    companion object {
        fun fromId(id: String): MemoryCategory = entries.find { it.id == id } ?: CURRENT_CONCERN
    }
}

enum class MemoryKind(val id: String, val label: String) {
    FACT("FACT", "明确事实"),
    INFERENCE("INFERENCE", "我的推测");

    companion object {
        fun fromId(id: String): MemoryKind = entries.find { it.id == id } ?: FACT
    }
}

data class RelationshipMemory(
    val id: String,
    val personId: String,
    val kind: MemoryKind,
    val category: MemoryCategory,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

class MemoryRepository private constructor(context: Context) {
    private val dao = RelationshipDatabase.getInstance(context).relationshipDao()
    private val cipher = MemoryCipher()

    fun observe(personId: String): Flow<List<RelationshipMemory>> =
        dao.observeMemories(personId).map { entities ->
            entities.mapNotNull { entity ->
                runCatching {
                    RelationshipMemory(
                        id = entity.id,
                        personId = entity.personId,
                        kind = MemoryKind.fromId(entity.kind),
                        category = MemoryCategory.fromId(entity.category),
                        content = cipher.decrypt(entity.ciphertext, entity.initializationVector),
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt,
                    )
                }.getOrNull()
            }
        }

    suspend fun save(
        personId: String,
        content: String,
        category: MemoryCategory,
        kind: MemoryKind = MemoryKind.FACT,
        existing: RelationshipMemory? = null,
    ) = withContext(Dispatchers.IO) {
        val normalized = content.trim().take(MAX_MEMORY_LENGTH)
        if (normalized.isEmpty()) return@withContext
        val encrypted = cipher.encrypt(normalized)
        val now = System.currentTimeMillis()
        dao.upsertMemory(
            MemoryEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                personId = personId,
                kind = kind.id,
                category = category.id,
                ciphertext = encrypted.ciphertext,
                initializationVector = encrypted.initializationVector,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteMemory(id)
    }

    companion object {
        const val MAX_MEMORY_LENGTH = 240

        @Volatile
        private var instance: MemoryRepository? = null

        fun getInstance(context: Context): MemoryRepository =
            instance ?: synchronized(this) {
                instance ?: MemoryRepository(context.applicationContext).also { instance = it }
            }
    }
}

private data class EncryptedMemory(
    val ciphertext: ByteArray,
    val initializationVector: ByteArray,
)

private class MemoryCipher {
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    fun encrypt(plaintext: String): EncryptedMemory {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedMemory(
            ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)),
            initializationVector = cipher.iv,
        )
    }

    fun decrypt(ciphertext: ByteArray, initializationVector: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, initializationVector),
        )
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "xime_relationship_memory_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
