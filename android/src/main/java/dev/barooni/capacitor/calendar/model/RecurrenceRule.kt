package dev.barooni.capacitor.calendar.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val end: Long?,
) {

    init {
        require(interval > 0)
    }

    /**
     * @return a RFC 5545 compliant recurrence rule
     */
    override fun toString(): String {
        val stringBuilder = StringBuilder()

        stringBuilder.append("FREQ=${frequency};")
        stringBuilder.append("INTERVAL=${interval};")

        when {
            end != null -> {
                val instant = end.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")) }
                stringBuilder.append(
                    "UNTIL=${
                        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(instant)
                    };"
                )
            }
        }

        return stringBuilder.removeSuffix(";").toString()
    }

}