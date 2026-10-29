# Integracja mimiki Iskry z Androidem

## Model
Umieść `iskra_avatar_facial_rig.glb` w `app/src/main/assets/models/` i ładuj ten plik zamiast `base_basic_pbr.glb`.
Model ma 21 morph targets zapisanych w tej kolejności:
`eyeBlinkLeft`, `eyeBlinkRight`, `eyesLeft`, `eyesRight`, `eyesUp`, `eyesDown`, `browInnerUp`, `browDownLeft`, `browDownRight`, `jawOpen`, `mouthSmile`, `mouthFrown`, `mouthFunnel`, `mouthPucker`, `viseme_MBP`, `viseme_FV`, `viseme_A`, `viseme_E`, `viseme_I`, `viseme_O`, `viseme_U`.

## Warstwa sterowania
Nie opieraj runtime na blenderowych ringach ani custom bone shapes. Aplikacja ustawia wagi morph targets w zakresie `0.0..1.0`.
Nazwy `CTRL_*` są grupami logicznymi:
- `CTRL_EYES`: mruganie i kierunek spojrzenia
- `CTRL_BROWS`: ruch brwi
- `CTRL_MOUTH`: uśmiech, smutek, funnel, pucker
- `CTRL_JAW`: otwarcie ust
- `CTRL_VISEMES`: kształty ust do mowy

Minimalna abstrakcja Kotlin:
```kotlin
interface MorphTargetBackend {
    fun setWeight(index: Int, value: Float)
}
class IskraFaceController(private val backend: MorphTargetBackend) {
    private val index = listOf(
        "eyeBlinkLeft", "eyeBlinkRight", "eyesLeft", "eyesRight",
        "eyesUp", "eyesDown", "browInnerUp", "browDownLeft",
        "browDownRight", "jawOpen", "mouthSmile", "mouthFrown",
        "mouthFunnel", "mouthPucker", "viseme_MBP", "viseme_FV",
        "viseme_A", "viseme_E", "viseme_I", "viseme_O", "viseme_U"
    ).withIndex().associate { it.value to it.index }
    fun set(name: String, value: Float) {
        backend.setWeight(index.getValue(name), value.coerceIn(0f, 1f))
    }
    fun neutral() = index.values.forEach { backend.setWeight(it, 0f) }
    fun blink(value: Float) {
        set("eyeBlinkLeft", value)
        set("eyeBlinkRight", value)
    }
    fun smile(value: Float) = set("mouthSmile", value)
}
```
`MorphTargetBackend` należy podłączyć do używanego w aplikacji API SceneView/Filament odpowiedzialnego za ustawienie wag morph targets renderable/instance. Konkretna nazwa metody zależy od wersji biblioteki obecnej w repozytorium aplikacji.

## Lip-sync
Dla każdego fragmentu mowy wyznacz aktywny viseme i płynnie interpoluj wagę przez 60–120 ms. Przed ustawieniem kolejnego viseme wyzeruj pozostałe wagi z grupy `CTRL_VISEMES`.

Proponowane mapowanie polskich fonemów:
- `m`, `b`, `p` -> `viseme_MBP`
- `f`, `w` -> `viseme_FV`
- `a` -> `viseme_A`
- `e`, `ę` -> `viseme_E`
- `i`, `y` -> `viseme_I`
- `o`, `ą` -> `viseme_O`
- `u`, `ó` -> `viseme_U`

Jeżeli TTS nie dostarcza znaczników fonemów, użyj energii audio do `jawOpen`, a tekstu i czasu wypowiedzi do przybliżonego harmonogramu visemów.
Nie steruj ustami wynikiem STT wejścia użytkownika, jeśli animowana ma być wypowiedź Iskry. Źródłem powinien być tekst/audio TTS Iskry.

## Mieszanie
- ciało i mimika mogą działać równolegle, ponieważ istniejące animacje ciała dotyczą kości, a mimika używa wag morph targets;
- stosuj wygładzanie `lerp(current, target, 1 - exp(-speed * dt))`;
- mruganie: szybkie wejście, wolniejsze wyjście;
- po zakończeniu mowy wyzeruj visemy i `jawOpen` w 100–180 ms.

## Test na urządzeniu
1. Załaduj GLB i uruchom każdą z 7 animacji ciała.
2. Podczas każdej animacji ustaw kolejno wszystkie 21 wag na `1.0`, potem `0.0`.
3. Sprawdź brak pęknięć siatki i zachowanie tekstur.
4. Odtwórz polską wypowiedź zawierającą `a e i o u m b p f w`.
5. Zapisz wynik testu na urządzeniu osobno od testu strukturalnego GLB.
