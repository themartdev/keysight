# KeySight: Adaptive Sight-Reading Trainer

> Android-native, MIDI-driven sight-reading practice built on a masked, continuous score.

This document is the product plan.
Its round ladder in section 13 drives development; `CLAUDE.md` describes the code as it is.
When the two disagree, the code is right about the present and this document is right about the direction.

---

## 1. Product direction

### North-star loop

Show unseen music, control how much of it the player may see and for how long, capture the MIDI performance, evaluate it, and adjust the difficulty for the next segment.

The distinctive mechanic is **visual exposure as a difficulty variable**.
A passage the player may see for four beats before playing it is a different exercise from the same passage seen for half a beat, and both differ from a passage the player may see at any time except while playing it.

The long-term product is an adaptive trainer that learns which kinds of notation need how much exposure for a particular player.
The near-term product is a practice mode that mimics real sight reading: a real score, read page by page, with the notes obscured according to a chosen policy.

### Where we are

Milestones 0 to 3 of the original plan are built and verified on a device: MIDI capture, the shared attempt clock and metronome, staff engraving with Bravura, pitch evaluation, and rhythm evaluation with beat-phase estimation.
They were built around a discrete attempt on a single one-measure exercise shown then hidden in full.
Section 2 replaces that model with the continuous masked score.
Everything below the presentation layer carries over.

---

## 2. The mechanic: a masked, continuous score

### 2.1 The run

A **run** is one continuous performance from Start to Stop, against one tempo, one configuration, and one monotonic clock.
The score of a run is a sequence of **segments**, one measure each by default.
Segment 0 is silent: it is engraved as a measure of rest and the metronome counts it in.
Segments 1 to n are performed one after the other on the same beat line, without stopping.

A run can be of fixed length or open-ended.
An open-ended run keeps generating segments until the player stops, and the page keeps turning.

### 2.2 The mask

The score is never hidden as a whole.
Instead, a **visibility policy** decides, for every segment and every beat, whether that segment's notes are drawn.
The staff, clefs, key and time signatures, and barlines are always drawn, so the player is always looking at a real score with some notes missing.

The policy has three parameters, all defined on the beat line relative to the segment being decided:

```text
VisibilityPolicy
- lookaheadBeats   how long before a segment starts its notes appear; unbounded means always
- hideWhilePlaying whether a segment's notes are hidden from its first beat to its last
- showAfter        whether a segment's notes come back once it is over
```

Every mode is a preset over those three:

| Mode | lookaheadBeats | hideWhilePlaying | showAfter | What it trains |
|---|---:|---|---|---|
| Flash | 4, 3, 2, 1.5, 1, 0.75, 0.5, 0.25 | yes | yes | Retention: read it, then play from memory |
| Read ahead | unbounded | yes | yes | Reading ahead: everything is visible except the bar being played |
| Open score | unbounded | no | yes | Plain sight reading, the baseline and the warm-up |

Flash with a one-measure lookahead is the original Flash Sight Reading exercise: the next bar appears while the current one is played from memory.
The original preview ladder is now the lookahead ladder.

The mask is a function of the beat, computed from the timeline on every frame, and never a timer.
The rule from the original plan stands: the notes of a segment disappear exactly on the beat its performance starts.

### 2.3 Pages

The score is read from a static page, not a scrolling strip, because real sight reading trains the eye to move across a fixed system while the hands play.

- A **system** is a horizontal row of measures across both staves, justified to the screen width.
- A **page** is a stack of systems, two at a time on a phone.
- The player reads the top system; when the cursor leaves it, the lower system moves up and a newly laid-out system fills the bottom.
- The next system is therefore always on screen before it is needed, so lookahead across a system boundary works.
- Measures per system depend on orientation, roughly two in portrait and four in landscape on a phone, decided by the layout width in staff spaces.

A thin cursor marks the current beat position on the current system.
Timing marks and pitch marks appear on a measure once its evaluation is committed, provided the mode shows the past.

### 2.4 Configuration

Flash-side and music-side configuration stay separate.

