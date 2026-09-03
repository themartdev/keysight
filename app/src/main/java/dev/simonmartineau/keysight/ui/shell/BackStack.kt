package dev.simonmartineau.keysight.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * The destinations on the way to the current one, [Destination.Practice] at the bottom. Back
 * pops one; the root is never popped, the system handles back there. The stack is snapshot
 * state, so the shell recomposes as it moves, and it survives a configuration change.
 */
class BackStack(initial: List<Destination>) {

    private val entries: SnapshotStateList<Destination> = mutableStateListOf<Destination>().apply { addAll(initial) }

    val current: Destination get() = entries.last()

    val canGoBack: Boolean get() = entries.size > 1

    fun push(destination: Destination) {
        entries.add(destination)
    }

    /** Pops the current destination; a no-op at the root. */
    fun pop() {
        if (canGoBack) entries.removeAt(entries.lastIndex)
    }

    companion object {
        val Saver: Saver<BackStack, Any> = listSaver(
            save = { stack -> stack.entries.map(Destination::save) },
            restore = { saved -> BackStack(saved.map(Destination::restore)) },
        )
    }
}

@Composable
fun rememberBackStack(root: Destination = Destination.Practice): BackStack =
    rememberSaveable(saver = BackStack.Saver) { BackStack(listOf(root)) }
