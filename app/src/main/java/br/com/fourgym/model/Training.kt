package br.com.fourgym.model

import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

enum class TrackingType(val label: String, val metrics: List<Metric>) {
    WEIGHT_REPS("Carga e repetições", listOf(Metric.WEIGHT, Metric.REPS)),
    REPS_ONLY("Repetições", listOf(Metric.REPS)),
    TIME("Tempo", listOf(Metric.TIME)),
    DISTANCE_TIME("Distância e tempo", listOf(Metric.DISTANCE, Metric.TIME)),
    TIME_WEIGHT("Carga e tempo", listOf(Metric.WEIGHT, Metric.TIME)),
    BPM_ONLY("Só BPM", listOf(Metric.BPM)),
    BPM_TIME("BPM e tempo", listOf(Metric.BPM, Metric.TIME)),
    BPM_DISTANCE("BPM e distância", listOf(Metric.BPM, Metric.DISTANCE));

    fun accepts(values: Measurements): Boolean = metrics.all { it.read(values) != null }

    fun describe(values: Measurements): String = metrics.mapNotNull { metric ->
        metric.read(values)?.let { "${metric.format(it)}${metric.unit}" }
    }.joinToString(" × ").ifEmpty { "—" }
}

data class Measurements(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val timeSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val bpm: Int? = null,
)

enum class Metric(val label: String, val unit: String, val isInteger: Boolean) {
    WEIGHT("Carga (kg)", " kg", false),
    REPS("Repetições", " reps", true),
    TIME("Tempo (mm:ss)", "", false),
    DISTANCE("Distância (m)", " m", false),
    BPM("BPM", " bpm", true);

    fun read(values: Measurements): Number? = when (this) {
        WEIGHT -> values.weightKg
        REPS -> values.reps
        TIME -> values.timeSeconds
        DISTANCE -> values.distanceMeters
        BPM -> values.bpm
    }

    fun change(values: Measurements, number: Number?): Measurements = when (this) {
        WEIGHT -> values.copy(weightKg = number?.toDouble())
        REPS -> values.copy(reps = number?.toInt())
        TIME -> values.copy(timeSeconds = number?.toInt())
        DISTANCE -> values.copy(distanceMeters = number?.toDouble())
        BPM -> values.copy(bpm = number?.toInt())
    }

    fun parse(raw: String): Number? = when (this) {
        WEIGHT -> raw.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        DISTANCE -> raw.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        REPS, BPM -> raw.toIntOrNull()?.takeIf { it > 0 }
        TIME -> parseDuration(raw)
    }

    fun format(number: Number): String = when (this) {
        TIME -> formatDuration(number.toLong())
        REPS, BPM -> number.toString()
        WEIGHT, DISTANCE -> number.toString().removeSuffix(".0").replace('.', ',')
    }
}

fun parseDuration(raw: String): Int? {
    val fields = raw.trim().split(':')
    val seconds = when (fields.size) {
        1 -> fields[0].toIntOrNull()
        2 -> {
            val minutes = fields[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val remainder = fields[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            (minutes * 60L + remainder).takeIf { it <= Int.MAX_VALUE }?.toInt()
        }
        else -> null
    }
    return seconds?.takeIf { it > 0 }
}

fun formatDuration(seconds: Long): String =
    String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)

enum class EquipmentCategory(val label: String) {
    FREE_WEIGHTS("Pesos livres"), MACHINES_CABLES("Máquinas e cabos"),
    BODYWEIGHT("Peso corporal"), CARDIO("Cardio"), OTHER("Outros")
}

enum class MuscleRegion(val label: String) {
    UPPER("Parte superior"), ARMS("Braços"), CORE("Core"),
    LOWER("Parte inferior"), GENERAL("Corpo inteiro / Geral")
}

enum class MuscleGroup(val label: String, val region: MuscleRegion) {
    CHEST("Peito", MuscleRegion.UPPER),
    UPPER_BACK("Costas superiores", MuscleRegion.UPPER),
    LATS("Dorsais", MuscleRegion.UPPER),
    TRAPS("Trapézio", MuscleRegion.UPPER),
    SHOULDERS("Ombros", MuscleRegion.UPPER),
    REAR_DELTS("Deltoides posteriores", MuscleRegion.UPPER),
    ROTATOR_CUFF("Manguito rotador", MuscleRegion.UPPER),
    NECK("Pescoço", MuscleRegion.UPPER),
    BICEPS("Bíceps", MuscleRegion.ARMS),
    TRICEPS("Tríceps", MuscleRegion.ARMS),
    FOREARMS("Antebraços", MuscleRegion.ARMS),
    ABS("Abdômen", MuscleRegion.CORE),
    OBLIQUES("Oblíquos", MuscleRegion.CORE),
    LOWER_BACK("Lombar", MuscleRegion.CORE),
    QUADS("Quadríceps", MuscleRegion.LOWER),
    HAMSTRINGS("Isquiotibiais", MuscleRegion.LOWER),
    GLUTES("Glúteos", MuscleRegion.LOWER),
    CALVES("Panturrilhas", MuscleRegion.LOWER),
    ADDUCTORS("Adutores do quadril", MuscleRegion.LOWER),
    ABDUCTORS("Abdutores do quadril", MuscleRegion.LOWER),
    HIP_FLEXORS("Flexores do quadril", MuscleRegion.LOWER),
    SHINS("Canelas", MuscleRegion.LOWER),
    FULL_BODY("Corpo inteiro", MuscleRegion.GENERAL),
    CARDIO("Cardio", MuscleRegion.GENERAL),
    MOBILITY("Mobilidade", MuscleRegion.GENERAL),
    OTHER("Outros", MuscleRegion.GENERAL)
}
