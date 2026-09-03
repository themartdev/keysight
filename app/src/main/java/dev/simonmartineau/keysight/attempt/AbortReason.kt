package dev.simonmartineau.keysight.attempt

/** Why an attempt ended before it was evaluated. These are ordinary practice, not edge cases. */
enum class AbortReason {
    /** The player stopped it. */
    CANCELLED,

    /** The keyboard went away mid-attempt. */
    MIDI_DISCONNECTED,

    /** The app left the foreground, so neither the metronome nor the notation can be trusted. */
    BACKGROUNDED,
}
