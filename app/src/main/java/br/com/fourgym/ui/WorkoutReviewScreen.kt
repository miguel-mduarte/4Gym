package br.com.fourgym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.fourgym.data.SessionDetail
import br.com.fourgym.data.SessionStatus
import br.com.fourgym.data.SetStatus
import br.com.fourgym.data.WorkoutReview

@Composable
internal fun ColumnScope.WorkoutReviewScreen(
    review: WorkoutReview,
    onChange: (WorkoutReview) -> Unit,
    onClose: () -> Unit,
    onSave: (WorkoutReview) -> Unit,
) {
    val timing = review.resolveTiming()
    val completed = review.completedCount
    val missingValues = review.hasIncompleteValues
    val detail = review.detail
    val editingHistory = detail.session.status == SessionStatus.COMPLETED
    val reviewedDetail = SessionDetail(detail.session, detail.exercises.map { item ->
        item.copy(sets = item.sets.map { set ->
            val result = review.result(set)
            set.copy(status = result.status, actual = result.actual)
        })
    })
    ScreenTitle(detail.session.name, if (editingHistory) "EDITAR SESSÃO" else "REVISÃO DO TREINO") {
        TextButton(onClick = onClose) { Text("Voltar") }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Quando foi o treino?", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        review.date, { onChange(review.copy(date = it)) }, Modifier.weight(1f),
                        label = { Text("Data de início") }, supportingText = { Text("DD/MM/AAAA") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        review.time, { onChange(review.copy(time = it)) }, Modifier.weight(1f),
                        label = { Text("Hora de início") }, supportingText = { Text("HH:mm") },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    review.duration, { onChange(review.copy(duration = it)) }, Modifier.fillMaxWidth(),
                    label = { Text("Duração total (mm:ss)") }, singleLine = true,
                )
                if (timing == null) {
                    Text("Confira a data, a hora e a duração.", color = MaterialTheme.colorScheme.error)
                } else {
                    val summary = reviewedDetail.copy(session = detail.session.copy(startedAt = timing.startedAt))
                    Text(summary.summary(timing.endedAt), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Séries registradas", style = MaterialTheme.typography.titleLarge)
                Text("${reviewedDetail.completedCount} concluída(s) · ${reviewedDetail.skippedCount} pulada(s)")
                if (completed == 0 || missingValues) {
                    Text("Confira os valores das séries concluídas e mantenha pelo menos uma.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        items(detail.exercises.sortedBy { it.exercise.position }, key = { it.exercise.id }) { item ->
            val exercise = item.exercise
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    item.sets.sortedBy { it.position }.forEachIndexed { index, set ->
                        key(set.id) {
                            val result = review.result(set)
                            HorizontalDivider()
                            Text("Série ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            Choice(
                                result.status.label,
                                listOf(SetStatus.COMPLETED, SetStatus.SKIPPED, SetStatus.PENDING),
                                { it.label },
                                { status -> onChange(review.withResult(set, result.copy(status = status))) },
                            )
                            if (result.status == SetStatus.COMPLETED) {
                                MetricsFields(exercise.tracking, result.actual) { metric, value ->
                                    val actual = metric.change(result.actual, value)
                                    onChange(review.withResult(set, result.copy(actual = actual)))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NotesField(review.notes) { onChange(review.copy(notes = it)) }
                Text("Autoavaliação de desempenho (opcional)")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (score in 1..5) {
                        FilterChip(
                            selected = review.rating == score,
                            onClick = { onChange(review.copy(rating = if (review.rating == score) null else score)) },
                            modifier = Modifier.weight(1f),
                            label = { Text(score.toString()) },
                        )
                    }
                }
                Text("1 · Fraco     5 · Excelente")
            }
        }
    }
    val saveLabel = if (editingHistory) "Salvar alterações" else "Salvar sessão"
    WorkoutFooter(saveLabel, enabled = timing != null && completed > 0 && !missingValues, onClick = {
        onSave(review)
    })
}
