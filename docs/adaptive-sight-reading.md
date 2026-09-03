# Adaptive Sight-Reading Trainer

> Android-native, MIDI-driven sight-reading practice with **Flash Sight Reading** as the first complete product feature.

---

## 1. Product direction

### North-star loop

**Show unseen music → hide it → capture MIDI performance → evaluate the attempt → diagnose errors → adjust difficulty → repeat.**

The long-term product remains an adaptive sight-reading trainer, but the first complete experience is deliberately narrower:

> Can the player look at a short piece of notation for a limited amount of time, retain it, and perform it accurately without seeing the music?

This gives the application a distinctive training mechanic while exercising most of the infrastructure needed later:

- score representation;
- score rendering;
- MIDI input;
- musical timing;
- performance evaluation;
- attempt history;
- difficulty modeling;
- adaptive exercise selection.

---

# 2. First complete feature: Flash Sight Reading

## 2.1 Core exercise

The user receives a short unseen passage, initially **one measure**.

A metronome gives a one-measure count-in.

During part or all of that count-in, the notation is visible.

The notation disappears exactly when the performance begins.

The player performs the passage from memory while the app captures MIDI.

The app then evaluates the performance and immediately presents the next exercise.

### Example: 4/4

Treat the count-in and performance as one continuous beat timeline:

```text
Count-in                    Performance
1       2       3       4 | 5       6       7       8
                            ^
                            music starts here
```

The easiest configuration shows the notation for all four count-in beats:

```text
VISIBLE
1-------2-------3-------4--|5
                           HIDE
```

Harder configurations shorten the preview:

```text
Preview = 3 beats
        2-------3-------4--|5

Preview = 2 beats
                3-------4--|5

Preview = 1 beat
                        4--|5

Preview = 0.5 beats
                          4&|5
```

Rather than representing these as separate modes, model them with one parameter:

```text
previewDurationBeats
```

For 4/4:

| Difficulty | `previewDurationBeats` |
|---|---:|
| Full count-in | 4.0 |
| Beats 2–4 | 3.0 |
| Beats 3–4 | 2.0 |
| Beat 4 | 1.0 |
| Last eighth note | 0.5 |
| Last sixteenth | 0.25 |

This generalizes naturally to 3/4, 6/8, changing tempos, and other exercises.

---

## 2.2 Initial exercise configuration

Separate **what the player reads** from **how the flash works**.

### Flash configuration

```text
FlashConfig
- tempoBpm
- timeSignature
- countInMeasures
- previewDurationBeats
- metronomeDuringAttempt
```

Initial defaults:

```text
timeSignature          = 4/4
countInMeasures        = 1
previewDurationBeats   = 4
metronomeDuringAttempt = false
```

The metronome therefore establishes the pulse, but disappears when the player starts.

Later, `metronomeDuringAttempt = true` can become an easier training variant.

### Exercise configuration

```text
ExerciseConfig
- measureCount
- clef
- key
- pitchRange
- noteValues
- rests
- maximumInterval
- chordSize
- hand
- polyphony
- rhythmicPatterns
```

Do **not** expose all of these in the first UI.

They are internal dimensions that eventually drive presets and adaptation.

---

# 3. V1 scope

## 3.1 Start much smaller than the eventual sight-reading system

The first polished version should probably support:

- one measure;
- 4/4;
- treble clef;
- one hand;
- monophonic notation;
- quarter notes and half notes;
- a constrained pitch range;
- one-bar count-in;
- USB MIDI keyboard;
- several preview durations;
- pitch and basic rhythm scoring.

Then progressively add:

1. eighth notes;
2. rests;
3. accidentals;
4. wider intervals;
5. bass clef;
6. chords;
7. two hands;
8. multiple measures;
9. additional meters;
10. polyphony.

This gives every added musical dimension a measurable effect rather than introducing the entire notation problem simultaneously.

---

# 4. Exercise lifecycle

Each Flash Sight Reading attempt should have an explicit state machine.

```text
READY
  ↓
COUNT_IN
  ↓
PREVIEW_VISIBLE
  ↓
PREVIEW_HIDDEN
  ↓
PERFORMING
  ↓
EVALUATING
  ↓
RESULT
  ↓
NEXT_EXERCISE
```

In many configurations, `COUNT_IN` and `PREVIEW_VISIBLE` overlap.

For example, with a four-beat count-in and a two-beat preview:

```text
Beat             1       2       3       4       5
Metronome        ●       ●       ●       ●
Score                            SHOW------------HIDE
Performance                                      START
```

The important engineering rule is:

> Audio, MIDI capture, score visibility, and performance start must all reference the same monotonic attempt clock.

Avoid having independent UI timers controlling the exercise.

---

# 5. Performance evaluation

