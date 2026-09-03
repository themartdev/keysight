# KeySight

Android sight-reading trainer built around the Flash Sight Reading mechanic.
Read [`docs/architecture.md`](docs/architecture.md) before changing structure and
[`docs/implementation-plan.md`](docs/implementation-plan.md) before adding a feature.
The product reasoning is in [`docs/product-plan.md`](docs/product-plan.md).

## Build and test

This checkout is shared with Android Studio on Windows, so `local.properties` keeps `sdk.dir`
pointing at the Windows SDK.
That path does not exist under WSL, so AGP warns and falls back to `systemProp.android.home` in
`~/.gradle/gradle.properties`, which points at the Linux SDK in `~/Android/Sdk`.
The "sdk.dir Directory does not exist" warning on every WSL build is expected; do not fix it by
editing `sdk.dir`, that breaks Android Studio.
Gradle provisions its own Java 25 daemon toolchain.

Do not build here while an Android Studio sync is running on Windows: both write `app/build` and
the result is intermittent lock and I/O errors. `./gradlew clean` fails the same way when Studio
holds the directory.

```bash
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
  There are no tests yet; the first piece of domain logic should arrive with them.
- Anything time-related reads from `MonotonicClock` and computes positions from absolute beats.
  Never use `delay` as a source of musical truth, and never introduce a second timer.
- The evaluator sees `ScoreNote` and `MidiEvent`, never notation. Keep it that way.
- Raw MIDI is never discarded or overwritten; evaluations are versioned so attempts can be
  re-scored later.
- Room and KSP are wired but unused until the first `@Entity`. Schema export is configured to
  `app/schemas`; check that directory in, and add a migration plus a migration test with every
  schema change after version 1.
