package com.muhammadridha.fjalar

internal data class FoldControlInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

/** Selects the larger pane and leaves touch clearance beside a separating fold. */
internal fun foldControlInsets(
    width: Int,
    height: Int,
    foldLeft: Int,
    foldTop: Int,
    foldRight: Int,
    foldBottom: Int,
    isVertical: Boolean,
    clearance: Int,
): FoldControlInsets {
    if (isVertical) {
        val leadingEnd = (foldLeft - clearance).coerceIn(0, width)
        val trailingStart = (foldRight + clearance).coerceIn(0, width)
        return if (leadingEnd >= width - trailingStart) {
            FoldControlInsets(right = width - leadingEnd)
        } else {
            FoldControlInsets(left = trailingStart)
        }
    }
    val topEnd = (foldTop - clearance).coerceIn(0, height)
    val bottomStart = (foldBottom + clearance).coerceIn(0, height)
    return if (topEnd >= height - bottomStart) {
        FoldControlInsets(bottom = height - topEnd)
    } else {
        FoldControlInsets(top = bottomStart)
    }
}