Flash Sight Reading simplifies the original alignment problem because:

- exercises are short;
- the expected tempo is known;
- the expected performance is known;
- the user cannot stop to reread the score.

The initial performance engine therefore does **not** need the full general-purpose score↔performance alignment system.

## 5.1 Phase 1 — pitch correctness

For the first playable prototype:

- expected notes;
- played notes;
- missing notes;
- extra notes;
- wrong pitches;
- completion.

Example result:

```text
7 / 8 notes correct

Wrong:
Beat 3: expected F4, played G4

Missing:
Beat 4: C5
```

This is enough to prove the complete loop.

## 5.2 Phase 2 — rhythm

Then add:

- onset timing;
- duration;
- early/late bias;
- tempo stability;
- pauses;
- pulse loss.

Do not score absolute MIDI timestamps directly against the scheduled audio click without accounting for device audio latency.

Instead, allow the performance engine to estimate the player's beat phase from the MIDI performance while using the configured BPM as the tempo prior.

That avoids making someone appear systematically late simply because their Android device has output latency.

## 5.3 Phase 3 — richer notation

Later:

- chord completeness;
- chord synchronization;
- hand synchronization;
- recovery after errors;
- local tempo changes;
- polyphonic alignment.

At that point the more sophisticated sequence-alignment architecture from the original plan becomes valuable.

---

# 6. Feedback

Keep feedback extremely compact during practice.

A user should be able to move from one exercise to the next in seconds.

### Immediate result

```text
Pitch       92%
Rhythm      88%
Continuity  Good
```

Then annotate the score:

```text
✓ ✓ ✓ ✗ ✓ ✓
```

Allow detail on demand, but do not make every attempt feel like an analytics dashboard.

### Session summary

After a session:

```text
12 exercises

Pitch accuracy      91%
Rhythm accuracy     84%
Average preview     1.8 beats

Strong:
- stepwise reading
- repeated notes

Needs work:
- ascending 4ths
- eighth-note patterns
```

---

# 7. Difficulty

Flash Sight Reading introduces an important distinction:

> **Notation difficulty** and **preview difficulty are separate variables.**

A simple passage shown for half a beat can be difficult.

A harder passage shown for four beats can also be difficult.

Model them independently.

## 7.1 Preview difficulty

Primary variable:

```text
previewDurationBeats
```

Possible progression:

```text
4 → 3 → 2 → 1.5 → 1 → 0.75 → 0.5 → 0.25
```

Avoid assuming these steps are psychologically linear.

Eventually the app should learn how strongly each reduction affects a particular player.

## 7.2 Musical difficulty

Examples:

- pitch range;
- interval size;
- note density;
- rhythmic complexity;
- rests;
- accidentals;
- register;
- chords;
- hand independence;
- polyphony.

## 7.3 Initial adaptive rule

Do not start with ML.

Use a simple controller.

For example:

```text
if recentSuccess > 90%:
    slightly increase difficulty

if recentSuccess < 65%:
    slightly decrease difficulty

otherwise:
    keep approximately the same level
```

But vary only **one dimension at a time** when possible.

Example:

```text
Current:
preview = 2 beats
musicDifficulty = 3

Player succeeds consistently.

Next:
preview = 1.5 beats
musicDifficulty = 3
```

This makes the adaptation interpretable.

---

# 8. Android architecture

## 8.1 Application

Use a single Android application.

```text
Kotlin
Jetpack Compose
Coroutines / Flow
Room
Android MIDI API
```

Recommended minimum SDK:

```text
minSdk = 23
```

The Android MIDI framework is available from API 23, so there is little value in supporting older Android versions for this product.

Jetpack Compose is a good fit for the surrounding application UI and keeps the project Kotlin-native.

Do not introduce a backend for V1.

---

## 8.2 Logical modules

```text
app/
│
├── exercise/
│   ├── Exercise
│   ├── ExerciseRepository
│   └── ExerciseSelector
│
├── score/
│   ├── ScoreModel
│   └── ScoreRenderer
│
├── midi/
│   ├── MidiDeviceManager
│   ├── MidiCapture
│   └── MidiEvent
│
├── timing/
│   ├── AttemptClock
│   └── MetronomeEngine
│
├── attempt/
│   ├── AttemptController
│   ├── AttemptState
│   └── AttemptRepository
│
├── evaluation/
│   ├── PerformanceEvaluator
│   ├── PitchEvaluator
│   └── RhythmEvaluator
│
└── session/
    ├── Session
    └── SessionSummary
```

Keep these as modules/packages within one application rather than separate services.

---

# 9. Score representation and rendering

Avoid making runtime MusicXML support a requirement for the first feature.

There are really two separate problems:

1. **representing the music for analysis;**
2. **drawing notation for the player.**

