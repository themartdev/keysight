package dev.simonmartineau.keysight.di

import android.content.Context

/**
 * Manual dependency container, created once in [dev.simonmartineau.keysight.KeySightApplication].
 *
 * The app is a single module with a handful of long-lived collaborators, so a container built by
 * hand is easier to follow than a code-generated graph and costs nothing at build time. Each
 * milestone adds the objects it needs here: the database, the MIDI device manager, the metronome
 * and the repositories.
 */
class AppContainer(context: Context) {

    @Suppress("unused")
    private val appContext: Context = context.applicationContext
}
