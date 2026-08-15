package com.kingzcheung.xime.relationship

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal fun combineReminderDateTime(
    selectedUtcDateMillis: Long,
    hour: Int,
    minute: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val selectedDate = Instant.ofEpochMilli(selectedUtcDateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    return selectedDate
        .atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}

internal fun reminderDateAsUtcPickerMillis(
    reminderAtMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long = Instant.ofEpochMilli(reminderAtMillis)
    .atZone(zoneId)
    .toLocalDate()
    .atStartOfDay(ZoneOffset.UTC)
    .toInstant()
    .toEpochMilli()
