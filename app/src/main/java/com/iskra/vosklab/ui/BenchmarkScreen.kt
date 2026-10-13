package com.iskra.vosklab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iskra.vosklab.data.RecordingRepository
import com.iskra.vosklab.metrics.TextMetrics
import com.iskra.vosklab.vosk.ModelManager
import com.iskra.vosklab.vosk.VoskEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BenchmarkResult(
    val recordingId: String,
    val referenceText: String,
    val modelResults: Map<String, ModelRunResult>
)

data class ModelRunResult(
    val transcript: String,
    val recognitionTimeMs: Long,
    val rtf: Float,
    val wer: Float,
    val cer: Float
)

@Composable
fun BenchmarkScreen(
    repository: RecordingRepository,
    modelManager: ModelManager,
    voskEngineManager: VoskEngineManager
) {
    val coroutineScope = rememberCoroutineScope()

    var availableModels by remember { mutableStateOf(modelManager.listInstalledModels()) }
    var selectedModels by remember { mutableStateOf(availableModels.map { it.name }.toSet()) }

    var isBenchmarking by remember { mutableStateOf(false) }
    var benchmarkResults by remember { mutableStateOf<List<BenchmarkResult>>(emptyList()) }
    var progressText by remember { mutableStateOf("") }

    val recordings = remember { repository.getAllRecordings() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Porównywanie modeli Vosk (Benchmark)", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Dostępne modele do porównania:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                availableModels.forEach { model ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = model.name)
                        Checkbox(
                            checked = selectedModels.contains(model.name),
                            onCheckedChange = { checked ->
                                selectedModels = if (checked) {
                                    selectedModels + model.name
                                } else {
                                    selectedModels - model.name
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    enabled = !isBenchmarking && selectedModels.isNotEmpty() && recordings.isNotEmpty(),
                    onClick = {
                        isBenchmarking = true
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                val results = mutableListOf<BenchmarkResult>()
                                val modelsToRun = availableModels.filter { selectedModels.contains(it.name) }

                                modelsToRun.forEach { modelInfo ->
                                    withContext(Dispatchers.Main) {
                                        progressText = "Ładowanie modelu ${modelInfo.name}..."
                                    }
                                    voskEngineManager.loadModel(File(modelInfo.path))

                                    recordings.forEach { item ->
                                        withContext(Dispatchers.Main) {
                                            progressText = "Przetwarzanie ${item.id} przez ${modelInfo.name}..."
                                        }
                                        val audioFile = File(item.audioPath)
                                        if (audioFile.exists()) {
                                            val recRes = voskEngineManager.transcribeWavFile(audioFile)
                                            val transcript = recRes.text

                                            val ref = item.correctedText.ifBlank { item.rawTranscript }
                                            val wer = TextMetrics.calculateWordMetrics(ref, transcript).errorRate
                                            val cer = TextMetrics.calculateCharacterMetrics(ref, transcript).errorRate

                                            var existing = results.find { it.recordingId == item.id }
                                            if (existing == null) {
                                                existing = BenchmarkResult(
                                                    recordingId = item.id,
                                                    referenceText = ref,
                                                    modelResults = mutableMapOf()
                                                )
                                                results.add(existing)
                                            }
                                            (existing.modelResults as MutableMap<String, ModelRunResult>)[modelInfo.name] = ModelRunResult(
                                                transcript = transcript,
                                                recognitionTimeMs = recRes.recognitionTimeMs,
                                                rtf = recRes.rtf,
                                                wer = wer,
                                                cer = cer
                                            )
                                        }
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    benchmarkResults = results
                                    isBenchmarking = false
                                    progressText = "Benchmark zakończony!"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isBenchmarking) "Trwa testowanie..." else "Uruchom Benchmark")
                }

                if (progressText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = progressText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(benchmarkResults) { res ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Referencja: ${res.referenceText}", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        res.modelResults.entries.forEach { entry ->
                            val modelName = entry.key
                            val runRes = entry.value
                            Text(text = "Model: $modelName", style = MaterialTheme.typography.labelMedium)
                            Text(text = "Transkrypcja: ${runRes.transcript}")
                            Text(text = "WER: ${(runRes.wer * 100).toInt()}% | CER: ${(runRes.cer * 100).toInt()}% | Czas: ${runRes.recognitionTimeMs} ms | RTF: %.2f".format(runRes.rtf))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
