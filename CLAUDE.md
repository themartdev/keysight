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
- `exercise/` the generator: `Hands`, `ExerciseConfig` (key, hands, accompaniment, a range per
  hand, note values, rests, the largest interval, meter; the musical side of difficulty, one
  field per dimension the controller may move), `RhythmEvent` (a note or a rest of a value),
  `SeededRandom` (SplitMix64, so a seed means the same thing in every Kotlin version) with
  `segmentSeed`, and `ExerciseGenerator` with `GENERATOR_VERSION`: one measure in C from a
  config and a seed, a constrained random walk with a contour over a rhythm from the config's
  vocabulary, then `transposed` into the key.
- `midi/` `MidiEvent` (raw bytes plus timestamp), `MidiMessage` (decoded), `MidiParser`.
- `timing/` `MonotonicClock` and `RunTimeline`, every scheduled instant of one run: segment k
  starts at beat `k * beatsPerMeasure`, segment 0 is the count-in, capture ends a tail after the
  last segment.
- `run/` `RunConfig` (tempo, metronome mode, visibility mode, lookahead, segment count or
  open-ended), `VisibilityPolicy` with the Flash, Read ahead and Open score presets, `runMask`
  (the policy at a beat as a `Mask`), `Segment` with its `SegmentOrigin` (generated from a
  version, seed and config, or a bundled exercise id), `runScore` (segments chained into one
  score with a resting measure 0) and `measureAsScore` (its inverse), `SegmentSource` (a run's
  segments by index) and `GeneratedSegmentSource` (the generator over a run seed), `RunContext`
  (with the run seed), `RunState`, `RunEvent`, the pure
  `RunMachine` reducer that also commits each segment's evaluation at its capture tail
  (`RunState.committed` pairs each performed segment with its result),
  `RunController` (the coroutine that drives it, tops up an open-ended run one segment at a
  time and reports every ended run), `RunHistory` and `RunRecord`.
- `audio/` `ClickSynth` and `ClickTrack` (pure PCM on a frame line), `Metronome`, and
  `AudioTrackMetronome`, which anchors beat 0 to the audio timestamp.
