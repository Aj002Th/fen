package app.fen.autobook.parse

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object DateTimeParser {
    private val full = Regex("""(\d{4})[-/](\d{1,2})[-/](\d{1,2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    private val fullCn = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日\s*(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    private val fullDot = Regex("""(\d{4})\.(\d{1,2})\.(\d{1,2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    private val dateOnlyDash = Regex("""^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$""")
    private val dateOnlyCn = Regex("""^(\d{4})年(\d{1,2})月(\d{1,2})日$""")
    private val short = Regex("""(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})""")
    private val relative = Regex("""(今天|昨天)\s*(\d{1,2}):(\d{2})""")
    private val labeledTime = Regex("""(?:支付时间|交易时间|创建时间|付款时间|时间)\s*(\d{1,2}):(\d{2})""")
    private val timeOnly = Regex("""^(\d{1,2}):(\d{2})$""")

    fun parse(text: String, nowMillis: Long, zone: ZoneId): Long? {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val normalized = TextNormalizer.normalize(text)

        full.find(normalized)?.let { m ->
            return fromParts(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(),
                m.groupValues[6].ifEmpty { "0" }.toInt(), zone,
            )
        }
        fullCn.find(normalized)?.let { m ->
            return fromParts(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(),
                m.groupValues[6].ifEmpty { "0" }.toInt(), zone,
            )
        }
        fullDot.find(normalized)?.let { m ->
            return fromParts(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(),
                m.groupValues[6].ifEmpty { "0" }.toInt(), zone,
            )
        }
        dateOnlyDash.find(normalized)?.let { m ->
            return fromParts(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                12, 0, 0, zone,
            )
        }
        dateOnlyCn.find(normalized)?.let { m ->
            return fromParts(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                12, 0, 0, zone,
            )
        }
        short.find(normalized)?.let { m ->
            val month = m.groupValues[1].toInt()
            val day = m.groupValues[2].toInt()
            var date = LocalDate.of(now.year, month, day)
            if (date.isAfter(now.toLocalDate().plusDays(1))) {
                date = date.minusYears(1)
            }
            return runCatching {
                LocalDateTime.of(
                    date,
                    LocalTime.of(m.groupValues[3].toInt(), m.groupValues[4].toInt()),
                ).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }
        relative.find(normalized)?.let { m ->
            val date = if (m.groupValues[1] == "今天") now.toLocalDate() else now.toLocalDate().minusDays(1)
            return runCatching {
                LocalDateTime.of(
                    date,
                    LocalTime.of(m.groupValues[2].toInt(), m.groupValues[3].toInt()),
                ).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }
        labeledTime.find(normalized)?.let { m ->
            return fromParts(
                now.year, now.monthValue, now.dayOfMonth,
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), 0, zone,
            )
        }
        timeOnly.find(normalized)?.let { m ->
            return fromParts(
                now.year, now.monthValue, now.dayOfMonth,
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), 0, zone,
            )
        }

        return null
    }

    private fun fromParts(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        zone: ZoneId,
    ): Long? = runCatching {
        LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}
