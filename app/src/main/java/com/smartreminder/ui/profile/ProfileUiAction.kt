package com.smartreminder.ui.profile

import java.time.LocalTime

sealed interface ProfileUiAction {
    data object RequestSignOut : ProfileUiAction
    data object ConfirmSignOut : ProfileUiAction
    data object DismissSignOutDialog : ProfileUiAction
    data object OpenWakeTimePicker : ProfileUiAction
    data object DismissWakeTimePicker : ProfileUiAction
    data class UpdateWakeTime(val time: LocalTime) : ProfileUiAction
    data object OpenSleepTimePicker : ProfileUiAction
    data object DismissSleepTimePicker : ProfileUiAction
    data class UpdateSleepTime(val time: LocalTime) : ProfileUiAction
    data object DismissError : ProfileUiAction
}
