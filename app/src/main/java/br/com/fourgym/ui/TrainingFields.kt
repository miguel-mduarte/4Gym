package br.com.fourgym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.fourgym.model.Measurements
import br.com.fourgym.model.Metric
import br.com.fourgym.model.TrackingType

@Composable
internal fun <T> Choice(
    label: String,
    items: List<T>,
    describe: (T) -> String,
    onChoose: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(describe(item)) },
                    onClick = { expanded = false; onChoose(item) },
                )
            }
        }
    }
}

@Composable
internal fun Instructions(text: String, label: String = "INSTRUÇÕES") {
    if (text.isBlank()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun MetricsFields(
    tracking: TrackingType,
    measurements: Measurements,
    timeLabel: String = Metric.TIME.label,
    timeEnabled: Boolean = true,
    onChange: (Metric, Number?) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tracking.metrics.forEach { metric ->
            val value = metric.read(measurements)
            val formatted = value?.let(metric::format).orEmpty()
            var raw by rememberSaveable(metric) { mutableStateOf(formatted) }
            var focused by remember { mutableStateOf(false) }
            LaunchedEffect(value, focused) {
                if (!focused) raw = formatted
            }
            OutlinedTextField(
                value = raw,
                onValueChange = {
                    raw = it
                    onChange(metric, metric.parse(it))
                },
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                label = { Text(if (metric == Metric.TIME) timeLabel else metric.label) },
                enabled = metric != Metric.TIME || timeEnabled,
                isError = raw.isNotBlank() && metric.parse(raw) == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = when {
                    metric.isInteger -> KeyboardType.Number
                    metric == Metric.TIME -> KeyboardType.Text
                    else -> KeyboardType.Decimal
                }),
            )
        }
    }
}