Keep them separate.

## 9.1 Canonical score model

The application should operate on something like:

```text
ScoreNote
- id
- pitch
- onsetBeat
- durationBeats
- voice
- hand
- chordId
```

The evaluator should never need to inspect SVG or MusicXML.

---

## 9.2 Rendering strategy

For the first content pack:

```text
MusicXML
   ↓
offline ingestion
   ├── canonical exercise data
   └── rendered SVG
```

Bundle both with the application.

This avoids putting a complex notation engine in the critical Android runtime path.

Verovio is a reasonable ingestion/rendering candidate because it can import MusicXML and produce SVG.

Later, if exercises need runtime transposition, responsive engraving, or dynamically generated notation, reevaluate:

- Verovio through a local WebView;
- a native integration;
- another Android-compatible renderer.

Do not solve that problem before it exists.

---

# 10. Metronome and timing

Timing deserves its own subsystem.

The exercise controller should schedule everything against a monotonic clock:

```text
attemptStart
previewStart
previewEnd
performanceStart
performanceEnd
```

Avoid:

```text
delay(1000)
delay(1000)
delay(1000)
```

as the source of musical truth.

A delay-based coroutine is fine for UI transitions, but elapsed delays should not define the authoritative musical timeline.

## Initial audio approach

Start with Android `AudioTrack`.

If device testing shows unacceptable click jitter or output latency, investigate a lower-level Oboe implementation rather than introducing native C++ immediately.

Oboe's own guidance notes that Kotlin/Java `AudioTrack` can be sufficient and recommends weighing the latency improvement against JNI complexity.

---

# 11. Data model

Keep the initial persistence model small.

```text
Exercise
ExerciseContent
Attempt
MidiEvent
EvaluationResult
Session
UserDifficultyState
```

### Attempt

```text
Attempt
- id
- exerciseId
- startedAt
- tempo
- previewDurationBeats
- configSnapshot
- evaluatorVersion
```

### MIDI event

```text
MidiEvent
- timestampNanos
- type
- channel
- pitch
- velocity
```

Raw MIDI events should be retained.

Derived evaluations can be recomputed when the evaluator improves.

---

# 12. Local-first V1

The entire first product should work offline.

```text
Android app
│
├── bundled exercises
├── bundled notation
├── MIDI capture
├── evaluation
├── Room attempt history
└── difficulty state
```

No account.

No backend.

No cloud corpus.

No ML service.

No microservices.

Later, a server can provide:

- content packs;
- cross-device profiles;
- corpus search;
- richer adaptive selection;
- aggregate difficulty models.

But none of those are necessary to determine whether Flash Sight Reading is useful.

---

# 13. First content strategy

Do not begin by curating thousands of excerpts.

For Flash Sight Reading, the first content pack can be deliberately controlled.

Start with roughly:

```text
50–100 one-measure exercises
```

Cover combinations such as:

- repeated notes;
- stepwise motion;
- thirds;
- fourths;
- fifths;
- basic rhythmic patterns;
- gradually expanding ranges.

This controlled material is actually useful during development because bugs become reproducible.

Once the exercise engine works, introduce real repertoire and retrieval.

---

# 14. Delivery roadmap

## Milestone 0 — Android + MIDI spike

### Build

- Compose application;
- MIDI device discovery;
- connect/disconnect keyboard;
- capture note-on/note-off;
- display received notes.

### Exit criterion

A physical MIDI keyboard can reliably play notes into the Android application.

---

## Milestone 1 — Flash timeline

### Build

- metronome;
- count-in;
- exercise screen;
- preview visibility;
- `previewDurationBeats`;
- shared attempt clock.

No scoring yet.

### Exit criterion

A one-measure exercise can reliably:

```text
appear → remain visible for N beats → disappear → begin performance
```

without visible timing drift.

---

## Milestone 2 — Complete Flash Sight Reading loop

### Build

- canonical score events;
- MIDI attempt capture;
- pitch evaluation;
- result screen;
- next exercise.

### Exit criterion

A user can repeatedly:

```text
see → memorize → play → receive score → continue
```

without developer intervention.

**This is the first real product milestone.**

---

## Milestone 3 — Rhythm evaluation

### Build

- beat-phase estimation;
- onset matching;
- rhythm accuracy;
- tempo stability;
- pauses.

### Exit criterion

Feedback distinguishes:

```text
right notes / wrong rhythm
```

from:

```text
wrong notes / right pulse
```

reliably.

---

## Milestone 4 — Difficulty progression

### Build

- preview difficulty;
- musical difficulty;
- recent performance model;
- automatic progression/regression.

### Exit criterion

Ten minutes of practice feels approximately matched to the player's level without manual configuration.

---

## Milestone 5 — Musical expansion

