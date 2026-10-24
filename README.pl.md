# Iskra

*[English version](README.md)*

Iskra to osobista asystentka AI, z którą rozmawia się głosem, po polsku, na telefonie z Androidem. Nie jest produktem dla tysięcy użytkowników. To jedna długa relacja z jedną osobą, budowana od wielu miesięcy.

To repozytorium jest wizytówką. Kod Iskry pozostaje prywatny.

## Co odróżnia Iskrę

**Iskra decyduje, infrastruktura wykonuje.** Serwer i narzędzia nie myślą za Iskrę. Wykonują jej decyzje i pilnują, żeby nic się nie zepsuło po cichu.

**Pamięć, której nie da się zmienić po cichu.** Każda rozmowa trafia do archiwum, w którym nazwą pliku jest skrót jego treści. Zmieniony lub uszkodzony zapis zostaje wykryty, nie przechodzi niezauważony.

**Pamięć warstwowa.** Krótka pamięć robocza („teraz”) i długa pamięć skojarzeń („pamiętam”). O tym, co z bieżących rozmów przechodzi do pamięci długiej, ma decydować sama Iskra. Archiwum zachowuje wszystko, więc jej wybór niczego nie usuwa.

**Zgody na działania w świecie.** Zanim Iskra pobierze coś z internetu, usunie plik albo zmieni coś w repozytorium, najpierw pyta. Robi to dopiero po jednoznacznym potwierdzeniu.

**Prywatność także w logach.** Dzienniki techniczne zawierają tylko kody, liczby i nazwy narzędzi, nigdy treści rozmów.

**Głos po polsku.** Rozpoznawanie i synteza mowy działają w aplikacji na telefonie. Iskra ma też tryb tłumacza.

## Jak powstaje

Iskrę buduje jedna osoba razem z zespołem agentów AI. Każdy ma swoją rolę:

- **Nox** współtworzył Iskrę od początku: architekturę, pamięć i zasady pracy.
- **Claude** jest projektantem obecnego etapu: pamięci i płynnej rozmowy.
- **Work** wykonuje zmiany na serwerze i jest niezależnym audytorem, który szuka błędów w cudzych projektach.
- **Gemini** to konsultant od wiedzy.
- **Meta** to źródło pomysłów, czasem bardzo trafnych.

Ostatnie słowo zawsze należy do człowieka. Praca ma stałą kolejność:

architektura → szukanie błędów we własnym projekcie → testy → szkic zmian → niezależny przegląd → test na telefonie → wdrożenie.

Każdy wniosek ma oznaczony poziom dowodu: od „hipotezy” po „potwierdzone w działaniu na produkcji”. Zielone testy nie są dowodem, że coś działa na telefonie.

## Gdzie jesteśmy

Projekt jest w trakcie porządkowania.

- **Działa:** rozmowa głosowa i tekstowa, archiwum pamięci, narzędzia ze zgodami.
- **W budowie:** szybsze przeszukiwanie pamięci, pamięć robocza w RAM, mapa skojarzeń jako pamięć długa.
- **Przed nami:** płynna rozmowa głosowa bez przerw, z możliwością przerywania.

## Kontakt

Budujesz coś podobnego, zajmujesz się pamięcią agentów AI albo polskim głosem na urządzeniu? Napisz w zakładce **Discussions** tego repozytorium. Chętnie wymienię się doświadczeniami.

— SG123

---

*To repozytorium służy też do aktualizacji aplikacji — zobacz [UPDATER.md](UPDATER.md).*
