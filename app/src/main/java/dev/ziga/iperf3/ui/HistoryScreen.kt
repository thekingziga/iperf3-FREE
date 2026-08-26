package dev.ziga.iperf3.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ziga.iperf3.Format
import dev.ziga.iperf3.RunPhase
import dev.ziga.iperf3.RunRecord

@Composable
fun HistoryScreen(
    records: List<RunRecord>,
    onRerun: (RunRecord) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Finished tests show up here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${records.size} runs", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClear) { Text("Clear all") }
            }
        }
        items(records, key = { it.id }) { record ->
            HistoryCard(record, onRerun = { onRerun(record) }, onDelete = { onDelete(record.id) })
        }
    }
}

@Composable
private fun HistoryCard(record: RunRecord, onRerun: () -> Unit, onDelete: () -> Unit) {
    val headline = record.summary?.headline(record.config.direction)
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.config.describe(), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(Format.timestamp(record.startedAt), record.networkLabel).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    headline?.let { Format.bitrate(it.bitsPerSecond) } ?: outcomeLabel(record.outcome),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            if (record.samples.size > 1) {
                ThroughputChart(record.samples, record.config.direction, height = 90.dp)
            }

            record.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            record.summary?.let { summary ->
                val parts = buildList {
                    summary.sent?.let { add("sent ${Format.bitrate(it.bitsPerSecond)}") }
                    summary.received?.let { add("received ${Format.bitrate(it.bitsPerSecond)}") }
                    summary.received?.lostPercent?.let { add("loss ${Format.percent(it)}") }
                    summary.received?.jitterMs?.let { add("jitter ${Format.milliseconds(it)}") }
                    summary.sent?.retransmits?.let { add("$it retransmits") }
                }
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRerun) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("  Use setup")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("  Delete")
                }
            }
        }
    }
}

private fun outcomeLabel(phase: RunPhase): String = when (phase) {
    RunPhase.CANCELLED -> "Stopped"
    RunPhase.FAILED -> "Failed"
    else -> "—"
}
