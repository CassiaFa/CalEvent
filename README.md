# CalEvent

Photographiez un planning papier, retrouvez vos créneaux dans l'agenda du téléphone.
**100 % hors ligne : aucune photo ni aucun texte ne quitte l'appareil.**

CalEvent lit un planning papier (calendrier mural, planning hebdo, planning d'équipe à codes) et ajoute les
créneaux à l'agenda du téléphone, y compris un agenda CalDAV synchronisé par DAVx⁵.

- Tout se passe sur l'appareil : pas de compte, pas de serveur, pas de statistiques d'usage.
  L'app n'a même pas la permission Internet.
- Relecture avant envoi : chaque créneau est « Retenu », « À revoir » ou « Ignoré » selon la confiance de la
  lecture ; balayage pour trier, fiche pour corriger date et horaires.
- Plannings d'équipe : choix de la personne (« Moi » mémorisé), codes de service (`M06`, `RH`…) définis par
  l'utilisateur au fil des plannings lus.
- Détection des doublons avant écriture dans l'agenda.

## Variantes

| Variante | Reconnaissance du texte | Pour |
|---|---|---|
| `play` | Google ML Kit (modèle embarqué) | usage général |
| `foss` | Tesseract (Tesseract4Android), aucun composant Google | F-Droid |

## Compiler

JDK 17+ et SDK Android (`local.properties` ou `ANDROID_HOME`).

```sh
./gradlew :core:test                 # logique métier (sans SDK Android)
./gradlew :app:assemblePlayDebug     # app/build/outputs/apk/play/debug/
./gradlew :app:assembleFossDebug     # app/build/outputs/apk/foss/debug/
```

Kotlin, Jetpack Compose (Material 3), CameraX. Le module `:core` (Kotlin pur) contient la lecture du
planning, les règles de confiance, les codes et les doublons ; `:app` contient l'interface.

## Licence

GPL-3.0-or-later, voir [LICENSE](LICENSE). Polices DM Sans et DM Mono : SIL Open Font License 1.1
(`LICENSES/OFL-DMSans.txt`).
