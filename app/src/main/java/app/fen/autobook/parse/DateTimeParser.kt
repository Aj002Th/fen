package app.fen.autobook.parse

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object DateTimeParser {
    private val full = Regex("""(\d{4})[-/](\d{1,2})[-/](\d{1,2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    private val short = Regex("""(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})""")
    private val relative = Regex("""(今天|昨天)\s*(\d{1,2}):(\d{2})""")
    private val timeOnly = Regex("""^(\d{1,2}):(\d{2})$""")

    fun parse(text: String, nowMillis: Long, zone: ZoneId): Long? {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val normalized = TextNormalizer.normalize(text)

        full.find(normalized)?.let { m ->
            return runCatching {
                LocalDateTime.of(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt(),
                    m.groupValues[4].toInt(),
                    m.groupValues[5].toInt(),
                    m.groupValues[6].ifEmpty { "0" }.toInt(),
                ).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }

        short.find(normalized)?.let { m ->
            return runCatching {
                LocalDateTime.of(
                    now.year,
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt(),
                    m.groupValues[4].toInt(),
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

        timeOnly.find(normalized)?.let { m ->
            return runCatching {
                LocalDateTime.of(
                    now.toLocalDate(),
                    LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()),
                ).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }

        return null
    }
}
