# KeySight

An Android sight-reading trainer built around one mechanic: **Flash Sight Reading**.

A short passage appears during a metronome count-in, disappears exactly when the performance
begins, and the player performs it from memory while the app captures MIDI and scores the attempt.
Shortening the preview window is the primary difficulty control, and it is modelled separately
from how hard the notation itself is.

The product direction, scope and rationale live in [`docs/product-plan.md`](docs/product-plan.md).
The engineering view is split into two documents:

- [`docs/architecture.md`](docs/architecture.md) - how the app is put together and why.
- [`docs/implementation-plan.md`](docs/implementation-plan.md) - what gets built, in what order.

## Status

Scaffolding complete.
The app launches, the build is green, and the canonical data model, the attempt timeline and the
practice-history schema are in place with tests.
No MIDI, no metronome, no notation and no scoring yet: those are Milestones 0 to 2.

## Building

The project uses Gradle 9.6 with AGP 9.4, which compiles Kotlin itself (no separate Kotlin plugin).
The Gradle daemon runs on a Java 25 toolchain that Gradle provisions automatically.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"

./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests, no device needed
./gradlew :app:connectedDebugAndroidTest   # instrumented tests, needs a device
./gradlew :app:lintDebug            # lint
```

The Android SDK location comes from `ANDROID_HOME` rather than `local.properties`, so the same
checkout builds from WSL and from Android Studio on Windows.
