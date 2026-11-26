# Durak for Android

Native Android port of the Durak card game.

## Features
- English interface and game rules
- 2–4 players (you + 1–3 AI opponents)
- Easy, Medium and Hard AI settings
- Imperfect-information Monte Carlo opponent simulation
- Tap a legal card or drag it upward to play
- Undo, take, finish-round and restart actions
- Responsive hand layout for portrait and landscape screens

## Build
Open this repository in Android Studio, let Gradle sync, then run the `app` configuration on an emulator or Android device.

The GitHub Actions workflow in `.github/workflows/android-build.yml` builds a debug APK on pushes to `main` and uploads it as the `durak-debug-apk` artifact.

## Developers
Alan (250444) and Shoxrux (250272).
