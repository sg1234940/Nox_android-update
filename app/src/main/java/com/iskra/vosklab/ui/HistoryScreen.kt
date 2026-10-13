package com.iskra.vosklab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iskra.vosklab.audio.AudioPlayer
import com.iskra.vosklab.data.RecordingItem
import com.iskra.vosklab.data.RecordingRepository
import com.iskra.vosklab.metrics.TextMetrics
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(repository: RecordingRepository) {
    var recordings by remember { mutableStateOf(repository.getAllRecordings()) }
    val audioPlayer = remember { AudioPlayer() }

    fun refresh() {
        recordings = repository.getAllRecordings()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Historia nagrań i korekta transkrypcji",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (recordings.isEmpty()) {
            Text(text = "Brak zapisanych nagrań w bazie.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recordings, key = { it.id }) { item ->
                    RecordingHistoryCard(
                        item = item,
                        onPlay = { audioPlayer.playFile(File(item.audioPath)) },
                        onSaveCorrection = { updatedText ->
                            repository.updateCorrection(item.id, updatedText)
                            refresh()
                        },
                        onDelete = {
                            repository.deleteRecording(item.id)
                            refresh()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingHistoryCard(
    item: RecordingItem,
    onPlay: () -> Unit,
    onSaveCorrection: (String) -> Unit,
    onDelete: () -> Unit
) {
    var editingText by remember { mutableStateOf(item.correctedText) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(item.timestamp) { sdf.format(Date(item.timestamp)) }

    val wordMetrics = remember(editingText, item.rawTranscript) {
        TextMetrics.calculateWordMetrics(editingText, item.rawTranscript)
    }
    val charMetrics = remember(editingText, item.rawTranscript) {
        TextMetrics.calculateCharacterMetrics(editingText, item.rawTranscript)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = formattedDate, style = MaterialTheme.typography.titleSmall)
                Text(text = "Model: ${item.modelName}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Oryginalna transkrypcja Vosk:", style = MaterialTheme.typography.labelSmall)
            Text(text = item.rawTranscript, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editingText,
                onValueChange = { editingText = it },
                label = { Text("Poprawiony tekst (Referencja)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "WER: ${(wordMetrics.errorRate * 100).toInt()}%")
                Text(text = "CER: ${(charMetrics.errorRate * 100).toInt()}%")
                Text(text = "SNR: ${item.metrics.snrDb} dB")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete) {
                    Text("Usuń")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onPlay) {
                    Text("Odtwórz")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSaveCorrection(editingText) }) {
                    Text("Zapisz")
                }
            }
        }
    }
}
