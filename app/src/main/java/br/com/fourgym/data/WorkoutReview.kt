package br.com.fourgym.data

import br.com.fourgym.model.Measurements
import br.com.fourgym.model.formatDuration
import br.com.fourgym.model.parseDuration
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

private val reviewDate = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)
private val reviewTime = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)

data class ReviewedSet(val status: SetStatus, val actual: Measurements)

data class ReviewTiming(
    val startedAt: Long,
    val endedAt: Long,
    val startedOffsetSeconds: Int,
    val endedOffsetSeconds: Int,
)

data class WorkoutReview(
    val detail: SessionDetail,
    val initialEndAt: Long,
    val date: String,
    val time: String,
    val duration: String,
    val notes: String,
    val rating: Int? = null,
    val edits: Map<String, ReviewedSet> = emptyMap(),
) {
    fun result(set: SessionSet): ReviewedSet = edits[set.id] ?: ReviewedSet(set.status, set.actual)

    fun withResult(set: SessionSet, result: ReviewedSet): WorkoutReview =
        copy(edits = edits + (set.id to result))

    val completedCount: Int get() = detail.exercises.sumOf { item ->
        item.sets.count { result(it).status == SetStatus.COMPLETED }
    }

    val hasIncompleteValues: Boolean get() = detail.exercises.any { item ->
        item.sets.any { set ->
            val reviewed = result(set)
            reviewed.status == SetStatus.COMPLETED && !item.exercise.tracking.accepts(reviewed.actual)
        }
    }

    fun resolveTiming(zone: ZoneId = ZoneId.systemDefault()): ReviewTiming? {
        val session = detail.session
        val original = Instant.ofEpochMilli(session.startedAt).atOffset(ZoneOffset.ofTotalSeconds(session.startedOffsetSeconds))
        val originalDuration = (initialEndAt - session.startedAt).coerceAtLeast(0)
        val durationMillis = if (duration.trim() == formatDuration(originalDuration / 1000)) {
            originalDuration
        } else {
            (parseDuration(duration) ?: return null) * 1000L
        }

        val start = try {
            LocalDateTime.of(LocalDate.parse(date.trim(), reviewDate), LocalTime.parse(time.trim(), reviewTime))
        } catch (_: DateTimeException) {
            return null
        }
        val sameStart = start == original.toLocalDateTime().withSecond(0).withNano(0)
        val zoned = start.atZone(zone)
        if (!sameStart && zoned.toLocalDateTime() != start) return null
        val startedAt = if (sameStart) session.startedAt else zoned.toInstant().toEpochMilli()
        val endedAt = startedAt + durationMillis
        val endedOffset = session.endedOffsetSeconds?.takeIf { sameStart && endedAt == session.endedAt }
        return ReviewTiming(
            startedAt, endedAt,
            if (sameStart) session.startedOffsetSeconds else zoned.offset.totalSeconds,
            endedOffset ?: zone.rules.getOffset(Instant.ofEpochMilli(endedAt)).totalSeconds,
        )
    }

    companion object {
        fun open(detail: SessionDetail, at: Long): WorkoutReview {
            val session = detail.session
            val start = Instant.ofEpochMilli(session.startedAt).atOffset(ZoneOffset.ofTotalSeconds(session.startedOffsetSeconds))
            val end = session.endedAt ?: at
            return WorkoutReview(
                detail, end, reviewDate.format(start), reviewTime.format(start),
                formatDuration((end - session.startedAt).coerceAtLeast(0) / 1000),
                session.notes, session.rating,
            )
        }
    }
}
