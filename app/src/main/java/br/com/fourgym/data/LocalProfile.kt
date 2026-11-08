package br.com.fourgym.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

enum class MeasureKind(val label: String, val unit: String) {
    WEIGHT("Peso", "kg"),
    BODY_FAT("Gordura corporal", "%"),
    LEAN_MASS("Massa magra", "kg"),
    WAIST("Cintura", "cm"),
    ARM("Braço", "cm"),
    THIGH("Coxa", "cm"),
    CUSTOM("Outra medida", ""),
    NOTE("Anotação", ""),
}

@Entity
data class BodyRecord(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val kind: MeasureKind,
    val value: Double?,
    val customName: String = "",
    val customUnit: String = "",
    val notes: String = "",
) {
    val name get() = if (kind == MeasureKind.CUSTOM) customName else kind.label
    val unit get() = if (kind == MeasureKind.CUSTOM) customUnit else kind.unit

    fun isValid(): Boolean {
        if (kind == MeasureKind.NOTE) return notes.isNotBlank() && value == null
        val number = value ?: return false
        return number.isFinite() && number > 0 &&
            (kind != MeasureKind.BODY_FAT || number <= 100) &&
            (kind != MeasureKind.CUSTOM || customName.isNotBlank() && customUnit.isNotBlank())
    }
}

@Entity
data class ReminderSettings(
    @PrimaryKey val id: Int = 0,
    val enabled: Boolean = false,
    val hour: Int = 18,
    val minute: Int = 0,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM BodyRecord ORDER BY epochDay DESC, id")
    fun records(): Flow<List<BodyRecord>>

    @Upsert
    suspend fun save(record: BodyRecord)

    @Query("DELETE FROM BodyRecord WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM ReminderSettings WHERE id = 0")
    fun reminders(): Flow<ReminderSettings?>

    @Upsert
    suspend fun save(settings: ReminderSettings)
}
