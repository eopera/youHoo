package com.eopera.youhoo.feature.chat

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MessageTimeFormatterTest {
    private lateinit var originalTimeZone: TimeZone
    private val formatter = MessageTimeFormatter()

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `formats fractional UTC timestamp in local time`() {
        assertEquals(
            "14:34",
            formatter.format("2024-01-01T12:34:56.123456+00:00")
        )
    }

    @Test
    fun `supports Z timezone`() {
        assertEquals("14:34", formatter.format("2024-01-01T12:34:56Z"))
    }

    @Test
    fun `malformed timestamp does not crash rendering`() {
        assertEquals("", formatter.format("not-a-date"))
    }
}
