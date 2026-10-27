# Iskra — Strategia Modelu Awatara 3D i Wyglądu Holograficznego

## Nowe Podejście (Pivoted Strategy)

Zgodnie z nową decyzją architektoniczną dotyczącą wizualnej reprezentacji Iskry, proces budowy awatara 3D został ustrukturyzowany w dwie następujące po sobie fazy:

---

### Faza 1: Baza Anatomiczna 3D (Szkielet, Rigging i Mimika)
1. **Model Bazowy Kobiety 3D:**
   - Pozyskanie / zaimportowanie kompletnej geometrii postaci kobiecej 3D.
   - Wsparcie dla standardowych formatów wymiany 3D (np. glTF / GLB / FBX).
2. **Szkielet i Animacja Ciała (Rigging):**
   - Pełna struktura kości (postura, głowa, dłonie, ramiona) dla płynnych ruchów i gestykulacji.
3. **Mimika Twarzy i Lip-Sync (Blendshapes / Morph Targets):**
   - Dedykowane blendshape'y twarzy dla ekspresji emocjonalnej (np. uśmiech, neutralny, skupienie).
   - Zestaw wizamów (visemes) dla precyzyjnej synchronizacji ust z syntezą i rozpoznawaniem mowy (Vosk).

---

### Faza 2: Stylizacja Holograficzna (Shader & Visual Effects)
Po zapewnieniu poprawnej geometrii, riggingu i animacji twarzy, na model nakładany jest holograficzny wygląd zgodny z pierwszą zatwierdzoną grafiką koncepcyjną:
1. **Holograficzny Shader i Materiał:**
   - Kolorystyka i estetyka zgodna z zatwierdzoną pierwszą grafiką (tonacja cyjan / błękit / neon).
   - Przezroczystość, świecące krawędzie (rim lighting).
   - Poziome linie skanujące (scanlines) oraz efekt lekkiego migotania/glitchu hologramu.
2. **Integracja z Silnikiem Renderującym:**
   - Dostosowanie parametrów shadera do ograniczeń wydajnościowych urządzeń mobilnych Android.

---

## Podsumowanie Zasad
- **Kolejność prac:** Najpierw prawdziwy, w pełni zrigowany model kobiety 3D ze szkieletem i mimiką twarzy → Dopiero potem nakładanie estetyki holograficznej według zatwierdzonej grafiki.
