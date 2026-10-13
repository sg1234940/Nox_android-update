package com.iskra.vosklab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iskra.vosklab.personalization.DictionaryReplacement
import com.iskra.vosklab.personalization.PostProcessingPipeline
import com.iskra.vosklab.personalization.ProperNounRule

@Composable
fun PersonalizationScreen(
    postProcessingPipeline: PostProcessingPipeline
) {
    var config by remember { mutableStateOf(postProcessingPipeline.config) }

    var newTrigger by remember { mutableStateOf("") }
    var newReplacement by remember { mutableStateOf("") }

    var newNoun by remember { mutableStateOf("") }
    var newCapitalized by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    fun updateConfigAndSave(newConfig: com.iskra.vosklab.personalization.PersonalizationConfig) {
        config = newConfig
        postProcessingPipeline.config = newConfig
        postProcessingPipeline.saveToStorage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Personalizacja rozpoznawania mowy",
            style = MaterialTheme.typography.titleLarge
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mechanizm A: Post-Processing i Reguły Tekstowe",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lokalna korekcja transkrypcji w czasie rzeczywistym bez modyfikacji wag modelu Vosk (post-processing).",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Włącz Mechanizm A")
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = {
                            updateConfigAndSave(config.copy(enabled = it))
                        }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Słownik zamienników (Substytucje)",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                config.customDictionary.forEach { rule ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${rule.trigger} -> ${rule.replacement}")
                        TextButton(onClick = {
                            val updatedList = config.customDictionary - rule
                            updateConfigAndSave(config.copy(customDictionary = updatedList))
                        }) {
                            Text("Usuń")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newTrigger,
                    onValueChange = { newTrigger = it },
                    label = { Text("Błędnie rozpoznane słowo (trigger)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = newReplacement,
                    onValueChange = { newReplacement = it },
                    label = { Text("Poprawna forma (replacement)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (newTrigger.isNotBlank() && newReplacement.isNotBlank()) {
                            val newList = config.customDictionary + DictionaryReplacement(newTrigger.trim(), newReplacement.trim())
                            updateConfigAndSave(config.copy(customDictionary = newList))
                            newTrigger = ""
                            newReplacement = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dodaj do słownika")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Nazwy własne (Kapitalizacja)",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                config.properNouns.forEach { noun ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${noun.word} -> ${noun.capitalizedWord}")
                        TextButton(onClick = {
                            val updatedList = config.properNouns - noun
                            updateConfigAndSave(config.copy(properNouns = updatedList))
                        }) {
                            Text("Usuń")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newNoun,
                    onValueChange = { newNoun = it },
                    label = { Text("Słowo małymi literami") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = newCapitalized,
                    onValueChange = { newCapitalized = it },
                    label = { Text("Właściwa nazwa z wielkiej litery") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (newNoun.isNotBlank() && newCapitalized.isNotBlank()) {
                            val newList = config.properNouns + ProperNounRule(newNoun.trim(), newCapitalized.trim())
                            updateConfigAndSave(config.copy(properNouns = newList))
                            newNoun = ""
                            newCapitalized = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dodaj nazwę własną")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mechanizm B: Przyszłe dostrajanie (Acoustic Adaptation)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Prawdziwe dostrajanie acoustic/language model Vosk wymaga zewnętrznego procesu fine-tuning (np. Kaldi/PyTorch).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aplikacja gromadzi nagrania audio oraz ich dokładnie sprawdzoną transkrypcję referencyjną i umożliwia wyeksportowanie gotowej paczki treningowej w zakładce Eksport.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
