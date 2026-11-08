package br.com.fourgym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.fourgym.data.SessionExerciseDetail
import br.com.fourgym.data.SessionSet
import br.com.fourgym.data.SetStatus
import br.com.fourgym.model.Metric

@Composable
internal fun WorkoutNotesDialog(initialNotes: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Anotação do treino") },
        text = { NotesField(notes) { notes = it } },
        confirmButton = { TextButton(onClick = { onSave(notes) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Voltar") } },
    )
}

@Composable
internal fun NotesField(notes: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        notes, onChange, Modifier.fillMaxWidth(),
        label = { Text("Anotação (opcional)") }, minLines = 2, maxLines = 5,
    )
}

@Composable
internal fun ExerciseSettingsDialog(
    detail: SessionExerciseDetail,
    onClose: () -> Unit,
    onSave: (String, Int, Boolean) -> Unit,
) {
    val item = detail.exercise
    var notes by rememberSaveable(item.id) { mutableStateOf(item.executionNotes) }
    var rest by rememberSaveable(item.id) { mutableStateOf(item.restSeconds) }
    var overtime by rememberSaveable(item.id) { mutableStateOf(item.allowOvertime) }
    val canChangeOvertime = detail.sets.none { it.measuredStartedAt != null || it.status == SetStatus.COMPLETED }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Configurar exercício") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    notes, { notes = it }, Modifier.fillMaxWidth(),
                    label = { Text("Como fazer neste treino") }, minLines = 2, maxLines = 5,
                )
                RestInput("Descanso padrão (s)", rest) { rest = it ?: 0 }
                if (Metric.TIME in item.tracking.metrics) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Permitir tempo extra", Modifier.weight(1f))
                        Switch(
                            checked = overtime,
                            onCheckedChange = { overtime = it },
                            enabled = canChangeOvertime,
                            modifier = Modifier.semantics { contentDescription = "Permitir tempo extra" },
                        )
                    }
                    if (!canChangeOvertime) Text("O tempo extra pode ser definido antes de registrar a primeira série.")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(notes, rest, overtime) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Voltar") } },
    )
}

@Composable
internal fun SetRestDialog(set: SessionSet, onClose: () -> Unit, onSave: (Int?) -> Unit) {
    var rest by rememberSaveable(set.id) { mutableStateOf(set.restOverrideSeconds) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Descanso da série") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RestInput("Descanso da série (s)", rest) { rest = it }
                Text("Deixe em branco para usar o descanso padrão do exercício.")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(rest) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Voltar") } },
    )
}
