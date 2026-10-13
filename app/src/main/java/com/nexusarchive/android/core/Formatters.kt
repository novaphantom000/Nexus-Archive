package com.nexusarchive.android.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Formatters {
    /** Base-1024 sizes labelled B/KB/MB/GB/TB. Negative input means "unknown" and renders as an em dash. */
    fun bytes(value: Long, locale: Locale = Locale.getDefault()): String {
        if (value < 0) return "\u2014"
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        var v = value.toDouble() / 1024.0
        var i = 0
        while (v >= 1024.0 && i < units.lastIndex) {
            v /= 1024.0
            i++
        }
        val pattern = if (v >= 100.0) "%.0f %s" else "%.1f %s"
        return String.format(locale, pattern, v, units[i])
    }

    fun date(ms: Long, locale: Locale = Locale.getDefault()): String {
        if (ms <= 0L) return "\u2014"
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        return formatter.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    }

    fun dateTime(ms: Long, locale: Locale = Locale.getDefault()): String {
        if (ms <= 0L) return "\u2014"
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        return formatter.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    }

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }

    fun plural(count: Int, singular: String): String = if (count == 1) "1 $singular" else "$count ${singular}s"
}
