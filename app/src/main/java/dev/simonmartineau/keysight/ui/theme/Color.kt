package dev.simonmartineau.keysight.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Ink and paper, per `docs/design.md`. The engraved score is the highest-contrast thing on
 * any screen, so the chrome is achromatic: its accent is ink, never a hue. Green, red and
 * amber are judgement colours, correct, wrong and extra, and no chrome element uses them.
 * Every grey the app needs is [Ink] at an alpha of [Palette]'s ramp, not a colour of its own.
 */

// Light, the default.
internal val Paper = Color(0xFFF7F5F0) // panels, rail, cards
internal val Ground = Color(0xFFEFECE4) // the page behind panels
internal val PaperDim = Color(0xFFEAE7DF) // selected row, hover
internal val Ink = Color(0xFF1B1A17) // text, notation, staff lines
internal val InkAccent = Color(0xFF26241F) // filled buttons, active rail mark, cursor
internal val OnAccent = Color(0xFFFBFAF6)

// Dark: the same structure inverted; notation becomes paper on ink.
internal val DarkGround = Color(0xFF121316)
internal val DarkPaper = Color(0xFF1B1D21)
internal val DarkDim = Color(0xFF24272C)
internal val DarkInk = Color(0xFFEDEBE5)
internal val DarkAccent = Color(0xFFEDEBE5)
internal val DarkOnAccent = Color(0xFF14140F)

// Judgement, never used for chrome.
internal val Correct = Color(0xFF2F6B45)
internal val CorrectDark = Color(0xFF6FBF7F)
internal val Wrong = Color(0xFFA62B21)
internal val WrongDark = Color(0xFFE79B95)
internal val Extra = Color(0xFF9A6B12)
internal val ExtraDark = Color(0xFFE0B168)
