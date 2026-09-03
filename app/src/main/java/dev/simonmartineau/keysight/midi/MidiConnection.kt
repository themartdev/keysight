package dev.simonmartineau.keysight.midi

/** Whether a keyboard is talking to the app. */
sealed interface MidiConnection {

    data object NoDevice : MidiConnection

    data class Connecting(val deviceName: String) : MidiConnection

    data class Connected(val deviceName: String) : MidiConnection

    data class Failed(val deviceName: String, val message: String) : MidiConnection
}
