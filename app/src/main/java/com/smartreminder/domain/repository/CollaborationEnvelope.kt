package com.smartreminder.domain.repository

enum class CollaborationErrorCode {
    NETWORK_UNAVAILABLE,
    NOT_AUTHORIZED,
    NOT_FOUND,
    CONFLICT,
    INVALID_STATE,
    VALIDATION,
    MEMBER_NOT_FOUND,
    INVITE_ALREADY_PENDING,
    ALREADY_MEMBER,
    SYNC_REJECTED,
    UNKNOWN
}

/** A typed server error; callers never need to inspect a database error string. */
data class CollaborationErrorEnvelope(
    val code: CollaborationErrorCode,
    val detail: String? = null
)

enum class CollaborationMutationStatus {
    APPLIED,
    QUEUED,
    NETWORK_REQUIRED,
    CONFLICT,
    NOT_AUTHORIZED,
    NOT_FOUND,
    VALIDATION,
    MEMBER_NOT_FOUND,
    ALREADY_MEMBER,
    INVITE_ALREADY_PENDING,
    INVALID_STATE,
    FAILURE
}

data class CollaborationMutationEnvelope(
    val status: CollaborationMutationStatus,
    val error: CollaborationErrorEnvelope? = null
)
