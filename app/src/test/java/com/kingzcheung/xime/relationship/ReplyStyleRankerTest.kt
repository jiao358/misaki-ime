package com.kingzcheung.xime.relationship

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyStyleRankerTest {
    @Test
    fun preferredStyleMovesAheadOfBaselineOrder() {
        val candidates = listOf("natural", "humorous", "measured")
        val profile = ReplyPreferenceProfile(
            naturalScore = 0,
            humorousScore = 6,
            measuredScore = 2,
            sampleCount = 4,
        )

        val ranked = ReplyStyleRanker.rank(candidates, styleOf = { it }, profile = profile)

        assertEquals(listOf("humorous", "measured", "natural"), ranked)
    }
}
