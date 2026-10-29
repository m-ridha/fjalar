package com.muhammadridha.fjalar.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Uses the real Compose edit buffer/change tracker shared by Android and iOS. */
@OptIn(ExperimentalFoundationApi::class)
class EditorTimeFieldStateTest {
    @Test
    fun tapAfterFocusKeepsSelectionAndFirstDigitReplacesHourOrMinute() {
        for (prefilled in listOf("07", "15", "7")) {
            for (cursor in 0..prefilled.length) {
                val field = EditorTimeFieldState(prefilled)
                field.onFocusChanged(true)
                assertEquals(TextRange(0, prefilled.length), field.state.selection)
                edit(field) { selection = TextRange(cursor) }
                assertEquals(TextRange(0, prefilled.length), field.state.selection)
                edit(field) { commitText("1") }
                assertValue(field, "1", TextRange(1))
                edit(field) { commitText("2") }
                assertValue(field, "12", TextRange(2))
            }
        }
    }

    @Test
    fun tapAndFirstDigitInOneEditBatchReplaceOnlyTheActualInsertedRange() {
        for (prefilled in listOf("07", "15", "7")) {
            for (cursor in 0..prefilled.length) {
                val field = EditorTimeFieldState(prefilled)
                field.onFocusChanged(true)
                edit(field) {
                    selection = TextRange(cursor)
                    commitText("1")
                    assertEquals(TextRange(cursor), changes.getOriginalRange(0))
                    assertEquals(TextRange(cursor, cursor + 1), changes.getRange(0))
                }
                assertValue(field, "1", TextRange(1))
                edit(field) { commitText("2") }
                assertValue(field, "12", TextRange(2))
            }
        }
    }

    @Test
    fun fullPasteOverOneDigitPreservesBothPastedDigitsEvenWhenTheyShareAPrefix() {
        val field = EditorTimeFieldState("1")
        field.onFocusChanged(true)
        edit(field) {
            commitText("12")
            assertEquals(TextRange(0, 1), changes.getOriginalRange(0))
            assertEquals(TextRange(0, 2), changes.getRange(0))
        }
        assertValue(field, "12", TextRange(2))
    }

    @Test
    fun invalidFullPasteIsRejectedInsteadOfStrippingSurvivingLookingDigits() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        edit(field) {
            commitText("007")
            assertEquals(TextRange(0, 2), changes.getOriginalRange(0))
            assertEquals(TextRange(0, 3), changes.getRange(0))
        }
        assertValue(field, "07", TextRange(0, 2))
    }

    @Test
    fun sameProposed007FromMiddleInsertionHasDifferentMetadataAndReplacesPrefillWith0() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        edit(field) {
            selection = TextRange(1)
            commitText("0")
            assertEquals("007", asCharSequence().toString())
            assertEquals(TextRange(1), changes.getOriginalRange(0))
            assertEquals(TextRange(1, 2), changes.getRange(0))
        }
        assertValue(field, "0", TextRange(1))
    }

    @Test
    fun batchedPasteRetainsAllInsertedCharactersAndRejectsAnOversizedInsertion() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        edit(field) {
            selection = TextRange(1)
            commitText("007")
        }
        assertValue(field, "07", TextRange(0, 2))
        edit(field) {
            selection = TextRange(1)
            commitText("12")
        }
        assertValue(field, "12", TextRange(2))
    }

    @Test
    fun acceptedFirstEditEqualToPrefillStillAllowsSecondDigitAndManualCursorChanges() {
        val field = EditorTimeFieldState("7")
        field.onFocusChanged(true)
        edit(field) { commitText("7") }
        assertValue(field, "7", TextRange(1))
        edit(field) { selection = TextRange.Zero }
        assertValue(field, "7", TextRange.Zero)
        edit(field) { commitText("1") }
        assertValue(field, "17", TextRange(1))
    }

    @Test
    fun refocusingSelectsEditedValueForReplacementAgain() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        edit(field) { commitText("2") }
        field.onFocusChanged(false)
        field.onFocusChanged(true)
        edit(field) {
            selection = TextRange(1)
            commitText("9")
        }
        assertValue(field, "9", TextRange(1))
    }

    @Test
    fun rejectedPastesKeepSelectionAndAllowTheNextValidReplacement() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        for (invalid in listOf("123", "x", "-1")) {
            edit(field) { commitText(invalid) }
            assertValue(field, "07", TextRange(0, 2))
        }
        edit(field) {
            selection = TextRange(2)
            commitText("8")
        }
        assertValue(field, "8", TextRange(1))
    }

    @Test
    fun deletingFirstSelectionOrBatchedFirstDigitClearsPrefillAndAllowsNormalTyping() {
        for (collapseFirst in listOf(false, true)) {
            val field = EditorTimeFieldState("07")
            field.onFocusChanged(true)
            edit(field) {
                if (collapseFirst) selection = TextRange(2)
                deletePreviousCharacter()
            }
            assertValue(field, "", TextRange.Zero)
            edit(field) { commitText("0") }
            edit(field) { commitText("9") }
            assertValue(field, "09", TextRange(2))
            edit(field) { deletePreviousCharacter() }
            assertValue(field, "0", TextRange(1))
        }
    }

    @Test
    fun externalResynchronizationSelectsNewValueButIdenticalTextPreservesCursor() {
        val field = EditorTimeFieldState("07")
        field.onFocusChanged(true)
        edit(field) { commitText("1") }
        field.synchronize("1")
        edit(field) { commitText("2") }
        assertValue(field, "12", TextRange(2))
        field.synchronize("15")
        assertValue(field, "15", TextRange(0, 2))
        edit(field) {
            selection = TextRange(1)
            commitText("3")
        }
        assertValue(field, "3", TextRange(1))
        field.onFocusChanged(false)
        field.synchronize("07")
        assertValue(field, "07", TextRange.Zero)
    }

    @Test
    fun confirmationReadsCurrentAuthoritativeTextWithoutWaitingForAParentCallback() {
        val hour = EditorTimeFieldState("00")
        val minute = EditorTimeFieldState("00")
        val confirm = { editorMinuteOfDay(hour.text, minute.text) }
        hour.onFocusChanged(true)
        edit(hour) { commitText("07") }
        minute.onFocusChanged(true)
        edit(minute) { commitText("13") }
        assertEquals(7 * 60 + 13, confirm())
        minute.onFocusChanged(false)
        minute.onFocusChanged(true)
        edit(minute) { commitText("") }
        assertNull(confirm())
        edit(minute) { commitText("60") }
        assertNull(confirm())
        minute.synchronize("59")
        assertEquals(7 * 60 + 59, confirm())
    }

    private fun edit(field: EditorTimeFieldState, commands: TextFieldBuffer.() -> Unit) {
        field.state.edit {
            commands()
            with(field.inputTransformation) { transformInput() }
        }
    }

    /** Equivalent to an IME commit replacing the current selection, in this same edit batch. */
    private fun TextFieldBuffer.commitText(text: String) {
        val start = selection.min
        replace(start, selection.max, text)
        selection = TextRange(start + text.length)
    }

    private fun TextFieldBuffer.deletePreviousCharacter() {
        if (!selection.collapsed) {
            commitText("")
        } else if (selection.min > 0) {
            val cursor = selection.min
            replace(cursor - 1, cursor, "")
            selection = TextRange(cursor - 1)
        }
    }

    private fun assertValue(field: EditorTimeFieldState, text: String, selection: TextRange) {
        assertEquals(text, field.text)
        assertEquals(selection, field.state.selection)
    }
}
