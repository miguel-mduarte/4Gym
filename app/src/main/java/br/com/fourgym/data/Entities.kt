package br.com.fourgym.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import br.com.fourgym.model.EquipmentCategory
import br.com.fourgym.model.Measurements
import br.com.fourgym.model.MuscleGroup
import br.com.fourgym.model.TrackingType

@Entity
data class Equipment(
    @PrimaryKey val id: String,
    val name: String,
    val category: EquipmentCategory,
    val updatedAt: Long,
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Equipment::class, parentColumns = ["id"], childColumns = ["equipmentId"]
    )],
    indices = [Index("equipmentId")],
)
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val equipmentId: String,
    val muscle: MuscleGroup,
    val tracking: TrackingType,
    val instructions: String,
    val updatedAt: Long,
)

@Entity
data class Routine(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "''") val description: String = "",
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExercise(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val restSeconds: Int,
    @ColumnInfo(defaultValue = "''") val executionNotes: String = "",
    @ColumnInfo(defaultValue = "0") val allowOvertime: Boolean = false,
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = RoutineExercise::class, parentColumns = ["id"], childColumns = ["routineExerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("routineExerciseId")],
)
data class RoutineSet(
    @PrimaryKey val id: String,
    val routineExerciseId: String,
    val position: Int,
    val restOverrideSeconds: Int? = null,
    @Embedded val target: Measurements = Measurements(),
)

enum class SessionStatus { IN_PROGRESS, COMPLETED, CANCELLED }
enum class WorkoutMode(val label: String) { LIST("Lista"), GUIDED("Guiado") }
enum class SetStatus(val label: String) {
    PENDING("Não registrada"), COMPLETED("Concluída"), SKIPPED("Pulada")
}

@Entity(indices = [Index("status"), Index("routineId", "endedAt")])
data class WorkoutSession(
    @PrimaryKey val id: String,
    val routineId: String?,
    val name: String,
    val startedAt: Long,
    val startedOffsetSeconds: Int,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val endedAt: Long? = null,
    val endedOffsetSeconds: Int? = null,
    val restEndsAt: Long? = null,
    @ColumnInfo(defaultValue = "'LIST'") val mode: WorkoutMode = WorkoutMode.LIST,
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    val rating: Int? = null,
    val planId: String? = null,
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = WorkoutSession::class, parentColumns = ["id"], childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class SessionExercise(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val name: String,
    val equipmentName: String,
    val muscle: MuscleGroup,
    val tracking: TrackingType,
    val instructions: String,
    val restSeconds: Int,
    @ColumnInfo(defaultValue = "''") val executionNotes: String = "",
    @ColumnInfo(defaultValue = "0") val allowOvertime: Boolean = false,
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = SessionExercise::class, parentColumns = ["id"], childColumns = ["sessionExerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionExerciseId"), Index("plannedSetId")],
)
data class SessionSet(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val position: Int,
    val plannedSetId: String? = null,
    val restOverrideSeconds: Int? = null,
    @Embedded(prefix = "actual_") val actual: Measurements = Measurements(),
    @Embedded(prefix = "previous_") val previous: Measurements? = null,
    val previousSessionEndedAt: Long? = null,
    val status: SetStatus = SetStatus.PENDING,
    val completedAt: Long? = null,
    val timerStartedAt: Long? = null,
    val timerElapsedMillis: Long = 0,
    val measuredStartedAt: Long? = null,
    val measuredEndedAt: Long? = null,
    val timerTargetSeconds: Int? = null,
) {
    val timerGoalSeconds: Int?
        get() = timerTargetSeconds ?: actual.timeSeconds.takeIf { measuredStartedAt == null }

    fun elapsedAt(at: Long): Long = timerElapsedMillis +
        (timerStartedAt?.let { (at - it).coerceAtLeast(0) } ?: 0)

    fun targetDeadline(): Long? {
        val started = timerStartedAt ?: return null
        val target = timerTargetSeconds ?: return null
        return started + (target * 1000L - timerElapsedMillis).coerceAtLeast(0)
    }

    fun extraSeconds(): Long {
        val target = timerTargetSeconds ?: return 0
        return ((actual.timeSeconds ?: 0).toLong() - target).coerceAtLeast(0)
    }
}

data class RoutineExerciseDetail(
    @Embedded val exercise: RoutineExercise,
    @Relation(parentColumn = "id", entityColumn = "routineExerciseId")
    val sets: List<RoutineSet>,
)

data class RoutineDetail(
    @Embedded val routine: Routine,
    @Relation(parentColumn = "id", entityColumn = "routineId", entity = RoutineExercise::class)
    val exercises: List<RoutineExerciseDetail>,
)

data class SessionExerciseDetail(
    @Embedded val exercise: SessionExercise,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SessionSet>,
)

fun List<SessionExerciseDetail>.guidedExerciseIndex(): Int {
    val running = indexOfFirst { item ->
        item.sets.any { it.status == SetStatus.PENDING && it.timerStartedAt != null }
    }
    return if (running >= 0) running else indexOfFirst { item ->
        item.sets.any { it.status == SetStatus.PENDING }
    }
}

data class SessionDetail(
    @Embedded val session: WorkoutSession,
    @Relation(parentColumn = "id", entityColumn = "sessionId", entity = SessionExercise::class)
    val exercises: List<SessionExerciseDetail>,
) {
    val completedCount: Int get() = exercises.sumOf { item ->
        item.sets.count { it.status == SetStatus.COMPLETED }
    }

    val skippedCount: Int get() = exercises.sumOf { item ->
        item.sets.count { it.status == SetStatus.SKIPPED }
    }

    val volumeKg: Double get() = exercises
        .filter { it.exercise.tracking == TrackingType.WEIGHT_REPS }
        .sumOf { item ->
            item.sets.filter { it.status == SetStatus.COMPLETED }
                .sumOf { (it.actual.weightKg ?: 0.0) * (it.actual.reps ?: 0) }
        }
}

data class PreviousSet(
    val plannedSetId: String,
    val exerciseId: String,
    val tracking: TrackingType,
    val endedAt: Long,
    @Embedded(prefix = "actual_") val actual: Measurements,
)
