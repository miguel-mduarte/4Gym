package br.com.fourgym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.fourgym.data.Equipment
import br.com.fourgym.data.Exercise
import br.com.fourgym.data.RoutineDetail
import br.com.fourgym.model.Metric

@Composable
internal fun RoutinePreview(
    detail: RoutineDetail,
    exercises: List<Exercise>,
    equipment: List<Equipment>,
    canStart: Boolean,
    onStart: () -> Unit,
    onClose: () -> Unit,
) {
    val routine = detail.routine
    val exerciseById = exercises.associateBy { it.id }
    val equipmentById = equipment.associateBy { it.id }
    ScreenTitle(routine.name, "4GYM · VISUALIZAÇÃO") {
        TextButton(onClick = onClose) { Text("Voltar") }
    }
    Column(Modifier.fillMaxWidth()) {
        LazyColumn(Modifier.weight(1f), contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (routine.description.isNotBlank()) {
                item { Text(routine.description, style = MaterialTheme.typography.bodyLarge) }
            }
            if (!canStart) item { Text("Já existe um treino ativo.") }
            items(detail.exercises.sortedBy { it.exercise.position }, key = { it.exercise.id }) { entry ->
                val planned = entry.exercise
                val exercise = exerciseById[planned.exerciseId] ?: return@items
                val equipmentName = equipmentById[exercise.equipmentId]?.name.orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                        Text("${exercise.muscle.label} · $equipmentName · ${exercise.tracking.label}")
                        Instructions(exercise.instructions)
                        Instructions(planned.executionNotes, label = "NESTE TREINO")
                        Text("Descanso padrão · ${planned.restSeconds} s")
                        if (planned.allowOvertime && Metric.TIME in exercise.tracking.metrics) {
                            Text("Tempo extra permitido")
                        }
                        entry.sets.sortedBy { it.position }.forEachIndexed { index, set ->
                            val rest = set.restOverrideSeconds?.let { " · descanso de $it s" }.orEmpty()
                            Text("Série ${index + 1} · ${exercise.tracking.describe(set.target)}$rest")
                        }
                    }
                }
            }
        }
        WorkoutFooter("Iniciar treino", enabled = canStart, onClick = onStart)
    }
}

@Composable
internal fun WorkoutFooter(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 48.dp),
        ) {
            Text(label)
        }
    }
}
