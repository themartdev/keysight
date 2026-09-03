package dev.simonmartineau.keysight.attempt

import dev.simonmartineau.keysight.attempt.AttemptState.ABORTED
import dev.simonmartineau.keysight.attempt.AttemptState.COUNT_IN
import dev.simonmartineau.keysight.attempt.AttemptState.EVALUATING
import dev.simonmartineau.keysight.attempt.AttemptState.PERFORMING
import dev.simonmartineau.keysight.attempt.AttemptState.PREVIEW_HIDDEN
import dev.simonmartineau.keysight.attempt.AttemptState.PREVIEW_VISIBLE
import dev.simonmartineau.keysight.attempt.AttemptState.READY
import dev.simonmartineau.keysight.attempt.AttemptState.RESULT
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttemptStateTest {

    @Test
    fun `the happy path runs from ready to result`() {
        val path = listOf(
            READY, PREVIEW_VISIBLE, PREVIEW_HIDDEN, PERFORMING, EVALUATING, RESULT, READY,
        )

        path.zipWithNext().forEach { (from, to) ->
            assertTrue(from.canTransitionTo(to), "$from should be able to reach $to")
        }
    }

    @Test
    fun `a shortened preview starts the count-in before the notation appears`() {
        assertTrue(READY.canTransitionTo(COUNT_IN))
        assertTrue(COUNT_IN.canTransitionTo(PREVIEW_VISIBLE))
    }

    @Test
    fun `every state before the result can be abandoned`() {
        listOf(READY, COUNT_IN, PREVIEW_VISIBLE, PREVIEW_HIDDEN, PERFORMING, EVALUATING)
            .forEach { assertTrue(it.canTransitionTo(ABORTED), "$it should be abortable") }
    }

    @Test
    fun `an abandoned attempt goes back to ready rather than resuming`() {
        assertTrue(ABORTED.canTransitionTo(READY))
        assertFalse(ABORTED.canTransitionTo(PERFORMING))
        assertFalse(ABORTED.canTransitionTo(EVALUATING))
    }

    @Test
    fun `the notation cannot reappear once it has been hidden`() {
        assertFalse(PREVIEW_HIDDEN.canTransitionTo(PREVIEW_VISIBLE))
        assertFalse(PERFORMING.canTransitionTo(PREVIEW_VISIBLE))
    }

    @Test
    fun `performing cannot be skipped`() {
        assertFalse(PREVIEW_HIDDEN.canTransitionTo(EVALUATING))
        assertFalse(PREVIEW_VISIBLE.canTransitionTo(PERFORMING))
    }
}
