# KeySight design system

Ink and paper. The engraved score is the highest-contrast thing on any screen; the chrome is
achromatic so that it never competes with it. Green, red and amber are reserved for judgement —
correct, wrong, extra — so no chrome element may use them, which is why the accent is ink rather
than a hue.

Landscape only. Phone and tablet get the same layout; the rail is what makes it scale.

## Colour

Extends the existing `ui/theme/Color.kt`. `Indigo`/`Amber` as *chrome* colours are retired;
the outcome colours stay, retuned slightly warmer to sit on paper.

```kotlin
// Light — the default
val Paper       = Color(0xFFF7F5F0)  // panels, rail, cards
val Ground      = Color(0xFFEFECE4)  // the page behind panels
val PaperDim    = Color(0xFFEAE7DF)  // selected row, hover
val Ink         = Color(0xFF1B1A17)  // text, notation, staff lines
val InkAccent   = Color(0xFF26241F)  // filled buttons, active rail mark, cursor
val OnAccent    = Color(0xFFFBFAF6)

// Dark — same structure inverted; notation becomes paper on ink
val DarkGround  = Color(0xFF121316)
val DarkPaper   = Color(0xFF1B1D21)
val DarkDim     = Color(0xFF24272C)
val DarkInk     = Color(0xFFEDEBE5)
val DarkAccent  = Color(0xFFEDEBE5)
val DarkOnAccent = Color(0xFF14140F)

// Judgement — never used for chrome
val Correct     = Color(0xFF2F6B45);  val CorrectDark = Color(0xFF6FBF7F)
val Wrong       = Color(0xFFA62B21);  val WrongDark   = Color(0xFFE79B95)
val Extra       = Color(0xFF9A6B12);  val ExtraDark   = Color(0xFFE0B168)
```

Alpha ramp over `Ink`, used instead of extra greys:

| Token | Light | Use |
| --- | --- | --- |
| `onSurface` | 1.0 | primary text, notation |
| `onSurfaceMuted` | 0.66 | secondary text, values in a row |
| `onSurfaceFaint` | 0.55 | section labels, axis labels |
| `outline` | 0.34 | outlined button border |
| `outlineWeak` | 0.18 | hairlines, disabled borders |
| `hairline` | 0.10 | row separators, panel edges |
| `fill` | 0.16 | inactive chart bars, rail marks |

In dark, the same ramp over `DarkInk`, each step +0.04.

## Type

One sans, three weights. Bundle **Work Sans** 400/500/600 as `res/font/`
(`work_sans_regular`, `_medium`, `_semibold`); no serif anywhere. Replaces the Material 3
default scale in `Type.kt`.

| Role | Size / weight | Tracking | Use |
| --- | --- | --- | --- |
| `screenTitle` | 22sp / 500 | −1.5% | "Good evening", "History", "Settings" |
| `paneTitle` | 20sp / 500 | −1% | mode name on Play |
| `lead` | 19sp / 500, 1.4 line | −1% | the resume line on Home |
| `numeral` | 28sp / 500 | −2% | the three dashboard numbers |
| `body` | 13sp / 400, 1.5 line | 0 | descriptions, table cells |
| `meta` | 12sp / 400 | 0 | MIDI status, last-run line |
| `micro` | 11sp / 400 | 0 | axis labels, sub-labels |
| `label` | 9.5sp / 600 UPPERCASE | +15% | section headings, rail labels |
| `button` | 12.5sp / 600 UPPERCASE | +13% | filled buttons |
| `buttonQuiet` | 11.5sp / 500 UPPERCASE | +12% | outlined buttons, chips |

Nothing below 11sp. Numbers are `FontFeatureSetting("tnum")` wherever they sit in a column.

## Metrics

- 4dp base. Page padding 26dp top / 32dp sides / 28dp bottom. Pane padding 24dp.
- Rail 80dp wide, full height, `Paper`, 1dp hairline on the right.
- Preset list pane 272dp wide.
- Gaps: 9dp within a control group, 14dp between controls, 20–24dp between blocks, 30dp between panes.
- Corner radius 4dp on buttons, chips, cards, param cells and the switch. 0dp on the app mark
  and rail marks. No other radii.
- Touch targets 48dp minimum; buttons are 44–48dp tall even though the label is small.
- **No elevation anywhere.** Material `Card`/`Surface` elevation is off; separation is done with
  hairlines and background steps.

## Components

**PrimaryButton — letterpress slab.** `InkAccent` fill, `OnAccent` label in `button` style,
4dp radius, 16dp × 30dp padding. Behind it, a solid 3dp-offset block of `Ink` at 16% (drawn, not
a shadow — no blur). On press, the button translates 2dp × 2dp and the block shrinks to 1dp, so it
reads as a key being struck. 120ms.

