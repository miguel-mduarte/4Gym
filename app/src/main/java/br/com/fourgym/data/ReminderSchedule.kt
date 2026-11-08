package br.com.fourgym.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ScheduledReminder(val planId: String, val date: LocalDate, val time: Instant)

fun ReminderSettings.timeOn(date: LocalDate, zone: ZoneId): Instant =
    date.atTime(hour, minute).atZone(zone).toInstant()

fun nextReminder(
    plan: PlanDetail,
    settings: ReminderSettings,
    workouts: Set<String>,
    sessions: List<WorkoutSession>,
    now: Instant,
    zone: ZoneId,
    lastNotified: LocalDate?,
): ScheduledReminder? {
    if (!settings.enabled || plan.workouts.none { it.routineId in workouts }) return null
    val today = now.atZone(zone).toLocalDate()
    val dates = when (plan.plan.schedule) {
        PlanSchedule.WEEKLY -> (0L..7L).map(today::plusDays)
        PlanSchedule.INTERVAL -> {
            val interval = plan.plan.intervalDays.toLong()
            if (interval <= 0) return null
            val first = LocalDate.ofEpochDay(plan.plan.startEpochDay)
            val elapsed = (today.toEpochDay() - first.toEpochDay()).coerceAtLeast(0)
            val next = first.plusDays((elapsed + interval - 1) / interval * interval)
            listOf(next, next.plusDays(interval))
        }
    }
    for (date in dates) {
        val time = settings.timeOn(date, zone)
        if (time > now && date != lastNotified && plan.today(date, workouts, sessions) is PlanToday.Scheduled) {
            return ScheduledReminder(plan.plan.id, date, time)
        }
    }
    return null
}
