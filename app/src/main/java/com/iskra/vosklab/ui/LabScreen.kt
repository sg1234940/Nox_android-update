package com.iskra.vosklab.ui

import android.os.Handler
import android.os.Looper
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
import com.iskra.vosklab.metrics.DiagnosticTimestamps
import com.iskra.vosklab.metrics.PerformanceProfiler
import com.iskra.vosklab.metrics.RecognitionMetrics
import com.iskra.vosklab.personalization.PostProcessingPipeline
import com.iskra.vosklab.vosk.VoskEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LabScreen(
    repository: RecordingRepository,
    voskEngineManager: VoskEngineManager,
    postProcessingPipeline: PostProcessingPipeline
) {
    val coroutineScope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    var currentRawTranscript by remember { mutableStateOf("") }
    var currentProcessedTranscript by remember { mutableStateOf("") }
    var currentAudioFile by remember { mutableStateOf<File?>(null) }

    var currentRmsDb by remember { mutableFloatStateOf(0f) }
    var estimatedSnrDb by remember { mutableFloatStateOf(0f) }
    var audioDurationMs by remember { mutableLongStateOf(0L) }

    var micStartMs by remember { mutableLongStateOf(0L) }
    var firstPcmMs by remember { mutableLongStateOf(0L) }
    var speechOnsetMs by remember { mutableLongStateOf(0L) }
    var firstPartialMs by remember { mutableLongStateOf(0L) }
    var firstWordMs by remember { mutableLongStateOf(0L) }
    var finalMs by remember { mutableLongStateOf(0L) }
    var stopMs by remember { mutableLongStateOf(0L) }

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
                val isLoaded = voskEngineManager.isModelLoadedState.collectAsState().value
                val activeModelName = voskEngineManager.activeModelNameState.collectAsState().value
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
                        enabled = !isProcessing,
                        onClick = {
                            if (!isRecording) {
                                val file = repository.getNewAudioFile()
                                currentAudioFile = file
                                currentRawTranscript = ""
                                currentProcessedTranscript = ""

                                firstPartialMs = 0L
                                firstWordMs = 0L
                                finalMs = 0L

                                audioRecorder.startRecording(
                                    outputFile = file,
                                    listener = object : AudioRecorder.AudioFrameListener {
                                        override fun onAudioFrame(buffer: ShortArray, readSize: Int, frameRmsDb: Float, timestampMs: Long) {
                                            val partial = voskEngineManager.acceptWaveform(buffer, readSize)
                                            mainHandler.post {
                                                currentRmsDb = frameRmsDb
                                                if (partial.isNotBlank()) {
                                                    if (firstPartialMs == 0L) {
                                                        firstPartialMs = timestampMs
                                                    }
                                                    if (firstWordMs == 0L && partial.trim().isNotEmpty()) {
                                                        firstWordMs = timestampMs
                                                    }
                                                    currentRawTranscript = partial
                                                    currentProcessedTranscript = postProcessingPipeline.applyPostProcessing(partial)
                                                }
                                            }
                                        }
                                    }
                                )

                                isRecording = true
                                micStartMs = audioRecorder.micStartMs
                            } else {
                                isProcessing = true
                                audioRecorder.stopRecording()
                                isRecording = false

                                micStartMs = audioRecorder.micStartMs
                                firstPcmMs = audioRecorder.firstPcmMs
                                speechOnsetMs = audioRecorder.speechOnsetMs
                                stopMs = audioRecorder.stopMs

                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        currentAudioFile?.let { wavFile ->
                                            val finalResult = voskEngineManager.getFinalResult()
                                            val endFinalTime = System.currentTimeMillis()

                                            val analysis = audioRecorder.analyzeAudioFile(wavFile)
                                            val audioDur = analysis.durationMs
                                            val processedText = postProcessingPipeline.applyPostProcessing(finalResult)

                                            val finalTime = endFinalTime

                                            val micStartup = if (firstPcmMs > 0 && micStartMs > 0) maxOf(0L, firstPcmMs - micStartMs) else 0L
                                            val speechDelay = if (speechOnsetMs > 0 && micStartMs > 0) maxOf(0L, speechOnsetMs - micStartMs) else 0L
                                            val partialLat = if (firstPartialMs > 0 && micStartMs > 0) maxOf(0L, firstPartialMs - micStartMs) else 0L
                                            val wordLat = if (firstWordMs > 0 && micStartMs > 0) maxOf(0L, firstWordMs - micStartMs) else 0L
                                            val totalRecMs = if (finalTime > 0 && micStartMs > 0) maxOf(0L, finalTime - micStartMs) else 0L
                                            val rtfVal = if (audioDur > 0) totalRecMs.toFloat() / audioDur.toFloat() else 0f

                                            val timestamps = DiagnosticTimestamps(
                                                micStartMs = micStartMs,
                                                firstPcmMs = firstPcmMs,
                                                speechOnsetMs = speechOnsetMs,
                                                firstPartialMs = firstPartialMs,
                                                firstWordMs = firstWordMs,
                                                finalMs = finalTime,
                                                stopMs = stopMs
                                            )

                                            val metrics = RecognitionMetrics(
                                                timestamps = timestamps,
                                                micStartupMs = micStartup,
                                                speechOnsetDelayMs = speechDelay,
                                                firstPartialLatencyMs = partialLat,
                                                firstWordLatencyMs = wordLat,
                                                totalRecognitionTimeMs = totalRecMs,
                                                audioDurationMs = audioDur,
                                                rtf = rtfVal,
                                                snrDb = analysis.snrDb
                                            )

                                            val item = RecordingItem(
                                                id = System.currentTimeMillis().toString(),
                                                timestamp = System.currentTimeMillis(),
                                                audioPath = wavFile.absolutePath,
                                                rawTranscript = finalResult,
                                                correctedText = processedText,
                                                metrics = metrics,
                                                modelName = voskEngineManager.activeModelName
                                            )

                                            repository.saveRecording(item)

                                            withContext(Dispatchers.Main) {
                                                finalMs = finalTime
                                                currentRawTranscript = finalResult
                                                currentProcessedTranscript = processedText
                                                audioDurationMs = audioDur
                                                estimatedSnrDb = analysis.snrDb
                                                heapUsageMb = PerformanceProfiler.getMemoryStats().usedMemoryMb
                                                isProcessing = false
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isProcessing) "Przetwarzanie..." else if (isRecording) "Zatrzymaj nagrywanie" else "Rozpocznij nagrywanie")
                    }

                    if (isRecording) {
                        Text(
                            text = "Głośność: %.1f dB".format(currentRmsDb),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Rozpoznany tekst (Surowy Vosk - RAW):", style = MaterialTheme.typography.labelMedium)
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
                    text = "Wyniki diagnostyczne (Timestampy)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                val micStartup = if (firstPcmMs > 0 && micStartMs > 0) "${firstPcmMs - micStartMs} ms" else "UNKNOWN"
                val speechDelay = if (speechOnsetMs > 0 && micStartMs > 0) "${speechOnsetMs - micStartMs} ms" else "UNKNOWN"
                val partialLat = if (firstPartialMs > 0 && micStartMs > 0) "${firstPartialMs - micStartMs} ms" else "UNKNOWN"
                val wordLat = if (firstWordMs > 0 && micStartMs > 0) "${firstWordMs - micStartMs} ms" else "UNKNOWN"
                val totalMs = if (finalMs > 0 && micStartMs > 0) "${finalMs - micStartMs} ms" else "UNKNOWN"

                Text(text = "Start mikrofonu (MIC_START -> FIRST_PCM): $micStartup")
                Text(text = "Początek mowy (SPEECH_ONSET): $speechDelay")
                Text(text = "Pierwszy partial Vosk: $partialLat")
                Text(text = "Pierwsze pełne słowo: $wordLat")
                Text(text = "Całkowity czas transkrypcji: $totalMs")
                Text(text = "Długość nagrania: ${audioDurationMs} ms")
                Text(text = "Estymowany SNR (heurystyka): %.1f dB".format(estimatedSnrDb))

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
