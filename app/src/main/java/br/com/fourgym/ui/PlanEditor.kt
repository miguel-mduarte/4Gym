package br.com.fourgym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.fourgym.data.PlanDetail
import br.com.fourgym.data.PlanDraft
import br.com.fourgym.data.PlanRule
import br.com.fourgym.data.PlanSchedule
import br.com.fourgym.data.PlanToday
import br.com.fourgym.data.RoutineDetail
import br.com.fourgym.data.WorkoutPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.format.TextStyle
import java.util.Locale

private val planLocale = Locale.forLanguageTag("pt-BR")
private val planDate = DateTimeFormatter.ofPattern("dd/MM/uuuu", planLocale).withResolverStyle(ResolverStyle.STRICT)
private val dayLabels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

@Composable
internal fun ColumnScope.PlanEditor(
    draft: PlanDraft,
    routines: List<RoutineDetail>,
    onChange: (PlanDraft) -> Unit,
    onCreateWorkout: () -> Unit,
    onEditWorkout: (RoutineDetail) -> Unit,
    onSave: (PlanDraft) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val detail = draft.detail
    val plan = detail.plan
    var picking by rememberSaveable(plan.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(plan.id) { mutableStateOf(false) }
    var name by rememberSaveable(plan.id) { mutableStateOf(plan.name) }
    var interval by rememberSaveable(plan.id) { mutableStateOf(plan.intervalDays.toString()) }
    var start by rememberSaveable(plan.id) { mutableStateOf(planDate.format(LocalDate.ofEpochDay(plan.startEpochDay))) }
    val intervalDays = interval.toIntOrNull()?.takeIf { it > 0 }
    val startDate = runCatching { LocalDate.parse(start, planDate) }.getOrNull()
    val weekly = plan.schedule == PlanSchedule.WEEKLY
    val selectedDays = DayOfWeek.values().filter(plan::includes)
    val byId = routines.associateBy { it.routine.id } + draft.workoutDrafts
    val linked = detail.workouts.map { it.routineId }.toSet()
    val available = routines.filterNot { it.routine.id in linked }
    val updatePlan: (WorkoutPlan) -> Unit = { onChange(draft.copy(detail = detail.copy(plan = it))) }

    ScreenTitle(if (draft.isNew) "Novo plano" else "Editar plano", "4GYM · PLANOS DE TREINO") {
        TextButton(onClick = onClose) { Text("Voltar") }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                name, {
                    name = it
                    updatePlan(plan.copy(name = it))
                }, Modifier.fillMaxWidth(),
                label = { Text("Nome do plano") }, singleLine = true,
            )
        }
        item {
            Column {
                Text("Frequência", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanSchedule.entries.forEach { schedule ->
                        FilterChip(
                            selected = plan.schedule == schedule,
                            onClick = { updatePlan(plan.copy(schedule = schedule)) },
                            label = { Text(schedule.label) },
                        )
                    }
                }
            }
        }
        if (weekly) {
            item {
                Column {
                    DayOfWeek.values().asList().chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { day ->
                                val selected = plan.includes(day)
                                FilterChip(
                                    selected = selected,
                                    onClick = { onChange(draft.selectDay(day, !selected)) },
                                    label = { Text(dayLabels[day.value - 1]) },
                                )
                            }
                        }
                    }
                    Text("Como escolher o treino", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanRule.entries.forEach { rule ->
                            FilterChip(
                                selected = plan.rule == rule,
                                onClick = { updatePlan(plan.copy(rule = rule)) },
                                label = { Text(rule.label) },
                            )
                        }
                    }
                    Text(
                        if (plan.rule == PlanRule.BY_DAY) "Vincule um treino a cada dia escolhido."
                        else "A sequência avança quando você conclui um treino pelo plano.",
                    )
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        interval, { raw ->
                            interval = raw.filter(Char::isDigit)
                            interval.toIntOrNull()?.takeIf { it > 0 }?.let {
                                updatePlan(plan.copy(intervalDays = it))
                            }
                        }, Modifier.fillMaxWidth(),
                        label = { Text("Treinar a cada quantos dias?") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = intervalDays == null, singleLine = true,
                    )
                    OutlinedTextField(
                        start, {
                            start = it
                            runCatching { LocalDate.parse(it, planDate) }.getOrNull()?.let { date ->
                                updatePlan(plan.copy(startEpochDay = date.toEpochDay()))
                            }
                        }, Modifier.fillMaxWidth(),
                        label = { Text("Data do primeiro treino") },
                        supportingText = { Text("DD/MM/AAAA") },
                        isError = startDate == null, singleLine = true,
                    )
                    Text("Os dias seguem o intervalo escolhido. A ordem avança ao concluir um treino do plano.")
                }
            }
        }
        item {
            Text("Treinos do plano", style = MaterialTheme.typography.titleLarge)
            if (detail.workouts.isEmpty()) Text("Você pode salvar o plano em branco e adicionar treinos depois.")
        }
        itemsIndexed(detail.workouts, key = { _, workout -> workout.routineId }) { index, entry ->
            val workout = byId[entry.routineId]
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}. ${workout?.routine?.name ?: "Treino indisponível"}", Modifier.weight(1f))
                    if (workout != null) TextButton(onClick = { onEditWorkout(workout) }) { Text("Editar") }
                    ItemMenu(
                        canMoveUp = index > 0,
                        canMoveDown = index < detail.workouts.lastIndex,
                        onMove = { onChange(draft.moveWorkout(index, it)) },
                        onRemove = { onChange(draft.removeWorkout(entry.routineId)) },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onCreateWorkout) { Text("+ Criar treino") }
                TextButton(onClick = { picking = true }) { Text("Usar treino existente") }
            }
        }
        if (weekly && plan.rule == PlanRule.BY_DAY && selectedDays.isNotEmpty() && linked.isNotEmpty()) {
            item { Text("Treino de cada dia", style = MaterialTheme.typography.titleLarge) }
            val choices = listOf(null) + detail.workouts.mapNotNull { byId[it.routineId] }
            items(selectedDays, key = { "day-${it.value}" }) { day ->
                val assigned = detail.days.firstOrNull { it.weekday == day.value }
                val nameForDay = byId[assigned?.routineId]?.routine?.name ?: "Sem treino"
                val dayName = day.getDisplayName(TextStyle.FULL, planLocale).replaceFirstChar { it.titlecase(planLocale) }
                Choice(
                    "$dayName · $nameForDay", choices, { it?.routine?.name ?: "Sem treino" },
                    { onChange(draft.assign(day, it?.routine?.id)) },
                )
            }
        }
        if (!draft.isNew) item {
            TextButton(onClick = { deleting = true }) { Text("Excluir plano") }
        }
    }
    WorkoutFooter("Salvar plano", enabled = name.isNotBlank() && (weekly || intervalDays != null && startDate != null)) {
        val finalPlan = if (weekly) plan.copy(name = name) else plan.copy(
            name = name, intervalDays = checkNotNull(intervalDays), startEpochDay = checkNotNull(startDate).toEpochDay(),
        )
        onSave(draft.copy(detail = detail.copy(plan = finalPlan)))
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Usar treino existente") },
            text = {
                if (available.isEmpty()) {
                    Text("Nenhum outro treino disponível. Use Criar treino para montar uma ficha neste plano.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(available, key = { it.routine.id }) { workout ->
                            TextButton(onClick = {
                                onChange(draft.attachWorkout(workout.routine.id))
                                picking = false
                            }) { Text(workout.routine.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Voltar") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Excluir plano?") },
            text = { Text("Os treinos cadastrados e as sessões do histórico continuam disponíveis.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Voltar") } },
        )
    }
}