- `evaluation/` `PlayedNotes` (MIDI to notes on the run's beat line), `NoteAlignment` (edit
  distance over chords by onset, pitch and time, both staves as one stream), `BeatPhase` (the player's lean on the click, bounded, and
  the bounded `step` that lets it run from segment to segment), `RhythmAnalysis` (timing,
  tempo ratio, pauses, continuity), `EvaluationResult` (one segment's judgement),
  `RunEvaluation` (the committed segments, the running phase, and the run-level views the
  summary reads), and `PerformanceEvaluator` with `EVALUATOR_VERSION`: `commit` judges one
  segment from a window of three (the previous segment's missing notes, the segment, the next
  one) once its capture tail has passed, and `evaluate` replays every commit from stored MIDI.
- `difficulty/` the controller of the plan's section 8, pure: `Dimension` (the fixed walk
  order, lookahead then interval, range, rhythm, rests, each saying whether it moves within a
  run),
  `Ladders` (every dimension's rungs, easiest first, in one place, over the generic `Ladder`),
  `MusicalLevel` (the generator fields the controller owns, as values), `DifficultyState`
  (the level and the dimension moved last), `Evidence` (`SegmentEvidence` from one committed
  segment, the `trailingWindow` at the current state, `successOf`), `DifficultyController`
  (`decide`, a pure function from a position and evidence to the next position and at most one
  `Move`), `DifficultyStore` (the persistence port), `DifficultyTracker` (the state and
  evidence across a session) and `AdaptiveSegmentSource` (the generator at the level the
  tracker decides for every segment still to come).
- `settings/` `RunSettings`, `ContentSettings` (key, hands, accompaniment, and the
  `ExerciseConfig` they make over the generator's defaults) and `ThemeSettings` (system, light,
  dark), SharedPreferences-backed.
- `data/` Room: entities (`runs` with the run seed, `segments` with their origin columns,
  `midi_events` by run, `evaluation_results` by segment and evaluator version, `sessions`,
  `difficulty_state`, one row of JSON), `RunDao` (with `recentCommitted`, the last committed
  segments across runs with the configurations they were played under), `SessionDao` and
  `DifficultyDao`, `KeySightDatabase` (schema version 5), `Migrations.kt` (2 to 3 turns every
  attempt row into a run with one segment per measure, reading both the `FlashConfig` and the
  `RunConfig` snapshot shapes through the pure `LegacyAttempts.kt`; 3 to 4 adds the seed to
  runs and rebuilds segments with a nullable exercise id and the generator columns; 4 to 5
  adds the controller's table), `RoomRunHistory`, `RoomDifficultyStore`, and the pure mappers.
- `notation/` the pure layout engine: `StaffPosition`, `Glyph` (SMuFL codepoints),
  `BravuraMetrics`, `AccidentalState` (when an accidental is written), `ScoreLayoutEngine`
  producing a `SystemLayout` (a row of measures across all staves, justified to a width) and a
  `PageLayout` (systems stacked, with the system at a time, the two-system `window` that is the
  page turn, and the `Cursor` at a time) in staff-space units, with flags on lone eighths,
  `BeamElement`s over eighths that share a beat and rests derived from the silences on each
  staff (`restsFilling` is the splitting rule), `Mask` (which score time is hidden), and
  `noteMarks`, the one place evaluation outcomes meet notation.
- `di/` `AppContainer`. `ui/notation/` the Compose Canvas renderer (`RunPage`, the two systems
  around the beat; `RunSummaryPage`, every system in a scroll; `drawPage`, `drawSystem`) that
  draws a `PageLayout` with the bundled Bravura font. `ui/practice/` the one screen, its view
  model, and `PracticePreviews` with one preview per screen state.
- `app/src/main/res/font/bravura.otf` is Bravura 1.482 (SMuFL, OFL); its licence ships in
  `app/src/main/assets/licenses/`. Glyph metrics are the table in `BravuraMetrics`, checked
  against the font file by `BravuraMetricsTest`.
- `app/src/test/resources/exercises/` the eighteen hand-written measures of the rounds before
  the generator, the four eighth-note measures of round 9 (beamed pairs on both clefs in C,
  B flat and D, and one syncopated measure of lone flagged eighths the generator never
  writes) and the four rest measures of round 10 (quarter, half and eighth rests on both
  clefs in C, G and F), test fixtures only: `BundledMeasuresTest` holds them to the layout
  envelope and to the generator's constraints (`violations`, the test-side statement of the
  contract).

Not built yet: session summaries and the generator dimensions after rests (accidentals,
chords, other meters, syncopation); the plan's round ladder covers them in order.

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
  `score`, `exercise`, `midi`, `timing`, `run`, `evaluation`, `difficulty`, `notation` and
  the data mappers have no Android imports; keep it that way so they stay testable on the JVM.
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
- The evaluator sees `ScoreNote` and `MidiEvent`, never notation. Keep it that way. Both staves
  are aligned as one stream of chords: expected notes sharing an onset are a chord, played
  notes within `NoteAlignment.CHORD_SPREAD_BEATS` of each other are a chord, and an outcome's
  hand is its expected note's staff. The spread must stay shorter than the shortest note value
  the generator writes.
- Content is generated, never curated: a segment is `ExerciseGenerator.generate(config, seed)`,
  written in C and transposed, and every segment stores its generator version, seed and config
  beside its score so history stands without the generator. A run has one seed and segment k's
  seed is `segmentSeed(runSeed, k)`. Bump `GENERATOR_VERSION` whenever the same inputs would
  produce a different score. The rhythm vocabulary is every way to fill the measure with the
  allowed values in which only a note shorter than the beat starts off the beat
  (`ExerciseConfig.mayStartAt`), so eighths come in pairs that fill a beat and nothing is
  syncopated; `ExerciseConfig.DEFAULT_NOTE_VALUES` stays wholes to quarters, so a stored
  configuration without eighths keeps its rhythms. A rest is the absence of a note over a
  span, not a score type: with `ExerciseConfig.rests` the vocabulary also holds a rest of any
  note value where `mayRestAt` allows (never first in the measure, never after another rest,
  and at a multiple of its own length, so a half rest starts on beat 1 or 3 of 4/4), and
  without rests the vocabulary is the old list in the old order, so `GENERATOR_VERSION` and
  every stored seed keep their meaning. A new generator dimension gets its constraint in
  `violations`, its generator test and, if a judgement changes, its evaluator test before the
  controller may move it; the layout draws no dots and one flag or beam at most, so dots and
  sixteenths wait for the layout.
- Difficulty is moved by the controller, never silently. It decides from the trailing window
  of `DifficultyController.WINDOW_SEGMENTS` committed segments played at exactly the current
  state (run exposure and exercise configuration), whose success is the smaller of the pooled
  pitch and rhythm accuracies the score line shows: above 90% one dimension steps up, below
  65% one steps down, else it holds, and a short window holds. One dimension per decision,
  and a move empties the window, so no two moves share evidence. The walk takes turns in
  `Dimension` order, lookahead, interval, range, rhythm, rests: up goes to the next dimension
  after the last moved that can move, down starts at the last moved and walks back. The lookahead moves only between runs and only in
  Flash, and is written into the run settings; the musical dimensions move within an
  open-ended run for the segments still to be generated (`SegmentSource.SEGMENTS_AHEAD` bars
  ahead of the cursor, never a segment already shown) and between runs otherwise. Key, hands,
  accompaniment, mode, tempo and length are the player's. The state (level and last moved) is
  one row; the window is derived from stored evaluations, so both survive a restart. A new
  dimension is a `Dimension` entry, its `Ladder` and its case in `DifficultyController.step`.
  What moved is always on screen: the Ready line names the level, the summary names every bar
  the level changed at and the move for the next run, and the lookahead chip moves.
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
  the only place evaluation outcomes meet notation. Eighths that share a beat are beamed and a
  beam never crosses a beat; a lone eighth hangs a flag from its stem tip by the flag's SMuFL
  anchor. A beamed group's stems follow the head farthest from the middle line, the beam
  follows its first and last heads (half their distance, at most one space) and sits where
  the stem nearest it has its own length; the beam carries no note id, the stems do. Rests
  are not in the score: the layout derives them per staff from the silences between what
  sounds in a measure and splits each silence into the largest rests that start at a multiple
  of their own length and stay inside their beat (a half's worth on beat 2 is two quarter
  rests); a rest takes a column at its onset with the room a note of its value gets, the whole
  rest hangs from the fourth line and the others sit by their origin on the middle line. A
  rest inside a bar is content: it carries its onset tick and the mask hides it with the bar,
  since a rest left on the page would tell which beat is silent; the whole rest of a staff
  silent through a measure, the count-in included, carries no tick and stays on the page.
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
  test with every schema change after version 1. Foreign keys are off while a migration runs,
  so a table may be rebuilt without cascading into its dependants.
