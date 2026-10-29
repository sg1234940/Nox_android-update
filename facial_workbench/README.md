# Iskra avatar facial rig - pakiet odtwarzalny

## Uzycie

1. Rozpakuj pakiet do osobnego katalogu.
2. Skopiuj oryginalny `base_basic_pbr.glb` do tego katalogu.
3. Uruchom:

```bash
./bootstrap_iskra_avatar.sh
```

Skrypt sprawdza obecność i rozmiar oryginalnego GLB, buduje `iskra_avatar_facial_rig.glb`, uruchamia testy strukturalne i tworzy `SHA256SUMS.txt`.

## Blender

Do authoringu w Blenderze użyj:

```bash
blender --background --python iskra_facial_rig_blender.py -- base_basic_pbr.glb iskra_avatar_facial_rig_blender.glb
```

Ten krok pozostaje NIE SPRAWDZONY w środowisku Lumena, ponieważ Blender nie był dostępny.

## Android

Instrukcja integracji i lip-sync znajduje się w `ANDROID_INTEGRATION.md`.

## Zakres pakietu

Pakiet nie wykonuje merge, deployu, release ani budowy APK.
