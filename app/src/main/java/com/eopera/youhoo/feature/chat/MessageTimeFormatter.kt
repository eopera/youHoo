package com.eopera.youhoo.feature.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class MessageTimeFormatter @Inject constructor() {
    private val fractionalSeconds = Regex("\\.\\d+")

    /** Converts an ISO-8601 backend timestamp to local HH:mm, or returns an empty value. */
    fun format(value: String): String {
        val normalized = fractionalSeconds.replace(value, "")
        val date = parse(normalized, "yyyy-MM-dd'T'HH:mm:ssXXX")
            ?: parse(normalized, "yyyy-MM-dd'T'HH:mm:ss")
            ?: return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    private fun parse(value: String, pattern: String): Date? = runCatching {
        SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            if (!pattern.endsWith("XXX")) {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }.parse(value)
    }.getOrNull()
}
