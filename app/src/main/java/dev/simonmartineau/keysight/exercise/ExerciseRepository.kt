package dev.simonmartineau.keysight.exercise

/**
 * Access to the exercise content pack.
 *
 * V1 content is read-only and bundled, so there is no write side. The interface exists so
 * selection and the practice loop can be tested against hand-built fixtures.
 */
interface ExerciseRepository {

    suspend fun all(): List<Exercise>

    suspend fun byId(id: String): Exercise?
}
