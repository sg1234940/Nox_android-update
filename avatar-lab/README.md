# Aktualizacja 0.6.2 — zweryfikowany kod w APK

Zgłoszenie z telefonu potwierdziło pasek mimo wersji 0.6.1. Weryfikacja DEX starego APK ujawniła, że manifest miał wersję 0.6.1, ale ekran nadal zawierał wcześniejsze sayStatus bez setVisibility i callback bezpośrednio ustawiający tekst. Wcześniejsze zapewnienie o poprawce w artefakcie było błędne; testy JVM i numer wersji nie wystarczyły.

W 0.6.2 wykonano clean obu modułów, kompilację bez cache i ponowne wykonanie wszystkich zadań. Nagłówek pokazuje wersję 0.6.2. Gotowy APK sprawdzono przez oficjalny dexdump: kod sayStatus przełącza widoczność, obsługa gotowego modelu nie wpisuje technicznego tekstu bezpośrednio, widoczny numer jest aktualny. Ten sam check_status_apk.py odrzuca stare 0.6.1 i przyjmuje nowe 0.6.2. Kontrola działa też w publicznym CI na compile-only APK bez GLB; ten pomocniczy APK nie jest publikowany jako pełny Lab.

Build pełnego hosta, 13/13 testów klienta/finality, podpis zgodny z 0.5/0.6 oraz zipalign 16 KiB PASS. APK 0.6.2-test / kod 8: 146070114 bajtów, SHA-256 `251af087510ec3c114b2b20d26ce2e1816c26b960e21ab5d5544b93cf1dfd24a`. Model GLB pozostaje niezmieniony. Wygląd po aktualizacji wymaga potwierdzenia na telefonie. Bez merge/deployu i zmian flag.

---

# Iskra Lab 0.6 — rozmowa, wygląd i podgląd

Samodzielna aplikacja `pl.iskra.avatar.test.v4`, wersja `0.6-test` (6), Android 8+ / ARM64 w pełnym wariancie. Nie zastępuje osobowości ani pamięci Iskry: korzysta z tej samej bramki backendu. Adres HTTPS i hasło wpisuje właściciel w ustawieniach; brak zaszytego adresu produkcji.

Lewe wysuwane menu: cała postać/twarz, kamera, rozmowa, ręczne sterowanie, podgląd, awatar, ubiór, styl zwykły/holograficzny, jasność, tło, głos/język i połączenie. Mikrofon jest po prawej; po wpisaniu tekstu zmienia się w Wyślij. Tryb głosu nie pokazuje chmurek, tekst ma półprzezroczyste chmurki i pole wpisywania. Klawiatura pozostaje systemowa.

Import awatara lub wariantu ubioru wymaga samowystarczalnego GLB zgodnego z kontraktem Iskry (21 morph targets i 7 animacji). To pełne warianty modelu, nie automatyczne przebieranie dowolnego modelu. Zdjęcie wybrane jako tło trafia także do podglądu dla Iskry. Model wbudowany pozostaje dokładnie tym z wersji 0.4/0.5.

## Dwa warianty budowania

Publiczny projekt `gradle -p avatar-lab` kompiluje renderer i klienta tekstowego. Nie zawiera prywatnego silnika głosu. Gdy adapter głosu jest nieobecny, interfejs mówi o tym wprost.

Pełna kompilacja do testu telefonu dołącza istniejący Promyk/Vosk/Piper przez interfejs `LabVoice` oraz moduł `:avatar-voice` z projektu głównego. Nie kopiujemy prywatnego kodu do publicznego repo. Głos wymaga importu rzeczywistych modeli przez menu Głos i język; domyślnym językiem jest polski. Wyjście głosowe odtwarza wyłącznie zatwierdzony plan finality/v2 z backendu, a nie dowolny tekst ze strumienia. Usta korzystają z energii PCM i zegara rzeczywistego odtwarzacza. Obsługa przerywania podczas mówienia wymaga działającego AEC; brak AEC jest jawny, potem dostępne jest zwykłe nagrywanie.

## Weryfikacja

