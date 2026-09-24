package com.smartreminder.ui.today

import com.smartreminder.domain.model.today.AiSuggestion
import com.smartreminder.domain.model.today.DailyProgress
import com.smartreminder.domain.model.today.ScheduleConflict
import com.smartreminder.domain.model.today.TodayTimelineItem
import java.time.LocalDate
import java.time.LocalTime

enum class TodayViewMode {
    OVERVIEW,       // Trang 1: Daily Progress + AI Card + Up Next
    TIMELINE,       // Trang 2: Detailed Chronological Timeline with Now indicator & Free slots
    MINIMAL_STREAM, // Trang 3: Streamlined / Minimalist view
    BUSY_CONFLICT   // Trang 4: Focused on busy schedule & conflict resolution
}

sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Success(
        val userName: String,
        val userAvatarUrl: String?,
        val currentDate: LocalDate,
        val currentTime: LocalTime,
        val progress: DailyProgress,
        val aiSuggestion: AiSuggestion?,
        val timelineItems: List<TodayTimelineItem>,
        val activeConflict: ScheduleConflict?,
        val selectedViewMode: TodayViewMode = TodayViewMode.TIMELINE,
        val showAddTaskSheet: Boolean = false,
        val addTaskDefaultTime: LocalTime? = null
    ) : TodayUiState

    data class Error(val message: String? = null) : TodayUiState
}
