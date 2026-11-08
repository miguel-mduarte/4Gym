package br.com.fourgym.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.com.fourgym.WorkoutViewModel
import br.com.fourgym.data.PlanDetail
import br.com.fourgym.data.PlanDraft
import br.com.fourgym.data.Routine
import br.com.fourgym.data.RoutineDetail
import br.com.fourgym.data.SessionDetail
import br.com.fourgym.data.WorkoutPlan
import br.com.fourgym.model.newId
import java.time.LocalDate
import kotlinx.coroutines.delay

private val colors = darkColorScheme(
    primary = Color(0xFFB9F578),
    onPrimary = Color(0xFF172409),
    background = Color(0xFF101612),
    surface = Color(0xFF101612),
    surfaceContainer = Color(0xFF1D2720),
    surfaceContainerHigh = Color(0xFF263229),
    onSurface = Color(0xFFEAF2EA),
    onSurfaceVariant = Color(0xFFBBCBBD),
    outline = Color(0xFF7D9081),
)

private enum class Tab(val label: String, val symbol: String) {
    WORKOUTS("Treinos", "▤"), HISTORY("Histórico", "◷"), LIBRARY("Biblioteca", "▦")
}

@Composable
fun FourGym(model: WorkoutViewModel) {
    val repository = model.repository
    val equipment by repository.equipment.collectAsStateWithLifecycle(emptyList())
    val exercises by repository.exercises.collectAsStateWithLifecycle(emptyList())
    val routines by repository.routines.collectAsStateWithLifecycle(emptyList())
    val plans by repository.plans.collectAsStateWithLifecycle(emptyList())
    val active by repository.activeSession.collectAsStateWithLifecycle(null)
    val history by repository.history.collectAsStateWithLifecycle(emptyList())
    val draft by model.routineDraft.collectAsStateWithLifecycle()
    val planDraft by model.planDraft.collectAsStateWithLifecycle()
    val review by model.workoutReview.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val today by produceState(LocalDate.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = LocalDate.now()
                delay(30_000)
            }
        }
    }
    var tab by rememberSaveable { mutableStateOf(Tab.WORKOUTS) }
    var showWorkout by rememberSaveable { mutableStateOf(true) }
    var selectedHistoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val historyDetail = history.find { it.session.id == selectedHistoryId }
    val preview = routines.find { it.routine.id == previewId }
    val workout = active?.takeIf { showWorkout && draft == null && planDraft == null && preview == null }
    val closeDraft = { model.routineDraft.value = null }
    val closePlan = { model.planDraft.value = null }
    val closeReview = { model.workoutReview.value = null }
    val startWorkout: (String?, String?) -> Unit = { id, planId ->
        model.submit {
            repository.start(id, planId)
            previewId = null
            showWorkout = true
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            if (model.error.value == it) model.error.value = null
        }
    }
    BackHandler(review != null || draft != null || planDraft != null || workout != null || preview != null || historyDetail != null) {
        when {
            review != null -> closeReview()
            draft != null -> closeDraft()
            planDraft != null -> closePlan()
            workout != null -> showWorkout = false
            preview != null -> previewId = null
            else -> selectedHistoryId = null
        }
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (review == null && draft == null && planDraft == null && workout == null && preview == null && historyDetail == null) {
                    NavigationBar {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Text(item.symbol, style = MaterialTheme.typography.titleLarge) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                when {
                    review != null -> WorkoutReviewScreen(
                        review = checkNotNull(review),
                        onChange = { model.workoutReview.value = it },
                        onClose = closeReview,
                        onSave = { result ->
                            model.submit {
                                repository.saveReview(result)
                                closeReview()
                            }
                        },
                    )
                    draft != null -> RoutineEditor(
                        draft = checkNotNull(draft),
                        exercises = exercises,
                        equipment = equipment,
                        model = model,
                        onChange = { model.routineDraft.value = it },
                        onClose = closeDraft,
                        saveLabel = if (planDraft == null) "Salvar" else "Salvar treino",
                        onSave = {
                            val plan = model.planDraft.value
                            if (plan != null) {
                                model.planDraft.value = plan.withWorkout(it)
                                closeDraft()
                            } else {
                                model.submit {
                                    repository.saveRoutine(it)
                                    closeDraft()
                                }
                            }
                        },
                    )
                    planDraft != null -> PlanEditor(
                        draft = checkNotNull(planDraft),
                        routines = routines,
                        onChange = { model.planDraft.value = it },
                        onCreateWorkout = { model.routineDraft.value = newWorkoutDraft() },
                        onEditWorkout = { model.routineDraft.value = it },
                        onSave = { result ->
                            model.submit {
                                repository.savePlan(result)
                                closePlan()
                            }
                        },
                        onDelete = {
                            val id = checkNotNull(model.planDraft.value).detail.plan.id
                            model.submit {
                                repository.deletePlan(id)
                                closePlan()
                            }
                        },
                        onClose = closePlan,
                    )
                    workout != null -> ActiveWorkout(workout, exercises, equipment, model) { showWorkout = false }
                    preview != null -> RoutinePreview(
                        preview, exercises, equipment,
                        canStart = active == null,
                        onStart = { startWorkout(preview.routine.id, null) },
                        onClose = { previewId = null },
                    )
                    historyDetail != null -> SessionHistoryDetail(
                        historyDetail,
                        onEdit = {
                            model.submit {
                                model.workoutReview.value = repository.openReview(historyDetail.session.id)
                            }
                        },
                        onClose = { selectedHistoryId = null },
                    )
                    tab == Tab.HISTORY -> HistoryScreen(history) { selectedHistoryId = it }
                    tab == Tab.LIBRARY -> LibraryScreen(exercises, equipment, model)
                    else -> Workouts(
                        routines = routines,
                        plans = plans,
                        history = history,
                        today = today,
                        active = active,
                        onStart = { startWorkout(it, null) },
                        onStartScheduled = startWorkout,
                        onCreatePlan = {
                            val plan = WorkoutPlan(newId(), "", today.toEpochDay())
                            model.planDraft.value = PlanDraft(PlanDetail(plan, emptyList(), emptyList()), isNew = true)
                        },
                        onEditPlan = { detail ->
                            model.planDraft.value = PlanDraft(detail.copy(workouts = detail.workouts.sortedBy { it.position }))
                        },
                        onActivatePlan = { id -> model.submit { repository.activatePlan(id) } },
                        onPreview = { previewId = it },
                        onEdit = { model.routineDraft.value = it },
                        onDuplicate = { id -> model.submit { repository.duplicateRoutine(id) } },
                        onDelete = { id -> model.submit { repository.deleteRoutine(id) } },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScreenTitle(title: String, eyebrow: String, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        action()
    }
}

internal val pagePadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp)

@Composable
internal fun EmptyCard(title: String, description: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Workouts(
    routines: List<RoutineDetail>,
    plans: List<PlanDetail>,
    history: List<SessionDetail>,
    today: LocalDate,
    active: SessionDetail?,
    onStart: (String?) -> Unit,
    onStartScheduled: (String, String) -> Unit,
    onCreatePlan: () -> Unit,
    onEditPlan: (PlanDetail) -> Unit,
    onActivatePlan: (String) -> Unit,
    onPreview: (String) -> Unit,
    onEdit: (RoutineDetail) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleting by remember { mutableStateOf<Routine?>(null) }
    val activePlan = plans.firstOrNull { it.plan.active }
    val byId = remember(routines) { routines.associateBy { it.routine.id } }
    val todayWorkout = remember(activePlan, byId, history, today) {
        activePlan?.today(today, byId.keys, history.map { it.session })
    }
    ScreenTitle("Treinos", "4GYM · DIÁRIO DE TREINO")
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(onClick = { onStart(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (active == null) "Iniciar treino livre" else "Retomar ${active.session.name}")
            }
        }
        if (activePlan != null && todayWorkout != null) {
            item {
                TodayWorkoutCard(activePlan, todayWorkout, byId, active == null, onStartScheduled)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Planos de treino", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onCreatePlan) { Text("+ Novo plano") }
            }
        }
        items(plans, key = { it.plan.id }) { detail ->
            PlanCard(detail, onEdit = { onEditPlan(detail) }, onActivate = { onActivatePlan(detail.plan.id) })
        }
        item {
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Meus treinos", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { onEdit(newWorkoutDraft()) }) { Text("+ Novo treino") }
            }
        }
        if (routines.isEmpty()) {
            item { EmptyCard("Sua primeira ficha", "Crie um treino aqui ou monte seus treinos dentro de um plano.") }
        }
        items(routines, key = { it.routine.id }) { detail ->
            var menu by remember { mutableStateOf(false) }
            Card(onClick = { onPreview(detail.routine.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(detail.routine.name, style = MaterialTheme.typography.titleLarge)
                    if (detail.routine.description.isNotBlank()) {
                        Text(
                            detail.routine.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("${detail.exercises.size} exercício(s) · ${detail.exercises.sumOf { it.sets.size }} série(s)")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onStart(detail.routine.id) }, enabled = active == null) { Text("Iniciar") }
                        TextButton(onClick = { menu = true }) { Text("Opções") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onEdit(detail) })
                            DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; onDuplicate(detail.routine.id) })
                            DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; deleting = detail.routine })
                        }
                    }
                }
            }
        }
    }
    deleting?.let { routine ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir treino?") },
            text = { Text("As sessões já registradas em ${routine.name} continuam no histórico.") },
            confirmButton = { TextButton(onClick = { onDelete(routine.id); deleting = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

private fun newWorkoutDraft() = RoutineDetail(Routine(newId(), "", 0), emptyList())
