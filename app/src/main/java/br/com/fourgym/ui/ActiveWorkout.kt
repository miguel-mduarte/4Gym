package br.com.fourgym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.com.fourgym.WorkoutViewModel
import br.com.fourgym.data.Equipment
import br.com.fourgym.data.Exercise
import br.com.fourgym.data.SessionDetail
import br.com.fourgym.data.SessionExercise
import br.com.fourgym.data.SessionSet
import br.com.fourgym.data.SetStatus
import br.com.fourgym.data.WorkoutMode
import br.com.fourgym.data.guidedExerciseIndex
import br.com.fourgym.model.Metric
import br.com.fourgym.model.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun ColumnScope.ActiveWorkout(
    detail: SessionDetail,
    exercises: List<Exercise>,
    equipment: List<Equipment>,
    model: WorkoutViewModel,
    onClose: () -> Unit,
) {
    val repository = model.repository
    val session = detail.session
    val id = session.id
    val items = detail.exercises.sortedBy { it.exercise.position }
    val guided = session.mode == WorkoutMode.GUIDED
    val guidedIndex = if (guided) items.guidedExerciseIndex() else -1
    val visibleIndices = if (guided) listOf(guidedIndex).filter { it >= 0 } else items.indices.toList()
    val guidedExerciseId = items.getOrNull(guidedIndex)?.exercise?.id
    val listState = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var picking by rememberSaveable(id) { mutableStateOf(false) }
    var replacing by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var removing by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var configuring by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var editingNotes by rememberSaveable(id) { mutableStateOf(false) }
    var discarding by rememberSaveable(id) { mutableStateOf(false) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                now = System.currentTimeMillis()
                delay(250)
            }
        }
    }
    LaunchedEffect(session.mode, guidedExerciseId) { listState.scrollToItem(0) }
    val elapsed = formatDuration((now - session.startedAt).coerceAtLeast(0) / 1000)
    val remaining = ((session.restEndsAt ?: now) - now).coerceAtLeast(0)

    ScreenTitle(session.name, "TREINO ATIVO · $elapsed") {
        TextButton(onClick = onClose) { Text("Minimizar") }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkoutMode.entries.forEach { mode ->
            FilterChip(
                selected = session.mode == mode,
                onClick = { model.submit { repository.setMode(id, mode) } },
                label = { Text(mode.label) },
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { discarding = true }) { Text("Descartar") }
    }
    if (remaining > 0) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Descanso · ${formatDuration((remaining + 999) / 1000)}", style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = { model.submit { repository.adjustRest(id, -15) } }) { Text("−15 s") }
                    TextButton(onClick = { model.submit { repository.adjustRest(id, 30) } }) { Text("+30 s") }
                    TextButton(onClick = { model.submit { repository.skipRest(id) } }) { Text("Pular descanso") }
                }
            }
        }
    }
    LazyColumn(
        modifier = Modifier.weight(1f),
        state = listState,
        contentPadding = pagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Instructions(session.notes, label = "ANOTAÇÃO DO TREINO")
            TextButton(onClick = { editingNotes = true }) { Text("Anotação do treino") }
        }
        if (items.isEmpty()) {
            item { EmptyCard("Treino sem exercícios", "Adicione ou crie um exercício para registrar sua primeira série.") }
        } else if (guidedIndex >= 0) {
            item {
                Text(
                    "Exercício ${guidedIndex + 1} de ${items.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (guided) {
            item { EmptyCard("Você chegou ao fim", "Conclua o treino ou volte à lista para revisar as séries.") }
        }
        items(visibleIndices, key = { items[it].exercise.id }) { index ->
            val item = items[index]
            val exercise = item.exercise
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                            Text("${exercise.equipmentName} · ${exercise.muscle.label}")
                        }
                        ItemMenu(
                            canMoveUp = index > 0,
                            canMoveDown = index < items.lastIndex,
                            onMove = { direction -> model.submit { repository.moveExercise(id, exercise.id, direction) } },
                            onRemove = { removing = exercise.id },
                            onConfigure = { configuring = exercise.id },
                            onReplace = if (item.sets.none { it.status == SetStatus.COMPLETED }) {
                                { replacing = exercise.id; picking = true }
                            } else null,
                        )
                    }
                    Instructions(exercise.instructions)
                    Instructions(exercise.executionNotes, label = "NESTE TREINO")
                    if (guided) {
                        OutlinedButton(
                            onClick = { model.submit { repository.skipExercise(id, exercise.id) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Pular exercício") }
                    }
                    item.sets.sortedBy { it.position }.forEachIndexed { setIndex, set ->
                        key(set.id) {
                            HorizontalDivider()
                            WorkoutSet(exercise, set, setIndex, now, model)
                        }
                    }
                    TextButton(onClick = { model.submit { repository.addSet(id, exercise.id) } }) { Text("+ Adicionar série") }
                }
            }
        }
        item {
            Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Adicionar exercício") }
        }
    }
    WorkoutFooter("Concluir treino", enabled = detail.completedCount > 0, onClick = {
        model.submit { model.workoutReview.value = repository.openReview(id) }
    })
    if (picking) {
        ExercisePicker(
            exercises, equipment, model, multiple = false,
            onClose = { picking = false; replacing = null },
        ) { selected ->
            val target = replacing
            selected.firstOrNull()?.let { exercise ->
                model.submit {
                    if (target == null) repository.addExercise(id, exercise.id)
                    else repository.replaceExercise(id, target, exercise.id)
                }
            }
            picking = false
            replacing = null
        }
    }
    items.find { it.exercise.id == configuring }?.let { item ->
        ExerciseSettingsDialog(item, onClose = { configuring = null }) { notes, rest, overtime ->
            model.submit { repository.updateExerciseSettings(id, item.exercise.id, notes, rest, overtime) }
            configuring = null
        }
    }
    removing?.let { itemId ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remover exercício?") },
            text = { Text("As séries deste exercício serão removidas da sessão atual.") },
            confirmButton = {
                TextButton(onClick = {
                    model.submit { repository.removeExercise(id, itemId) }
                    removing = null
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Voltar") } },
        )
    }
    if (editingNotes) {
        WorkoutNotesDialog(session.notes, onClose = { editingNotes = false }) { notes ->
            model.submit { repository.saveSessionNotes(id, notes) }
            editingNotes = false
        }
    }
    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text("Descartar treino?") },
            text = { Text("As séries desta sessão serão descartadas.") },
            confirmButton = {
                TextButton(onClick = {
                    model.submit { repository.discard(id) }
                    discarding = false
                }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { discarding = false }) { Text("Voltar") } },
        )
    }
}

