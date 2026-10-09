# AutoRynek – mod Fabric 1.21.4 (tylko klient)

Mod odtwarza „Auto Rynek” z nagrania „Minestar.pl Autorynek / DrzewoBot”: okno z listą skupu,
automatyczne otwieranie rynku komendą (`/ah open`), kupowanie ofert poniżej ustawionej ceny
za sztukę i HUD ze statystykami.

> ⚠️ **Uwaga:** to automat klikający za gracza. Regulamin wielu serwerów (w tym prawdopodobnie
> Minestar) zabrania makr i botów – używasz na własne ryzyko, możliwy ban.

## Budowanie

Wymagania: **Java 21**, internet (Gradle pobierze Fabric Loom, Minecrafta i Fabric API).

```
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

Gotowy plik: `build/libs/autorynek-1.0.0.jar` → wrzuć do `.minecraft/mods`
razem z **Fabric Loader ≥ 0.16.10** i **Fabric API 0.119.4+1.21.4**.

Do uruchomienia z IDE: `./gradlew runClient`.

## Obsługa

| Akcja | Jak |
|---|---|
| Otwórz okno Auto Rynku | **Prawy Shift** (zmienisz w Sterowanie → AutoRynek) |
| Dodaj przedmiot ręcznie | pola „Przedmiot” + „Maks $/szt.” → **+ Dodaj** |
| Dodaj przedmiot z rynku | najedź na przedmiot w rynku i wciśnij **Ctrl+A** → wpisz maks. cenę → **Dodaj do listy** (cena z lore jest wykrywana) |
| Start | **▶ START** (okno się zamyka, bot otwiera rynek komendą) |
| Stop | **ESC** lub **Prawy Shift** na otwartym rynku, albo klawisz „Włącz / wyłącz Auto Rynek” (domyślnie nieprzypisany) |

Ceny w polu „Maks” mogą mieć skróty: `60`, `$60`, `1.5k`, `2m`.
Nazwa przedmiotu to dowolny fragment nazwy (bez wielkości liter i polskich znaków), np. `kamień inteligencji`.

### Okno
- **Klik (ms)** – odstęp między kliknięciami w oferty (min. 50).
- **Skan (ms)** – co ile odświeżać rynek, gdy nie ma okazji (min. 100).
- **Komenda rynku** – domyślnie `/ah open`.
- **Pojedynczo** – jeden zakup na widok rynku; **Wszystkie** – kupuje każdą pasującą ofertę.
- Kolor ceny na liście: zielony = dobra cena na rynku, czerwony = za drogo, żółty = brak ceny w lore.

Bot kupuje tylko, gdy `cena oferty / liczba sztuk ≤ Maks $/szt.`, zaczynając od najtańszej.

## Konfiguracja – `.minecraft/config/autorynek.json`

Poza ustawieniami z okna:
- `refreshSlot` – numer slotu przycisku „Odśwież” w rynku. `-1` (domyślnie) = bot zamyka i otwiera rynek komendą.
- `buyPattern` / `missPattern` – wyrażenia regularne rozpoznające w czacie udany zakup / „ktoś cię ubiegł”.
  Zasilają statystyki na HUD (Kupione / Wydano / Zaoszczędzono / Przegapione). **Jeśli statystyki
  nie liczą się poprawnie, wklej do tych pól fragment prawdziwych komunikatów serwera.**

## Co jest z nagrania, a co założone

Z nagrania odtworzone: układ okna, pola i przyciski, kolory, domyślne wartości (150 / 600 ms, `/ah open`),
okienko Ctrl+A z wykrywaniem ceny, HUD (Czas, Kupione, Wydano, Zaoszczędzono, Przegapione, Klik).

Założenia (z filmu nie da się ich odczytać, popraw jeśli działa inaczej):
1. „Pojedynczo / Wszystkie” = jeden zakup na widok vs. wszystkie pasujące oferty.
2. Cena w lore („Cena $125”) to cena całej oferty; przeliczana na sztukę.
3. Kliknięcie oferty od razu kupuje (na nagraniu nie widać okna potwierdzenia).
4. Treść komunikatów o zakupie / przegapieniu (patrz wyżej).
5. Odświeżanie rynku: ponowne otwarcie komendą. Na HUD z filmu widać „Klik: slot 26: Filtrowanie” –
   możliwe, że oryginał odświeża rynek klikając któryś z przycisków po prawej stronie. Jeśli chcesz tak samo,
   ustaw w configu `refreshSlot` na numer slotu odpowiedniego przycisku (sprawdź go w grze).

Czasy są mierzone w tickach klienta (20/s), więc realna rozdzielczość to ok. 50 ms.

DrzewoBot (kopanie generatorów drewna) z tego samego nagrania **nie** jest częścią tego moda.

## Gotowy .jar bez instalowania Javy (GitHub Actions)

1. Załóż darmowe repozytorium na github.com i wrzuć do niego całą zawartość tego folderu
   (razem z ukrytym folderem `.github`).
2. Wejdź w zakładkę **Actions** → workflow **Build mod jar** (uruchomi się sam po wrzuceniu plików,
   albo kliknij **Run workflow**).
3. Po ok. 2–4 minutach otwórz zakończone uruchomienie i pobierz z sekcji **Artifacts** plik
   `autorynek-jar` – w środku jest `autorynek-1.0.0.jar` do folderu `mods`.
