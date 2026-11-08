package br.com.fourgym.data

import androidx.room.withTransaction
import br.com.fourgym.model.EquipmentCategory
import br.com.fourgym.model.Measurements
import br.com.fourgym.model.Metric
import br.com.fourgym.model.newId
import java.time.Instant
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val DEFAULT_REST_SECONDS = 90

class TrainingRepository(
    private val database: TrainingDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.training()
    private val planDao = database.plans()
    private val profileDao = database.profile()
    val equipment = dao.equipment()
    val exercises = dao.exercises()
    val routines = dao.routines()
    val activeSession = dao.activeSession()
    val history = dao.history()
    val plans = planDao.observePlans()
    val bodyRecords = profileDao.records()
    val reminderSettings = profileDao.reminders().map { it ?: ReminderSettings() }

    suspend fun saveBodyRecord(record: BodyRecord) {
        check(record.isValid()) { "Confira a medida e preencha os campos obrigatórios." }
        profileDao.save(record.copy(
            customName = record.customName.trim(), customUnit = record.customUnit.trim(), notes = record.notes.trim(),
        ))
    }

    suspend fun deleteBodyRecord(id: String) = profileDao.delete(id)

    suspend fun saveReminder(settings: ReminderSettings) {
        check(settings.hour in 0..23 && settings.minute in 0..59) { "Informe um horário válido." }
        profileDao.save(settings)
    }

    private data class ScheduledTimer(val sessionId: String, val deadline: Long)

    suspend fun monitorTimedSets() {
        activeSession.map { detail ->
            detail?.exercises?.firstNotNullOfOrNull { item ->
                if (item.exercise.allowOvertime) null else item.sets.firstNotNullOfOrNull { set ->
                    set.targetDeadline()?.let { ScheduledTimer(detail.session.id, it) }
                }
            }
        }.distinctUntilChanged().collectLatest { timer ->
            if (timer != null) {
                while (now() < timer.deadline) delay(timer.deadline - now())
                inActiveSession(timer.sessionId) {}
            }
        }
    }

    suspend fun initialize() {
        val defaults = listOf(
            Triple("barbell", "Barra", EquipmentCategory.FREE_WEIGHTS),
            Triple("dumbbells", "Halteres", EquipmentCategory.FREE_WEIGHTS),
            Triple("plates", "Anilha", EquipmentCategory.FREE_WEIGHTS),
            Triple("kettlebell", "Kettlebell", EquipmentCategory.FREE_WEIGHTS),
            Triple("cable", "Polia", EquipmentCategory.MACHINES_CABLES),
            Triple("machine", "Máquina", EquipmentCategory.MACHINES_CABLES),
            Triple("bodyweight", "Nenhum", EquipmentCategory.BODYWEIGHT),
            Triple("band", "Faixa elástica", EquipmentCategory.OTHER),
            Triple("suspension", "Fita de suspensão", EquipmentCategory.OTHER),
            Triple("treadmill", "Esteira", EquipmentCategory.CARDIO),
            Triple("bike", "Bicicleta ergométrica", EquipmentCategory.CARDIO),
        )
        dao.seedEquipment(defaults.map { (key, name, category) ->
            val id = UUID.nameUUIDFromBytes("4gym/equipment/$key".toByteArray(Charsets.UTF_8))
            Equipment(id.toString(), name, category, 0)
        })
    }

    suspend fun saveEquipment(item: Equipment) =
        dao.saveEquipment(item.copy(name = item.name.trim(), updatedAt = now()))

    suspend fun saveExercise(item: Exercise) =
        dao.saveExercise(item.copy(name = item.name.trim(), instructions = item.instructions.trim(), updatedAt = now()))

    suspend fun saveRoutine(detail: RoutineDetail) = database.withTransaction {
        val routine = detail.routine.copy(
            name = detail.routine.name.trim(),
            description = detail.routine.description.trim(),
            updatedAt = now(),
        )
        dao.saveRoutine(routine)
        dao.deleteRoutineExercises(routine.id)
        dao.insertRoutineExercises(detail.exercises.mapIndexed { position, item ->
            item.exercise.copy(
                routineId = routine.id, position = position, executionNotes = item.exercise.executionNotes.trim(),
            )
        })
        dao.insertRoutineSets(detail.exercises.flatMap { item ->
            item.sets.mapIndexed { position, set ->
                set.copy(routineExerciseId = item.exercise.id, position = position)
            }
        })
    }

    suspend fun duplicateRoutine(routineId: String) = database.withTransaction {
        val original = dao.routineById(routineId) ?: return@withTransaction
        val id = newId()
        val copy = original.exercises.sortedBy { it.exercise.position }.map { item ->
            val exercise = item.exercise.copy(id = newId(), routineId = id)
            RoutineExerciseDetail(exercise, item.sets.sortedBy { it.position }.map {
                it.copy(id = newId(), routineExerciseId = exercise.id)
            })
        }
        saveRoutine(RoutineDetail(original.routine.copy(id = id, name = "${original.routine.name} (cópia)"), copy))
    }

    suspend fun deleteRoutine(id: String) = dao.archiveRoutine(id, now())

    suspend fun savePlan(draft: PlanDraft) = database.withTransaction {
        val detail = draft.detail
        val plan = detail.plan
        val stored = planDao.byId(plan.id)?.plan
        check(draft.isNew || stored != null) { "Plano indisponível." }
        check(plan.name.isNotBlank()) { "Dê um nome ao plano." }
        check(plan.schedule != PlanSchedule.INTERVAL || plan.intervalDays > 0) { "Informe um intervalo válido." }

        val workouts = detail.workouts.sortedBy { it.position }.mapIndexed { index, item ->
            PlanWorkout(plan.id, item.routineId, index)
        }
        val linkedIds = workouts.map { it.routineId }.toSet()
        for ((id, workout) in draft.workoutDrafts) {
            if (id !in linkedIds) continue
            if (workout.routine.updatedAt != 0L) {
                check(dao.routineById(id)?.routine?.updatedAt == workout.routine.updatedAt) {
                    "Um dos treinos mudou. Abra o plano novamente."
                }
            }
            saveRoutine(workout)
        }
        planDao.save(plan.copy(
            name = plan.name.trim(),
            rule = if (plan.schedule == PlanSchedule.INTERVAL) PlanRule.ROTATION else plan.rule,
            active = stored?.active ?: !planDao.hasActivePlan(),
            updatedAt = now(),
        ))
        planDao.clearWorkouts(plan.id)
        planDao.insertWorkouts(workouts)
        planDao.insertDays(detail.days.filter {
            it.routineId in linkedIds && it.weekday in 1..7 && plan.includes(DayOfWeek.of(it.weekday))
        }.map { it.copy(planId = plan.id) })
    }

    suspend fun activatePlan(id: String) = database.withTransaction {
        checkNotNull(planDao.byId(id)) { "Plano indisponível." }
        planDao.clearActive()
        planDao.activate(id)
    }

    suspend fun deletePlan(id: String) = planDao.delete(id)

    suspend fun start(routineId: String? = null, planId: String? = null): String = database.withTransaction {
        dao.findActiveSession()?.let { return@withTransaction it.id }
        val startedAt = now()
        if (planId != null) {
            val detail = checkNotNull(planDao.byId(planId)) { "Plano indisponível." }
            val date = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val scheduled = detail.today(
                date, planDao.availableWorkoutIds(planId).toSet(), planDao.completedSessions(planId),
            )
            check(detail.plan.active && scheduled is PlanToday.Scheduled && scheduled.routineId == routineId) {
                "O treino de hoje mudou. Confira seu plano."
            }
        }
        val routine = routineId?.let { checkNotNull(dao.routineById(it)) { "Rotina indisponível." } }
        val session = WorkoutSession(
            newId(), routineId, routine?.routine?.name ?: "Treino livre",
            startedAt, offsetAt(startedAt),
            planId = planId,
        )
        dao.insertSession(session)

        if (routine != null) {
            val previous = dao.previousSets(routine.routine.id)
                .groupBy { Triple(it.plannedSetId, it.exerciseId, it.tracking) }
            for (planned in routine.exercises) {
                val exercise = checkNotNull(dao.exerciseById(planned.exercise.exerciseId))
                val item = snapshot(
                    session.id, exercise, planned.exercise.position, planned.exercise.restSeconds,
                ).copy(
                    executionNotes = planned.exercise.executionNotes,
                    allowOvertime = Metric.TIME in exercise.tracking.metrics && planned.exercise.allowOvertime,
                )
                dao.insertSessionExercise(item)
                dao.insertSessionSets(planned.sets.map { set ->
                    val reference = previous[Triple(set.id, exercise.id, exercise.tracking)]?.firstOrNull()
                    val suggested = reference?.actual ?: set.target
                    val target = if (Metric.TIME in exercise.tracking.metrics) {
                        set.target.timeSeconds ?: suggested.timeSeconds
                    } else null
                    SessionSet(
                        id = newId(), sessionExerciseId = item.id, position = set.position,
                        plannedSetId = set.id, restOverrideSeconds = set.restOverrideSeconds,
                        actual = suggested.copy(timeSeconds = target),
                        previous = reference?.actual, previousSessionEndedAt = reference?.endedAt,
                        timerTargetSeconds = target,
                    )
                })
            }
        }
        session.id
    }

    suspend fun setMode(sessionId: String, mode: WorkoutMode) = inActiveSession(sessionId) {
        if (it.mode != mode) dao.updateSession(it.copy(mode = mode))
    }

    suspend fun saveSessionNotes(sessionId: String, notes: String) = inActiveSession(sessionId) {
        dao.updateSession(it.copy(notes = notes.trim()))
    }

    suspend fun addExercise(sessionId: String, exerciseId: String) = inActiveSession(sessionId) {
        val exercise = dao.exerciseById(exerciseId) ?: return@inActiveSession
        val position = (dao.sessionExercises(sessionId).lastOrNull()?.position ?: -1) + 1
        val item = snapshot(sessionId, exercise, position, DEFAULT_REST_SECONDS)
        dao.insertSessionExercise(item)
        dao.insertSessionSets(listOf(SessionSet(newId(), item.id, 0)))
    }

    suspend fun replaceExercise(sessionId: String, itemId: String, exerciseId: String) =
        inActiveSession(sessionId) {
            val current = dao.sessionExercises(sessionId).find { it.id == itemId } ?: return@inActiveSession
            val exercise = dao.exerciseById(exerciseId) ?: return@inActiveSession
            val sets = dao.sessionSets(itemId)
            check(sets.none { it.status == SetStatus.COMPLETED }) { "Este exercício já tem séries concluídas." }
            val replacement = snapshot(sessionId, exercise, current.position, current.restSeconds)
            dao.removeSessionExercise(itemId)
            dao.insertSessionExercise(replacement)
            dao.insertSessionSets(sets.map {
                SessionSet(newId(), replacement.id, it.position, restOverrideSeconds = it.restOverrideSeconds)
            }.ifEmpty { listOf(SessionSet(newId(), replacement.id, 0)) })
        }

    suspend fun moveExercise(sessionId: String, itemId: String, direction: Int) = inActiveSession(sessionId) {
        val items = dao.sessionExercises(sessionId)
        val index = items.indexOfFirst { it.id == itemId }
        val other = index + direction
        if (index in items.indices && other in items.indices) {
            dao.updateSessionExercises(listOf(
                items[index].copy(position = items[other].position),
                items[other].copy(position = items[index].position),
            ))
        }
    }

    suspend fun removeExercise(sessionId: String, itemId: String) = inActiveSession(sessionId) {
        if (dao.sessionExercises(sessionId).any { it.id == itemId }) dao.removeSessionExercise(itemId)
    }

    suspend fun updateExerciseSettings(
        sessionId: String, itemId: String, notes: String, restSeconds: Int, allowOvertime: Boolean,
    ) = inActiveSession(sessionId) {
        val item = dao.sessionExercises(sessionId).find { it.id == itemId } ?: return@inActiveSession
        val timingStarted = dao.sessionSets(itemId).any {
            it.measuredStartedAt != null || it.status == SetStatus.COMPLETED
        }
        dao.updateSessionExercise(item.copy(
            executionNotes = notes.trim(),
            restSeconds = restSeconds.coerceAtLeast(0),
            allowOvertime = if (timingStarted) item.allowOvertime else allowOvertime && Metric.TIME in item.tracking.metrics,
        ))
    }

    suspend fun skipExercise(sessionId: String, itemId: String) = inActiveSession(sessionId) {
        if (dao.sessionExercises(sessionId).none { it.id == itemId }) return@inActiveSession
        val at = now()
        dao.sessionSets(itemId).forEach { skipPendingSet(it, at) }
    }

    suspend fun addSet(sessionId: String, itemId: String) = inActiveSession(sessionId) {
        if (dao.sessionExercises(sessionId).none { it.id == itemId }) return@inActiveSession
        val last = dao.sessionSets(itemId).lastOrNull()
        val set = SessionSet(
            newId(), itemId, (last?.position ?: -1) + 1,
            actual = last?.actual?.copy(timeSeconds = last.timerTargetSeconds ?: last.actual.timeSeconds)
                ?: Measurements(),
            timerTargetSeconds = last?.timerTargetSeconds,
        )
        dao.insertSessionSets(listOf(set))
    }

    suspend fun updateMetric(sessionId: String, itemId: String, setId: String, metric: Metric, value: Number?) =
        withSet(sessionId, itemId, setId) { _, item, set ->
            if (set.status != SetStatus.PENDING || metric !in item.tracking.metrics) return@withSet
            if (metric == Metric.TIME && set.timerStartedAt != null) return@withSet
            val actual = metric.change(set.actual, value)
            val target = if (metric == Metric.TIME && set.measuredStartedAt == null) {
                actual.timeSeconds
            } else set.timerTargetSeconds
            dao.updateSessionSet(set.copy(actual = actual, timerTargetSeconds = target))
        }

    suspend fun completeSet(sessionId: String, itemId: String, setId: String) =
        withSet(sessionId, itemId, setId) { session, item, set ->
            if (set.status != SetStatus.PENDING) return@withSet
            val at = now()
            val stopped = stopTimer(set, at)
            check(item.tracking.accepts(stopped.actual)) { "Preencha os valores da série para concluir." }
            recordCompletedSet(session, item, stopped, at)
        }

    suspend fun skipSet(sessionId: String, itemId: String, setId: String) =
        withSet(sessionId, itemId, setId) { _, _, set ->
            skipPendingSet(set, now())
        }

    suspend fun restoreSkippedSet(sessionId: String, itemId: String, setId: String) =
        withSet(sessionId, itemId, setId) { _, _, set ->
            if (set.status == SetStatus.SKIPPED) dao.updateSessionSet(set.copy(status = SetStatus.PENDING))
        }

    suspend fun updateSetRest(sessionId: String, itemId: String, setId: String, restSeconds: Int?) =
        withSet(sessionId, itemId, setId) { _, _, set ->
            if (set.status == SetStatus.PENDING) {
                dao.updateSessionSet(set.copy(restOverrideSeconds = restSeconds?.coerceAtLeast(0)))
            }
        }

    suspend fun removeSet(sessionId: String, itemId: String, setId: String) =
        withSet(sessionId, itemId, setId) { _, _, set -> dao.removeSessionSet(set.id) }

    suspend fun toggleTimer(sessionId: String, itemId: String, setId: String) =
        withSet(sessionId, itemId, setId) { _, item, set ->
            if (set.status != SetStatus.PENDING || Metric.TIME !in item.tracking.metrics) return@withSet
            val at = now()
            if (set.timerStartedAt != null) {
                dao.updateSessionSet(stopTimer(set, at))
            } else {
                val target = set.timerGoalSeconds
                if (!item.allowOvertime && target != null && set.timerElapsedMillis >= target * 1000L) {
                    return@withSet
                }
                dao.runningTimers(sessionId).forEach { dao.updateSessionSet(stopTimer(it, at)) }
                dao.updateSessionSet(set.copy(
                    timerStartedAt = at, measuredStartedAt = set.measuredStartedAt ?: at,
                    timerTargetSeconds = target,
                ))
            }
        }

    suspend fun adjustRest(sessionId: String, adjustmentSeconds: Int) = inActiveSession(sessionId) { session ->
        val current = session.restEndsAt ?: return@inActiveSession
        val at = now()
        val deadline = current.coerceAtLeast(at) + adjustmentSeconds * 1000L
        dao.updateSession(session.copy(restEndsAt = deadline.takeIf { it > at }))
    }

    suspend fun skipRest(sessionId: String) = inActiveSession(sessionId) {
        dao.updateSession(it.copy(restEndsAt = null))
    }

    suspend fun openReview(sessionId: String): WorkoutReview? = database.withTransaction {
        val detail = dao.sessionById(sessionId) ?: return@withTransaction null
        when (detail.session.status) {
            SessionStatus.COMPLETED -> return@withTransaction WorkoutReview.open(detail, now())
            SessionStatus.CANCELLED -> return@withTransaction null
            SessionStatus.IN_PROGRESS -> settleExpiredTimer(detail.session)
        }
        check(dao.completedCount(sessionId) > 0) { "Conclua pelo menos uma série para salvar o treino." }
        val at = now().coerceAtLeast(detail.session.startedAt)
        dao.runningTimers(sessionId).forEach { dao.updateSessionSet(stopTimer(it, at)) }
        WorkoutReview.open(checkNotNull(dao.sessionById(sessionId)), at)
    }

    suspend fun saveReview(review: WorkoutReview) = database.withTransaction {
        val timing = checkNotNull(review.resolveTiming()) { "Confira a data, a hora e a duração do treino." }
        check(review.completedCount > 0 && !review.hasIncompleteValues) {
            "Registre os valores de pelo menos uma série concluída."
        }
        val originalSession = review.detail.session
        val current = checkNotNull(dao.sessionById(originalSession.id)) { "Sessão indisponível." }
        val session = current.session
        val originalSets = review.detail.exercises.flatMap { it.sets }.associateBy { it.id }
        val currentSets = current.exercises.flatMap { it.sets }.associateBy { it.id }
        check(session.status != SessionStatus.CANCELLED && originalSession == session && originalSets == currentSets) {
            "A sessão mudou. Volte e abra a revisão novamente."
        }
        val at = now()
        for (set in currentSets.values) {
            val result = review.result(set)
            if (result == ReviewedSet(set.status, set.actual)) continue
            dao.updateSessionSet(set.copy(
                actual = result.actual,
                status = result.status,
                completedAt = if (result.status == SetStatus.COMPLETED) set.completedAt ?: at else null,
            ))
        }
        dao.updateSession(session.copy(
            status = SessionStatus.COMPLETED,
            startedAt = timing.startedAt,
            endedAt = timing.endedAt,
            startedOffsetSeconds = timing.startedOffsetSeconds,
            endedOffsetSeconds = timing.endedOffsetSeconds,
            restEndsAt = null,
            notes = review.notes.trim(),
            rating = review.rating?.takeIf { it in 1..5 },
        ))
    }

    suspend fun discard(sessionId: String) = inActiveSession(sessionId) { session ->
        val at = now().coerceAtLeast(session.startedAt)
        dao.runningTimers(sessionId).forEach { dao.updateSessionSet(stopTimer(it, at)) }
        dao.updateSession(session.copy(
            status = SessionStatus.CANCELLED,
            endedAt = at, endedOffsetSeconds = offsetAt(at), restEndsAt = null,
        ))
    }

    private suspend fun snapshot(sessionId: String, exercise: Exercise, position: Int, rest: Int): SessionExercise {
        val equipment = checkNotNull(dao.equipmentById(exercise.equipmentId))
        return SessionExercise(
            newId(), sessionId, exercise.id, position, exercise.name, equipment.name, exercise.muscle,
            exercise.tracking, exercise.instructions, rest,
        )
    }

    private suspend fun inActiveSession(id: String, action: suspend (WorkoutSession) -> Unit) {
        database.withTransaction {
            val session = dao.findActiveSession()?.takeIf { it.id == id } ?: return@withTransaction
            action(settleExpiredTimer(session))
        }
    }

    private suspend fun settleExpiredTimer(session: WorkoutSession): WorkoutSession {
        val set = dao.runningTimers(session.id).firstOrNull() ?: return session
        val deadline = set.targetDeadline()?.takeIf { it <= now() } ?: return session
        val item = dao.sessionExercises(session.id).find { it.id == set.sessionExerciseId } ?: return session
        if (item.allowOvertime) return session

        val stopped = stopTimer(set, deadline)
        if (item.tracking.accepts(stopped.actual)) return recordCompletedSet(session, item, stopped, deadline)

        dao.updateSessionSet(stopped)
        return session
    }

    private suspend fun recordCompletedSet(
        session: WorkoutSession, item: SessionExercise, set: SessionSet, at: Long,
    ): WorkoutSession {
        dao.updateSessionSet(set.copy(status = SetStatus.COMPLETED, completedAt = at))
        val rest = set.restOverrideSeconds ?: item.restSeconds
        val updated = session.copy(restEndsAt = if (rest > 0) at + rest * 1000L else null)
        dao.updateSession(updated)
        return updated
    }

    private suspend fun withSet(
        sessionId: String,
        itemId: String,
        setId: String,
        action: suspend (WorkoutSession, SessionExercise, SessionSet) -> Unit,
    ) = inActiveSession(sessionId) { session ->
        val item = dao.sessionExercises(sessionId).find { it.id == itemId } ?: return@inActiveSession
        val set = dao.sessionSets(itemId).find { it.id == setId } ?: return@inActiveSession
        action(session, item, set)
    }

    private suspend fun skipPendingSet(set: SessionSet, at: Long) {
        if (set.status == SetStatus.PENDING) {
            dao.updateSessionSet(stopTimer(set, at).copy(status = SetStatus.SKIPPED))
        }
    }

    private fun stopTimer(set: SessionSet, at: Long): SessionSet {
        val started = set.timerStartedAt ?: return set
        val elapsed = set.elapsedAt(at)
        val seconds = (elapsed / 1000L).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        return set.copy(
            actual = set.actual.copy(timeSeconds = seconds),
            timerStartedAt = null, timerElapsedMillis = elapsed, measuredEndedAt = at.coerceAtLeast(started),
        )
    }

    private fun offsetAt(at: Long): Int =
        ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(at)).totalSeconds
}
