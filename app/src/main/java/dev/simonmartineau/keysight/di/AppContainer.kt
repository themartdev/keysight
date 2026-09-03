package dev.simonmartineau.keysight.di

import android.content.Context
import dev.simonmartineau.keysight.data.KeySightDatabase

/**
 * Manual dependency container.
 *
 * The app is a single module with a handful of long-lived collaborators, so a container built by
 * hand is easier to follow than a code-generated graph and costs nothing at build time. Each
 * milestone adds the objects it needs here.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: KeySightDatabase by lazy { KeySightDatabase.build(appContext) }
}
