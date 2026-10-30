# ISKRA MOUTH V3 — naprawa i wyniki, 30.10.2026

Baza: `c9873e0e40b2d24769b1515300953fd25314980e`.
Naprawa na osobnej gałęzi `work/iskra-mouth-v3-repair`. Bez merge, deployu, APK,
zmian Androida, Rendera i bez nadpisania jakiegokolwiek źródłowego modelu.

## Rzeczywiste wyniki

| Sprawdzenie | Wynik | Dowód |
|---|---|---|
| Czysty Python: plan, topologia, indeksy/tożsamość, geometria, negatywne przypadki | **52/52** | TEST, `validation/python-tests.txt` |
| Blender 4.3.2: transfer UV/wag/21 keys, kolizje, sprzątanie, mutant zabezpieczenia, GLB round-trip | **10/10** | RUNTIME, `validation/blender-tests.txt` |
| Mutacje pojedynczych instrukcji ochronnych | **5 w Pythonie + 1 w Blenderze wykryte** | Testy badają konkretny etap, nie dowolny końcowy abort |
| Syntetyczny GLB po eksporcie i imporcie | **21 nazwanych targets, 7 klipów, skin i 2 warstwy UV** | RUNTIME; sprawdzono też różne końcowe obroty Head w danych 7 klipów |
| Legacy generator 21 targets na kopii rzeczywistego modelu | **47/47** istniejących testów | TEST; wynik nie jest nowym modelem ust V3 |
| Legacy 21 targets po imporcie do Blendera | **Basis + 21 keys, 7 akcji, Head, UVMap, SUPPORTED** | RUNTIME, `validation/legacy-blender-results.json` |
| Oryginalny binary prefix, JSON 7 animacji, skin, materiały i obrazy w legacy wyniku | **bez zmian** | TEST, `validation/legacy-binary-preservation.json` |
| Rzeczywisty model po imporcie | **53 004 vertices, 95 628 polygons, Head, 7 akcji, UVMap** | RUNTIME, `validation/blender-results.json` |
| Brak planu anatomicznego na rzeczywistym modelu | **BLOCKED, źródło nienaruszone** | RUNTIME |

SHA-256 oryginalnego `base_basic_pbr.glb`:
`beb034a9f13df1f05d8acaeb21f723161bf1b93e43ab23fe31dc634f30bac294`.

Fingerprint geometrii zaimportowanej przez Blender 4.3.2:
`8ebc0a7e13fe23e098175212915b7cea3aad0d85fe02ad4cf4bf82869745b6ca`.

Oryginalny GLB nie zawiera morph targets. Test legacy utworzył 21 targets w osobnym
pliku roboczym, bez zmiany źródła; jego SHA jest zapisane w raporcie binarnym.

## Zamknięcie ustaleń audytu

| Ustalenie | Naprawa i potwierdzenie |
|---|---|
| Nieaktualny BMesh lookup | Stary błąd potwierdzony w Blenderze: `IndexError: outdated internal index table`. Nowa ścieżka nie używa BMesh delete/new ani indeksów zmienianej sekwencji; rekonstruuje geometrię przez jawne source IDs. Test wykrywa zamianę tożsamości przy tej samej liczbie wierzchołków. |
| Winding | Kierunek pętli musi odpowiadać usuwanym ścianom; odwrócenie kończy się konkretnym BLOCKED. Edge/vertex-manifoldness i szwy sprawdzane po przebudowie. |
| Kierunek i przecięcia | Oś musi wskazywać do wnętrza względem normalnych usuwanej łatki. Kolizja z tyłem cienkiej obudowy wykryta w prawdziwym BVH; usunięcie warunku ochronnego zmienia wynik i jest wykrywane testem. |
| Zły obszar usunięcia | Jawny zatwierdzony region, dokładny chroniony dopełniający zbiór wierzchołków, spójny dysk i limit 10% powierzchni. Wszystkie zachowane ściany porównywane po source IDs i windingu. To nie zastępuje anatomicznego wyznaczenia ust. |
| Wire/nonmanifold | W starym BMesh potwierdzono 6 pozostawionych szprych. Rekonstrukcja nie tworzy wire edges, usuwa nieużywane wnętrze przez mapowanie, zachowuje istniejące dopuszczalne brzegi/komponenty. Sprawdzany link wierzchołka, nie tylko liczba ścian na krawędzi. |
| Uszkodzony plan | Typy i liczby sprawdzane przed set/float. Nested lists, huge int, null, NaN, zły SHA, duplikaty, brak danych kończą się BLOCKED. CLI zawsze wystawia report-only, nie zapisuje modelu. |
| UV/wagi/shape keys | Per-corner UV i materiały zachowane poza cięciem; jawne strip/disk UV i oddzielny materiał wnętrza. Wagi i deltas przekazywane przez źródłową proweniencję. Wszystkie 21 nazw i kolejność pozostają; prosta akcja shape-key zostaje zachowana. |
| Niezgodne schematy | Jeden współdzielony `/3` i jedna kanonizacja, również int/float/-0. Stare plany nie dostają automatycznej zgody. |
| Czerwony test fixture | Zastąpiony rzeczywistą zamkniętą siatką z pierścieniem i 52 testami, w tym geometrią i mutacjami. |
| Wycieki | Test wymusza awarię już po tworzeniu siatki/materiału i porównuje obiekty, siatki, keys, materiały przed/po. Brak wycieku; także normalne dispose czyści własne dane. |

