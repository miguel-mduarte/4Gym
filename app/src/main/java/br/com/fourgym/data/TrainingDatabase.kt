package br.com.fourgym.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        Equipment::class, Exercise::class, Routine::class, RoutineExercise::class, RoutineSet::class,
        WorkoutSession::class, SessionExercise::class, SessionSet::class,
        WorkoutPlan::class, PlanWorkout::class, PlanDay::class,
        BodyRecord::class, ReminderSettings::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun training(): TrainingDao
    abstract fun plans(): PlanDao
    abstract fun profile(): ProfileDao
}

@Dao
interface PlanDao {
    @Transaction
    @Query("SELECT * FROM WorkoutPlan ORDER BY active DESC, name COLLATE NOCASE, id")
    fun observePlans(): Flow<List<PlanDetail>>

    @Transaction
    @Query("SELECT * FROM WorkoutPlan WHERE id = :id")
    suspend fun byId(id: String): PlanDetail?

    @Query("SELECT EXISTS(SELECT 1 FROM WorkoutPlan WHERE active = 1)")
    suspend fun hasActivePlan(): Boolean

    @Upsert
    suspend fun save(plan: WorkoutPlan)

    @Query("UPDATE WorkoutPlan SET active = 0 WHERE active = 1")
    suspend fun clearActive()

    @Query("UPDATE WorkoutPlan SET active = 1 WHERE id = :id")
    suspend fun activate(id: String)

    @Query("DELETE FROM WorkoutPlan WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM PlanWorkout WHERE planId = :planId")
    suspend fun clearWorkouts(planId: String)

    @Insert
    suspend fun insertWorkouts(items: List<PlanWorkout>)

    @Insert
    suspend fun insertDays(items: List<PlanDay>)

    @Query("SELECT Routine.id FROM Routine JOIN PlanWorkout ON PlanWorkout.routineId = Routine.id WHERE PlanWorkout.planId = :planId AND Routine.deletedAt IS NULL")
    suspend fun availableWorkoutIds(planId: String): List<String>

    @Query("SELECT * FROM WorkoutSession WHERE planId = :planId AND status = 'COMPLETED'")
    suspend fun completedSessions(planId: String): List<WorkoutSession>
}

@Dao
interface TrainingDao {
    @Query("SELECT * FROM Equipment ORDER BY name COLLATE NOCASE")
    fun equipment(): Flow<List<Equipment>>

    @Query("SELECT * FROM Equipment WHERE id = :id")
    suspend fun equipmentById(id: String): Equipment?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedEquipment(items: List<Equipment>)

    @Upsert
    suspend fun saveEquipment(equipment: Equipment)

    @Query("SELECT * FROM Exercise ORDER BY name COLLATE NOCASE")
    fun exercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM Exercise WHERE id = :id")
    suspend fun exerciseById(id: String): Exercise?

    @Upsert
    suspend fun saveExercise(exercise: Exercise)

    @Transaction
    @Query("SELECT * FROM Routine WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun routines(): Flow<List<RoutineDetail>>

    @Transaction
    @Query("SELECT * FROM Routine WHERE id = :id AND deletedAt IS NULL")
    suspend fun routineById(id: String): RoutineDetail?

    @Upsert
    suspend fun saveRoutine(routine: Routine)

    @Query("DELETE FROM RoutineExercise WHERE routineId = :routineId")
    suspend fun deleteRoutineExercises(routineId: String)

    @Insert
    suspend fun insertRoutineExercises(items: List<RoutineExercise>)

    @Insert
    suspend fun insertRoutineSets(items: List<RoutineSet>)

    @Query("UPDATE Routine SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun archiveRoutine(id: String, at: Long)

    @Transaction
    @Query("SELECT * FROM WorkoutSession WHERE status = 'IN_PROGRESS' LIMIT 1")
    fun activeSession(): Flow<SessionDetail?>

    @Query("SELECT * FROM WorkoutSession WHERE status = 'IN_PROGRESS' LIMIT 1")
    suspend fun findActiveSession(): WorkoutSession?

    @Transaction
    @Query("SELECT * FROM WorkoutSession WHERE id = :id")
    suspend fun sessionById(id: String): SessionDetail?

    @Transaction
    @Query("SELECT * FROM WorkoutSession WHERE status = 'COMPLETED' ORDER BY endedAt DESC, id")
    fun history(): Flow<List<SessionDetail>>

    @Query("""
        SELECT s.plannedSetId, e.exerciseId, e.tracking, w.endedAt,
            s.actual_weightKg, s.actual_reps, s.actual_timeSeconds, s.actual_distanceMeters, s.actual_bpm
        FROM SessionSet s
        JOIN SessionExercise e ON e.id = s.sessionExerciseId
        JOIN WorkoutSession w ON w.id = e.sessionId
        WHERE w.routineId = :routineId AND w.status = 'COMPLETED' AND w.endedAt IS NOT NULL
            AND s.status = 'COMPLETED' AND s.plannedSetId IS NOT NULL
        ORDER BY w.endedAt DESC, w.id
    """)
    suspend fun previousSets(routineId: String): List<PreviousSet>

    @Insert
    suspend fun insertSession(session: WorkoutSession)

    @Update
    suspend fun updateSession(session: WorkoutSession)

    @Query("SELECT * FROM SessionExercise WHERE sessionId = :sessionId ORDER BY position, id")
    suspend fun sessionExercises(sessionId: String): List<SessionExercise>

    @Insert
    suspend fun insertSessionExercise(exercise: SessionExercise)

    @Update
    suspend fun updateSessionExercise(exercise: SessionExercise)

    @Update
    suspend fun updateSessionExercises(items: List<SessionExercise>)

    @Query("DELETE FROM SessionExercise WHERE id = :id")
    suspend fun removeSessionExercise(id: String)

    @Query("SELECT * FROM SessionSet WHERE sessionExerciseId = :exerciseId ORDER BY position, id")
    suspend fun sessionSets(exerciseId: String): List<SessionSet>

    @Query("""
        SELECT s.* FROM SessionSet s JOIN SessionExercise e ON e.id = s.sessionExerciseId
        WHERE e.sessionId = :sessionId AND s.timerStartedAt IS NOT NULL
    """)
    suspend fun runningTimers(sessionId: String): List<SessionSet>

    @Query("""
        SELECT COUNT(*) FROM SessionSet s JOIN SessionExercise e ON e.id = s.sessionExerciseId
        WHERE e.sessionId = :sessionId AND s.status = 'COMPLETED'
    """)
    suspend fun completedCount(sessionId: String): Int

    @Insert
    suspend fun insertSessionSets(sets: List<SessionSet>)

    @Update
    suspend fun updateSessionSet(set: SessionSet)

    @Query("DELETE FROM SessionSet WHERE id = :id")
    suspend fun removeSessionSet(id: String)
}