**QuietButton — keycap.** Transparent, 1dp `outline` border, `Ink` label in `buttonQuiet`, 4dp
radius. Hover/press fills `PaperDim`. Used for Stop, Try again, Change settings.

**Chip.** Same as QuietButton at 11sp, 11dp × 15dp padding. Selected state is a `PaperDim` fill,
not a border change. A not-yet-available chip uses a 1dp dashed `outlineWeak` border and
`onSurfaceFaint` label.

**SectionHeading.** `label` text, then a 1dp `outlineWeak` rule filling the remaining width,
12dp gap. Every section on every screen uses it — this is the app's signature.

**DoubleRule.** 3dp tall: 1dp at `Ink` 28%, 1dp transparent, 1dp at `Ink` 13%. Borrowed from a
final barline. **Horizontal only** — vertical separations are a plain 1dp `hairline`.

**Rail + RailItem.** 20dp square mark above a 9.5sp `label`, 10dp vertical padding, full rail
width. Active: `PaperDim` background, `InkAccent` mark, `Ink` label at 500. Inactive: mark at
`fill` 24%, label at `onSurfaceFaint`. Settings pinned to the bottom.

**ParamCell.** Grid of 3 columns, 1dp `hairline` gutters, 4dp outer radius, `Paper` cells.
Each cell is a button: `micro` label over a 13.5sp/500 value. Tapping opens that parameter's picker.

**Switch.** Square, not a pill: 44 × 26dp track at 4dp radius, 20dp square knob at 2dp radius with
a 1dp `Ink` 40% ring so the off state stays visible. Off track `Ink` 30%, on track `InkAccent`.

**StatNumber.** `micro` caption above a `numeral` value. Three side by side, 30dp apart, no boxes.

**SessionRow.** Fixed columns — when 160dp, what (flex), runs 90dp, "pitch · rhythm" 120dp,
chevron 16dp — 14dp vertical padding, 1dp `hairline` under each, `PaperDim` on press. Header row
uses `label`.

**Sparkbars.** Bars at `fill`, the most recent at `InkAccent`, 4–5dp wide, 2dp radius, gap 4–5dp.
A day with no practice is **not** a short bar: it is a 5dp-tall dash at `Ink` 32% on the baseline.

## Notation

Unchanged: `ScoreLayoutEngine` and Bravura own it. Two things the chrome must respect:

- Staff lines and noteheads draw at `Ink` (or `DarkInk`), full strength, never tinted by chrome.
- The cursor is a 2dp `InkAccent` line running 8dp above and below the staff.
- Outcome tinting uses the judgement colours only, per `noteMarks`.

## Rules of thumb

1. If a colour is not ink, paper or a judgement, it is a bug.
2. Separate with a hairline before reaching for a box; separate with space before a hairline.
3. Every section gets a `SectionHeading`. No orphan headings in body type.
4. One filled button per screen. Everything else is a keycap.
5. Numbers are large and unlabelled-by-unit; the caption carries the unit.

## Settled in code

Values the sections above left open, decided while building `ui/theme/Components.kt` and now
part of the system. Change them here and there together.

- Chip text is 11sp as the component says, not the 11.5sp of `buttonQuiet`; the chip copies
  the style at that size.
- QuietButton padding is the slab's, 16dp × 30dp, so the two buttons sit level in a row.
- A disabled slab has no block behind it: the fill is `outlineWeak`, the label `onSurfaceFaint`.
- Sparkbars default to 44dp tall (Home draws them at 36dp). The no-practice dash is the bar's
  width by 2dp, on the baseline, at `Ink` 32%; a bar with anything in it is at least 4dp.
- The switch's knob is `Paper` in both themes; the knob travels 3dp in from each end.
- ParamGrid has a hairline around it as well as in its gutters, so it holds on a `Paper` pane.
- Home's session lines are not `SessionRow`s: the right column is too narrow for its fixed
  columns, so a line is when (96dp, `meta`), what (flex, `body`) and the two accuracies.
- The failed-keyboard state does not use a judgement colour: the dot is `outline` whenever no
  keyboard is talking, and the words carry the message.
- Rail marks are plain 20dp squares, `InkAccent` active and `Ink` at 24% inactive, until the
  app has an icon set.
- Pickers are a 360dp `Paper` panel with a 1dp `outlineWeak` border, never a Material sheet.
- The run screen itself is untouched by this system beyond the palette and the type it reads
  through Material's roles.
