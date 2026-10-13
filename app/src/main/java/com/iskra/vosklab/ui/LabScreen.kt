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
import com.iskra.vosklab.metrics.RecordingSessionState
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

    var activeSessionState by remember { mutableStateOf<RecordingSessionState?>(null) }
    var displayTimestamps by remember { mutableStateOf(DiagnosticTimestamps()) }
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

                                val sessionState = RecordingSessionState()
                                activeSessionState = sessionState
                                displayTimestamps = DiagnosticTimestamps()

                                audioRecorder.startRecording(
                                    outputFile = file,
                                    sessionState = sessionState,
                                    listener = object : AudioRecorder.AudioFrameListener {
                                        override fun onAudioFrame(buffer: ShortArray, readSize: Int, frameRmsDb: Float, timestampMs: Long) {
                                            val acceptStart = System.currentTimeMillis()
                                            val partial = voskEngineManager.acceptWaveform(buffer, readSize)
                                            val acceptDuration = System.currentTimeMillis() - acceptStart
                                            sessionState.addVoskProcessingTime(acceptDuration)

                                            if (partial.isNotBlank()) {
                                                sessionState.recordFirstPartial(timestampMs)
                                                if (partial.trim().isNotEmpty()) {
                                                    sessionState.recordFirstWord(timestampMs)
                                                }
                                            }

                                            mainHandler.post {
                                                currentRmsDb = frameRmsDb
                                                displayTimestamps = sessionState.getDiagnosticTimestamps()
                                                if (partial.isNotBlank()) {
                                                    currentRawTranscript = partial
                                                    currentProcessedTranscript = postProcessingPipeline.applyPostProcessing(partial)
                                                }
                                            }
                                        }
                                    }
                                )

                                isRecording = true
                            } else {
                                isProcessing = true
                                val sessionState = activeSessionState ?: RecordingSessionState()
                                audioRecorder.stopRecording()
                                isRecording = false

                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        currentAudioFile?.let { wavFile ->
                                            val finalStart = System.currentTimeMillis()
                                            val finalResult = voskEngineManager.getFinalResult()
                                            val endFinalTime = System.currentTimeMillis()
                                            sessionState.addVoskProcessingTime(endFinalTime - finalStart)
                                            sessionState.recordFinal(endFinalTime)

                                            val timestamps = sessionState.getDiagnosticTimestamps()
                                            val analysis = audioRecorder.analyzeAudioFile(wavFile)
                                            val audioDur = analysis.durationMs
                                            val processedText = postProcessingPipeline.applyPostProcessing(finalResult)

                                            val micStartup = if (timestamps.firstPcmMs > 0 && timestamps.micStartMs > 0) maxOf(0L, timestamps.firstPcmMs - timestamps.micStartMs) else 0L
                                            val speechDelay = if (timestamps.speechOnsetMs > 0 && timestamps.micStartMs > 0) maxOf(0L, timestamps.speechOnsetMs - timestamps.micStartMs) else 0L
                                            val partialLat = if (timestamps.firstPartialMs > 0 && timestamps.micStartMs > 0) maxOf(0L, timestamps.firstPartialMs - timestamps.micStartMs) else 0L
                                            val wordLat = if (timestamps.firstWordMs > 0 && timestamps.micStartMs > 0) maxOf(0L, timestamps.firstWordMs - timestamps.micStartMs) else 0L
                                            val totalRecMs = if (timestamps.finalMs > 0 && timestamps.micStartMs > 0) maxOf(0L, timestamps.finalMs - timestamps.micStartMs) else 0L

                                            val voskCpuProcessingMs = sessionState.voskProcessingTimeMs
                                            val streamingRtf = if (audioDur > 0) voskCpuProcessingMs.toFloat() / audioDur.toFloat() else 0f

                                            val metrics = RecognitionMetrics(
                                                timestamps = timestamps,
                                                micStartupMs = micStartup,
                                                speechOnsetDelayMs = speechDelay,
                                                firstPartialLatencyMs = partialLat,
                                                firstWordLatencyMs = wordLat,
                                                totalRecognitionTimeMs = totalRecMs,
                                                audioDurationMs = audioDur,
                                                rtf = streamingRtf,
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
                                                displayTimestamps = timestamps
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

                val ts = displayTimestamps
                val micStartup = if (ts.firstPcmMs > 0 && ts.micStartMs > 0) "${ts.firstPcmMs - ts.micStartMs} ms" else "UNKNOWN"
                val speechDelay = if (ts.speechOnsetMs > 0 && ts.micStartMs > 0) "${ts.speechOnsetMs - ts.micStartMs} ms" else "UNKNOWN"
                val partialLat = if (ts.firstPartialMs > 0 && ts.micStartMs > 0) "${ts.firstPartialMs - ts.micStartMs} ms" else "UNKNOWN"
                val wordLat = if (ts.firstWordMs > 0 && ts.micStartMs > 0) "${ts.firstWordMs - ts.micStartMs} ms" else "UNKNOWN"
                val totalMs = if (ts.finalMs > 0 && ts.micStartMs > 0) "${ts.finalMs - ts.micStartMs} ms" else "UNKNOWN"

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
