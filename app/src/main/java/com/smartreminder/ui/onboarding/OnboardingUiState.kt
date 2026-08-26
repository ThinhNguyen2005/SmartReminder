package com.smartreminder.ui.onboarding

import androidx.annotation.StringRes
import com.smartreminder.R
import com.smartreminder.domain.model.preferences.UserGoal
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class OnboardingStep(val stepIndex: Int) {
    RHYTHM(0),
    GOALS(1),
    TIMELINE(2);

    val nextStep: OnboardingStep?
        get() = when (this) {
            RHYTHM -> GOALS
            GOALS -> TIMELINE
            TIMELINE -> null
        }

    val previousStep: OnboardingStep?
        get() = when (this) {
            RHYTHM -> null
            GOALS -> RHYTHM
            TIMELINE -> GOALS
        }
}

data class GoalOption(
    val goal: UserGoal,
    @get:StringRes val titleRes: Int,
    @get:StringRes val descriptionRes: Int
)

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.RHYTHM,
    val wakeUpTime: LocalTime = LocalTime.of(7, 0),
    val sleepTime: LocalTime = LocalTime.of(23, 30),
    val selectedGoals: Set<UserGoal> = setOf(UserGoal.TASKS, UserGoal.PLANNING),
    val activeTimePicker: TimePickerTarget? = null,
    val isSaving: Boolean = false,
    val saveError: Boolean = false
) {
    val wakeUpTimeFormatted: String
        get() = wakeUpTime.format(TIME_FORMATTER)

    val sleepTimeFormatted: String
        get() = sleepTime.format(TIME_FORMATTER)

    companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

        val DEFAULT_GOALS = listOf(
            GoalOption(
                goal = UserGoal.TASKS,
                titleRes = R.string.goal_tasks_title,
                descriptionRes = R.string.goal_tasks_desc
            ),
            GoalOption(
                goal = UserGoal.ROUTINES,
                titleRes = R.string.goal_routines_title,
                descriptionRes = R.string.goal_routines_desc
            ),
            GoalOption(
                goal = UserGoal.PLANNING,
                titleRes = R.string.goal_planning_title,
                descriptionRes = R.string.goal_planning_desc
            ),
            GoalOption(
                goal = UserGoal.STUDY,
                titleRes = R.string.goal_study_title,
                descriptionRes = R.string.goal_study_desc
            ),
            GoalOption(
                goal = UserGoal.TEAMWORK,
                titleRes = R.string.goal_teamwork_title,
                descriptionRes = R.string.goal_teamwork_desc
            )
        )
    }
}

enum class TimePickerTarget {
    WAKE_UP,
    SLEEP
}
