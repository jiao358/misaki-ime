package com.kingzcheung.xime.relationship

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimeTest {
    @Test
    fun combinesUtcPickerDateWithLocalTime() {
        val selectedDate = Instant.parse("2026-09-12T00:00:00Z").toEpochMilli()

        val result = combineReminderDateTime(
            selectedUtcDateMillis = selectedDate,
            hour = 8,
            minute = 30,
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertEquals(
            Instant.parse("2026-09-12T00:30:00Z").toEpochMilli(),
            result,
        )
    }

    @Test
    fun clampsInvalidTimeValues() {
        val selectedDate = Instant.parse("2026-09-12T00:00:00Z").toEpochMilli()

        val result = combineReminderDateTime(
            selectedUtcDateMillis = selectedDate,
            hour = 26,
            minute = -2,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(
            Instant.parse("2026-09-12T23:00:00Z").toEpochMilli(),
            result,
        )
    }

    @Test
    fun convertsLocalReminderDateToUtcPickerDate() {
        val reminderAt = Instant.parse("2026-09-11T16:30:00Z").toEpochMilli()

        val result = reminderDateAsUtcPickerMillis(
            reminderAtMillis = reminderAt,
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertEquals(
            Instant.parse("2026-09-12T00:00:00Z").toEpochMilli(),
            result,
        )
    }
}
