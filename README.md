# KeySight

An Android sight-reading trainer built around a masked, continuous score.

A run of one-measure segments is read from pages of systems while a visibility policy decides,
on every beat, which segments' notes are drawn; the MIDI performance is scored one segment at a
time behind the cursor.
Flash Sight Reading, reading a bar and then playing it from memory, is one preset of that policy.
How much of the score the player may see is the primary difficulty control, and it is modelled
separately from how hard the notation itself is.

The product plan is `docs/adaptive-sight-reading.md`.
`CLAUDE.md` describes the package layout and the engineering rules.

## Status

A USB MIDI keyboard connects with hot plug, an `AudioTrack` metronome counts in and anchors the
run clock, generated measures are engraved with Bravura on a treble, bass or grand staff in any
key, shown and hidden on the timeline, and the performance is captured, scored for pitch and
rhythm with both hands aligned as one stream, and stored with its raw MIDI, its seeds and its
configuration so history re-evaluates on its own.
Content is generated from a seed and an `ExerciseConfig`, and a difficulty controller moves one
dimension at a time from a window of recent bars: the lookahead between runs, the music within
an open-ended run, every move named on the summary.
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
