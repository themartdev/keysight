package dev.simonmartineau.keysight.exercise

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The exercises shipped as JSON files in the `assets/exercises` directory, loaded once and kept.
 *
 * A malformed file is a build defect, not a runtime condition, so loading fails loudly with
 * the file name rather than skipping it.
 */
class BundledExerciseRepository(
    private val source: AssetSource,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExerciseRepository {

    private val mutex = Mutex()
    private var loaded: List<Exercise>? = null

    override suspend fun all(): List<Exercise> = mutex.withLock {
        loaded ?: withContext(ioDispatcher) { load() }.also { loaded = it }
    }

    override suspend fun byId(id: String): Exercise? = all().firstOrNull { it.id == id }

    private fun load(): List<Exercise> = source.list(DIRECTORY)
        .filter { it.endsWith(".json") }
        .sorted()
        .map { name ->
            val text = source.open("$DIRECTORY/$name").bufferedReader().use { it.readText() }
            runCatching { json.decodeFromString(Exercise.serializer(), text) }
                .getOrElse { throw IllegalStateException("bundled exercise $name is invalid", it) }
        }

    companion object {
        const val DIRECTORY = "exercises"
    }
}
