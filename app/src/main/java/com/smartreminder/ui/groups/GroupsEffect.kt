package com.smartreminder.ui.groups

sealed interface GroupsEffect {
    data object NavigateToList : GroupsEffect
    data class MutationCompleted(val mutation: GroupsMutation) : GroupsEffect
}
