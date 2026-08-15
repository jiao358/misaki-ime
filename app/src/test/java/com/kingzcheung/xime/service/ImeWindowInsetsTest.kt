package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeWindowInsetsTest {

    @Test
    fun `zero reported inset does not create artificial bottom gap`() {
        assertEquals(0, normalizeBottomInsetDp(0))
    }

    @Test
    fun `reported navigation inset is preserved`() {
        assertEquals(24, normalizeBottomInsetDp(24))
    }

    @Test
    fun `vendor gesture inset is capped to navigation bar height`() {
        assertEquals(48, normalizeBottomInsetDp(96))
    }
}
