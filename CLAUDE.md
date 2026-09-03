# KeySight

Android sight-reading trainer built around a masked, continuous score: a run of one-measure
segments is read from pages of systems while a visibility policy decides, on every beat, which
segments' notes are drawn, and the MIDI performance is scored.
Flash Sight Reading (read a bar, then play it from memory) is one preset of that policy.
The product plan is `docs/adaptive-sight-reading.md`; its round ladder drives the rounds.

## Layout

One module, packages by concern, all under `dev.simonmartineau.keysight`:

- `score/` the canonical music model: `Ticks`, `Pitch`, `SpelledPitch`, `KeySignature`, `Staff`,
  `ScoreNote` (with its staff index), `Score` (a list of staves), and `transposed`, diatonic
  transposition between major keys.
- `exercise/` an `Exercise` wraps a `Score`; content is bundled, not stored. `adaptedTo` puts a
  bundled single-voice exercise in the chosen key on the staves the `Hands` setting asks for.
- `midi/` `MidiEvent` (raw bytes plus timestamp), `MidiMessage` (decoded), `MidiParser`.
- `timing/` `MonotonicClock` and `RunTimeline`, every scheduled instant of one run: segment k
  starts at beat `k * beatsPerMeasure`, segment 0 is the count-in, capture ends a tail after the
  last segment.
