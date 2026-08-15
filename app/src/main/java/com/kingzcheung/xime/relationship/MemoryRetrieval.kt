package com.kingzcheung.xime.relationship

object MemoryRetrieval {
    fun rankRelevant(
        message: String,
        memories: List<RelationshipMemory>,
        limit: Int = 6,
    ): List<RelationshipMemory> {
        if (memories.isEmpty() || limit <= 0) return emptyList()
        val queryTerms = terms(message)
        return memories
            .map { memory ->
                val overlap = queryTerms.intersect(terms(memory.content)).size.toDouble()
                val categoryBoost = when (memory.category) {
                    MemoryCategory.BOUNDARY -> 1.4
                    MemoryCategory.CURRENT_CONCERN -> 1.2
                    MemoryCategory.IMPORTANT_EVENT -> 1.0
                    MemoryCategory.PREFERENCE -> 0.8
                }
                val factBoost = if (memory.kind == MemoryKind.FACT) 0.3 else 0.0
                val recency = memory.updatedAt / 1_000_000_000_000.0
                memory to (overlap * 3.0 + categoryBoost + factBoost + recency)
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun terms(text: String): Set<String> {
        val normalized = text.lowercase().filter { it.isLetterOrDigit() }
        if (normalized.isEmpty()) return emptySet()
        val terms = normalized.map { it.toString() }.toMutableSet()
        normalized.windowed(size = 2, step = 1, partialWindows = false).forEach(terms::add)
        return terms
    }
}
