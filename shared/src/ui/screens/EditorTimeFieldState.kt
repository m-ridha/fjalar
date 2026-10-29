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
        if (proposed.text == value.text) {
            if (!isAccepted(proposed.text)) return false
            // A tap can collapse the selection after focus is gained. Restore it before typing.
            value = if (awaitingFirstEdit) {
                proposed.copy(selection = TextRange(0, proposed.text.length))
            } else {
                proposed
            }
            return false
        }

        // Some IMEs batch the tap's collapsed cursor with the first digit. Keep only the
        // typed characters so the prefilled value is replaced instead of appended to.
        val editedText = if (awaitingFirstEdit) {
            textInsertedInto(value.text, proposed.text)
        } else {
            proposed.text
        }
        if (!isAccepted(editedText)) return false

        value = if (awaitingFirstEdit) {
            TextFieldValue(editedText, TextRange(editedText.length))
        } else {
            proposed
        }
        awaitingFirstEdit = false
        return true
    }

    private fun isAccepted(text: String): Boolean =
        text.length <= 2 && text.all { it in '0'..'9' }

    private fun textInsertedInto(previous: String, proposed: String): String {
        if (previous.isEmpty()) return proposed
        // A collapsed tap cursor makes the IME append or prepend to the prefilled value.
        if (proposed.startsWith(previous)) return proposed.removePrefix(previous)
        if (proposed.endsWith(previous)) return proposed.removeSuffix(previous)
        // The IME already replaced the selection with a complete new value.
        if (isAccepted(proposed)) return proposed
        return textBetweenCommonEdges(previous, proposed)
    }

    /** Strips the characters that survived from [previous] when the first digit lands mid-value. */
    private fun textBetweenCommonEdges(previous: String, proposed: String): String {
        val commonPrefixLength = previous.commonPrefixWith(proposed).length
        val remainingPrevious = previous.drop(commonPrefixLength)
        val remainingProposed = proposed.drop(commonPrefixLength)
        val commonSuffixLength = remainingPrevious.commonSuffixWith(remainingProposed).length
        return remainingProposed.dropLast(commonSuffixLength)
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
