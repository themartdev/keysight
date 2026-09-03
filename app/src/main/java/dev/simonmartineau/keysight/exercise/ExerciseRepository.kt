package dev.simonmartineau.keysight.exercise

/**
 * Access to the bundled exercise content pack.
 *
 * V1 reads from app assets and never writes, so there is no suspending write side. The interface
 * exists so that evaluation and selection can be tested against hand-built fixtures.
 */
interface ExerciseRepository {

    suspend fun all(): List<Exercise>

    suspend fun byId(id: String): Exercise?
}
