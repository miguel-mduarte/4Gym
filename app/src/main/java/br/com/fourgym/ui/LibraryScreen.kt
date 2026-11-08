package br.com.fourgym.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.fourgym.WorkoutViewModel
import br.com.fourgym.data.Equipment
import br.com.fourgym.data.Exercise
import br.com.fourgym.model.EquipmentCategory
import br.com.fourgym.model.MuscleGroup
import br.com.fourgym.model.MuscleRegion
import br.com.fourgym.model.TrackingType
import br.com.fourgym.model.newId
import java.text.Normalizer

@Composable
internal fun LibraryScreen(exercises: List<Exercise>, equipment: List<Equipment>, model: WorkoutViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscleFilter by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var equipmentFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var managingEquipment by rememberSaveable { mutableStateOf(false) }
    var equipmentEditor by remember { mutableStateOf<Equipment?>(null) }
    val equipmentNames = equipment.associate { it.id to it.name }
    val filtered = exercises.filter {
        it.name.matchesSearch(query) && (muscleFilter == null || muscleFilter == it.muscle) &&
            (equipmentFilter == null || equipmentFilter == it.equipmentId)
    }
    ScreenTitle("Biblioteca", "4GYM · EXERCÍCIOS") {
        TextButton(onClick = { editorId = newId() }) { Text("+ Novo") }
    }
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Buscar exercício") }, singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(
                    muscleFilter?.label ?: "Grupos musculares",
                    listOf(null) + MuscleGroup.entries, { it?.label ?: "Todos os grupos" },
                    { muscleFilter = it }, Modifier.weight(1f),
                )
                Choice(
                    equipmentNames[equipmentFilter] ?: "Equipamentos",
                    listOf(null) + equipment, { it?.name ?: "Todos os equipamentos" },
                    { equipmentFilter = it?.id }, Modifier.weight(1f),
                )
            }
            TextButton(onClick = { managingEquipment = true }) { Text("Gerenciar equipamentos") }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyCard(
                    if (exercises.isEmpty()) "Sua biblioteca começa aqui" else "Nenhum exercício encontrado",
                    if (exercises.isEmpty()) "Toque em Novo para cadastrar seu primeiro exercício."
                    else "Experimente outro termo ou ajuste os filtros.",
                )
            }
        }
        items(filtered, key = { it.id }) { item ->
            Card(onClick = { editorId = item.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge)
                    Text("${item.muscle.label} · ${equipmentNames[item.equipmentId].orEmpty()}")
                    Text(item.tracking.label, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    editorId?.let { id ->
        ExerciseEditor(
            id = id,
            existing = exercises.find { it.id == id },
            equipment = equipment,
            onClose = { editorId = null },
            onSave = { item ->
                model.submit {
                    model.repository.saveExercise(item)
                    editorId = null
                }
            },
            onSaveEquipment = { item -> model.submit { model.repository.saveEquipment(item) } },
        )
    }
    if (managingEquipment) {
        AlertDialog(
            onDismissRequest = { managingEquipment = false },
            title = { Text("Equipamentos") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(equipment, key = { it.id }) { item ->
                        TextButton(onClick = { equipmentEditor = item }) {
                            Text("${item.name} · ${item.category.label}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { equipmentEditor = newEquipment() }) { Text("+ Novo") }
            },
            dismissButton = { TextButton(onClick = { managingEquipment = false }) { Text("Fechar") } },
        )
    }
    equipmentEditor?.let { item ->
        EquipmentEditor(item, { equipmentEditor = null }) {
            model.submit {
                model.repository.saveEquipment(it)
                equipmentEditor = null
            }
        }
    }
}

@Composable
private fun ExerciseEditor(
    id: String,
    existing: Exercise?,
    equipment: List<Equipment>,
    onClose: () -> Unit,
    onSave: (Exercise) -> Unit,
    onSaveEquipment: (Equipment) -> Unit,
) {
    var name by rememberSaveable(id) { mutableStateOf(existing?.name.orEmpty()) }
    var equipmentId by rememberSaveable(id) { mutableStateOf(existing?.equipmentId) }
    var region by rememberSaveable(id) { mutableStateOf(existing?.muscle?.region) }
    var muscle by rememberSaveable(id) { mutableStateOf(existing?.muscle) }
    var tracking by rememberSaveable(id) { mutableStateOf(existing?.tracking ?: TrackingType.WEIGHT_REPS) }
    var instructions by rememberSaveable(id) { mutableStateOf(existing?.instructions.orEmpty()) }
    var addingEquipment by remember { mutableStateOf<Equipment?>(null) }
    val selectedEquipment = equipment.find { it.id == equipmentId }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (existing == null) "Novo exercício" else "Editar exercício") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true)
                Choice(selectedEquipment?.name ?: "Selecionar equipamento", equipment, { it.name }, { equipmentId = it.id })
                TextButton(onClick = { addingEquipment = newEquipment() }) { Text("+ Cadastrar equipamento") }
                Choice(region?.label ?: "Região do corpo", MuscleRegion.entries, { it.label }, {
                    if (region != it) muscle = null
                    region = it
                })
                region?.let { current ->
                    Choice(muscle?.label ?: "Grupo muscular", MuscleGroup.entries.filter { it.region == current },
                        { it.label }, { muscle = it })
                }
                Choice(tracking.label, TrackingType.entries, { it.label }, { tracking = it })
                OutlinedTextField(
                    instructions, { instructions = it }, Modifier.fillMaxWidth(),
                    label = { Text("Instruções técnicas") }, minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selectedEquipment != null && muscle != null,
                onClick = {
                    onSave(Exercise(id, name, checkNotNull(equipmentId), checkNotNull(muscle), tracking, instructions, 0))
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    )
    addingEquipment?.let { item ->
        EquipmentEditor(item, { addingEquipment = null }) {
            onSaveEquipment(it)
            equipmentId = it.id
            addingEquipment = null
        }
    }
}

private fun newEquipment() = Equipment(newId(), "", EquipmentCategory.OTHER, 0)

@Composable
private fun EquipmentEditor(item: Equipment, onClose: () -> Unit, onSave: (Equipment) -> Unit) {
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var category by rememberSaveable(item.id) { mutableStateOf(item.category) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (item.name.isEmpty()) "Novo equipamento" else "Editar equipamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                Choice(category.label, EquipmentCategory.entries, { it.label }, { category = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(item.copy(name = name, category = category)) }) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    )
}

@Composable
internal fun ExercisePicker(
    exercises: List<Exercise>,
    equipment: List<Equipment>,
    model: WorkoutViewModel,
    multiple: Boolean,
    onClose: () -> Unit,
    onPick: (List<Exercise>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var creatingId by rememberSaveable { mutableStateOf<String?>(null) }
    val matches = exercises.filter { it.name.matchesSearch(query) }
    val selectedExercises = selectedIds.mapNotNull { id -> exercises.find { it.id == id } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (multiple) "Adicionar exercícios" else "Selecionar exercício") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar exercício") }, singleLine = true)
                TextButton(onClick = { creatingId = newId() }) { Text("+ Criar exercício") }
                if (matches.isEmpty()) Text(
                    if (exercises.isNotEmpty()) "Nenhum resultado."
                    else "Crie um exercício para adicioná-lo ao treino.",
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(matches, key = { it.id }) { item ->
                        val selected = item.id in selectedIds
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (!multiple) onPick(listOf(item))
                                else selectedIds = if (selected) selectedIds - item.id else selectedIds + item.id
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (multiple) Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 10.dp))
                            Column {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(item.muscle.label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multiple) {
                TextButton(
                    enabled = selectedExercises.isNotEmpty(),
                    onClick = { onPick(selectedExercises) },
                ) { Text("Adicionar (${selectedExercises.size})") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    )
    creatingId?.let { id ->
        ExerciseEditor(
            id, existing = null, equipment = equipment,
            onClose = { creatingId = null },
            onSave = { exercise ->
                model.submit {
                    model.repository.saveExercise(exercise)
                    if (creatingId == id) {
                        creatingId = null
                        if (multiple) {
                            selectedIds = selectedIds + id
                            query = ""
                        } else {
                            onPick(listOf(exercise))
                        }
                    }
                }
            },
            onSaveEquipment = { item -> model.submit { model.repository.saveEquipment(item) } },
        )
    }
}

private val accents = Regex("\\p{M}+")
private fun String.matchesSearch(query: String): Boolean {
    fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(accents, "").lowercase()
    return normalize(this).contains(normalize(query.trim()))
}
