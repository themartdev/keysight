package dev.simonmartineau.keysight.exercise

/** Which staves the player practises on: one hand on its own staff, or the grand staff. */
enum class Hands(val label: String) {
    RIGHT("Right hand"),
    LEFT("Left hand"),
    BOTH("Both hands"),
}
