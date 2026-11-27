package app.fen.autobook.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DateTimeParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 11, 23, 15, 30)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `parses dashed full date`() {
        assertEquals(at(2026, 11, 23, 12, 30), DateTimeParser.parse("2026-11-23 12:30", now, zone))
    }

    @Test
    fun `parses slashed full date`() {
        assertEquals(at(2026, 11, 23, 12, 30), DateTimeParser.parse("2026/11/23 12:30", now, zone))
    }

    @Test
    fun `parses short date with current year`() {
        assertEquals(at(2026, 11, 23, 12, 30), DateTimeParser.parse("11-23 12:30", now, zone))
    }

    @Test
    fun `parses today relative`() {
        assertEquals(at(2026, 11, 23, 12, 30), DateTimeParser.parse("今天 12:30", now, zone))
    }

    @Test
    fun `parses yesterday relative`() {
        assertEquals(at(2026, 11, 22, 9, 0), DateTimeParser.parse("昨天 09:00", now, zone))
    }

    @Test
    fun `parses time only as today`() {
        assertEquals(at(2026, 11, 23, 12, 30), DateTimeParser.parse("12:30", now, zone))
    }

    @Test
    fun `returns null when no time present`() {
        assertNull(DateTimeParser.parse("没有时间", now, zone))
    }

    @Test
    fun `returns null for invalid date`() {
        assertNull(DateTimeParser.parse("2026-13-40 99:99", now, zone))
    }

    @Test
    fun `parses chinese full date`() {
        assertEquals(
            at(2026, 11, 23, 12, 30),
            DateTimeParser.parse("2026年11月23日 12:30", now, zone),
        )
    }

    @Test
    fun `parses dotted full date`() {
        assertEquals(
            at(2026, 11, 23, 12, 30),
            DateTimeParser.parse("2026.11.23 12:30", now, zone),
        )
    }

    @Test
    fun `parses date only at noon`() {
        assertEquals(
            at(2026, 11, 23, 12, 0),
            DateTimeParser.parse("2026-11-23", now, zone),
        )
    }

    @Test
    fun `parses labeled time only`() {
        assertEquals(
            at(2026, 11, 23, 12, 30),
            DateTimeParser.parse("支付时间 12:30", now, zone),
        )
    }

    @Test
    fun `rolls short date back across new year`() {
        val newYear = LocalDateTime.of(2026, 1, 1, 10, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val expected = LocalDateTime.of(2025, 12, 31, 12, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, DateTimeParser.parse("12-31 12:30", newYear, zone))
    }
}
