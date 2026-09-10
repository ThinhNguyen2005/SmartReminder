package com.smartreminder.domain.repository

sealed interface CollaborationError {
    companion object {
        fun fromEnvelope(envelope: CollaborationErrorEnvelope): CollaborationError =
            when (envelope.code) {
                CollaborationErrorCode.NETWORK_UNAVAILABLE -> NetworkUnavailable()
                CollaborationErrorCode.NOT_AUTHORIZED -> NotAuthorized
                CollaborationErrorCode.NOT_FOUND -> NotFound
                CollaborationErrorCode.CONFLICT -> Conflict(envelope.detail)
                CollaborationErrorCode.INVALID_STATE -> InvalidState(envelope.detail)
                CollaborationErrorCode.VALIDATION ->
                    Validation(envelope.detail ?: "Validation failed")
                CollaborationErrorCode.MEMBER_NOT_FOUND -> MemberNotFound
                CollaborationErrorCode.INVITE_ALREADY_PENDING -> InviteAlreadyPending
                CollaborationErrorCode.ALREADY_MEMBER -> AlreadyMember
                CollaborationErrorCode.SYNC_REJECTED -> SyncRejected(envelope.detail)
                CollaborationErrorCode.UNKNOWN -> Unknown()
            }
    }

    data class NetworkUnavailable(val cause: Throwable? = null) : CollaborationError
    data object ConfigurationMissing : CollaborationError
    data object NotAuthorized : CollaborationError
    data object NotFound : CollaborationError
    data class Conflict(val message: String? = null) : CollaborationError
    data class InvalidState(val message: String? = null) : CollaborationError
    data class Validation(val message: String) : CollaborationError
    data object MemberNotFound : CollaborationError
    data object InviteAlreadyPending : CollaborationError
    data object AlreadyMember : CollaborationError
    data class SyncRejected(val message: String? = null) : CollaborationError
    data class MappingFailure(
        val message: String,
        val cause: Throwable? = null
    ) : CollaborationError
    data class Unknown(val cause: Throwable? = null) : CollaborationError
}
