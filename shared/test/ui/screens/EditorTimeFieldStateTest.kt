package com.muhammadridha.fjalar.ui.screens

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Common tests exercise the same text input commands on Android and iOS. */
class EditorTimeFieldStateTest {
    @Test
    fun tapAfterFocusKeepsSelectionAndFirstDigitReplacesHourOrMinute() {
        for (prefilled in listOf("07", "15", "7")) {
            for (cursor in 0..prefilled.length) {
                val field = EditorTimeFieldState(prefilled)
                field.onFocusChanged(true)
                assertEquals(TextRange(0, prefilled.length), field.value.selection)

                assertFalse(edit(field, SetSelectionCommand(cursor, cursor)))
                assertEquals(TextRange(0, prefilled.length), field.value.selection)
                assertTrue(edit(field, CommitTextCommand("1", 1)))
                assertEquals(TextFieldValue("1", TextRange(1)), field.value)

                assertTrue(edit(field, CommitTextCommand("2", 1)))
                assertEquals(TextFieldValue("12", TextRange(2)), field.value)
            }
        }
    }

    @Test
    fun cursorPlacementAfterFirstEditIsRespected() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        edit(field, CommitTextCommand("2", 1))
        edit(field, SetSelectionCommand(0, 0))
        assertEquals(TextRange.Zero, field.value.selection)
        edit(field, CommitTextCommand("1", 1))
        assertEquals("12", field.value.text)
    }

    @Test
    fun refocusingSelectsEditedValueForReplacementAgain() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        edit(field, CommitTextCommand("2", 1))
        field.onFocusChanged(false)
        field.onFocusChanged(true)
        edit(field, SetSelectionCommand(1, 1))
        edit(field, CommitTextCommand("9", 1))
        assertEquals(TextFieldValue("9", TextRange(1)), field.value)
    }

    @Test
    fun rejectedPasteKeepsInitialReplacementAndValidation() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        for (invalid in listOf("123", "x", "-1")) {
            assertFalse(edit(field, CommitTextCommand(invalid, 1)))
            assertEquals(TextFieldValue("07", TextRange(0, 2)), field.value)
        }
        edit(field, SetSelectionCommand(2, 2))
        edit(field, CommitTextCommand("8", 1))
        assertEquals("8", field.value.text)
    }

    @Test
    fun deletingTheSelectionAllowsNormalTypingIntoBlankField() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        assertTrue(edit(field, CommitTextCommand("", 1)))
        assertEquals("", field.value.text)
        edit(field, CommitTextCommand("0", 1))
        edit(field, CommitTextCommand("9", 1))
        assertEquals("09", field.value.text)
    }

    @Test
    fun externalChangeWhileFocusedRestoresFirstEditSelection() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        edit(field, CommitTextCommand("2", 1))
        field.synchronize("15")
        edit(field, SetSelectionCommand(2, 2))
        edit(field, CommitTextCommand("3", 1))
        assertEquals("3", field.value.text)
    }

    @Test
    fun synchronizingAnAcceptedEditKeepsCursorAndDoesNotRestartReplacement() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        edit(field, CommitTextCommand("1", 1))
        field.synchronize("1")
        edit(field, SetSelectionCommand(1, 1))
        edit(field, CommitTextCommand("2", 1))
        assertEquals("12", field.value.text)
    }

    private fun edit(field: EditorTimeFieldState, command: EditCommand): Boolean {
        val processor = EditProcessor()
        processor.reset(field.value, null)
        return field.onValueChange(processor.apply(listOf(command)))
    }
}
