# KeySight

Android sight-reading trainer built around the Flash Sight Reading mechanic: a short passage is
shown during a metronome count-in, hidden exactly when the performance starts, and the MIDI
performance is scored.
The product plan is `docs/adaptive-sight-reading.md`; its milestone ladder drives the rounds.

## Layout

One module, packages by concern, all under `dev.simonmartineau.keysight`:

- `score/` the canonical music model: `Ticks`, `Pitch`, `SpelledPitch`, `KeySignature`, `Staff`,
  `ScoreNote` (with its staff index), `Score` (a list of staves), and `transposed`, diatonic
  transposition between major keys.
- `exercise/` an `Exercise` wraps a `Score`; content is bundled, not stored. `adaptedTo` puts a
  bundled single-voice exercise in the chosen key on the staves the `Hands` setting asks for.
- `midi/` `MidiEvent` (raw bytes plus timestamp), `MidiMessage` (decoded), `MidiParser`.
- `timing/` `MonotonicClock` and `AttemptTimeline`, every scheduled instant of one attempt.
- `attempt/` `FlashConfig`, `AttemptState`, `AttemptEvent`, the pure `AttemptMachine` reducer,
  `AttemptController` (the coroutine that drives it), `AttemptHistory` and `AttemptRecord`.
- `audio/` `ClickSynth` and `ClickTrack` (pure PCM on a frame line), `Metronome`, and
  `AudioTrackMetronome`, which anchors beat 0 to the audio timestamp.
- `evaluation/` `PlayedNotes` (MIDI to notes on the beat line), `NoteAlignment` (edit distance
  over pitch and onset), `BeatPhase` (the player's lean on the click, bounded), `RhythmAnalysis`
  (timing, tempo ratio, pauses, continuity), `PerformanceEvaluator` with `EVALUATOR_VERSION`.
- `settings/` `FlashSettings`, `ContentSettings` (key and hands) and `ThemeSettings` (system,
  light, dark), SharedPreferences-backed.
- `data/` Room: entities, DAOs, `KeySightDatabase` (schema version 2, `Migrations.kt`),
  `RoomAttemptHistory`, and the pure mappers.
- `notation/` the pure layout engine: `StaffPosition`, `Glyph` (SMuFL codepoints),
  `BravuraMetrics`, `AccidentalState` (when an accidental is written), `ScoreLayoutEngine`
  producing a `SystemLayout` (a row of measures across all staves, justified to a width) and a
  `PageLayout` (systems stacked) in staff-space units, `Mask` (which score time is hidden), and
  `noteMarks`, the one place evaluation outcomes meet notation.
- `di/` `AppContainer`. `ui/notation/` the Compose Canvas renderer (`Page`, `drawPage`,
  `drawSystem`) that draws a `PageLayout` with the bundled Bravura font. `ui/practice/` the one
  screen, its view model, and `PracticePreviews` with one preview per screen state.
- `app/src/main/res/font/bravura.otf` is Bravura 1.482 (SMuFL, OFL); its licence ships in
  `app/src/main/assets/licenses/`. Glyph metrics are the table in `BravuraMetrics`, checked
  against the font file by `BravuraMetricsTest`.
- `app/src/main/assets/exercises/` the content pack, one JSON `Exercise` per file, validated
  by `BundledExercisesTest` on every unit test run.

Not built yet: the continuous masked run, the generator, difficulty adaptation, session summaries;
the plan's round ladder covers them in order.

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
  `score`, `midi`, `timing`, `attempt`, `evaluation`, `notation` and the data mappers have no
  Android imports; keep it that way so they stay testable on the JVM.
- Musical time in the score is integer `Ticks` (960 per quarter note), never a double.
  Doubles are for wall-clock beats in `FlashConfig` and `AttemptTimeline` only.
- Anything time-related reads from `MonotonicClock` and computes positions from absolute beats.
  Never use `delay` as a source of musical truth, and never introduce a second timer.
- The evaluator sees `ScoreNote` and `MidiEvent`, never notation. Keep it that way. Alignment is
  monophonic until the generator round; content for two hands stays one voice at a time.
- Notation is laid out in staff spaces by `ScoreLayoutEngine`, y up from the bottom line of the
  system's top staff, each staff carrying its own baseline offset; only `ui/notation` converts
  to pixels. The staff, clefs, signatures and barlines are always drawn; a `Mask` hides notes
  by their onset tick, never the score as a whole. Glyphs are placed by their SMuFL origin (baseline, left
  edge) using the `BravuraMetrics` table, never by eyeballed offsets, and `noteMarks` is the
  only place evaluation outcomes meet notation.
- Raw MIDI is never discarded or overwritten: `midi_events` rows are the three raw bytes and a
  timestamp, and attempts snapshot their score and config so history is re-evaluable on its
  own. Evaluations are keyed by `(attemptId, evaluatorVersion)`; bump `EVALUATOR_VERSION`
  whenever a judgement would change.
- `AttemptMachine` is a pure reducer. `AttemptController` wakes up only at
  `AttemptMachine.nextDeadlineNanos`, and whether a MIDI event is captured is decided by its own
  timestamp, not by the state at delivery. All state changes happen on the main dispatcher.
- Beat 0 is when the first click reaches the listener: `AudioTrackMetronome` reads
  `AudioTrack.getTimestamp` and returns that instant as the attempt start. Clicks are placed by
  sample position in `ClickTrack`, never by sleeping. Visuals that follow the beat read the
  frame time in `withFrameNanos` and derive the beat from the timeline; they do not tick.
- Nothing device-facing is trusted until it has run on a phone: the metronome anchor, MIDI hot
  plug and the practice screen are verified from Android Studio, not here.
- Schema export goes to `app/schemas`, which is checked in. Add a migration plus a migration
  test with every schema change after version 1.
