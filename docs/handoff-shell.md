# Handoff — app scaffolding

Build the shell the app is missing: global navigation and four screens around the run. Visual
spec is `docs/design.md`; the interactive reference is a design prototype (Home, Play, History,
Settings, plus a run screen).

**The run screen in the prototype is a weak reference.** It is missing concepts the real one has.
Do not touch `ui/practice/PracticeScreen.kt`, `RunPage`, `RunSummaryContent`, `run/`,
`evaluation/`, `notation/` or `audio/`. The run keeps working exactly as it does today; this work
is what surrounds it.

## 1. Navigation

`Screen.kt` grows from three cases to six. Practice becomes the *run* destination rather than the
home destination.

```kotlin
sealed interface Screen {
    data object Home : Screen
    data object Play : Screen
    data object Settings : Screen
    data class Run(val sessionId: String?) : Screen        // today's PracticeScreen
    data class History(val currentSessionId: String?) : Screen
    data class RunDetail(val runId: String, val currentSessionId: String?) : Screen
}
```

- `Home` is the launch destination.
- The rail is drawn by `MainActivity` around the destination, **not** by each screen, and is
  hidden for `Run` only. A run is full-bleed.
- Back: `Home` exits; `Play`/`History`/`Settings` return to `Home`; `RunDetail` returns to
  `History`; `Run` returns to wherever it was started from (`Home` or `Play`).
- Keep the existing `Saver` pattern; add the new cases.
- Landscape only — set `android:screenOrientation="sensorLandscape"` on the activity if it is not
  already.

`AppRail` is a new composable in `ui/shell/`: Home, Play, History, then Settings pinned to the
bottom. Marks are placeholders in the prototype — use the project's icon set, or plain 20dp
squares until there is one.

## 2. Home

The launcher. One job: get the player into a run in one tap.

**Left column (flex 1.2)**

- `SectionHeading("Pick up where you left off")`.
- `lead` line: the current settings read back as prose — `"Read ahead · C major · right hand"`.
- `body` line: `"8 bars at 72 bpm, count-in only. Up to thirds, quarter notes."`
- Primary **Start** → `Screen.Run`, using `RunSettings` + `ContentSettings` unchanged.
- Quiet **Change settings** → `Screen.Play`.
- `DoubleRule`, then `SectionHeading("Or start something else")` and a chip row: Flash,
  Open score, Both hands — each writes that one setting and goes straight to `Screen.Run`.
  A dashed chip lists the unbuilt modes; it is not clickable.

**Right column (flex 1), 1dp hairline between**

- Three `StatNumber`s: pitch %, rhythm %, bars — pooled over the last 7 days.
- `Sparkbars`: bars read per day for 7 days, most recent highlighted, skipped days as dashes.
- `DoubleRule`, `SectionHeading("Recent sessions")` with an "All history" text button.
- Three tappable session lines → `Screen.History` with that session expanded.

**Data.** Everything except the two aggregates already exists. Add to `SessionDao`/`RunDao`:

```kotlin
// bars per calendar day over a window, for the sparkbars
suspend fun barsPerDay(sinceEpochMillis: Long): List<DayCount>   // day start millis + count
// pooled correct/expected and onTime/matched over a window, for the two percentages
suspend fun pooledAccuracy(sinceEpochMillis: Long): PooledCounts
```

Pool them the way `summarise` already does — sum of counts over sum of counts, never a mean of
percentages. Reuse `SessionSummary`'s arithmetic; do not invent a second definition.

**States.** No keyboard: the status dot goes grey, the label becomes "Connect a MIDI keyboard",
Start stays enabled (the run screen already handles the not-connected case). No history yet:
the right column collapses to a single `body` line, "Nothing recorded yet" — no zeroed numbers,
no empty chart.

## 3. Play

Settings, laid out as a preset list. Replaces the chip-and-dropdown `SettingsRow` currently
inside `PracticeScreen`; that row should be deleted from the run screen once Play exists, which
is the one edit to `PracticeScreen.kt` this work permits.

- **Left pane, 272dp, `Paper`.** `SectionHeading("Sight reading")` then one row per
  `VisibilityMode` — title plus a one-line description straight from `RunConfig.description()`'s
  vocabulary. Selected row: `PaperDim` fill and a 2dp `InkAccent` left edge.
  `SectionHeading("Technique")` below it lists Hanon, Scales & arpeggios, Chords as disabled rows.
  Selecting a mode writes `RunSettings` immediately — there is no save step anywhere in this app.
