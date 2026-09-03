# Implementation plan

The milestones follow [`product-plan.md`](product-plan.md).
This document turns them into engineering work against the code that now exists, and records the
decisions each milestone has to make.

## Already in place

The scaffolding is done, so Milestone 0 starts against a green build rather than an empty project.

- Gradle build on AGP 9.4 with built-in Kotlin, Compose, KSP, Room and kotlinx.serialization.
- Debug and release builds, release minified by R8, lint clean apart from the pinned-toolchain
  notices explained in [`architecture.md`](architecture.md).
- The canonical score model: `Pitch`, `PitchRange`, `TimeSignature`, `Clef`, `Hand`, `ScoreNote`,
  `Score`.
- `Exercise`, `ExerciseTag` and `ExerciseConfig`, the eleven musical dimensions difficulty will
  eventually move along.
- `FlashConfig` with the default configuration and the preview ladder, and `AttemptState` with its
  legal transitions.
- `AttemptTimeline`, which computes every scheduled moment of an attempt from an absolute beat
  position, and `MonotonicClock` as the injectable time source.
- `MidiEvent` and the `EvaluationResult` types for phase 1 scoring.
- The Room practice-history schema: sessions, attempts, raw MIDI, evaluations keyed by evaluator
  version, and the difficulty state, with the schema exported to `app/schemas`.
- 24 unit tests covering the timeline, the state machine and the score model, plus one
  instrumented test asserting that an attempt keeps its raw MIDI.

The app launches to a placeholder screen.
There is no MIDI, no metronome, no notation and no scoring.

---

## Milestone 0 - MIDI spike

The first thing to prove is that a physical keyboard reaches the app reliably, because everything
downstream is worthless if capture is unreliable.

### Build

- `midi/MidiDeviceManager`: wrap `MidiManager`, enumerate input ports, open and close them, and
  expose connection state as a `StateFlow`.
- Handle hot plug and unplug through `MidiManager.registerDeviceCallback`, so a keyboard can be
  unplugged and replugged without restarting the app.
- `midi/MidiCapture`: a `MidiReceiver` that turns the raw byte stream into `MidiEvent`.
- `ui/midi/MidiMonitorScreen`: the connected device, and the notes arriving, live.
- Wire both into `AppContainer`.

### Tests

Byte-stream parsing is pure logic and belongs in unit tests, not on a device.

- A message split across two `onSend` calls is reassembled correctly.
- Running status is handled.
- Note-on with velocity 0 is a note-off, which is what many keyboards actually send.
- System real-time bytes interleaved mid-message do not corrupt it.
- Timestamps survive unchanged from `onSend` to `MidiEvent`.

### Exit criterion

A physical MIDI keyboard plays notes into the app, and unplug and replug recovers without a
restart.

### Decisions to make

Stay on the legacy `MidiManager` API rather than the API 35 UMP interfaces: MIDI 1.0 over USB is
what the target keyboards speak, and adding a second transport before the mechanic is validated
would be premature.

---

## Milestone 1 - Flash timeline

### Build

- `timing/AttemptClock`: anchors an `AttemptTimeline` to a `MonotonicClock` and reports the
  current beat and phase.
- `timing/MetronomeEngine` on `AudioTrack`: render one click buffer once, then write clicks into
  a continuous stream at computed sample offsets.
  The click spacing has to come from sample positions in the stream, never from sleeping between
  writes, or the pulse will wander.
- `attempt/AttemptController`: drives `AttemptState` from the clock and exposes it as a `Flow`.
- `ui/practice/PracticeScreen`: a placeholder notation area that appears and disappears at the
  instants the timeline dictates.
- Record the device's reported audio output latency now, so Milestone 3 has it available.
  Do not correct for it yet.

### Tests

- Drive a whole attempt through a fake `MonotonicClock` and assert the state at exact
  nanosecond boundaries, with no real waiting.
- The plan's reference timeline: 60 BPM, four-beat count-in, two-beat preview, and the notation
  appears at 2 s and vanishes at 4 s.
- Twenty consecutive attempts end where arithmetic says they should, proving nothing accumulates.
- Every transition in `AttemptState`, including the interrupted ones: MIDI disconnect during
  count-in, app backgrounded mid-attempt, attempt cancelled, MIDI arriving before the performance
  starts, MIDI still arriving after the attempt window closes.

### Exit criterion

A one-measure exercise appears, stays visible for N beats, disappears, and the performance begins,
with no visible drift across a long session.

---

## Milestone 2 - The complete loop

This is the first real product milestone.

### Build

- 10 to 20 hand-authored one-measure exercises: treble clef, one hand, monophonic, quarter and
  half notes, constrained range.
- `exercise/BundledExerciseRepository` reading them from assets.
- Notation rendering (see the decision below).
- `evaluation/PitchEvaluator`: match played note-ons against expected notes in order, and classify
  each as correct, wrong pitch, missing or extra.
- `attempt/AttemptRepository`: persist the attempt, its raw MIDI and its evaluation in one
  transaction.