```text
RunConfig
- tempoBpm
- metronome        COUNT_IN_ONLY | THROUGHOUT
- mode             the VisibilityPolicy preset
- lookaheadBeats   for Flash mode
- length           fixed segment count, or open-ended

ExerciseConfig     what the generator produces, see section 7
- keySignature
- hands            right hand, left hand, or both
- accompaniment    with both hands: the other staff rests, or holds a tone of the tonic triad
- rightHandRange, leftHandRange    spelled in C, so a range is a hand position that moves with the key
- noteValues       whole, half, quarter, eighth; the rhythm vocabulary is every way to fill the measure with them in which only a note shorter than the beat starts off the beat
- rests            whether the vocabulary also holds a rest of one of those values between notes
- accidentals      whether one note of the bar may be a chromatic neighbour, written with an accidental
- maxInterval      the largest distance between consecutive melody notes, in letters
- timeSignature
```

Only the mode, the lookahead, the length, the key, the hands, the accompaniment, and the tempo are exposed in the UI.
The rest are generator dimensions the difficulty controller moves on the player's behalf.
Dotted values, syncopation and chords within a hand are dimensions still to add, each once the layout can draw it.

---

## 3. Scope now and next

Built and verified:

- USB MIDI capture with monotonic timestamps;
- one shared attempt timeline, an `AudioTrack` metronome anchored to the audio timestamp;
- treble or bass staff engraving with Bravura, quarter and half notes, C major;
- pitch evaluation by edit-distance alignment over pitch and onset;
- rhythm evaluation with bounded beat-phase estimation, timing marks, tempo ratio, pauses, and continuity;
- Room history with raw MIDI, score and config snapshots, evaluations keyed by evaluator version;
- the grand staff, key signatures, systems and pages, the per-tick mask;
- the continuous run's presentation: the run timeline with a silent segment 0, the visibility policy and its three presets, the mask on every frame, the cursor, the page turn, the run reducer and controller, bundled measures chained in one key;
- the continuous run's evaluation: segments committed one beat after they end from a three-segment window, the running beat phase, marks behind the cursor, runs and segments in the database with attempts migrated, the run summary, open-ended runs;
- the generator: every segment from an `ExerciseConfig` and a seed, in every key, on either staff or both, with the other hand resting or holding a note; both staves aligned as one stream of chords; seeds and configurations stored per segment and the run seed per run;
- the difficulty controller: a window of recent committed bars, one dimension moved at a time, the lookahead between runs and the music within an open-ended run, its state stored, every move named on the summary;
- eighth notes: flags and beams in the layout, a rhythm vocabulary in which eighths come in pairs on a beat, the rung above quarters on the rhythm ladder, fixtures on both clefs in three keys;
- rests: silences derived from the score and split into rests by the layout, a rest of any note value in the vocabulary where one may go, the rests rung after rhythm on the controller's walk, fixtures on both clefs in three keys;
- accidentals: one chromatic neighbour per bar resolving by step, spelled by transposition so a natural appears where the key alters the letter and never a double, the accidentals rung after rests on the controller's walk, fixtures on both clefs in four keys;
- history and the session summary: sessions newest first, each expandable to its pooled summary and its runs, every run opening its annotated page, every judgement re-evaluated to the current evaluator version as it is read.

Next, in the order of section 13, one generator dimension at a time: chords, other meters, syncopation, and the cadence on the last bar of a fixed-length run.

---

## 4. Run lifecycle

```text
READY
  ↓ Start
COUNT_IN        segment 0, metronome sounding, segments masked by the policy
  ↓ beat n(0)
PERFORMING      segments 1..n, mask and cursor follow the beat, evaluation commits behind the cursor
  ↓ Stop, or the last segment's capture tail ends
SUMMARY
  ↓ Next
READY
```

Every scheduled instant of a run is computed from its absolute beat and the tempo, never accumulated, so a long run cannot drift.

The engineering rule from the original plan is unchanged and still the most important one:

> Audio, MIDI capture, the mask, the cursor, the page turn, and evaluation windows all reference the same monotonic clock.

Legal interruptions, each a tested transition:

- MIDI device disconnects during the run: the run aborts and is stored as aborted with everything captured so far.
- The app is backgrounded: the run aborts the same way.
- The player stops: the run ends after the current segment's capture tail and is summarised.
- MIDI arrives before beat n(0): captured, attributed to no segment, kept.
- The player stops during the count-in: nothing was performed, so the run is cancelled rather than summarised.

