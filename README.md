# KeySight

An Android sight-reading trainer built around one mechanic: **Flash Sight Reading**.

A short passage appears during a metronome count-in, disappears exactly when the performance
begins, and the player performs it from memory while the app captures MIDI and scores the attempt.
Shortening the preview window is the primary difficulty control, and it is modelled separately
from how hard the notation itself is.

The product direction, scope and rationale live in the product plan kept outside the repo.
`CLAUDE.md` describes the package layout and the engineering rules.

## Status

The complete Flash Sight Reading loop is implemented: a USB MIDI keyboard connects with hot
plug, an `AudioTrack` metronome counts in and anchors the attempt clock, the passage is shown
and hidden on the timeline, the performance is captured, scored for pitch, stored with its raw
MIDI, and the next exercise follows.
Eighteen one-measure exercises are bundled.
Notation is a note-name placeholder; staff engraving is the next round, along with difficulty
adaptation.
The JVM test suite covers everything except the Android shells, which are verified on a device.

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
