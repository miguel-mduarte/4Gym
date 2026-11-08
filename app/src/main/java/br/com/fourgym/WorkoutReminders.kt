package br.com.fourgym

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import br.com.fourgym.data.PlanToday
import br.com.fourgym.data.ScheduledReminder
import br.com.fourgym.data.TrainingRepository
import br.com.fourgym.data.nextReminder
import br.com.fourgym.data.timeOn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val CHANNEL = "workout-plans"
private const val NOTIFICATION_ID = 51
private const val REMINDER_ACTION = "br.com.fourgym.WORKOUT_REMINDER"
private const val PLAN = "plan"
private const val DAY = "day"
private const val TIME = "time"

internal fun canPostReminders(context: Context): Boolean =
    (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED) &&
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

class WorkoutReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as FourGymApplication
        val pending = goAsync()
        application.refreshReminders(intent.takeIf { it.action == REMINDER_ACTION }) { pending.finish() }
    }
}

internal class WorkoutReminders(private val context: Context, private val repository: TrainingRepository) {
    private val mutex = Mutex()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val delivery = context.getSharedPreferences("reminder-delivery", Context.MODE_PRIVATE)

    suspend fun refresh(deliveredIntent: Intent? = null) = mutex.withLock {
        try {
            schedule(deliveredIntent)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e("4Gym", "Falha ao programar lembrete de treino", failure)
        }
    }

    private suspend fun schedule(deliveredIntent: Intent?) {
        val plan = repository.plans.first().firstOrNull { it.plan.active }
        val settings = repository.reminderSettings.first()
        if (plan == null || !settings.enabled || !canPostReminders(context)) {
            alarms.cancel(alarmIntent(null))
            notifications.cancel(NOTIFICATION_ID)
            return
        }

        val routines = repository.routines.first().associateBy { it.routine.id }
        val sessions = repository.history.first().map { it.session }
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        var last = if (delivery.getString(PLAN, null) == plan.plan.id && delivery.contains(DAY)) {
            LocalDate.ofEpochDay(delivery.getLong(DAY, 0))
        } else null
        if (deliveredIntent != null && last != today &&
            deliveredIntent.getStringExtra(PLAN) == plan.plan.id &&
            deliveredIntent.getLongExtra(DAY, Long.MIN_VALUE) == today.toEpochDay() &&
            deliveredIntent.getLongExtra(TIME, Long.MIN_VALUE) == settings.timeOn(today, zone).toEpochMilli()
        ) {
            val workout = plan.today(today, routines.keys, sessions) as? PlanToday.Scheduled
            val active = repository.activeSession.first()?.session
            if (workout != null && active?.planId != plan.plan.id) {
                val name = routines.getValue(workout.routineId).routine.name
                if (notifyWorkout(name, plan.plan.name)) {
                    last = today
                    delivery.edit().putString(PLAN, plan.plan.id).putLong(DAY, today.toEpochDay()).apply()
                }
            }
        }
        if (plan.today(today, routines.keys, sessions) is PlanToday.Done) notifications.cancel(NOTIFICATION_ID)

        val next = nextReminder(plan, settings, routines.keys, sessions, now, zone, last)
        val pending = alarmIntent(next)
        if (next == null) alarms.cancel(pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.time.toEpochMilli(), pending)
    }

    private fun alarmIntent(reminder: ScheduledReminder?): PendingIntent {
        val intent = Intent(context, WorkoutReminderReceiver::class.java).setAction(REMINDER_ACTION)
        if (reminder != null) {
            intent.putExtra(PLAN, reminder.planId)
            intent.putExtra(DAY, reminder.date.toEpochDay())
            intent.putExtra(TIME, reminder.time.toEpochMilli())
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notifyWorkout(workout: String, plan: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Lembretes de treino", NotificationManager.IMPORTANCE_DEFAULT))
        if (notifications.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return false
        val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle("Treino de hoje")
            .setContentText("$workout · $plan")
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
        return true
    }
}
