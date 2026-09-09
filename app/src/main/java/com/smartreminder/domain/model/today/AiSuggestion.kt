package com.smartreminder.domain.model.today

import java.time.LocalTime

/**
 * AI suggestion banner model displayed at the top of the Today screen.
 */
data class AiSuggestion(
    val id: String,
    val message: String = "",
    val focusWindowStart: LocalTime? = null,
    val focusWindowEnd: LocalTime? = null,
    val actionLabel: String = "View plan",
    val suggestedTaskTitle: String? = null,
    val durationMinutes: Int = 0
)
