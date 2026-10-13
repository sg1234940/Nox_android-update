package com.iskra.vosklab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iskra.vosklab.audio.AudioPlayer
import com.iskra.vosklab.audio.AudioRecorder
import com.iskra.vosklab.data.RecordingItem
import com.iskra.vosklab.data.RecordingRepository
import com.iskra.vosklab.metrics.PerformanceProfiler
import com.iskra.vosklab.metrics.RecognitionMetrics
import com.iskra.vosklab.personalization.PostProcessingPipeline
import com.iskra.vosklab.vosk.VoskEngineManager
import java.io.File

@Composable
fun LabScreen(
    repository: RecordingRepository,
    voskEngineManager: VoskEngineManager,
    postProcessingPipeline: PostProcessingPipeline
) {
    var isRecording by remember { mutableStateOf(false) }
    var currentRawTranscript by remember { mutableStateOf("") }
    var currentProcessedTranscript by remember { mutableStateOf("") }
    var currentAudioFile by remember { mutableStateOf<File?>(null) }

    var currentRmsDb by remember { mutableFloatStateOf(0f) }
    var audioDurationMs by remember { mutableLongStateOf(0L) }

    var listeningLatencyMs by remember { mutableLongStateOf(0L) }
    var totalRecognitionMs by remember { mutableLongStateOf(0L) }
    var droppedWordWarning by remember { mutableStateOf(false) }
    var snrDbEstimate by remember { mutableFloatStateOf(0f) }

    var heapUsageMb by remember { mutableFloatStateOf(0f) }

    val audioRecorder = remember { AudioRecorder() }
    val audioPlayer = remember { AudioPlayer() }

    LaunchedEffect(Unit) {
        heapUsageMb = PerformanceProfiler.getMemoryStats().usedMemoryMb
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Status silnika Vosk",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                val isLoaded = voskEngineManager.isModelLoaded.collectAsState().value
                val activeModelName = voskEngineManager.activeModelName.collectAsState().value
                Text(
                    text = if (isLoaded) "Aktywny model: $activeModelName" else "Brak załadowanego modelu Vosk",
                    color = if (isLoaded) Color(0xFF2E7D32) else Color.Red
                )
                Text(text = "Zużycie pamięci RAM: %.1f MB".format(heapUsageMb))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Laboratorium nagrywania i STT",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (!isRecording) {
                                val file = repository.getNewAudioFile()
                                currentAudioFile = file
                                PerformanceProfiler.markMicStart()

                                audioRecorder.startRecording(
                                    outputFile = file,
                                    listener = object : AudioRecorder.AudioFrameListener {
                                        override fun onAudioFrame(buffer: ShortArray, readSize: Int, currentRmsDbVal: Float) {
                                            currentRmsDb = currentRmsDbVal
                                            val partial = voskEngineManager.acceptWaveform(buffer, readSize)
                                            if (partial.isNotBlank()) {
                                                currentRawTranscript = partial
                                                currentProcessedTranscript = postProcessingPipeline.applyPostProcessing(partial)
                                            }
                                        }
                                    }
                                )
                                isRecording = true
                                listeningLatencyMs = PerformanceProfiler.markListeningReady()
                            } else {
                                audioRecorder.stopRecording()
                                isRecording = false

                                currentAudioFile?.let { wavFile ->
                                    val analysis = audioRecorder.analyzeAudioFile(wavFile)
                                    audioDurationMs = analysis.durationMs
                                    snrDbEstimate = analysis.snrDb
                                    droppedWordWarning = analysis.droppedFirstWordsDetected

                                    val startRecTime = System.currentTimeMillis()
                                    val finalResult = voskEngineManager.getFinalResult()
                                    totalRecognitionMs = System.currentTimeMillis() - startRecTime

                                    currentRawTranscript = finalResult
                                    currentProcessedTranscript = postProcessingPipeline.applyPostProcessing(finalResult)
                                    heapUsageMb = PerformanceProfiler.getMemoryStats().usedMemoryMb

                                    val item = RecordingItem(
                                        id = System.currentTimeMillis().toString(),
                                        timestamp = System.currentTimeMillis(),
                                        audioPath = wavFile.absolutePath,
                                        rawTranscript = currentRawTranscript,
                                        correctedText = currentProcessedTranscript,
                                        metrics = RecognitionMetrics(
                                            firstWordLatencyMs = listeningLatencyMs,
                                            totalRecognitionTimeMs = totalRecognitionMs,
                                            audioDurationMs = audioDurationMs,
                                            snrDb = snrDbEstimate,
                                            droppedInitialWords = droppedWordWarning
                                        ),
                                        modelName = voskEngineManager.activeModelName.value
                                    )
                                    repository.saveRecording(item)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isRecording) "Zatrzymaj nagrywanie" else "Rozpocznij nagrywanie")
                    }

                    if (isRecording) {
                        Text(
                            text = "Głośność: %.1f dB".format(currentRmsDb),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (droppedWordWarning) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = "Ostrzeżenie VAD: Wykryto prawdopodobne obcięcie pierwszego słowa!",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(text = "Rozpoznany tekst (Surowy Vosk):", style = MaterialTheme.typography.labelMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = currentRawTranscript.ifBlank { "Brak transkrypcji..." },
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "Tekst po korekcji (Mechanizm A):", style = MaterialTheme.typography.labelMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = currentProcessedTranscript.ifBlank { "Brak..." },
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Wyniki diagnostyczne",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Opóźnienie startu nasłuchu: ${listeningLatencyMs} ms")
                Text(text = "Czas przetwarzania STT: ${totalRecognitionMs} ms")
                Text(text = "Długość nagrania: ${audioDurationMs} ms")
                Text(text = "Szacowany stosunek sygnał/szum (SNR): %.1f dB".format(snrDbEstimate))
                currentAudioFile?.let { file ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { audioPlayer.playFile(file) }) {
                        Text("Odtwórz nagranie")
                    }
                }
            }
        }
    }
}