- **Right pane.** Mode title and description; MIDI status top-right.
- **ParamGrid**, 3 × 2: Key, Hands, Length, Tempo, Click, and one mode-dependent cell —
  Lookahead for Flash, Notes (the generator's interval/rhythm rungs in words) otherwise.
  Each cell opens a picker; the choices are already in `RunChoices`, `KeySignature.ALL` and
  `Hands.entries`.
- **Preview.** The first bars of the run about to be generated, engraved by the existing
  `ScoreLayoutEngine` from `GeneratedSegmentSource` at the current settings and seed. Static, no
  cursor, no mask. Regenerate when a parameter changes.
- Primary **Start** → `Screen.Run`. Disabled label when the mode is unbuilt or no keyboard.
- `meta` line under it: the last run in this mode, from `HistoryReader`.

## 4. History

`ui/history/HistoryScreen.kt` keeps its data layer; the presentation becomes the table in the
prototype.

- `Sparkbars` for 14 days at the top, same query as Home with a longer window.
- A `label` header row, then `SessionRow` per session, newest first — when / what / runs /
  "pitch · rhythm" / chevron. Row → `RunDetail` for a single-run session, otherwise expands to
  its runs.
- The existing session-summary text lines (level, moves, weakest bars) move **inside** the
  expanded state. They are not on the collapsed row and not on Home.

## 5. Settings

A single 620dp column, one row per setting, `hairline` between.

| Row | Control | Source |
| --- | --- | --- |
| Keyboard | value text, device name | `MidiDeviceManager` |
| Theme | three chips: Light / Dark / System | `ThemeSettings` |
| Adapt difficulty as I play | square switch, **default off** | new — see below |
| Keep raw MIDI | value text, "Always" | not configurable, stated for trust |

### The one behavioural change

The adaptive controller currently always drives the musical dimensions. It becomes opt-in.

- Add `adaptEnabled: Boolean = false` to `RunSettings`.
- When off, a run uses `GeneratedSegmentSource` at the configuration the player set — the Notes
  cell on Play is the level, chosen by hand — and `DifficultyTracker` records evidence but issues
  no moves.
- When on, behaviour is exactly today's, and the run summary keeps naming every move.
- `difficulty/` itself does not change. This is a wiring decision in `AppContainer` and
  `PracticeViewModel`, plus the Notes picker on Play.

Confirm this is wanted before implementing it; everything else here is additive.

## Out of scope

The run screen, the run summary, onboarding, the technique modes' own screens, piece import,
accounts.

## Done when

1. Launching lands on Home in landscape, and Start reaches a working run with the rail hidden.
2. Every rail destination is reachable and back behaves as specified above.
3. Play writes settings that the run actually honours, and its preview matches what gets generated.
4. No screen shows a zeroed-out chart or a 0% where there is no data.
5. `./gradlew :app:testDebugUnitTest` passes, including new tests for the two aggregate queries.
6. No new dependency, no Material elevation, no colour outside `docs/design.md`.

## Outcome

Built as described, on the tree before the `WIP` commit, which was reverted. Where the
handoff and the code disagreed, the code was reconciled as follows.

- `Screen.Run` carries the screen that started it (Home or Play) rather than a session id:
  nothing knows a session before the run screen opens one, and the origin is what back needs.
- `RunConfig.description()` did not exist; the mode descriptions are in `PlayScreen.kt`, and
  `modeLabel` and `tempoLabel` were factored out of `ResultText.kt` for the rows.
- The two aggregates are digests: `RunDao` returns run digest rows and the latest judgement
  per segment since a moment, `RoomRunHistory.runDigests` combines them, and `history/`
  pools and buckets them in pure functions with JVM tests (`PooledCounts`, `barsPerDay`,
  `sessionDigests`). Rhythm counts live only in the result JSON, so they are read from it
  rather than added as columns. The SQL has an instrumented test.
- Calendar days are the player's zone; a window of n days opens at local midnight n - 1 days
  ago.
- Every settings change, from any screen, rebuilds a run waiting in Ready from the same seed.
- The preview is drawn from a seed of its own, per visit: it shows the kind of music, not
  the run's bars.
- In Flash with adaptation off, the grid has seven cells, Lookahead and Notes both.
- With adaptation off the level is `ContentConfig.level`, picked from `NotesLadder`, the
  controller's own walk up from the default. With it on, the Notes cell shows the
  controller's level and its picker only says where to turn adaptation off.
- Digest rows show the stored judgement version and are not re-evaluated; a run's page is.
- The history and run detail pages lost their in-page back buttons to the rail and system
  back; the run detail keeps a quiet History button.
