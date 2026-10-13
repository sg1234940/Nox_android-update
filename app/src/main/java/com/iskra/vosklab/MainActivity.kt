package com.iskra.vosklab

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.iskra.vosklab.data.RecordingRepository
import com.iskra.vosklab.personalization.PostProcessingPipeline
import com.iskra.vosklab.ui.*
import com.iskra.vosklab.vosk.ModelManager
import com.iskra.vosklab.vosk.VoskEngineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var repository: RecordingRepository
    private lateinit var modelManager: ModelManager
    private lateinit var voskEngineManager: VoskEngineManager
    private lateinit var postProcessingPipeline: PostProcessingPipeline

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback<Boolean> { isGranted ->
            if (!isGranted) {
                Toast.makeText(this@MainActivity, "Wymagane uprawnienie do mikrofonu", Toast.LENGTH_LONG).show()
            }
        }
    )

    private val getZipLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
        ActivityResultCallback<Uri?> { uri ->
            handleZipUriResult(uri)
        }
    )

    private fun handleZipUriResult(uri: Uri?) {
        uri?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                val success = modelManager.importModelFromZip(it)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@MainActivity, "Zaimportowano nowy model Vosk", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Błąd lub nieprawidłowa struktura modelu Vosk w ZIP", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = RecordingRepository(applicationContext)
        modelManager = ModelManager(applicationContext)
        voskEngineManager = VoskEngineManager(applicationContext)
        postProcessingPipeline = PostProcessingPipeline(applicationContext)

        checkPermissions()

        lifecycleScope.launch(Dispatchers.IO) {
            modelManager.copyDefaultModelFromAssetsIfNeeded()
            val defaultModelDir = modelManager.getModelDirectory("vosk-model-small-pl-0.22")
            if (defaultModelDir != null && defaultModelDir.exists()) {
                voskEngineManager.loadModel(defaultModelDir.absolutePath)
            }
        }

        setContent {
            MaterialTheme {
                MainNavigationScreen(
                    repository = repository,
                    modelManager = modelManager,
                    voskEngineManager = voskEngineManager,
                    postProcessingPipeline = postProcessingPipeline,
                    onRequestZipImport = { getZipLauncher.launch("application/zip") }
                )
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

enum class LabTab(val title: String) {
    LAB("Laboratorium"),
    HISTORY("Historia"),
    BENCHMARK("Benchmark"),
    PERSONALIZATION("Personalizacja"),
    MODELS("Modele"),
    EXPORT("Eksport")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationScreen(
    repository: RecordingRepository,
    modelManager: ModelManager,
    voskEngineManager: VoskEngineManager,
    postProcessingPipeline: PostProcessingPipeline,
    onRequestZipImport: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(LabTab.LAB) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Iskra VoskLab - ${selectedTab.title}") }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == LabTab.LAB,
                    onClick = { selectedTab = LabTab.LAB },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Lab") },
                    label = { Text("Lab") }
                )
                NavigationBarItem(
                    selected = selectedTab == LabTab.HISTORY,
                    onClick = { selectedTab = LabTab.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = "Historia") },
                    label = { Text("Historia") }
                )
                NavigationBarItem(
                    selected = selectedTab == LabTab.BENCHMARK,
                    onClick = { selectedTab = LabTab.BENCHMARK },
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Benchmark") },
                    label = { Text("Benchmark") }
                )
                NavigationBarItem(
                    selected = selectedTab == LabTab.PERSONALIZATION,
                    onClick = { selectedTab = LabTab.PERSONALIZATION },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Korekty") },
                    label = { Text("Korekty") }
                )
                NavigationBarItem(
                    selected = selectedTab == LabTab.MODELS,
                    onClick = { selectedTab = LabTab.MODELS },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Modele") },
                    label = { Text("Modele") }
                )
                NavigationBarItem(
                    selected = selectedTab == LabTab.EXPORT,
                    onClick = { selectedTab = LabTab.EXPORT },
                    icon = { Icon(Icons.Default.Share, contentDescription = "Eksport") },
                    label = { Text("Eksport") }
                )
            }
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                LabTab.LAB -> LabScreen(repository, voskEngineManager, postProcessingPipeline)
                LabTab.HISTORY -> HistoryScreen(repository)
                LabTab.BENCHMARK -> BenchmarkScreen(repository, modelManager, voskEngineManager)
                LabTab.PERSONALIZATION -> PersonalizationScreen(postProcessingPipeline)
                LabTab.MODELS -> ModelManagerScreen(modelManager, voskEngineManager, onRequestZipImport)
                LabTab.EXPORT -> ExportScreen(repository, voskEngineManager.context.filesDir)
            }
        }
    }
}