- `run/` `RunConfig` (tempo, metronome mode, visibility mode, lookahead, segment count or
  open-ended), `VisibilityPolicy` with the Flash, Read ahead and Open score presets, `runMask`
  (the policy at a beat as a `Mask`), `Segment`, `runScore` (segments chained into one score
  with a resting measure 0) and `measureAsScore` (its inverse), `SegmentSource` (where an
  open-ended run's next segments come from), `RunContext`, `RunState`, `RunEvent`, the pure
  `RunMachine` reducer that also commits each segment's evaluation at its capture tail,
  `RunController` (the coroutine that drives it and tops up an open-ended run), `RunHistory`
  and `RunRecord`.
- `audio/` `ClickSynth` and `ClickTrack` (pure PCM on a frame line), `Metronome`, and
  `AudioTrackMetronome`, which anchors beat 0 to the audio timestamp.
- `evaluation/` `PlayedNotes` (MIDI to notes on the run's beat line), `NoteAlignment` (edit
  distance over pitch and onset), `BeatPhase` (the player's lean on the click, bounded, and
  the bounded `step` that lets it run from segment to segment), `RhythmAnalysis` (timing,
  tempo ratio, pauses, continuity), `EvaluationResult` (one segment's judgement),
  `RunEvaluation` (the committed segments, the running phase, and the run-level views the
  summary reads), and `PerformanceEvaluator` with `EVALUATOR_VERSION`: `commit` judges one
  segment from a window of three (the previous segment's missing notes, the segment, the next
  one) once its capture tail has passed, and `evaluate` replays every commit from stored MIDI.
- `settings/` `RunSettings`, `ContentSettings` (key and hands) and `ThemeSettings` (system,
  light, dark), SharedPreferences-backed.
- `data/` Room: entities (`runs`, `segments`, `midi_events` by run, `evaluation_results` by
  segment and evaluator version, `sessions`), `RunDao` and `SessionDao`, `KeySightDatabase`
  (schema version 3), `Migrations.kt` (2 to 3 turns every attempt row into a run with one
  segment per measure, reading both the `FlashConfig` and the `RunConfig` snapshot shapes
  through the pure `LegacyAttempts.kt`), `RoomRunHistory`, and the pure mappers.
- `notation/` the pure layout engine: `StaffPosition`, `Glyph` (SMuFL codepoints),
  `BravuraMetrics`, `AccidentalState` (when an accidental is written), `ScoreLayoutEngine`
  producing a `SystemLayout` (a row of measures across all staves, justified to a width) and a
  `PageLayout` (systems stacked, with the system at a time, the two-system `window` that is the
  page turn, and the `Cursor` at a time) in staff-space units, `Mask` (which score time is
  hidden), and `noteMarks`, the one place evaluation outcomes meet notation.
- `di/` `AppContainer`. `ui/notation/` the Compose Canvas renderer (`RunPage`, the two systems
  around the beat; `RunSummaryPage`, every system in a scroll; `drawPage`, `drawSystem`) that
  draws a `PageLayout` with the bundled Bravura font. `ui/practice/` the one screen, its view
  model, and `PracticePreviews` with one preview per screen state.
- `app/src/main/res/font/bravura.otf` is Bravura 1.482 (SMuFL, OFL); its licence ships in
  `app/src/main/assets/licenses/`. Glyph metrics are the table in `BravuraMetrics`, checked
  against the font file by `BravuraMetricsTest`.
- `app/src/main/assets/exercises/` the content pack, one JSON `Exercise` per file, validated
  by `BundledExercisesTest` on every unit test run.

Not built yet: the generator, difficulty adaptation and session summaries; the plan's round
ladder covers them in order.

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
  `score`, `midi`, `timing`, `run`, `evaluation`, `notation` and the data mappers have no
  Android imports; keep it that way so they stay testable on the JVM.
- Musical time in the score is integer `Ticks` (960 per quarter note), never a double.
  Doubles are for wall-clock beats in `RunConfig`, `VisibilityPolicy` and `RunTimeline` only.
- A run's score is one `Score` whose measure k is segment k, measure 0 resting; the run's beat
  line and the score's ticks coincide, so tick 0 is beat 0 is the first count-in click, and the
  evaluator's beats are run beats. Segments are one measure each and share meter, key and
  staves. A stopped run ends after the segment the stop landed in; its summary is the score
  cut there. An open-ended run keeps `SegmentSource.SEGMENTS_AHEAD` segments beyond the cursor
  so the last, partial system is never one being read; its timeline's `segmentCount` is what
  is known so far and only the click is endless.
- Anything time-related reads from `MonotonicClock` and computes positions from absolute beats.
  Never use `delay` as a source of musical truth, and never introduce a second timer.
- The evaluator sees `ScoreNote` and `MidiEvent`, never notation. Keep it that way. Alignment is
  monophonic until the generator round; content for two hands stays one voice at a time.
- A segment is committed once, one beat after it ends, and never revised: a commit sees only
  the events that had arrived by then, so a live run and a replay from history agree. Every
  played note gets exactly one committed outcome (a note that arrives after its bar was judged
  is `TooLate`, neither correct nor extra) and every expected note exactly one. The phase steps
  by at most `BeatPhase.MAX_STEP_WITHOUT_CLICK_BEATS` per segment, a fifth of that under the
  click, so a drift is followed and a jump is not.
- Notation is laid out in staff spaces by `ScoreLayoutEngine`, y up from the bottom line of the
  system's top staff, each staff carrying its own baseline offset; only `ui/notation` converts
  to pixels. The staff, clefs, signatures and barlines are always drawn; a `Mask` hides notes
  by their onset tick, never the score as a whole, and `runMask` is the policy at a beat. The
  page on screen is the two-system window around the beat; the turn is the window moving one
  system when the cursor enters the next. Glyphs are placed by their SMuFL origin (baseline,
  left edge) using the `BravuraMetrics` table, never by eyeballed offsets, and `noteMarks` is
  the only place evaluation outcomes meet notation.
- Raw MIDI is never discarded or overwritten: `midi_events` rows are the three raw bytes and a
  timestamp, and runs snapshot their score and config so history is re-evaluable on its own.
  Evaluations are keyed by `(segmentId, evaluatorVersion)`, a segment id being `runId:index`;
  bump `EVALUATOR_VERSION` whenever a judgement would change.
- `RunMachine` is a pure reducer, commits included: its deadlines while performing are the
  capture tails of the segments. `RunController` wakes up only at
  `RunMachine.nextDeadlineNanos` or when that deadline moves, and whether a MIDI event is
  captured is decided by its own timestamp, not by the state at delivery. All state changes
  happen on the main dispatcher.
- Beat 0 is when the first click reaches the listener: `AudioTrackMetronome` reads
  `AudioTrack.getTimestamp` and returns that instant as the run start. Clicks are placed by
  sample position in `ClickTrack`, never by sleeping. Visuals that follow the beat read the
  frame time in `withFrameNanos` and derive the beat from the timeline; the mask, the cursor,
  the page window and the beat dots all come from that one number, and none of them tick. The
  marks on the page are the committed segments' outcomes and follow the state, not the frame.
- Nothing device-facing is trusted until it has run on a phone: the metronome anchor, MIDI hot
  plug and the practice screen are verified from Android Studio, not here.
- Schema export goes to `app/schemas`, which is checked in. Add a migration plus a migration
  test with every schema change after version 1.
