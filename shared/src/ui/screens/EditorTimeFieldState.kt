package com.muhammadridha.fjalar.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.ui.text.TextRange

/** Keeps replace-all intent through selection/IME batches without guessing from proposed text. */
@OptIn(ExperimentalFoundationApi::class)
internal class EditorTimeFieldState(text: String) {
    val state = TextFieldState(initialText = text)
    val text: String get() = state.text.toString()
    private var focused = false

    val inputTransformation = InputTransformation {
        val replacingPrefill = originalText.isNotEmpty() &&
            originalSelection.min == 0 && originalSelection.max == originalText.length

        if (changes.changeCount == 0) {
            // The focus tap may collapse the select-all range before the first text edit.
            if (replacingPrefill) selectAll()
            return@InputTransformation
        }

        if (replacingPrefill && changes.changeCount == 1) {
            val originalRange = changes.getOriginalRange(0)
            val editedRange = changes.getRange(0)
            val replacesWholeValue = originalRange.min == 0 && originalRange.max == originalText.length
            // A full replacement (including paste) owns the whole proposed value. A batched
            // cursor move + insertion owns exactly the inserted range, not surviving old digits.
            val replacement = if (replacesWholeValue) {
                asCharSequence().toString()
            } else {
                asCharSequence().subSequence(editedRange.min, editedRange.max).toString()
            }
            if (!isAccepted(replacement)) {
                revertAllChanges()
                return@InputTransformation
            }
            replace(0, length, replacement)
            placeCursorAtEnd()
        } else if (!isAccepted(asCharSequence())) {
            revertAllChanges()
        }
    }

    fun onFocusChanged(isFocused: Boolean) {
        if (isFocused && !focused) state.edit { selectAll() }
        focused = isFocused
    }

    fun synchronize(text: String) {
        if (text == this.text) return
        state.edit {
            replace(0, length, text)
            selection = if (focused) TextRange(0, length) else TextRange.Zero
        }
    }

    private fun isAccepted(text: CharSequence): Boolean =
        text.length <= 2 && text.all { it in '0'..'9' }
}
