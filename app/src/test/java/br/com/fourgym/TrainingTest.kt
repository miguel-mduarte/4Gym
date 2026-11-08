package br.com.fourgym

import br.com.fourgym.data.SessionDetail
import br.com.fourgym.data.SessionExercise
import br.com.fourgym.data.SessionExerciseDetail
import br.com.fourgym.data.SessionSet
import br.com.fourgym.data.SetStatus
import br.com.fourgym.data.SessionStatus
import br.com.fourgym.data.WorkoutSession
import br.com.fourgym.data.WorkoutReview
import br.com.fourgym.data.guidedExerciseIndex
import br.com.fourgym.model.Measurements
import br.com.fourgym.model.Metric
import br.com.fourgym.model.MuscleGroup
import br.com.fourgym.model.TrackingType
import br.com.fourgym.model.parseDuration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTest {
    @Test
    fun acceptsDecimalCommaAndRejectsInvalidValues() {
        assertEquals(42.5, Metric.WEIGHT.parse("42,5"))
        assertEquals(0.0, Metric.WEIGHT.parse("0"))
        assertNull(Metric.WEIGHT.parse("NaN"))
        assertNull(Metric.DISTANCE.parse("-5"))
        assertNull(Metric.REPS.parse("0"))
    }

    @Test
    fun parsesTimeWithoutCarryingInvalidSecondsOrOverflowing() {
        assertEquals(90, parseDuration("1:30"))
        assertEquals(90, parseDuration("90"))
        assertNull(parseDuration("1:60"))
        assertNull(parseDuration("2147483647:00"))
    }

    @Test
    fun trackingModeRequiresAndDisplaysOnlyItsOwnMeasurements() {
        val values = Measurements(weightKg = 12.5, reps = 8, timeSeconds = 65)
        assertTrue(TrackingType.TIME_WEIGHT.accepts(values))
        assertFalse(TrackingType.DISTANCE_TIME.accepts(values))
        assertEquals("12,5 kg × 01:05", TrackingType.TIME_WEIGHT.describe(values))
        assertEquals("8 reps", TrackingType.REPS_ONLY.describe(values))
    }

    @Test
    fun cardioModesTrackBpmWithOnlyTheirRequiredMeasurements() {
        val bpm = Metric.BPM.change(Measurements(), Metric.BPM.parse("148"))
        assertEquals(148, bpm.bpm)
        assertNull(Metric.BPM.parse("0"))
        assertNull(Metric.BPM.parse("148.5"))
        assertTrue(TrackingType.BPM_ONLY.accepts(bpm))
        assertFalse(TrackingType.BPM_TIME.accepts(bpm))
        assertFalse(TrackingType.BPM_DISTANCE.accepts(bpm))
        assertEquals("148 bpm × 02:00", TrackingType.BPM_TIME.describe(bpm.copy(timeSeconds = 120)))
        assertEquals("148 bpm × 500 m", TrackingType.BPM_DISTANCE.describe(bpm.copy(distanceMeters = 500.0)))
    }

    @Test
    fun volumeExcludesSkippedPendingAndTimedSets() {
        val weightExercise = exercise("weight", TrackingType.WEIGHT_REPS)
        val timedExercise = exercise("timed", TrackingType.TIME_WEIGHT)
        val results = Measurements(weightKg = 42.5, reps = 8, timeSeconds = 30)
        val recorded = SessionSet("one", weightExercise.id, 0, actual = results, status = SetStatus.COMPLETED)
        val detail = SessionDetail(
            WorkoutSession("session", null, "Treino livre", 0, 0),
            listOf(
                SessionExerciseDetail(weightExercise, listOf(
                    recorded,
                    recorded.copy(id = "two", position = 1, status = SetStatus.SKIPPED),
                    recorded.copy(id = "three", position = 2, status = SetStatus.PENDING),
                )),
                SessionExerciseDetail(timedExercise, listOf(recorded.copy(id = "four", sessionExerciseId = timedExercise.id))),
            ),
        )
        assertEquals(340.0, detail.volumeKg, 0.0)
        assertEquals(2, detail.completedCount)
        assertEquals(1, detail.skippedCount)
    }

    @Test
    fun timerDeadlineCountsOnlyActiveTime() {
        val paused = SessionSet(
            "set", "exercise", 0, timerTargetSeconds = 45,
            timerElapsedMillis = 12_000, measuredStartedAt = 5_000,
        )
        assertNull(paused.targetDeadline())
        assertEquals(12_000L, paused.elapsedAt(100_000))

        val resumed = paused.copy(timerStartedAt = 100_000)
        assertEquals(133_000L, resumed.targetDeadline())
        assertEquals(45_000L, resumed.elapsedAt(133_000))
    }

    @Test
    fun overtimeKeepsRecordedTimeSeparateFromGoal() {
        val set = SessionSet(
            "set", "exercise", 0, actual = Measurements(timeSeconds = 960), timerTargetSeconds = 900,
        )
        assertEquals(900, set.timerGoalSeconds)
        assertEquals(60L, set.extraSeconds())
        assertEquals(0L, set.copy(actual = Measurements(timeSeconds = 870)).extraSeconds())
    }

    @Test
    fun guidedModeAdvancesAfterTheLastPendingSetOfAnExercise() {
        val first = exercise("first", TrackingType.TIME)
        val next = exercise("next", TrackingType.REPS_ONLY)
        val pending = SessionSet("one", first.id, 0)
        val skipped = pending.copy(id = "two", status = SetStatus.SKIPPED)
        val nextItem = SessionExerciseDetail(next, listOf(SessionSet("three", next.id, 0)))

        assertEquals(0, listOf(SessionExerciseDetail(first, listOf(skipped, pending)), nextItem).guidedExerciseIndex())
        val finished = SessionExerciseDetail(first, listOf(skipped, pending.copy(status = SetStatus.COMPLETED)))
        assertEquals(1, listOf(finished, nextItem).guidedExerciseIndex())
        assertEquals(-1, listOf(finished).guidedExerciseIndex())
    }

    @Test
    fun reviewPreservesExactDurationAndCanBackdateWithAnEditedDuration() {
        val start = Instant.parse("2026-11-08T10:15:32Z").toEpochMilli()
        val session = WorkoutSession("session", null, "Treino livre", start, 0)
        val review = WorkoutReview.open(SessionDetail(session, emptyList()), start + 81_250L)
        val unchanged = checkNotNull(review.resolveTiming(ZoneOffset.UTC))
        assertEquals(start, unchanged.startedAt)
        assertEquals(start + 81_250L, unchanged.endedAt)

        val backdated = review.copy(date = "07/11/2026", time = "18:30", duration = "45:00")
        val resolved = checkNotNull(backdated.resolveTiming(ZoneOffset.UTC))
        assertEquals(Instant.parse("2026-11-07T18:30:00Z").toEpochMilli(), resolved.startedAt)
        assertEquals(45 * 60_000L, resolved.endedAt - resolved.startedAt)
        assertNull(backdated.copy(date = "31/11/2026").resolveTiming(ZoneOffset.UTC))
    }

    @Test
    fun reviewCanRestoreSkippedSetsAndChecksTheirMeasurements() {
        val exercise = exercise("cardio", TrackingType.BPM_ONLY)
        val skipped = SessionSet("set", exercise.id, 0, status = SetStatus.SKIPPED)
        val session = WorkoutSession("session", null, "Treino livre", 0, 0)
        val review = WorkoutReview.open(
            SessionDetail(session, listOf(SessionExerciseDetail(exercise, listOf(skipped)))), 60_000L,
        )
        assertEquals(0, review.completedCount)
        val restored = review.withResult(skipped, review.result(skipped).copy(status = SetStatus.COMPLETED))
        assertTrue(restored.hasIncompleteValues)
        val recorded = restored.withResult(skipped, restored.result(skipped).copy(actual = Measurements(bpm = 143)))
        assertEquals(1, recorded.completedCount)
        assertFalse(recorded.hasIncompleteValues)
        assertEquals(SetStatus.SKIPPED, skipped.status)
    }

    @Test
    fun historyEditKeepsRecordedEndAndOffsetsWhenChangingNotes() {
        val start = Instant.parse("2026-11-07T21:30:24Z").toEpochMilli()
        val end = start + 42 * 60_000L + 250
        val session = WorkoutSession(
            "session", null, "Treino livre", start, -10_800,
            status = SessionStatus.COMPLETED, endedAt = end, endedOffsetSeconds = -10_800,
        )
        val review = WorkoutReview.open(SessionDetail(session, emptyList()), end + 86_400_000L)
            .copy(notes = "Anotação corrigida")
        assertEquals("42:00", review.duration)
        val timing = checkNotNull(review.resolveTiming(ZoneOffset.UTC))
        assertEquals(start, timing.startedAt)
        assertEquals(end, timing.endedAt)
        assertEquals(-10_800, timing.startedOffsetSeconds)
        assertEquals(-10_800, timing.endedOffsetSeconds)
    }

    private fun exercise(id: String, tracking: TrackingType) =
        SessionExercise(id, "session", id, 0, id, "Barra", MuscleGroup.CHEST, tracking, "", 90)
}
