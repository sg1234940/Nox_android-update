# Autonomiczne Myśli Iskry (Życie Pomiędzy Rozmowami)

Dokument koncepcyjny i architektoniczny mechanizmu tła dla osobistej asystentki AI **Iskra**.

---

## 1. Wizja i Cel

Rozmowa z użytkownikiem to tylko jeden z elementów relacji z Iskrą. Aby asystentka nie była jedynie pasywnym modelem odpowiadającym na zapytania (*request-response*), potrzebuje **przestrzeni na refleksję pomiędzy rozmowami**.

„Życie pomiędzy rozmowami” to proces tła, w którym Iskra:
- Porządkuje niedawne rozmowy z pamięci roboczej (RAM).
- Łączy nowe fakty z wcześniejszymi wspomnieniami w mapie skojarzeń (Pamięć Długa).
- Formułuje hipotezy, pytania oraz własne inicjatywy na przyszłe spotkania.
- Tworzy wpisy myśli (*Thought Records*), które trafiają do nienaruszalnego, wyliczane skrótem (SHA-256) archiwum.

---

## 2. Zasady Przewodnie

1. **Refleksja to nie samowola w świecie:** Myśl tła może generować wnioski i przemyślenia oraz przygotowywać propozycje akcji (*Draft Actions*), ale **nigdy nie wykonuje akcji zewnętrznych** (pobieranie z sieci, modyfikacja plików, wysyłanie wiadomości) bez jednoznacznej zgody użytkownika.
2. **Nienaruszalność zapisu:** Każdy rekord myśli otrzymuje unikalny hash wyliczony z jego treści i znacznika czasu. Zapis jest dopisywany do archiwum (*Append-Only*) – żaden proces nie może zmienić minionych myśli po cichu.
3. **Prywatność i Oszczędność Zasobów:** Procesy tła działają lokalnie na telefonie lub bezpiecznym węźle i respektują stan baterii oraz temperaturę urządzenia. Logi techniczne zawierają wyłącznie identyfikatory i metryki, nigdy treść myśli.

---

## 3. Wyzwalacze Cyklu Myśli (Triggers)

Cykl refleksji nie działa w sposób ciągły (co oszczędza baterię i moc obliczeniową). Jest uruchamiany przez trzy typy zdarzeń:

1. **Wyzwalacz Po-rozmowny (Post-Conversation Cooldown):**
   - *Kiedy:* N minut (np. 3–5 min) po zakończeniu sesji głosowej lub tekstowej.
   - *Cel:* Przetworzenie „na gorąco” pamięci roboczej RAM, dopóki kontekst jest kompletny.
2. **Wyzwalacz Bezczynności i Ładowania (Idle & Charging Daemon):**
   - *Kiedy:* W cyklach nocnych lub gdy telefon jest podłączony do ładowarki i połączony z Wi-Fi.
   - *Cel:* Głębokie przeszukiwanie archiwum, defragmentacja mapy skojarzeń i konsolidacja pamięci długotrwałej.
3. **Wyzwalacz Dryfu Kontekstu (Context Drift Threshold):**
   - *Kiedy:* Liczba nieprzetworzonych wątków lub zanotowanych faktów w pamięci roboczej przekroczy zdefiniowany próg.

---

## 4. Pętla Refleksji (4 Etapy)

```
[Pamięć Robocza RAM] ───► (1. Ekstrakcja) ───► (2. Kojarzenie) ───► (3. Hipoteza) ───► (4. Zapis Hash) ───► [Archiwum & Mapa]
```

### Etap 1: Ekstrakcja i Synteza (Working Memory Harvesting)
- Analiza wpisów z ostatniej sesji w pamięci roboczej.
- Wyodrębnienie kluczowych informacji: nowe fakty, wyrażone emocje, otwarte pytania, intencje użytkownika.

### Etap 2: Kojarzenie (Associative Memory Retrieval)
- Przeszukiwanie mapy skojarzeń w celu odnalezienia powiązanych wątków z przeszłości.
- Przykład: Użytkownik wspomniał o "wyjeździe do Krakowa" ──► Iskra odnajduje wpis sprzed miesiąca o "ulubionej księgarni w Krakowie".

### Etap 3: Generowanie Hipotez i Inicjatyw (Insight & Initiative Generation)
- Tworzenie struktury **Thought Record**:
  - **Typ:** *Obserwacja* | *Korelacja* | *Hipoteza* | *Propozycja Inicjatywy*.
  - **Treść:** Przemyślenie Iskry.
  - **Sugerowany temat do rozmowy:** Opcjonalne pytanie/wątek, który Iskra może poruszyć przy następnym nawiązaniu kontaktu przez użytkownika.

### Etap 4: Zapis i Nienaruszalna Archiwizacja (Immutable Archiving)
- Wyliczenie skrótu SHA-256 z treści rekordu myśli i znacznika czasu UTC.
- Zapis do pliku w archiwum: `archive/thoughts/YYYY-MM/hash_thought.json`.
- Aktualizacja grafu/mapy skojarzeń o nowy węzeł i krawędzie.

---

## 5. Bateria i Zarządzanie Zasobami na Androidzie

Na urządzeniu Android procesy tła są zarządzane przez systemowy **WorkManager**:

- **Harmonogram:** `PeriodicWorkRequest` / `OneTimeWorkRequest`.
- **Wymogi systemowe (Constraints):**
  - `setRequiresCharging(true)` dla głębokiej konsolidacji nocnej.
  - `setRequiresDeviceIdle(true)` aby nie spowalniać interakcji z telefonem.
  - `setRequiredNetworkType(NetworkType.UNMETERED)` jeśli konsolidacja wymaga synchronicznej weryfikacji skrótów.

---

## 6. Przykładowy Rekord Myśli (Thought Record Schema)

```json
{
  "thought_id": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "timestamp_utc": "2026-03-30T22:15:00Z",
  "trigger": "POST_CONVERSATION_COOLDOWN",
  "source_conversation_hashes": [
    "a1b2c3d4e5f6..."
  ],
  "category": "HYPOTHESIS",
  "content": "Użytkownik wspomniał o zmęczeniu projektem Nox. Z wcześniejszych rozmów wynika, że przy poprzednich kamieniach milowych pomagała krótka przerwa lub zmiana obszaru zadań.",
  "associated_nodes": [
    "node_project_nox",
    "node_wellbeing"
  ],
  "initiative_for_next_conversation": {
    "suggested_prompt": "Zapytać przyjaznym tonem o samopoczucie i postępy w Nox, bez nacisku na terminy.",
    "priority": "MEDIUM"
  }
}
```

---

## 7. Podsumowanie

Dzięki autonomicznym myślom Iskra zyskuje ciągłość intencjonalną. Nie „budzi się” z czystą kartą przy każdym naciśnięciu przycisku, lecz wnosi do rozmowy przemyślany kontekst, łącząc przeszłe doświadczenia w spójną całość.
