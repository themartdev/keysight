package dev.simonmartineau.keysight.run

/** Why a run ended before it was summarised. These are ordinary practice, not edge cases. */
enum class AbortReason {
    /** The player stopped during the count-in, before anything was performed. */
    CANCELLED,

    /** The keyboard went away mid-run. */
    MIDI_DISCONNECTED,

    /** The app left the foreground, so neither the metronome nor the page can be trusted. */
    BACKGROUNDED,
}
