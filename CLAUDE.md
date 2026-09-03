# KeySight

Android sight-reading trainer built around the Flash Sight Reading mechanic.
Read [`docs/architecture.md`](docs/architecture.md) before changing structure and
[`docs/implementation-plan.md`](docs/implementation-plan.md) before adding a feature.
The product reasoning is in [`docs/product-plan.md`](docs/product-plan.md).

## Build and test

The Android SDK lives at `~/Android/Sdk` and is found through `ANDROID_HOME`, not
`local.properties`, so that this checkout also builds from Android Studio on Windows.
Gradle provisions its own Java 25 daemon toolchain.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"

./gradlew :app:testDebugUnitTest    # fast, no device
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:assembleRelease      # exercises R8; run before claiming a change is safe
```

Instrumented tests (`:app:connectedDebugAndroidTest`) need a connected device and cannot be run
from this environment.

## Conventions

- Kotlin, Jetpack Compose, coroutines and Flow, Room. Manual DI through `AppContainer`.
- The Kotlin, Compose-compiler, serialization-plugin and KSP versions are pinned to whatever AGP
  embeds. Never bump one of them alone; they move with AGP or not at all.
- New logic goes in a JVM unit test unless it genuinely needs a device.
- Anything time-related reads from `MonotonicClock` and computes positions from absolute beats.
  Never use `delay` as a source of musical truth, and never introduce a second timer.
- The evaluator sees `ScoreNote` and `MidiEvent`, never notation. Keep it that way.
- Raw MIDI is never discarded or overwritten; evaluations are versioned so attempts can be
  re-scored later.
- Add Room migrations plus a migration test with every schema change; `app/schemas` is checked in.