Lokalnie na pełnej kompilacji z prawdziwym GLB: SDK 49/49, klient i finality 13/13, Promyk 26/26. Podpis APK 0.6 zgadza się z 0.5; model ma SHA-256 `451ebc87620b95500163716e77b7433d6ef7d765a2b33391a0df08f2b921848c`. Publiczne CI kompiluje UI i testuje wyłącznie testy niewymagające nieopublikowanego GLB; nie udaje testu modelu ani pełnego APK głosowego.

Działanie PixelCopy/Filament, wygląd, AEC, uprawnienia mikrofonu, import modeli oraz obrót i powrót z tła wymagają testu na telefonie. Backend musi obsługiwać avatar/phone-v1 i hasło; ta zmiana nie wdraża ani nie aktywuje serwera.

---

Poniżej historia wcześniejszych wersji, z dawnymi parametrami i ograniczeniami.

# Wersja 0.5 — sterowanie i podgląd dla Iskry

Aktualny kontrakt, budowanie i test telefonu: [CONTROL_V1.md](CONTROL_V1.md). Model pozostaje z poprawionej wersji 0.4. Poniżej historia wcześniejszych etapów.

# Zmiana 0.4 — lokalna mimika i stabilniejsze kąciki

Kandydat na bazie sprawdzonego na telefonie modelu 0.3. Uśmiech i grymas ograniczono do okolicy ust; stabilizowano kąciki podczas otwierania oraz delikatnie skorygowano spoczynek warg i brody. Dodano normalne dla ruchów ust. Zachowano tekstury, topologię, szkielet, 21 nazw mimiki oraz siedem animacji. Podobieństwo do zdjęcia i wygląd w Filamencie wymagają oceny na telefonie.

APK ma osobny identyfikator pl.iskra.avatar.test.v4, więc instaluje się obok 0.3. Bieżący SHA256 modelu: 451ebc87620b95500163716e77b7433d6ef7d765a2b33391a0df08f2b921848c.

Poniżej historia wcześniejszych etapów; wcześniejsze numery, SHA i opis pakietu nie oznaczają stanu bieżącej wersji.

# Zmiana 0.3 — usta i nieruchoma poza

Brwi pozostają bez zmian: zachowano ich trzy źródłowe accessors morph POSITION.
Skorygowano pochylenie ust w pozycji bazowej o 3.62 stopnia. Różnica wysokości kącików zmalała z 4 mm do 0 mm. To lokalna korekta, bez przebudowy geometrii całej twarzy. Podobieństwo do portretu NIE jest zatwierdzone: bazowa geometria głowy i włosów wymaga dalszej oceny, a wygląd w Filamencie wymaga zrzutu z telefonu.

Domyślna poza jest teraz naprawdę nieruchoma. Zatrzymanie przywraca zapisane lokalne macierze pozy bazowej, zamiast klatki HumanHappyIdle, która mogła przechylać głowę. Osobny przycisk zamraża / wznawia bieżącą pozę ciała, zachowując możliwość testowania mimiki. Wszystkie siedem klipów pozostaje w modelu i na liście.

SHA aktualnego modelu: 451ebc87620b95500163716e77b7433d6ef7d765a2b33391a0df08f2b921848c.

Poniżej historia etapu 0.2; jej SHA i opis poprzedniego ruchu zastępuje powyższa informacja.

# Iskra — niezależne laboratorium awatara 0.2

Baza: `sg1234940/Nox_android`, `3a684c15e668da14509309ebf416d7b71033e4f7`.

## Moduły i zakres

`:avatar` jest biblioteką Android z kontrolerem zdarzeń i własnym SurfaceView / Filamentem 1.66.0. `:avatar-test` jest osobną aplikacją `pl.iskra.avatar.test`, bez uprawnień, czatu, pamięci, QMAPY, mikrofonu i sieci. Przyciski symulują stany rozmowy. Suwaki sterują 21 morph targets; lista pozwala odtwarzać siedem istniejących klipów ciała. Kamera ma widok twarzy/całej postaci i obrót.

Nie przywracamy wycofanych dyrektyw `ISKRA_AVATAR` / `ISKRA_AVATAR_FILE`. Sterowanie przez model językowy nie jest jeszcze podłączone. Moduł i główna aplikacja nie mają nowej zależności; jedyna zmiana istniejącego pliku repo to dopisanie dwóch projektów do root settings.

