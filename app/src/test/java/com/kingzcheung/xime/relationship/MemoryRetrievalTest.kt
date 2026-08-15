package com.kingzcheung.xime.relationship

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryRetrievalTest {
    @Test
    fun examMessageRanksExamMemoryFirst() {
        val memories = listOf(
            memory("likes-walk", "喜欢晚上散步", MemoryCategory.PREFERENCE, 2),
            memory("exam", "9月12日参加考试，最近备考压力大", MemoryCategory.CURRENT_CONCERN, 1),
            memory("food", "不喜欢香菜", MemoryCategory.BOUNDARY, 3),
        )

        val ranked = MemoryRetrieval.rankRelevant("最近真的不想继续考试复习了", memories)

        assertEquals("exam", ranked.first().id)
    }

    @Test
    fun respectsLimit() {
        val memories = (1..10).map { memory("$it", "记忆$it", MemoryCategory.PREFERENCE, it.toLong()) }
        assertEquals(3, MemoryRetrieval.rankRelevant("记忆", memories, limit = 3).size)
    }

    private fun memory(
        id: String,
        content: String,
        category: MemoryCategory,
        updatedAt: Long,
    ) = RelationshipMemory(
        id = id,
        personId = "person",
        kind = MemoryKind.FACT,
        category = category,
        content = content,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )
}
