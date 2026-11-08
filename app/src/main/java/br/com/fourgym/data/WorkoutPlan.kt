package br.com.fourgym.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class PlanSchedule(val label: String) { WEEKLY("Dias fixos"), INTERVAL("Por intervalo") }
enum class PlanRule(val label: String) { BY_DAY("Treino por dia"), ROTATION("Sequência") }

@Entity
data class WorkoutPlan(
    @PrimaryKey val id: String,
    val name: String,
    val startEpochDay: Long,
    val schedule: PlanSchedule = PlanSchedule.WEEKLY,
    val rule: PlanRule = PlanRule.ROTATION,
    val weekdayMask: Int = 0,
    val intervalDays: Int = 2,
    val active: Boolean = false,
    val updatedAt: Long = 0,
) {
    fun includes(day: DayOfWeek): Boolean = (weekdayMask and day.bit()) != 0

    fun scheduled(date: LocalDate): Boolean {
        val elapsed = date.toEpochDay() - startEpochDay
        return when (schedule) {
            PlanSchedule.WEEKLY -> includes(date.dayOfWeek)
            PlanSchedule.INTERVAL -> elapsed >= 0 && intervalDays > 0 && elapsed % intervalDays == 0L
        }
    }

    fun withDay(day: DayOfWeek, selected: Boolean): WorkoutPlan {
        val mask = if (selected) weekdayMask or day.bit() else weekdayMask and day.bit().inv()
        return copy(weekdayMask = mask)
    }

    private fun DayOfWeek.bit(): Int = 1 shl (value - 1)
}

@Entity(
    primaryKeys = ["planId", "routineId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlan::class, parentColumns = ["id"], childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"]),
    ],
    indices = [Index("routineId")],
)
data class PlanWorkout(val planId: String, val routineId: String, val position: Int)

@Entity(
    primaryKeys = ["planId", "weekday"],
    foreignKeys = [ForeignKey(
        entity = PlanWorkout::class, parentColumns = ["planId", "routineId"],
        childColumns = ["planId", "routineId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("planId", "routineId")],
)
data class PlanDay(val planId: String, val weekday: Int, val routineId: String)

data class PlanDetail(
    @Embedded val plan: WorkoutPlan,
    @Relation(parentColumn = "id", entityColumn = "planId") val workouts: List<PlanWorkout>,
    @Relation(parentColumn = "id", entityColumn = "planId") val days: List<PlanDay>,
) {
    fun expectsWorkout(date: LocalDate, availableIds: Set<String>): Boolean {
        if (!plan.scheduled(date) || workouts.none { it.routineId in availableIds }) return false
        return plan.schedule != PlanSchedule.WEEKLY || plan.rule != PlanRule.BY_DAY ||
            days.any { it.weekday == date.dayOfWeek.value && it.routineId in availableIds }
    }

    fun today(date: LocalDate, availableIds: Set<String>, sessions: List<WorkoutSession>): PlanToday {
        val orderedIds = workouts.sortedBy { it.position }.map { it.routineId }.filter { it in availableIds }
        if (orderedIds.isEmpty()) return PlanToday.Empty
        if (plan.schedule == PlanSchedule.WEEKLY && plan.weekdayMask == 0) return PlanToday.Unscheduled

        if (!plan.scheduled(date)) return PlanToday.Rest

        val completed = sessions.filter { it.planId == plan.id && it.status == SessionStatus.COMPLETED }
        val newest = compareBy<WorkoutSession> { it.endedAt }.thenBy { it.id }
        val doneToday = completed.filter { it.startDate() == date }.maxWithOrNull(newest)
        if (doneToday != null) return PlanToday.Done(doneToday.name)

        if (plan.schedule == PlanSchedule.WEEKLY && plan.rule == PlanRule.BY_DAY) {
            val assigned = days.firstOrNull { it.weekday == date.dayOfWeek.value }?.routineId
            return if (assigned != null && assigned in orderedIds) PlanToday.Scheduled(assigned) else PlanToday.Unassigned
        }
        val previous = completed.filter { it.routineId in orderedIds && it.startDate().isBefore(date) }
            .maxWithOrNull(newest)
        val next = (orderedIds.indexOf(previous?.routineId) + 1) % orderedIds.size
        return PlanToday.Scheduled(orderedIds[next])
    }
}

sealed interface PlanToday {
    data object Empty : PlanToday
    data object Unscheduled : PlanToday
    data object Rest : PlanToday
    data object Unassigned : PlanToday
    data class Done(val workoutName: String) : PlanToday
    data class Scheduled(val routineId: String) : PlanToday
}

data class PlanDraft(
    val detail: PlanDetail,
    val workoutDrafts: Map<String, RoutineDetail> = emptyMap(),
    val isNew: Boolean = false,
) {
    fun withWorkout(workout: RoutineDetail): PlanDraft {
        val id = workout.routine.id
        return attachWorkout(id).copy(workoutDrafts = workoutDrafts + (id to workout))
    }

    fun attachWorkout(routineId: String): PlanDraft {
        if (detail.workouts.any { it.routineId == routineId }) return this
        return copy(detail = detail.copy(workouts = detail.workouts +
            PlanWorkout(detail.plan.id, routineId, detail.workouts.size)))
    }

    fun removeWorkout(routineId: String): PlanDraft = copy(
        detail = detail.copy(
            workouts = detail.workouts.filterNot { it.routineId == routineId }.mapIndexed { index, item ->
                item.copy(position = index)
            },
            days = detail.days.filterNot { it.routineId == routineId },
        ),
        workoutDrafts = workoutDrafts - routineId,
    )

    fun moveWorkout(index: Int, direction: Int): PlanDraft {
        val items = detail.workouts.toMutableList()
        val target = index + direction
        if (index !in items.indices || target !in items.indices) return this
        items.add(target, items.removeAt(index))
        return copy(detail = detail.copy(workouts = items.mapIndexed { position, item ->
            item.copy(position = position)
        }))
    }

    fun selectDay(day: DayOfWeek, selected: Boolean): PlanDraft = copy(detail = detail.copy(
        plan = detail.plan.withDay(day, selected),
        days = if (selected) detail.days else detail.days.filterNot { it.weekday == day.value },
    ))

    fun assign(day: DayOfWeek, routineId: String?): PlanDraft {
        val otherDays = detail.days.filterNot { it.weekday == day.value }
        val assigned = routineId?.let { PlanDay(detail.plan.id, day.value, it) }
        return copy(detail = detail.copy(days = otherDays + listOfNotNull(assigned)))
    }
}

internal fun WorkoutSession.startDate(): LocalDate = Instant.ofEpochMilli(startedAt)
    .atOffset(ZoneOffset.ofTotalSeconds(startedOffsetSeconds)).toLocalDate()
