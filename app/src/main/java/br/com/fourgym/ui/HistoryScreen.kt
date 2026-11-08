package br.com.fourgym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.fourgym.data.SessionDetail
import br.com.fourgym.data.SetStatus
import br.com.fourgym.model.formatDuration
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val locale = Locale.forLanguageTag("pt-BR")
private val sessionDate = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy · HH:mm", locale)
private val referenceDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)

internal fun formatSessionDate(timestamp: Long, offsetSeconds: Int): String =
    sessionDate.format(Instant.ofEpochMilli(timestamp).atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds)))

internal fun formatReferenceDate(timestamp: Long): String =
    referenceDate.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

internal fun SessionDetail.summary(endedAt: Long): String {
    val time = formatDuration((endedAt - session.startedAt).coerceAtLeast(0) / 1000)
    val volume = NumberFormat.getNumberInstance(locale).format(volumeKg)
    return "$time · $volume kg de volume"
}

@Composable
internal fun HistoryScreen(history: List<SessionDetail>, onOpen: (String) -> Unit) {
    ScreenTitle("Histórico", "4GYM · LINHA DO TEMPO")
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (history.isEmpty()) {
            item { EmptyCard("Nenhum treino concluído", "As sessões salvas aparecem aqui com os resultados de cada série.") }
        }
        items(history, key = { it.session.id }) { detail ->
            Card(onClick = { onOpen(detail.session.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(detail.session.name, style = MaterialTheme.typography.titleLarge)
                    Text(formatSessionDate(detail.session.startedAt, detail.session.startedOffsetSeconds))
                    Text(detail.summary(detail.session.endedAt ?: detail.session.startedAt), color = MaterialTheme.colorScheme.primary)
                    if (detail.session.notes.isNotBlank()) {
                        Text(detail.session.notes, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    detail.session.rating?.let { Text("Autoavaliação · $it/5") }
                }
            }
        }
    }
}

@Composable
internal fun SessionHistoryDetail(detail: SessionDetail, onEdit: () -> Unit, onClose: () -> Unit) {
    val session = detail.session
    ScreenTitle(session.name, "4GYM · SESSÃO CONCLUÍDA") {
        TextButton(onClick = onClose) { Text("Voltar") }
        TextButton(onClick = onEdit) { Text("Editar") }
    }
    LazyColumn(contentPadding = pagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(formatSessionDate(session.startedAt, session.startedOffsetSeconds))
            Text(detail.summary(session.endedAt ?: session.startedAt), color = MaterialTheme.colorScheme.primary)
            Text("${detail.completedCount} concluída(s) · ${detail.skippedCount} pulada(s)")
            session.rating?.let { Text("Autoavaliação · $it/5") }
            Instructions(session.notes, label = "ANOTAÇÃO DO TREINO")
        }
        items(detail.exercises.sortedBy { it.exercise.position }, key = { it.exercise.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.exercise.name, style = MaterialTheme.typography.titleLarge)
                    Text("${item.exercise.equipmentName} · ${item.exercise.muscle.label}")
                    Instructions(item.exercise.executionNotes, label = "NESTE TREINO")
                    item.sets.sortedBy { it.position }.forEachIndexed { index, set ->
                        val result = if (set.status == SetStatus.COMPLETED) item.exercise.tracking.describe(set.actual) else "—"
                        val extra = if (item.exercise.allowOvertime && set.status == SetStatus.COMPLETED && set.extraSeconds() > 0) {
                            " · +${formatDuration(set.extraSeconds())} extra"
                        } else ""
                        Text("Série ${index + 1} · ${set.status.label} · $result$extra")
                    }
                }
            }
        }
    }
}