@Composable
private fun WorkoutSet(exercise: SessionExercise, set: SessionSet, index: Int, now: Long, model: WorkoutViewModel) {
    var menu by remember { mutableStateOf(false) }
    var editingRest by rememberSaveable(set.id) { mutableStateOf(false) }
    val repository = model.repository
    val tracking = exercise.tracking
    val pending = set.status == SetStatus.PENDING
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Série ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (!pending) Text(set.status.label, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = { menu = true }) { Text("Ações") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (pending) {
                DropdownMenuItem(text = { Text("Configurar descanso") }, onClick = {
                    menu = false
                    editingRest = true
                })
                DropdownMenuItem(text = { Text("Pular série") }, onClick = {
                    menu = false
                    model.submit { repository.skipSet(exercise.sessionId, exercise.id, set.id) }
                })
            }
            if (set.status == SetStatus.SKIPPED) {
                DropdownMenuItem(text = { Text("Restaurar série") }, onClick = {
                    menu = false
                    model.submit { repository.restoreSkippedSet(exercise.sessionId, exercise.id, set.id) }
                })
            }
            DropdownMenuItem(text = { Text("Excluir série") }, onClick = {
                menu = false
                model.submit { repository.removeSet(exercise.sessionId, exercise.id, set.id) }
            })
        }
    }
    val previous = set.previous?.let(tracking::describe) ?: "—"
    val date = set.previousSessionEndedAt?.let { " · ${formatReferenceDate(it)}" }.orEmpty()
    Text("Anterior · $previous$date", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (pending) {
        Text("Descanso · ${set.restOverrideSeconds ?: exercise.restSeconds} s", style = MaterialTheme.typography.bodySmall)
        MetricsFields(tracking, set.actual, timeEnabled = set.timerStartedAt == null) { metric, value ->
            model.submit { repository.updateMetric(exercise.sessionId, exercise.id, set.id, metric, value) }
        }
        if (Metric.TIME in tracking.metrics) {
            SetTimer(set, now, exercise.allowOvertime) {
                model.submit { repository.toggleTimer(exercise.sessionId, exercise.id, set.id) }
            }
        }
        Button(onClick = {
            model.submit { repository.completeSet(exercise.sessionId, exercise.id, set.id) }
        }) { Text("✓ Concluir série") }
    } else if (set.status == SetStatus.COMPLETED) {
        Text(tracking.describe(set.actual), style = MaterialTheme.typography.bodyLarge)
        if (exercise.allowOvertime && set.extraSeconds() > 0) {
            Text("Tempo extra · +${formatDuration(set.extraSeconds())}", color = MaterialTheme.colorScheme.primary)
        }
    }
    if (editingRest && pending) {
        SetRestDialog(set, onClose = { editingRest = false }) { rest ->
            model.submit { repository.updateSetRest(exercise.sessionId, exercise.id, set.id, rest) }
            editingRest = false
        }
    }
}

@Composable
private fun SetTimer(set: SessionSet, now: Long, allowOvertime: Boolean, onToggle: () -> Unit) {
    val target = set.timerGoalSeconds?.toLong()
    val elapsed = set.elapsedAt(now) / 1000
    val reached = target != null && elapsed >= target
    val running = set.timerStartedAt != null
    val shownElapsed = if (!allowOvertime && target != null) elapsed.coerceAtMost(target) else elapsed
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (reached && allowOvertime) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                target?.let { "META · ${formatDuration(it)}" } ?: "TEMPO DE EXECUÇÃO",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                formatDuration(shownElapsed),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            if (reached) {
                Text(
                    if (allowOvertime) "Meta atingida" else "Meta atingida · confira os valores da série",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (allowOvertime) {
                    Text("+${formatDuration(elapsed - target)} extra", style = MaterialTheme.typography.titleMedium)
                }
            }
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                enabled = allowOvertime || !reached,
            ) {
                Text(
                    when {
                        running -> "Pausar"
                        set.measuredStartedAt != null -> "Retomar"
                        else -> "Iniciar tempo"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
