package com.iskra.vosklab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iskra.vosklab.vosk.ModelManager
import com.iskra.vosklab.vosk.VoskEngineManager
import java.io.File

@Composable
fun ModelManagerScreen(
    modelManager: ModelManager,
    voskEngineManager: VoskEngineManager,
    onRequestZipImport: () -> Unit
) {
    var installedModels by remember { mutableStateOf(modelManager.listInstalledModels()) }
    val activeModelName = voskEngineManager.activeModelName.collectAsState().value

    fun refresh() {
        installedModels = modelManager.listInstalledModels()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Zarządzanie modelami Vosk", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Import nowego modelu (paczka ZIP)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRequestZipImport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Wybierz plik ZIP z modelem")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Zainstalowane modele:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (installedModels.isEmpty()) {
            Text("Brak zainstalowanych modeli.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(installedModels) { model ->
                    val isActive = model.name == activeModelName
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = model.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Rozmiar: %.1f MB".format(model.sizeMb),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (isActive) {
                                    Text("AKTYWNY", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelLarge)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (!isActive) {
                                    Button(onClick = {
                                        voskEngineManager.loadModel(File(model.path))
                                        refresh()
                                    }) {
                                        Text("Załaduj")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(onClick = {
                                        modelManager.deleteModel(model)
                                        refresh()
                                    }) {
                                        Text("Usuń")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
