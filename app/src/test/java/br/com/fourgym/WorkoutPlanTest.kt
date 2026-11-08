package br.com.fourgym

import br.com.fourgym.data.PlanDay
import br.com.fourgym.data.PlanDetail
import br.com.fourgym.data.PlanDraft
import br.com.fourgym.data.PlanRule
import br.com.fourgym.data.PlanSchedule
import br.com.fourgym.data.PlanToday
import br.com.fourgym.data.PlanWorkout
import br.com.fourgym.data.Routine
import br.com.fourgym.data.RoutineDetail
import br.com.fourgym.data.SessionStatus
import br.com.fourgym.data.WorkoutPlan
import br.com.fourgym.data.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val plan = WorkoutPlan("plan", "ABC", monday.toEpochDay())
    private val workouts = listOf(PlanWorkout(plan.id, "a", 0), PlanWorkout(plan.id, "b", 1))
    private val ids = setOf("a", "b")

    @Test
    fun weeklyPlanUsesAssignmentAndDistinguishesRestFromMissingWorkout() {
        val configured = plan.copy(rule = PlanRule.BY_DAY)
            .withDay(DayOfWeek.MONDAY, true).withDay(DayOfWeek.WEDNESDAY, true)
        val detail = PlanDetail(configured, workouts, listOf(PlanDay(plan.id, DayOfWeek.MONDAY.value, "b")))
        assertEquals(PlanToday.Scheduled("b"), detail.today(monday, ids, emptyList()))
        assertEquals(PlanToday.Rest, detail.today(monday.plusDays(1), ids, emptyList()))
        assertEquals(PlanToday.Unassigned, detail.today(monday.plusDays(2), ids, emptyList()))
        assertEquals(PlanToday.Unassigned, detail.today(monday, setOf("a"), emptyList()))
    }

    @Test
    fun intervalUsesCalendarAnchorAndOnlyCompletedSessionsFromThePlan() {
        val detail = PlanDetail(plan.copy(schedule = PlanSchedule.INTERVAL), workouts, emptyList())
        val previous = session("a", monday)
        val canceled = session("b", monday.plusDays(1)).copy(status = SessionStatus.CANCELLED)
        val other = session("b", monday.plusDays(1)).copy(planId = "another")
        assertEquals(PlanToday.Rest, detail.today(monday.minusDays(2), ids, emptyList()))
        assertEquals(PlanToday.Scheduled("a"), detail.today(monday, ids, emptyList()))
        assertEquals(PlanToday.Done("a"), detail.today(monday, ids, listOf(previous)))
        assertEquals(PlanToday.Rest, detail.today(monday.plusDays(1), ids, listOf(previous)))
        assertEquals(PlanToday.Scheduled("b"), detail.today(monday.plusDays(2), ids, listOf(previous, canceled, other)))
    }

    @Test
    fun rotationWrapsAndIgnoresFutureOrUnavailableWorkouts() {
        val detail = PlanDetail(plan.withDay(DayOfWeek.MONDAY, true), workouts, emptyList())
        val previous = session("b", monday.minusDays(7))
        val future = session("a", monday.plusDays(7))
        assertEquals(PlanToday.Scheduled("a"), detail.today(monday, ids, listOf(previous, future)))
        assertEquals(PlanToday.Scheduled("a"), detail.today(monday, setOf("a"), listOf(previous)))
    }

    @Test
    fun blankPlanCanAttachAndCreateWorkoutsAndRemoveTheirAssignments() {
        val blank = PlanDraft(PlanDetail(plan, emptyList(), emptyList()), isNew = true)
        assertEquals(PlanToday.Empty, blank.detail.today(monday, ids, emptyList()))
        val created = RoutineDetail(Routine("b", "B", 0), emptyList())
        val draft = blank.attachWorkout("a").withWorkout(created).moveWorkout(1, -1)
            .selectDay(DayOfWeek.MONDAY, true).assign(DayOfWeek.MONDAY, "b")
        assertEquals(listOf("b", "a"), draft.detail.workouts.map { it.routineId })
        assertEquals(created, draft.workoutDrafts["b"])
        assertEquals(PlanToday.Unscheduled, blank.attachWorkout("a").detail.today(monday, ids, emptyList()))
        val removed = draft.removeWorkout("b")
        assertTrue(removed.detail.days.isEmpty())
        assertTrue(removed.workoutDrafts.isEmpty())
        assertEquals(listOf("a"), removed.detail.workouts.map { it.routineId })
    }

    private fun session(routineId: String, date: LocalDate): WorkoutSession {
        val start = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return WorkoutSession(
            "$routineId-$date", routineId, routineId, start, 0,
            status = SessionStatus.COMPLETED, endedAt = start + 60_000, planId = plan.id,
        )
    }
}