- A compact result screen: the score, the annotated notes, and Next.

### Tests

Captured MIDI fixtures, replayed deterministically, for each of: a perfect performance, a wrong
pitch, a missing note, an extra note, a repeated note, an early note, a late note and a pause.
The same fixture must always produce the same result; the evaluator has no clock of its own.

### Exit criterion

A player can see, memorise, play, receive a score and continue, twenty times in a row, without
developer intervention.

### Decision to make: how notation gets drawn

The product plan proposes ingesting MusicXML with Verovio offline and bundling the rendered SVG.
The difficulty is that Android has no native SVG renderer, so "bundle SVG" really means either
converting to `VectorDrawable` offline or embedding a WebView.

Two routes are worth weighing, and the `score` boundary keeps the choice reversible either way.

1. **Offline Verovio to `VectorDrawable`.**
   Faithful engraving with no runtime engine, but the conversion is a build-time pipeline to
   maintain, and Verovio output leans on SMuFL glyph paths that converters handle unevenly.
2. **Draw from `ScoreNote` with Compose Canvas and a SMuFL font such as Bravura.**
   One measure of monophonic treble notation is staff lines, a clef, a handful of glyphs and
   stems.
   It needs no asset pipeline, it scales cleanly to any screen, and it is driven by the same model
   the evaluator reads.

Route 2 is the recommendation for V1, with the caveat that it stops being cheap somewhere around
chords, two hands and beamed groups.
Reassess at Milestone 5; that is the natural point to bring Verovio back for the harder notation
rather than the natural point to regret not having it.

---

## Milestone 3 - Rhythm evaluation

### Build

- Estimate the player's beat phase from the performance itself, using the configured BPM as a
  tempo prior.
  Scoring MIDI timestamps directly against the scheduled click would make every player on a
  high-latency device look systematically late.
- Onset matching, note duration, early/late bias, tempo stability, pauses and pulse loss.
- Introduce `RhythmResult`, raise the evaluator version to 2, and add the Room migration.
  Old attempts stay scored by version 1 until they are re-evaluated, which is exactly what the
  composite key is for.

### Tests

Fixtures for right notes with wrong rhythm, wrong notes with right pulse, a mid-bar pause and a
gradual tempo drift.

### Exit criterion

Feedback reliably distinguishes right notes with wrong rhythm from wrong notes with right pulse.

---

## Milestone 4 - Difficulty progression

### Build

- A recent-performance model over the stored attempt history.
- The controller from the product plan: above 90 percent success move up, below 65 percent move
  down, otherwise hold.
- Move one dimension at a time, preview or musical difficulty, never both.
- `exercise/ExerciseSelector` picking the next exercise from the current state.
- Persist the state in `UserDifficultyStateEntity`.

### Tests

The controller is pure and takes history as input, so drive it with synthetic histories and assert
the exact ladder step it lands on.
Deliberately include the oscillation case: alternating success and failure must not thrash between
levels.

### Exit criterion

Ten minutes of practice feels matched to the player without any manual configuration.

---

## Milestone 5 - Musical expansion

Add dimensions in this order, because each one adds a single failure mode to the evaluator rather
than several: eighth notes, rests, accidentals, wider ranges, bass clef, chords, two hands,
additional meters.

Each dimension needs fixtures and evaluator tests before it is allowed into adaptive selection.
This is also the point to revisit notation rendering, and the point where chord completeness,
chord synchronisation and hand synchronisation enter evaluation.

---

## Milestone 6 - Real corpus

Only now does the retrieval-first architecture earn its place: MusicXML ingestion, segmentation,
feature extraction, an exercise database and learner-specific selection.
Exercises stop being bundled assets and become rows, which is the first genuine reason for a
backend.

---

## Milestone 7 - Learner model

Track weakness per dimension, and add the flash-specific one the mechanic makes possible:
`minimumPreviewByMusicalContext`, the shortest preview at which a player still reads a given kind
of passage accurately.

The question the app can then answer is not "what music suits this player" but "what should this
player see next, and for how long".

---

## Cross-cutting

### Testing

Everything that can be a JVM unit test should be.
The three seams that make that possible are already in place: `MonotonicClock` for time,
`ScoreNote` versus `MidiEvent` for evaluation, and repository interfaces for content and history.

Instrumented tests are reserved for what genuinely needs a device: the Room schema, MIDI transport
and audio output.

### Interruptions

These are not edge cases, they are ordinary practice.
Every one of them needs a defined outcome before Milestone 2 ships: the keyboard disconnects
mid-attempt, the app is backgrounded during the count-in, the player abandons an attempt, notes
arrive before the performance window opens, and notes keep arriving after it closes.

### What is deliberately not being built

No AI generation, no ML, no learner embeddings, no runtime MusicXML editing, no accounts, no
cloud sync, no social features, no teacher dashboards, no microphone pitch detection and no
general-purpose performance alignment engine.
The constrained flash exercise has to prove it needs any of that first.
