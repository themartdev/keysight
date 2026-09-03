# Architecture

This document describes how the app is put together and, more importantly, why.
The product reasoning behind it is in [`product-plan.md`](product-plan.md).

## Shape

One Android application, no modules, no backend.

The product is local-first for V1: bundled exercises, bundled notation, MIDI capture, evaluation
and history all live on the device.
There is no account, no synchronisation and no server.
A backend only becomes interesting once content packs and cross-device profiles matter, which is
well past the point where the core mechanic has been validated.

Splitting the app into Gradle modules would buy build parallelism the project does not need yet
and would freeze package boundaries before they have settled.
The logical separation is enforced by packages instead, and the boundaries that matter are the
ones described below.

## Packages

```
dev.simonmartineau.keysight
├── attempt/      one flash attempt: its configuration and its lifecycle
├── data/         Room practice history (entities, DAOs, database)
├── di/           the manual dependency container
├── evaluation/   scoring an attempt against its score
├── exercise/     what the player reads, and the dimensions that make it harder
├── midi/         events received from the keyboard
├── score/        the canonical music model
├── session/      a stretch of practice and its summary
├── timing/       the monotonic clock and the attempt timeline
└── ui/           Compose screens and theme
```

### The boundary that matters most

`score` is the canonical representation of music, and nothing downstream of it may inspect
notation.
The evaluator compares `ScoreNote` against `MidiEvent`; it never sees MusicXML or SVG.
This is what makes it possible to change the renderer, or to render offline entirely, without
touching scoring.

The mirror of that rule: notation is a rendering concern.
V1 ingests MusicXML offline and bundles both the canonical exercise data and a pre-rendered SVG,
which keeps a notation engine out of the Android runtime path.
Runtime engraving only becomes a question when exercises need transposition or dynamic
generation, and that question is deliberately not answered yet.

### The other boundary that matters

Everything time-related reads from one monotonic clock.

Audio scheduling, MIDI timestamps, notation visibility and performance start are phases of a
single timeline, not independent UI timers.
`AttemptTimeline` computes every moment from an absolute beat position rather than by
accumulating deltas, so repeated attempts cannot drift.
`MonotonicClock` exists as an interface purely so that a four-second timeline can be tested
without waiting four seconds.

Android delivers MIDI timestamps on the `System.nanoTime` base, which is why that is the base the
whole app uses.

## Key decisions

### Kotlin version is pinned to AGP

AGP 9 compiles Kotlin itself and actively rejects the standalone `org.jetbrains.kotlin.android`
plugin.
The Kotlin version is therefore whatever AGP embeds, currently 2.2.10 for AGP 9.4.0, even though
newer Kotlin releases exist.
The Compose compiler plugin, the serialization plugin and KSP all have to match it exactly, so
those four versions move together with AGP and never on their own.

Lint reports "a newer version is available" for exactly these entries.
Those three notices are expected and should not be acted on independently.

### KSP needs one experimental flag

KSP registers its generated sources through `kotlin.sourceSets`, which AGP's built-in Kotlin
rejects by default.
`android.disallowKotlinSourceSets=false` in `gradle.properties` is the escape hatch AGP itself
points at.
It can be removed once KSP registers generated sources through `android.sourceSets`.

### Manual dependency injection

`AppContainer` is a hand-written container built in `KeySightApplication`.

A single-module app with a handful of long-lived collaborators does not need a generated
dependency graph, and avoiding one keeps annotation processing to just Room.
If the graph grows past roughly a dozen objects, or if scoping gets genuinely awkward, Hilt is the
obvious next step.

### Exercises are content, not rows

The practice-history database stores sessions, attempts, raw MIDI, evaluations and the difficulty
state.
It deliberately does not store exercises: for V1 they are authored offline and shipped as assets,
so putting them in Room would mean maintaining a migration path for content that ships with the
APK anyway.
That changes at Milestone 6, when a real corpus arrives.

### Raw MIDI is never discarded

`MidiEventEntity` rows are stored verbatim, and `EvaluationResultEntity` is keyed by
`(attemptId, evaluatorVersion)`.

Re-scoring an old attempt with an improved evaluator therefore adds a row instead of overwriting
a judgement, and every historical attempt stays reevaluable.
This is why the evaluator version lives on the evaluation rather than on the attempt: the attempt
is raw data and has no version, the interpretation does.

### `minSdk = 28`

The Android MIDI framework arrives at API 23, which is the floor the product plan identifies.
28 is a deliberate step above it: it avoids compatibility shims across the whole androidx stack
while excluding only devices that are, in practice, no longer plausible hosts for a USB MIDI
keyboard.

`android.software.midi` is declared as a required feature, because the app genuinely cannot work
without it.
USB host is declared as optional so that Bluetooth MIDI stays available to later milestones.

### No dynamic colour

The notation and the correct/wrong annotations need a fixed, high-contrast relationship.
A wallpaper-derived palette cannot guarantee that, so `KeySightTheme` uses a fixed scheme in both
light and dark.