@Composable
internal fun TodayWorkoutCard(
    plan: PlanDetail,
    result: PlanToday,
    routines: Map<String, RoutineDetail>,
    canStart: Boolean,
    onStart: (String, String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Treino de hoje", style = MaterialTheme.typography.titleLarge)
            Text(plan.plan.name, color = MaterialTheme.colorScheme.primary)
            when (result) {
                PlanToday.Empty -> Text("Adicione treinos para começar o plano.")
                PlanToday.Unscheduled -> Text("Escolha os dias de treino no plano.")
                PlanToday.Rest -> Text("Dia de descanso no plano.")
                PlanToday.Unassigned -> Text("Vincule um treino ao dia de hoje.")
                is PlanToday.Done -> Text("${result.workoutName} · Concluído hoje")
                is PlanToday.Scheduled -> {
                    Text(routines[result.routineId]?.routine?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { onStart(result.routineId, plan.plan.id) }, enabled = canStart) { Text("Iniciar treino de hoje") }
                }
            }
        }
    }
}

@Composable
internal fun PlanCard(detail: PlanDetail, onEdit: () -> Unit, onActivate: () -> Unit) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(detail.plan.name, style = MaterialTheme.typography.titleLarge)
            val frequency = if (detail.plan.schedule == PlanSchedule.INTERVAL) {
                "A cada ${detail.plan.intervalDays} dia(s)"
            } else DayOfWeek.values().filter(detail.plan::includes)
                .joinToString(", ") { dayLabels[it.value - 1] }.ifEmpty { "Dias a definir" }
            Text("${detail.workouts.size} treino(s) · $frequency")
            if (detail.plan.active) Text("Plano ativo", color = MaterialTheme.colorScheme.primary)
            else TextButton(onClick = onActivate) { Text("Ativar plano") }
        }
    }
}
