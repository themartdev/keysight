package dev.simonmartineau.keysight.data

import dev.simonmartineau.keysight.difficulty.DifficultyState
import dev.simonmartineau.keysight.difficulty.DifficultyStore
import dev.simonmartineau.keysight.difficulty.SegmentEvidence

/** The controller's state in its own table, and its evidence from the run, segment and evaluation tables. */
class RoomDifficultyStore(
    private val database: KeySightDatabase,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : DifficultyStore {

    override suspend fun load(): DifficultyState? = database.difficultyDao().get()?.toState()

    override suspend fun save(state: DifficultyState) {
        database.difficultyDao().put(state.toEntity(wallClock()))
    }

    override suspend fun recentEvidence(limit: Int): List<SegmentEvidence> =
        database.runDao().recentCommitted(limit).asReversed().map { it.toEvidence() }
}
