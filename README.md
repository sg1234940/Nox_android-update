# Iskra VoskLab

**Iskra VoskLab** to osobna aplikacja laboratoryjna przeznaczona do diagnozowania, testowania, benchmarkingowania i personalizacji rozpoznawania mowy (STT) przez silnik **Vosk** dla polskiego asystenta głosowego **Iskra**.

---

## 🚀 Główne funkcjonalności

1. **Laboratorium nagrywania i STT (`LabScreen`)**
   - Bezpośrednie nagrywanie z mikrofonu w formacie PCM 16kHz 16-bit Mono WAV.
   - Rozpoznawanie mowy w czasie rzeczywistym (streaming) oraz wsadowym (batch).
   - Analiza opóźnienia startu nasłuchiwania (latency ms) oraz całkowitego czasu rozpoznawania.
   - Wskaźnik poziomu audio (dB), szacowanie stosunku sygnał/szum (SNR) oraz detekcja obcięcia pierwszych słów przez VAD.
   - Śledzenie zużycia pamięci RAM (Heap MB).

2. **Historia i Korekta (`HistoryScreen`)**
   - Przeglądanie zapisanych nagrań i ich odsłuchiwanie.
   - Edycja i ręczna korekta błędnie rozpoznanych słów.
   - Automatyczne wyliczanie wskaźników **WER (Word Error Rate)** oraz **CER (Character Error Rate)** na podstawie zaktualizowanego tekstu referencyjnego.

3. **Benchmark i Porównywanie Modeli (`BenchmarkScreen`)**
   - Przetwarzanie posiadanych nagrań testowych przez wiele zainstalowanych modeli Vosk.
   - Porównywanie wyników transkrypcji, czasu przetwarzania oraz wskaźników WER/CER na tym samym zbiorze próbek audio.

4. **Personalizacja (`PersonalizationScreen`)**
   - **Mechanizm A (Post-processing bez modyfikacji wag)**:
     - Słownik zamienników (np. `wloncz` -> `włącz`).
     - Reguły kapitalizacji nazw własnych (np. `iskra` -> `Iskra`).
     - Dopasowywanie intencji z gramatyki kontekstowej.
   - **Mechanizm B (Dostrajanie akustyczne)**:
     - Zbieranie dokładnych próbek mowy i przygotowanie ich do eksportu.

5. **Zarządzanie Modelami (`ModelManagerScreen`)**
   - Import modeli Vosk z zewnętrznych plików ZIP.
   - Przełączanie aktywnego modelu w locie.

6. **Eksport Danych (`ExportScreen`)**
   - Generowanie paczki `.zip` zawierającej nagrania WAV, plik manifestu JSON (`dataset_manifest.json`) oraz strukturę dla narzędzia Kaldi (`text`, `wav.scp`, `utt2spk`).

---

## 🛠 Jak zbudować aplikację (Instrukcja budowania APK)

### Wymagania:
- JDK 17 lub wyższy
- Android SDK 36 (Android 16 / Android 14+ / Galaxy S24+)
- Gradle 8.8 (załączony wrapper `./gradlew`)

### Komendy budowania:

1. **Uruchomienie testów jednostkowych:**
   ```bash
   ./gradlew test
   ```

2. **Kompilacja APK w wersji Debug:**
   ```bash
   ./gradlew assembleDebug
   ```
   Wygenerowany plik APK znajdziesz w ścieżce:
   `app/build/outputs/apk/debug/app-debug.apk`

3. **Kompilacja APK w wersji Release:**
   ```bash
   ./gradlew assembleRelease
   ```

---

## 🔐 Bezpieczeństwo i Prywatność
Wszystkie nagrania oraz przetworzone dane przechowywane są **wyłącznie lokalnie** w pamięci aplikacji i nie są przesyłane do chmury, chyba że użytkownik jawnie wyeksportuje paczkę ZIP.
