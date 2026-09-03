package dev.simonmartineau.keysight.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.history.DayCount
import dev.simonmartineau.keysight.history.HistoryReader
import dev.simonmartineau.keysight.history.SessionDigest
import dev.simonmartineau.keysight.history.barsPerDay
import dev.simonmartineau.keysight.history.sessionDigests
import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.history.SessionSummary
import dev.simonmartineau.keysight.history.StoredRun
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** A stored run's page as it loads. */
sealed interface RunPageState {
    data object Loading : RunPageState

    /** No run has that id: it was deleted, or the link is stale. */
    data object Missing : RunPageState

    data class Loaded(val run: StoredRun) : RunPageState
}

/**
 * The history screens' glue: the sessions, the one expanded and its summary, and the run page
 * opened. Everything is read through the [reader], so every judgement shown is at the
 * current evaluator version, and read off the main thread, since a read may re-evaluate a run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val reader: HistoryReader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** Newest first; null until the first read. */
    val sessions: StateFlow<List<SessionRecord>?> = reader.sessions()
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    /** Every session as a row, newest first, with its runs' digests; null until the first read. */
    val digests: StateFlow<List<SessionDigest>?> = combine(reader.sessions(), reader.runDigests(0L)) { sessions, runs -> sessionDigests(sessions, runs) }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    /** Bars read per day over the last [CHART_DAYS] days, oldest first. */
    val days: StateFlow<List<DayCount>> = reader.runDigests(0L)
        .map { runs -> barsPerDay(runs, CHART_DAYS, Instant.ofEpochMilli(now()).atZone(zone).toLocalDate(), zone) }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), emptyList())

    private val _expanded = MutableStateFlow<String?>(null)
    val expanded: StateFlow<String?> = _expanded.asStateFlow()

    /** The expanded session pooled, kept up to date as its runs land; null while none is expanded or it is loading. */
    val summary: StateFlow<SessionSummary?> = combine(sessions, _expanded) { sessions, expanded -> sessions?.firstOrNull { it.id == expanded } }
        .distinctUntilChanged()
        .flatMapLatest { session -> if (session == null) flowOf(null) else reader.summaryOf(session) }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    private val _runPage = MutableStateFlow<RunPageState>(RunPageState.Loading)
    val runPage: StateFlow<RunPageState> = _runPage.asStateFlow()

    fun expand(sessionId: String?) {
        _expanded.value = sessionId
    }

    fun toggle(sessionId: String) {
        _expanded.value = if (_expanded.value == sessionId) null else sessionId
    }

    fun openRun(runId: String) {
        _runPage.value = RunPageState.Loading
        viewModelScope.launch {
            val run = withContext(dispatcher) { reader.run(runId) }
            _runPage.value = run?.let { RunPageState.Loaded(it) } ?: RunPageState.Missing
        }
    }

    companion object {
        private const val STOP_AFTER_MILLIS = 5_000L

        /** How many days the chart at the top looks back, today included. */
        const val CHART_DAYS = 14

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(container.historyReader()) }
        }
    }
}
