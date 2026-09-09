package com.smartreminder.domain.repository

sealed interface CollaborationError {
    data class NetworkUnavailable(val cause: Throwable? = null) : CollaborationError
    data object NotAuthorized : CollaborationError
    data object NotFound : CollaborationError
    data class Conflict(val message: String? = null) : CollaborationError
    data class InvalidState(val message: String? = null) : CollaborationError
    data class Validation(val message: String) : CollaborationError
    data object MemberNotFound : CollaborationError
    data object InviteAlreadyPending : CollaborationError
    data object AlreadyMember : CollaborationError
    data class SyncRejected(val message: String? = null) : CollaborationError
    data class Unknown(val cause: Throwable? = null) : CollaborationError
}
