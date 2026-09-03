package dev.simonmartineau.keysight.difficulty

/** Where the controller's state is kept and where its stored evidence comes from. */
interface DifficultyStore {

    /** The state the last session left, or null before any was saved. */
    suspend fun load(): DifficultyState?

    suspend fun save(state: DifficultyState)

    /** The most recent [limit] committed segments of history, oldest first. */
    suspend fun recentEvidence(limit: Int): List<SegmentEvidence>
}

class InMemoryDifficultyStore(
    initial: DifficultyState? = null,
    private val evidence: List<SegmentEvidence> = emptyList(),
) : DifficultyStore {

    var state: DifficultyState? = initial
        private set

    val saves = mutableListOf<DifficultyState>()

    override suspend fun load(): DifficultyState? = state

    override suspend fun save(state: DifficultyState) {
        this.state = state
        saves += state
    }

    override suspend fun recentEvidence(limit: Int): List<SegmentEvidence> = evidence.takeLast(limit)
}