---

## 5. Performance evaluation

### 5.1 What stays

The evaluator sees `ScoreNote` and `MidiEvent`, never notation.
It is deterministic from stored MIDI, versioned by `EVALUATOR_VERSION`, and every judgement change bumps the version.

Pitch is an edit-distance alignment over pitch and onset that yields correct, wrong-pitch, missing, and extra outcomes.
Rhythm measures each matched note's onset error after removing the player's beat phase, then reports per-note timing, tempo ratio, pauses, and continuity.

The beat phase absorbs device latency and the player's habitual lean, bounded to a fraction of a beat, so nobody appears late merely because their phone has output latency.

### 5.2 Incremental evaluation

A run can be long or endless, so the evaluator works on a trailing window.

- Segment k is **committed** once the cursor has passed its end plus the capture tail, from a window of three segments: the notes of k-1 that were committed missing, k itself, and k+1.
- The played notes of the window are those from the start of k-1 to the end of k's tail that no earlier commit has consumed, and only the MIDI that had arrived by the commit is seen, so a live run and a replay from history agree.
- Boundary notes therefore land in the right segment: the downbeat of k+1 played in k's tail is that downbeat, not an extra of k; a final note of k that arrives after k was committed is absorbed by the missing note and is neither correct nor an extra.
- Every played note ends up with exactly one committed outcome, every expected note with exactly one, and a commit is never revised.
- Committing is what makes marks appear on the page and what feeds the difficulty controller.

### 5.3 A running beat phase

One phase per run is not enough.
Without a click after the count-in, a player's pulse drifts over a long run, and a fixed phase would call every late-run note wrong.

- The phase is re-estimated at every commit from the segment's on-pulse residuals, with the previous phase as the prior and a bounded step per segment: an eighth of a beat without the click, a fiftieth with it, the first segment setting the phase outright.
- The tempo ratio is likewise a per-segment estimate; the run's is their mean.
- With the metronome throughout, the phase stays within the latency bound and barely moves; without it, the estimate follows the player, a slow drift but never a jump.

### 5.4 Two hands

Both staves are aligned as one stream of onset-grouped chords.
Each outcome is attributed to a hand through the expected note's staff, so hand-specific feedback falls out without a second alignment.
Polyphonic alignment that tolerates one hand lagging the other is deferred until the union alignment proves insufficient.

---

## 6. Feedback

Feedback stays compact.
During a run, feedback is the marks on the page behind the cursor, nothing else.

Rules for what is said:

- **Every remark must point at something visible on the page.**
  A lean remark is made only when there is an early or late mark for it to explain; a passage played entirely on the player's own pulse earns no remark, whatever the phase estimate says.
- Marks are the only place evaluation meets notation, through `noteMarks`.
- Detail is available on demand from the summary, never pushed.

### Run summary

```text
16 bars   Flash 2 beats   G major   both hands

Pitch       91%
Rhythm      84%
Continuity  Good

Weakest bars: 7, 12
Needs work: descending 4ths, left-hand entries
```

The "needs work" lines arrive with the generator, since it knows what each segment was made of.

### Session summary

A session is the practice screen's lifetime: it opens with the first run recorded and closes when the screen is left.
Its summary is the history screen's top card, expanded when the player arrives from practice, and the same card serves every past session:

```text
This session
3 runs   14 bars
Pitch 91%   Rhythm 84%

Started: 4 beats ahead, up to thirds, five notes, quarter notes, no rests, no accidentals.
Ended: 3 beats ahead, up to fourths, five notes, quarter notes, no rests, no accidentals.
Harder from bar 3 of run 2: up to fourths
Harder from run 3: 3 beats ahead

Weakest bars: Run 1, bar 2   Run 3, bar 2
```

