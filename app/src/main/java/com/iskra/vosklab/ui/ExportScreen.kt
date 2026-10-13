package com.iskra.vosklab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iskra.vosklab.data.RecordingRepository
import com.iskra.vosklab.personalization.DatasetExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ExportScreen(
    repository: RecordingRepository,
    contextFilesDir: File
) {
    val coroutineScope = rememberCoroutineScope()
    val recordings = remember { repository.getAllRecordings() }

    var exportKaldi by remember { mutableStateOf(true) }
    var exportStatusMessage by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Eksport danych treningowych i diagnostycznych", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Statystyki zbioru danych", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Liczba gotowych nagrań: ${recordings.size}")
                val totalDurationSec = recordings.fold(0L) { acc, rec -> acc + rec.metrics.audioDurationMs } / 1000L
                Text(text = "Łączny czas nagrań: $totalDurationSec s")

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Generuj manifest Kaldi (text, wav.scp, utt2spk)")
                    Checkbox(checked = exportKaldi, onCheckedChange = { exportKaldi = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    enabled = !isExporting && recordings.isNotEmpty(),
                    onClick = {
                        isExporting = true
                        exportStatusMessage = "Generowanie paczki eksportowej..."
                        coroutineScope.launch {
                            val exportDir = File(contextFilesDir, "exports")
                            val exporter = DatasetExporter(exportDir)
                            val outputFile = File(exportDir, "iskra_vosklab_dataset_${System.currentTimeMillis()}.zip")

                            val success = withContext(Dispatchers.IO) {
                                exporter.exportDatasetZip(
                                    recordings = recordings,
                                    outputZipFile = outputFile,
                                    exportKaldiFormat = exportKaldi
                                )
                            }

                            if (success) {
                                exportStatusMessage = "Pomyślnie wyeksportowano paczkę ZIP do:\n${outputFile.absolutePath}"
                            } else {
                                exportStatusMessage = "Błąd podczas generowania paczki eksportowej."
                            }
                            isExporting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isExporting) "Pobieranie i pakowanie..." else "Eksportuj zbiorczy ZIP")
                }

                if (exportStatusMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = exportStatusMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
