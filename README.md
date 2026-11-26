# Durak — Android

Native Android version of the Durak card game, built with Kotlin and Jetpack Compose.

## Gameplay
- 2–4 players: you against one to three AI opponents.
- 36-card deck, six-card hands, trump suit and standard attack/defend rounds.
- Transfer (perevodnoy) moves: match a rank on the table and pass the attack to the next player when the rules allow it.
- Bots occasionally attempt a bluff. Tap an un-beaten suspicious table card marked with **?** to challenge it.
- Easy, Medium and Hard AI settings.
- Monte Carlo move selection with hidden-card determinization: bots sample unseen cards and do not read opponents' actual hands.
- Tap a legal card or drag it upward to play it.
- Undo, take, finish-round and restart controls.
- Landscape hand layout expands to use the available width when the hand contains more than seven cards.

## Algorithms and data structures
The project includes custom implementations of:
- Dynamic array
- Stack
- Circular queue
- Separate-chaining integer hash table
- Merge sort
- Counting sort
- Fisher–Yates shuffle

The main menu includes a benchmark comparing Merge Sort and Counting Sort.

## Build
1. Open this repository in Android Studio.
2. Allow Gradle to sync.
3. Select the `app` run configuration and run it on an Android device or emulator.

GitHub Actions builds a debug APK on every push to `main` and uploads it as the `durak-debug-apk` artifact.

## Developers
Alan (250444) and Shoxrux (250272).