Dodatkowe odkrycie w rzeczywistym imporcie: Blender tworzy pomocniczą Icosphere dla
wyświetlania kości. Stare `len(all_meshes)==1` blokowało prawdziwy model. Analyzer
oraz CLI wybierają skinned mesh (lub dokładną zatwierdzoną nazwę), nie tę pomocniczą
siatkę. Nie czyszczą sceny użytkownika przez `read_factory_settings()`.

## Czego wyniki nie dowodzą

To jest naprawiony i przetestowany **builder kandydata**, nie gotowy nowy awatar.
Nie ma w repo zatwierdzonego planu obrysu ust rzeczywistej twarzy. Analyzer zapisuje
uczciwy draft z pustymi listami i `anatomy_reviewed:false`; nie udaje ust na podstawie
koloru, PCA, globalnych brzegów ani nazwy kości. Rzeczywista twarz nie została wycięta.

Prosty algorytm jamy świadomie odmawia dla obrysów mocno niepłaskich,
niestaro-kształtnych, samoprzecinających się lub zbyt dużych. Nie obsługuje migracji
sterowników/NLA/absolutnych lub maskowanych shape keys, nietypowych atrybutów,
wielu armatur i innych modyfikatorów — blokuje przed zmianą, nie usuwa danych.
Przy nowych wagach skinned mesh wymaga znormalizowanej sumy i najwyżej 8 wpływów;
nie obcina wpływów po cichu. Polityka eksportu dla Filament wymaga osobnego dowodu.

Sprawdzono Basis, pojedyncze istniejące targets i bieżącą mieszankę. Nie jest to
matematyczny dowód dla każdej możliwej ciągłej kombinacji morphów ani ocena wizualna.
Weryfikacja syntetycznych 7 klipów i zachowanie oryginalnych danych legacy nie są
odtworzeniem 7 animacji na gotowej nowej twarzy Iskry.

Pełnego kodu Work GLB V2.3 nie ma w tym katalogu. Audyt tego walidatora nie jest jego
implementacją. Mapa source IDs dotyczy siatki Blendera **przed eksportem**, więc nie
jest fałszywie oznaczona jako manifest GLB. Eksporter produkcyjny, prawidłowa mapa po
eksporcie, wizualna kontrola ust i test Android/Filament pozostają osobnym etapem.
Żeby zachować mimikę, walidator musi akceptować kontrakt 21 targets, nie wymuszać
ich usunięcia do dokładnie 12.

## Werdykt dla naprawy

**Naprawy podstawy potwierdzone w powyższym zakresie TEST/RUNTIME.** Można poddać
review tę gałąź i wyznaczyć anatomiczny plan na rzeczywistym modelu. Nadal **NO GO
jako gotowy GLB do Androida** bez tego planu, docelowego eksportu i oceny wizualnej.
