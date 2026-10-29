# Zadanie dla Lumena — mimika Iskry

Źródło: `base_basic_pbr.glb` z repozytorium `sg1234940/Nox_android-update`. Nie zakładaj, że `iskra_avatar_facial_rig.glb` istnieje — to oczekiwany wynik.

## Materiały
- `add_iskra_control_rings.py`: pomocnicze kontrolery Blendera, nie implementacja morph targets.
- `test_iskra_facial_rig.py`: test strukturalny, nie wizualny.
- `ANDROID_INTEGRATION.md`: projekt integracji Android, nie gotowy runtime.

## Wymagane wykonanie
1. Odczytaj rzeczywistą geometrię oryginalnego GLB, układ osi, siatki, kości, animacje i UV.
2. Przygotuj generator 21 nazwanych morph targets zgodnych z dokumentacją, bez zgadywania regionów twarzy.
3. Zapisz **rzeczywisty plik** `iskra_avatar_facial_rig.glb`, nie tylko skrypt ani log.
4. Zweryfikuj deformacje wizualnie, integralność GLB, tekstury i animacje ciała; pokaż obrazy oraz logi testów.
5. Zgłoś SHA-256 wynikowego pliku, ścieżkę w repo i wszystkie ograniczenia.
6. Nie modyfikuj aplikacji Android, nie wykonuj merge/deploy/release bez osobnej zgody.

**Uwaga:** kod generatora 21 deformacji wspominany w rozmowie nie został dostarczony w pełnej postaci w bieżącym materiale. Nie oznaczaj go jako załączonego ani działającego. Test 47/47 PASS pochodzi z raportu, nie z niezależnego uruchomienia na dostępnym GLB.
