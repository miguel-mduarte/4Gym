package br.com.fourgym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.fourgym.WorkoutViewModel
import br.com.fourgym.data.Equipment
import br.com.fourgym.data.Exercise
import br.com.fourgym.data.RoutineDetail
import br.com.fourgym.data.RoutineExercise
import br.com.fourgym.data.RoutineExerciseDetail
import br.com.fourgym.data.RoutineSet
import br.com.fourgym.model.Metric
import br.com.fourgym.model.newId

@Composable
internal fun RoutineEditor(
    draft: RoutineDetail,
    exercises: List<Exercise>,
    equipment: List<Equipment>,
    model: WorkoutViewModel,
    onChange: (RoutineDetail) -> Unit,
    onClose: () -> Unit,
    saveLabel: String,
    onSave: (RoutineDetail) -> Unit,
) {
    var picking by rememberSaveable(draft.routine.id) { mutableStateOf(false) }
    val items = draft.exercises
    val byId = exercises.associateBy { it.id }
    val valid = draft.routine.name.isNotBlank() && items.isNotEmpty() && items.all { it.sets.isNotEmpty() }
    ScreenTitle(if (draft.routine.updatedAt == 0L) "Novo treino" else "Editar treino", "4GYM · PLANEJAMENTO") {
        TextButton(onClick = onClose) { Text("Voltar") }
        TextButton(onClick = { onSave(draft) }, enabled = valid) { Text(saveLabel) }
    }
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                draft.routine.name,
                { onChange(draft.copy(routine = draft.routine.copy(name = it))) },
                Modifier.fillMaxWidth(), label = { Text("Nome do treino") }, singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                draft.routine.description,
                { onChange(draft.copy(routine = draft.routine.copy(description = it))) },
                Modifier.fillMaxWidth(), label = { Text("Descrição do treino") }, minLines = 2,
            )
        }
        itemsIndexed(items, key = { _, item -> item.exercise.id }) { index, item ->
            val exercise = byId[item.exercise.exerciseId] ?: return@itemsIndexed
            val update = { changed: RoutineExerciseDetail ->
                onChange(draft.copy(exercises = items.toMutableList().apply { set(index, changed) }))
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                            Text("${exercise.muscle.label} · ${exercise.tracking.label}")
                        }
                        ItemMenu(
                            canMoveUp = index > 0,
                            canMoveDown = index < items.lastIndex,
                            onMove = { direction ->
                                onChange(draft.copy(exercises = items.toMutableList().apply {
                                    add(index + direction, removeAt(index))
                                }))
                            },
                            onRemove = { onChange(draft.copy(exercises = items.filter { it.exercise.id != item.exercise.id })) },
                        )
                    }
                    Instructions(exercise.instructions)
                    OutlinedTextField(
                        item.exercise.executionNotes,
                        { update(item.copy(exercise = item.exercise.copy(executionNotes = it))) },
                        Modifier.fillMaxWidth(),
                        label = { Text("Como fazer neste treino") },
                        placeholder = { Text("Cadência, isometria, ajuste da máquina…") },
                        minLines = 2,
                    )
                    if (Metric.TIME in exercise.tracking.metrics) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Permitir tempo extra", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (item.exercise.allowOvertime) "A meta aparece na tela e o tempo continua."
                                    else "A série encerra automaticamente ao atingir a meta.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = item.exercise.allowOvertime,
                                onCheckedChange = { update(item.copy(exercise = item.exercise.copy(allowOvertime = it))) },
                            )
                        }
                    }
                    RestInput("Descanso padrão (s)", item.exercise.restSeconds) {
                        update(item.copy(exercise = item.exercise.copy(restSeconds = it ?: 0)))
                    }
                    item.sets.forEachIndexed { setIndex, set ->
                        key(set.id) {
                            val updateSet = { changed: RoutineSet ->
                                update(item.copy(sets = item.sets.toMutableList().apply { this[setIndex] = changed }))
                            }
                            HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Série ${setIndex + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton(
                                    onClick = { update(item.copy(sets = item.sets.filter { it.id != set.id })) },
                                    enabled = item.sets.size > 1,
                                ) { Text("Remover") }
                            }
                            MetricsFields(exercise.tracking, set.target, timeLabel = "Meta (mm:ss)") { metric, value ->
                                updateSet(set.copy(target = metric.change(set.target, value)))
                            }
                            RestInput("Descanso da série (s) · opcional", set.restOverrideSeconds) {
                                updateSet(set.copy(restOverrideSeconds = it))
                            }
                        }
                    }
                    TextButton(onClick = {
                        val last = item.sets.last()
                        val set = last.copy(id = newId(), position = item.sets.size)
                        update(item.copy(sets = item.sets + set))
                    }) { Text("+ Adicionar série") }
                }
            }
        }
        item {
            Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Adicionar exercícios") }
        }
    }
    if (picking) {
        ExercisePicker(
            exercises, equipment, model, multiple = true, onClose = { picking = false },
        ) { selected ->
            val additions = selected.mapIndexed { index, exercise ->
                val id = newId()
                RoutineExerciseDetail(
                    RoutineExercise(id, draft.routine.id, exercise.id, items.size + index, 90),
                    listOf(RoutineSet(newId(), id, 0)),
                )
            }
            onChange(draft.copy(exercises = items + additions))
            picking = false
        }
    }
}

@Composable
internal fun RestInput(label: String, value: Int?, onChange: (Int?) -> Unit) {
    var raw by rememberSaveable { mutableStateOf(value?.toString().orEmpty()) }
    val number = raw.toIntOrNull()?.takeIf { it >= 0 }
    OutlinedTextField(
        value = raw,
        onValueChange = {
            raw = it.filter(Char::isDigit).take(6)
            onChange(raw.toIntOrNull())
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = raw.isNotEmpty() && number == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
internal fun ItemMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onReplace: (() -> Unit)? = null,
    onConfigure: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) { Text("Opções") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (onConfigure != null) {
            DropdownMenuItem(text = { Text("Configurar exercício") }, onClick = {
                expanded = false
                onConfigure()
            })
        }
        if (onReplace != null) {
            DropdownMenuItem(text = { Text("Substituir") }, onClick = { expanded = false; onReplace() })
        }
        DropdownMenuItem(
            text = { Text("Mover para cima") }, enabled = canMoveUp,
            onClick = { expanded = false; onMove(-1) },
        )
        DropdownMenuItem(
            text = { Text("Mover para baixo") }, enabled = canMoveDown,
            onClick = { expanded = false; onMove(1) },
        )
        DropdownMenuItem(text = { Text("Remover") }, onClick = { expanded = false; onRemove() })
    }
}