The numbers are pooled by their counts, so the session's pitch accuracy is the sum of the runs' correct notes over the sum of their expected notes, the same numbers each run's score line shows.
The moves are read off the stored configurations, within a run at the bar the level changed and between runs at the run, and only what the stored data proves is listed: a change of mode, key or hands is the player's and is not a move.
Every move and every weakest bar names a run of the list below and opens that run's page, the whole score open with its marks, so every remark points at something visible.
A run whose stored judgement is older than the current evaluator is re-evaluated from its segments and raw MIDI as it is read, and the result is stored beside the old judgement, never over it.

---

## 7. Content: generated, not curated

Endless runs, every key, and two staves cannot be served by hand-written measures.
Content comes from a **generator**: `ExerciseConfig` plus a seed to a `Score`, deterministic, tested against its own constraints.

Principles:

- Generate in C and transpose diatonically into the requested key, so key coverage is free and spelling is consistent.
- Constrained random walk with a contour (steps that carry on in the bar's direction weigh twice the others, and the direction turns at the edge of the range), and a rhythm vocabulary that is every way to fill the measure with the allowed note values, drawn uniformly.
  The vocabulary keeps to readable rhythms: only a note shorter than the beat may start off the beat, so eighths come in pairs that fill a beat and nothing is syncopated until syncopation is a rung of its own.
  With rests on, a silence of one of the note values may follow a note: never first in the measure, never after another rest, and only at a multiple of its own length, so a half rest starts on beat 1 or 3 of 4/4 and an eighth rest sits on either half of a beat.
  A rest is the absence of a note: the score has no rest type, the generator writes a gap, and the layout draws the rest.
  With accidentals on, one note of the bar is a chromatic neighbour: a note of the walk followed without a rest by a whole-tone step, raised or lowered by a semitone so that it resolves to the next note by that step, a sharp resolving up or a flat resolving down; never the last note of the bar, never before a rest, and none in a bar that has no such step.
  The altered note keeps its letter, so the interval rule, measured in letters, stands, and the spelling in the key follows from transposition: a natural appears exactly where the key signature alters the letter, and a double is respelled as the natural of the neighbouring letter, so a raised sixth in A is G natural resolving to G sharp, never F double sharp.
  A cadence on the last segment of a fixed-length run is a per-segment configuration difference for a later round.
- Each musical dimension is a generator parameter with fixtures and evaluator tests before the controller may move it.
- The seed, the generator version, and the parameters are stored with every segment, and so is the resulting score, because generators change and history must re-evaluate on its own.
  A run has one seed; segment k's seed is derived from it and k, so one stored run seed reproduces the run and every segment reproduces itself.
- The generator's chance comes from its own SplitMix64 stream, not the platform's random, so a stored seed keeps its meaning across Kotlin versions.

The 18 bundled measures, the four eighth-note measures, the four rest measures and the four accidental measures remain as test fixtures: the layout engine and the generator's constraints are held to real content.

---

## 8. Difficulty

Exposure difficulty and musical difficulty are separate variables and are moved separately.

Dimensions, in the order the controller walks them, easiest to move first:

| Dimension | Ladder | Moves |
|---|---|---|
| Lookahead | 4, 3, 2, 1.5, 1, 0.75, 0.5, 0.25 beats | between runs, in Flash |
| Mode | Open score, Read ahead, Flash | the player's, for now |
| Staves | one hand, both hands | the player's, for now |
| Key | C, then one accidental, then two, outward on the circle of fifths | the player's, for now |
| Interval | steps, thirds, fourths, fifths, sixths, octaves | within and between runs |
| Range | five notes, a sixth, an octave, a tenth, a twelfth, both hands together | within and between runs |
| Rhythm | half notes, quarter notes, eighth notes | within and between runs |
| Rests | off, on | within and between runs |
| Accidentals | off, on | within and between runs |

An interval must fit inside the range, a rhythm rung must fill the meter, and accidentals need an interval of at least a step to resolve by; a rung that would not is skipped.
Rests follow the note values because a rest is read against the note values already in play: a rest is a silence of one of them.
Accidentals come last because a chromatic neighbour is read against the key and the steps already in play.
Key, hands and accompaniment stay the player's choices in this version: they are visible settings, and a setting the player chose must not move under them.
Mode, staves and key join the walk, between lookahead and interval, when their rounds come.

### Controller

No learning in the first version.
The controller decides from a window of the last eight committed segments that were played at exactly the current state, the run's exposure (tempo, click, visibility policy) and the exercise configuration; a segment played at any other state ends the window, so any change, the controller's or the player's, empties it.
The window's success is the smaller of its pooled pitch accuracy and its pooled rhythm accuracy, the two numbers the score line shows:

```text
if the window is short: hold
if recentSuccess > 90%: step one dimension up
if recentSuccess < 65%: step one dimension down
otherwise: hold
```

It moves **one dimension at a time**, so every change is interpretable and the player can feel it, and a move empties the window, so no two moves rest on the same evidence.
Which dimension moves is a walk of the order above, taking turns: a step up goes to the first dimension after the one moved last that can still go up, so the music is never stranded behind the exposure; a step down starts at the dimension moved last and walks back, so the latest step is the first undone.

Within a run, the controller changes the generator's parameters for the segments still to be generated, twelve bars ahead of the cursor in an open-ended run and never a bar already shown; a fixed-length run is generated whole, so its music moves between runs.
The exposure dimensions change between runs so the mode of a run is stable; a moved lookahead is written into the run settings, where the player sees the chip move.
The state, the musical level and the dimension moved last, is stored in its own row; the window is computed from stored evaluations, so the next session continues where the last left off.

What moved is always shown: the Ready screen names the level, and the summary names every bar the level changed at ("Harder from bar 13: up to fourths") and the move for the next run ("Easier next run: 4 beats ahead").

Later, the learner model of section 14 replaces the fixed ladders with per-context comfort levels.

---

## 9. Architecture

Kotlin, Jetpack Compose, coroutines and Flow, Room, the Android MIDI API, `minSdk` 23, one module, no backend.
Manual dependency injection through `AppContainer`.

Packages by concern, all pure JVM except `audio`, `settings`, `data`'s Room layer, `di`, and `ui`:

```text
score/       the canonical model: Ticks, Pitch, SpelledPitch, ScoreNote, Staff, Score
exercise/    ExerciseConfig, the seeded generator
midi/        MidiEvent, MidiMessage, MidiParser
timing/      MonotonicClock, the run timeline
run/         RunConfig, VisibilityPolicy, the pure run reducer and its controller
audio/       ClickTrack, AudioTrackMetronome
evaluation/  PlayedNotes, NoteAlignment, BeatPhase, RhythmAnalysis, incremental PerformanceEvaluator
difficulty/  the controller and the recent-performance window
history/     the read side of history: stored runs at the current version, the session summary
settings/    run and theme settings
data/        Room entities, DAOs, migrations, mappers
notation/    the layout engine: systems, pages, the mask by tick
ui/          the shell (destinations, the back stack, the two frames), the renderer, practice, history, settings
```

Navigation is a back stack of sealed `Destination`s in `ui/shell`, practice at its root, without a navigation library.
A destination is a music stand (`StageScaffold`: the window is the score, one strip of chrome above it, the system bars hidden, controls as sheets over the stage) or a page (`ScreenScaffold`: a title row and content inside the bars).
A mode produces a run the evaluator judges and lives inside practice as a row of its setup sheet; a tool does not and is a destination of its own on the same frame.

`run/` replaced `attempt/` in Round 6; `CLAUDE.md` names what each package holds today.

### Score representation

```text
Score
- timeSignature
- keySignature
- staves: List<Staff>    each with a clef; one for a single hand, two for the grand staff
- measureCount
- notes: List<ScoreNote>

ScoreNote
- id
- spelling
- onset, duration       integer Ticks, 960 per quarter
- staff                 index into staves
- voice
- hand                  kept separate from staff because cross-staff writing exists
```

The evaluator never inspects notation.
The layout engine never inspects evaluation, except through `noteMarks`.

### Notation

The layout engine is native and pure: it lays out a system of measures in staff spaces, justified to a width, with a brace and spanning barlines for the grand staff, key signatures, accidentals, flags, beams and rests.
Eighths that share a beat are beamed and a beam never crosses a beat; a beamed group's stems follow the head farthest from the middle line, and the beam slants with its first and last heads, half their distance and at most one space.
Rests are derived, per staff, from the silences between what sounds in a measure, and a silence is split into the largest rests that start at a multiple of their own length and stay inside their beat; a rest takes a column at its onset like a note.
An accidental is written only when the note differs from what the reader assumes, the key signature at the barline and then every accidental written in the bar, per staff, letter and octave; it sits a fixed gap left of the head on the head's line, takes room in its column that never stretches, and belongs to its note for tinting and masking.
Every note and every rest inside a bar carries the tick it starts at, which is what the mask and the marks key on: a rest inside a hidden bar is hidden with it, since a rest left on the page would tell which beat is silent.
The whole rest of a staff silent through a measure, the count-in included, is not content, carries no tick and stays on the page.
Only the Compose renderer converts staff spaces to pixels, and glyphs are placed by their SMuFL origins from the `BravuraMetrics` table.

MusicXML ingestion and offline SVG rendering from the original plan are not needed and are dropped.

---

## 10. Timing

Unchanged from what is built:

- Beat 0 is when the first click reaches the listener, read from `AudioTrack.getTimestamp`.
- Clicks are placed by sample position, never by sleeping.
- The reducer is pure and wakes only at its next deadline; whether a MIDI event is captured is decided by its own timestamp.
- Visuals read the frame time in `withFrameNanos` and derive the beat, the mask, the cursor, and the page from the timeline; they never tick.

If device testing ever shows unacceptable click jitter, investigate Oboe before writing native code.

A **latency calibration** step, tapping along with the click to measure the player's device offset, is a later addition that would let the lean be reported on its own.

---

## 11. Data model

```text
Run
- id, startedAt, status, abortReason
- tempoBpm, configJson          the RunConfig snapshot
- seed                          the run seed every generated segment's seed derives from
- clock anchor

Segment
- id, runId, index              the segment starts at beat index * beatsPerMeasure
- scoreJson                     the segment's own one-measure score snapshot
- generatorVersion, seed, exerciseConfigJson, or the bundled exercise id

MidiEvent
- runId, timestampNanos, the three raw bytes

Evaluation
- segmentId, evaluatorVersion, resultJson

Session
- id, startedAt, endedAt      the practice screen's lifetime from its first recorded run

DifficultyState
- one row: the musical level and the dimension moved last, as JSON
```

Invariants:

- Raw MIDI is never discarded or overwritten.
- A run's MIDI, its config, and its segments' scores are enough to re-evaluate the run with any evaluator version.
- Schema changes ship with a migration and a migration test; the move from attempts to runs converted every existing attempt into a run with one segment per measure, its raw MIDI untouched, and dropped only the whole-run evaluations that no segment could own, since evaluations are derived.
  The generator's columns were added beside the exercise id, which stayed on every segment recorded before.
  The controller's state is its own table because a move for the next run may be applied to a run that is never recorded; its window is derived from the evaluations, never stored twice.

---

## 12. Tests that matter

- **MIDI fixtures** per segment: perfect, wrong pitch, missing, extra, repeated, early, late, pause, and a late final note that must not leak into the next segment.
- **Timeline**: with an injected clock, the beat of every scheduled instant for a run with count-in, lookahead, and a page turn.
- **Mask**: for each mode preset, the visibility of every segment at every beat, including the exact beat notes disappear.
- **Reducer**: every legal transition, including disconnect, background, stop, and MIDI before the first performed beat.
- **Layout**: justification to a width, the brace and spanning barlines, key signature placement in every key, accidentals against the key and remembered within the bar, the splitting of a silence into rests.
- **Generator**: every produced score satisfies its config, is deterministic for a seed, and transposes back to C, on pitch always and on spelling wherever no double was avoided.
- **Transposition**: every chromatic neighbour of C into every key keeps its semitone to its resolution with a plain accidental, never a double.
- **Incremental evaluation**: committing the same segment from a trailing window gives the same outcomes as evaluating it alone with unlimited context.
- **Running phase**: a slow drift is followed, a sudden jump is not.
- **Controller**: the thresholds, one dimension per decision, the turn-taking order and the undo, the window emptying on any change, the lookahead only between runs, and every rung of every ladder a configuration the generator satisfies.
- **Migration**: every schema step, on a device.

---

## 13. Round ladder

Each round is planned in plan mode, verified with unit tests, debug and release builds, androidTest compilation, and lint, then checked on a device from Android Studio before the next round starts.

### Round 5: grand staff, keys, pages

- `Staff` on the score, a staff index on notes, serializer defaults so existing JSON loads.
- System layout with brace, spanning barlines, justification, key signatures, accidentals.
- Pages of two systems; the mask by tick in the renderer.
- Diatonic transposition and staff assignment so the bundled measures can appear in any key on either staff.
- Device check: the grand staff renders correctly in every key, in both orientations.

### Round 6: the continuous run

Split into two halves.

Presentation, built:

- The run timeline with a silent segment 0, the `VisibilityPolicy` and its three presets, the mask from the beat on every frame, the cursor and the page turn.
- The run reducer and controller replacing the attempt ones, with Stop ending the run after the current segment.
- Content: bundled measures chained in one key.
- Device check: a fixed-length Flash run in both orientations, the page turning as the cursor enters the next system, notes disappearing on the beat.

Evaluation, built:

- Incremental evaluation on a trailing window, committed by the reducer at each segment's capture tail, the running beat phase, marks appearing behind the cursor.
- Schema version 3: runs, segments, MIDI by run, evaluations by segment; attempts migrated to runs of one segment per measure.
- Run summary with the header, the score line, the remarks and the weakest bars; open-ended runs topped up from a segment source, with the run length in settings.
- Device check: an open-ended Read-ahead run for five minutes without drift, marks on the right notes across page turns, the migration of a phone's existing history.

### Round 7: the generator

Built:

- `ExerciseConfig`, the seeded generator, its constraint tests over many seeds, every key and every hands.
- Key, hands and accompaniment in settings; both hands, one at a time or together over a held note; both staves aligned as one stream of chords.
- Schema version 4: the run seed, and the generator version, seed and configuration per segment.
- Device check: ten minutes of two-hand Flash in G major without repetition or an unplayable bar.

### Round 8: the difficulty controller

Built:

- The recent-performance window from stored evaluations and the one-dimension-at-a-time controller, a pure function with a fixed walk order.
- Within-run movement of generator parameters through an adapting segment source; between-run movement of the lookahead, written into the settings.
- Schema version 5: the controller's state. The level on the Ready screen, every move named on the summary.
- Device check: ten minutes of practice feels matched to the player without touching settings.

### Round 9: eighth notes

Built:

- Fixtures first: four eighth-note measures beside the eighteen, beamed pairs on both clefs in C, B flat and D, and a syncopated measure of lone flagged eighths that the layout is held to and the generator never writes.
- The layout draws flags from their SMuFL anchors and beams as filled parallelograms: eighths that share a beat are beamed, a beam never crosses a beat, the group's stems follow the farthest head, the beam slants with the outer heads.
- `NoteValue.EIGHTH` in the vocabulary, with the readability rule that only a note shorter than the beat starts off the beat; the default vocabulary and the generator version unchanged, pinned by test.
- The rung above quarters on the rhythm ladder; the evaluator checked on half-beat onsets, its version unchanged.
- Device check: beamed and flagged eighths at the phone's staff size in both orientations, a run at the eighth rung with the click, marks landing on both notes of a pair.

### Round 10: rests

Built:

- Fixtures first: four rest measures beside the twenty-two, a quarter rest, a half rest on beat 3, eighth rests beside lone eighths and a mix with a beamed pair, on both clefs in C, G and F.
- The score gains no rest type: a rest is the absence of a note, the layout derives rests per staff from the silences in a measure and splits each into the largest rests that start at a multiple of their own length and stay inside their beat, placed by their SMuFL origin, the whole rest hanging from the fourth line and the others on the middle line, each taking a column with the room a note of its value gets; a rest inside a bar is masked with the bar, a whole-measure rest never.
- `ExerciseConfig.rests` with the readability rule that a rest is never first, never after another rest and starts at a multiple of its own length, the same rule as the layout's splitting, so every generated rest is one glyph; the vocabulary without rests and the generator version unchanged, pinned by test.
- The rests rung after rhythm on the controller's walk, off then on; the level line and the summary name it; a stored level without the field reads as before.
- The evaluator checked on a measure with a rest: silence through it is right, a note in it is an extra, the notated gap is not a pause and a hesitation after it still is; its version unchanged.
- Device check: rests at the phone's staff size on both staves, a hidden bar losing its rests with its notes while the resting staff keeps its whole rest, an extra played in a rest drawn beside it, the level line reading "with rests".

### Round 11: accidentals

Built:

- Fixtures first: four accidental measures beside the twenty-six, a sharp on a ledger line in C, a flat on the bass staff in G, a natural cancelling the key in F, and in D the same sharp twice in a bar drawn once with a natural restoring the letter later, which the layout is held to and the generator never writes.
- The layout confirmed and pinned: the sharp, flat and natural placed by their SMuFL origin a fixed gap left of the head on the head's line, remembered per staff, letter and octave within the bar and reset at the barline, given fixed room in their column, and belonging to their note for the mask and the marks.
- Transposition carries an accidental's meaning relative to the key and never writes a double: a double is respelled as the natural of the neighbouring letter, so every key is served with plain accidentals and a natural appears where the key alters the letter.
- `ExerciseConfig.accidentals` with the readability rule that one note of the bar is a chromatic neighbour resolving by step to the next note, a sharp up or a flat down, never last and never before a rest; the draw comes last, so the generator version is unchanged, pinned by test.
- The accidentals rung after rests on the controller's walk, off then on; the level line and the summary name it; a stored level without the field reads as before.
- The evaluator checked on an altered note: right at its own pitch, a wrong pitch at its diatonic neighbour; its version unchanged. A cue beside an altered note always carries its accidental.
- Device check: sharps, flats and naturals at the phone's staff size on both staves, a natural after a sharp in the same bar, a cue with its flat beside a written natural, a run at the accidentals rung in a flat key, the level line reading "with accidentals".

### Round 12: history and session summary

Built:

- `history/`, pure: a stored run with the judgement of each committed segment, current or not against the evaluator version; a reader that re-evaluates an older run from its segments and raw MIDI as it is read and stores the result under the current version beside the old one; the session pooled by counts, the level at its first and last bar, every move as it was announced, and the weakest bars across the session, each naming its run.
- The history screen: sessions newest first, the practice screen's own expanded on arrival as the session summary, each expandable to its summary and its runs (time, length, mode and lookahead, key and hands, the level line, the score line, why it stopped); every run, move and weakest bar opening the run's page, the practice summary of that run with the score open and its marks.
- Navigation as a sealed screen state in the activity, back retracing the step; no schema change.
- Device check: History from the strip's menu after a run, this session expanded with its runs landing, a run page's marks on the right notes, back returning to the list and then to practice, a rotation on each screen, and a phone's older history opened and re-evaluated once.

### After

- Generator dimensions one at a time: chords, other meters, syncopation, each with fixtures first and a rung on its ladder.
- Mode, staves and key on the controller's walk.
- Latency calibration.
- A real corpus, segmented into runs, once generated material stops being enough.
- The learner model.

---

## 14. Learner model, later

Track weaknesses such as interval reading, rhythm reading, accidentals, left and right hand, chords, pulse maintenance, and exposure tolerance.

The Flash-specific signal is:

```text
minimumLookaheadByMusicalContext
```

Stepwise melody comfortable at 0.75 beats, large leaps at 2 beats, chords at 3 beats.
That is the per-context comfort level the controller of section 8 will eventually consult, and it is the reason the mechanic is worth building.

---

## 15. Explicitly deferred

- AI music generation and any ML;
- accounts, cloud synchronisation, social features, teacher dashboards;
- microphone pitch detection, fingering detection, computer vision;
- a runtime MusicXML editor;
- a general performance alignment engine, until the constrained runs prove it necessary;
- microservices, or a backend of any kind for V1.

---

## 16. Validation questions

Before investing in a corpus or a learner model, answer:

> Does controlling visual exposure on a real, page-turned score produce a practice mode players want to repeat?

Then:

> Can the application tell which kinds of notation need more exposure for a particular player?

If both are yes, the central adaptive question becomes:

> What music should this player see next, and how much of it should they be allowed to see, and for how long?