## Naprawa ust w 0.2

W 0.1 `jawOpen` rozciągał zamkniętą skórę. W nowym modelu przecięto ciągłą powierzchnię dokładnie wzdłuż ręcznie wskazanej na renderze linii styku warg, podzielono przecinane trójkąty i rozdzielono górną/dolną krawędź. Dołączono zamknięte wnętrze ust. Dolna część twarzy wykonuje ograniczony obrót wokół zawiasu szczęki, zamiast szerokiej deformacji Gaussa. Oryginalne rozdzielenia UV/normalnych otrzymują ten sam ruch, żeby nie rozrywać szyi.

Zachowano wszystkie 21 nazw morph targets i siedem animacji ciała. Przebudowano ruchy `jawOpen`, funnel/pucker oraz siedem visemów; to nadal przybliżone kształty, nie skończony anatomiczny rig mowy. Brak zębów i języka. Wnętrze ma osobny ciemny materiał i nie jest jeszcze rozłożone pod docelową teksturę.

Każdy z dwóch primitives używa dokładnie tych samych atrybutów i accessorów wszystkich 21 morph targets. Runtime sprawdza liczbę primitives, nazwy i kolejność. Jedno ustawienie wag dotyczy obu części. Suma wkładów otwarcia jaw/funnel/samogłosek jest ograniczana do przebadanego zakresu; żaden suwak nie jest usuwany.

Model: `avatar-test/src/main/assets/avatar/iskra.glb`, SHA-256 `71aa904245dac768f1934f444946173a7c785190cab108f92cf04ef1b111aa71`. GLB jest osobnym plikiem binarnym, nie jest osadzony w łatce tekstowej.

## Sprawdzenie bez rdzenia

Wymagane: pełny JDK 17/21, Gradle 8.13, SDK Android 36, build-tools 36.0.0. Ustaw `ANDROID_HOME`.

```sh
gradle -p avatar-lab :avatar:compileDebugKotlin :avatar-test:compileDebugKotlin \
  :avatar:testDebugUnitTest :avatar:lintDebug :avatar-test:lintDebug
```

`avatar-lab` konfiguruje wyłącznie dwa nowe moduły. Filament 1.77.3 z poprzedniego kandydata wymaga SDK 37; pozostawiamy 1.66.0 bez podnoszenia wymagań rdzenia i bez pomijania kontroli zgodności.

## Lokalny interfejs zdarzeń

Host wiąże `avatarView.controller.bindConversation(id)`, rozpoczyna `beginTurn(turnId)` i wysyła `AvatarController.Event(conversationId, turnId, rosnącaSekwencja, command)`. Dostępne polecenia: `PhaseChanged`, `Weight`, `Animation`, `SpeechStarted`, `SpeechFrame`, `SpeechStopped`, `Interrupted`, `Reset`.

Stare rozmowy/tury, zdublowane/przestawione zdarzenia i nieaktualny playbackId są odrzucane. Stop/pauza zerują usta; po pauzie host musi rozpocząć nową turę. `SpeechFrame` przyjmuje pozycję odtwarzacza, energię wyjścia TTS i opcjonalny viseme, nigdy sygnał mikrofonu użytkownika. Wyznaczanie fonemów i powiązanie z rzeczywistym odtwarzaczem pozostają osobnym etapem.

Host wywołuje `avatarView.setResumed(true/false)` z cyklu życia ekranu. Native Filament ma jednego właściciela na UI; odłączenie widoku zatrzymuje klatki, unieważnia asynchroniczne ładowanie i zwalnia zasoby. Biblioteka w jednym procesie nie izoluje natywnego SIGABRT; osobna aplikacja laboratoryjna pozwala testować ją poza procesem czatu.

## Granice dowodu

Wyniki testów i renderów są w raporcie naprawy ust. Samodzielne compile/lint/JVM i testy Blendera nie potwierdzają poprawnego wyglądu, cyklu życia natywnego renderera, FPS i temperatury na S24+. Nie wykonano pełnego walidatora Work V2.3, pełnej regresji rdzenia ani fizycznego testu aktualnej 0.2. Nie ma merge/push/deployu ani zmian wersji właściwej Iskry.
