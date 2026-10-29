package com.muhammadridha.fjalar.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Keeps the initial selection through tap/IME selection updates until the first text edit. */
internal class EditorTimeFieldState(text: String) {
    var value by mutableStateOf(TextFieldValue(text))
        private set

    private var focused = false
    private var awaitingFirstEdit = false

    fun onFocusChanged(isFocused: Boolean) {
        if (isFocused && !focused) {
            awaitingFirstEdit = true
            value = value.copy(selection = TextRange(0, value.text.length))
        }
        if (!isFocused) awaitingFirstEdit = false
        focused = isFocused
    }

    /** Returns true only when an accepted edit changes the text. */
    fun onValueChange(proposed: TextFieldValue): Boolean {
        if (proposed.text.length > 2 || proposed.text.any { it !in '0'..'9' }) return false

        val textChanged = proposed.text != value.text
        value = if (awaitingFirstEdit && !textChanged) {
            // A tap can collapse the selection after focus is gained. Restore it before typing.
            proposed.copy(selection = TextRange(0, proposed.text.length))
        } else {
            proposed
        }
        if (textChanged) awaitingFirstEdit = false
        return textChanged
    }

    fun synchronize(text: String) {
        if (text == value.text) return
        awaitingFirstEdit = focused
        value = TextFieldValue(
            text = text,
            selection = if (focused) TextRange(0, text.length) else TextRange.Zero,
        )
    }
}