Incrementally add:

- eighth notes;
- rests;
- accidentals;
- wider ranges;
- bass clef;
- chords;
- two hands;
- additional meters.

Each dimension should have fixtures and evaluator tests before becoming part of adaptive selection.

---

## Milestone 6 — Real corpus

Only now bring back the larger retrieval-first architecture:

```text
MusicXML corpus
    ↓
ingestion
    ↓
segmentation
    ↓
feature extraction
    ↓
exercise database
    ↓
learner-specific selection
```

The original larger adaptive vision begins here.

---

## Milestone 7 — Learner model

Track weaknesses such as:

- interval reading;
- rhythm reading;
- accidentals;
- left/right hand;
- chords;
- pulse maintenance;
- preview-memory tolerance.

A useful Flash-specific learner dimension becomes:

```text
minimumPreviewByMusicalContext
```

For example:

```text
Simple stepwise melody:
comfortable at 0.75 beats

Large leaps:
comfortable at 2 beats

Chords:
comfortable at 3 beats
```

That could become one of the application's most interesting adaptive signals.

---

# 15. Things explicitly deferred

Do not build these for the first product:

- AI music generation;
- ML;
- advanced learner embeddings;
- huge repertoire corpus;
- runtime general-purpose MusicXML editor;
- accounts;
- cloud synchronization;
- social features;
- teacher dashboards;
- microphone pitch detection;
- fingering detection;
- computer vision;
- microservices.

Also defer a fully general performance alignment engine until the constrained Flash exercises prove that it is required.

---

# 16. Engineering tests that matter early

## MIDI fixtures

Replay captured MIDI for:

- perfect performance;
- wrong pitch;
- missing note;
- extra note;
- repeated note;
- early note;
- late note;
- pause.

The evaluator should be deterministic.

## Timing tests

Given:

```text
tempo = 60 BPM
countIn = 4 beats
previewDuration = 2 beats
```

the expected timeline is always:

```text
0s    beat 1
1s    beat 2
2s    beat 3 / preview appears
3s    beat 4
4s    performance starts / preview disappears
```

The timing engine should be testable without waiting four real seconds by injecting a clock.

## State-machine tests

Test every legal transition.

Especially:

- MIDI disconnect during count-in;
- app backgrounded during exercise;
- exercise canceled;
- MIDI arrives before performance start;
- MIDI continues after timeout.

---

# 17. First implementation backlog

1. Create Android project with Compose.
2. Implement MIDI device discovery.
3. Capture raw MIDI events with monotonic timestamps.
4. Create the canonical `ScoreNote` representation.
5. Hard-code one one-measure exercise.
6. Render that exercise.
7. Build the metronome.
8. Create the central `AttemptClock`.
9. Implement the attempt state machine.
10. Implement `previewDurationBeats`.
11. Hide notation at performance start.
12. Record one MIDI attempt.
13. Implement exact pitch matching.
14. Show a result.
15. Add “Next”.
16. Add 10–20 test exercises.
17. Persist attempts with Room.
18. Add rhythm evaluation.
19. Expand to 50–100 exercises.
20. Add automatic preview difficulty.

At step **15**, you already have something worth repeatedly playing yourself.

---

# 18. MVP acceptance criteria

## MIDI

- USB MIDI keyboard connects reliably.
- Disconnect/reconnect works without restarting.
- Note-on, note-off, pitch, velocity, and timestamps are retained.

## Flash timing

- One-measure count-in is stable.
- Preview duration supports fractional beats.
- Score disappearance and performance start share the same timeline.
- Repeated attempts do not accumulate timer drift.

## Score

- One-measure notation is clearly readable on Android phone and tablet screens.
- Score representation is independent from rendering.

## Evaluation

- Correct notes are recognized.
- Missing, extra, and wrong notes are detected.
- Basic rhythmic mistakes are identified.
- Evaluation is deterministic from stored MIDI.

## Session UX

A player can complete at least:

```text
20 consecutive flash exercises
```

without configuration or developer intervention.

## Data

- Raw MIDI is preserved.
- Attempt configuration is preserved.
- Evaluator version is preserved.
- Historical attempts can be reevaluated later.

---

# 19. Product validation milestone

Before investing heavily in adaptive retrieval or ML, answer this:

> **Does shortening the preview window produce a compelling sight-reading exercise that players want to repeat?**

Then:

> **Can the application identify which kinds of notation require more preview time for a particular player?**

If both answers are yes, the original adaptive architecture becomes much more valuable.

Instead of merely asking:

> “What music is appropriate for this player?”

the system can eventually ask:

> “What music should this player see next, and how long should they be allowed to see it?”

That combination of **musical difficulty + visual exposure time** can become the central adaptive mechanic of the product.