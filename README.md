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

Scaffolding complete, no product code written.

The app builds in debug and release, launches to a placeholder screen, and the Compose, Room, KSP
and serialization toolchain is wired and verified.
The domain model, MIDI, metronome, notation and scoring are all still ahead: see
[`docs/implementation-plan.md`](docs/implementation-plan.md).

## Building

The project uses Gradle 9.6 with AGP 9.4, which compiles Kotlin itself (no separate Kotlin plugin).
The Gradle daemon runs on a Java 25 toolchain that Gradle provisions automatically.

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests, no device needed
./gradlew :app:connectedDebugAndroidTest   # instrumented tests, needs a device
./gradlew :app:lintDebug            # lint
```

### One checkout, two SDKs

The checkout is shared between Android Studio on Windows and a WSL shell, which need different
Android SDK paths out of the same gitignored `local.properties`.

AGP resolves the SDK from `sdk.dir` first, and only when that path does not exist does it fall
back to the `android.home` system property and then to `ANDROID_HOME`.
So `sdk.dir` holds the Windows path for Android Studio, and WSL builds pick up the Linux SDK from
`systemProp.android.home` in `~/.gradle/gradle.properties`, which is a WSL-only Gradle home.
Building from WSL prints a "Directory does not exist" warning for `sdk.dir`.
That warning is expected: do not fix it by editing `sdk.dir`, that breaks Android Studio.

Do not run an Android Studio sync on Windows and a Gradle build in WSL at the same time.
Two build systems writing `app/build` on the same directory produce intermittent lock and I/O
failures.
