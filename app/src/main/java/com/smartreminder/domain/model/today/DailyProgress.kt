package com.smartreminder.domain.model.today

/**
 * Value object tracking task and routine completion progress for today.
 */
data class DailyProgress(
    val completedCount: Int,
    val totalCount: Int
) {
    val fraction: Float = if (totalCount > 0) {
        (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
}
