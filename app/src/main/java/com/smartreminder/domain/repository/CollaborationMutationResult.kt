package com.smartreminder.domain.repository

sealed interface CollaborationMutationResult {
    companion object {
        fun fromEnvelope(envelope: CollaborationMutationEnvelope): CollaborationMutationResult {
            val error = envelope.error?.let(CollaborationError::fromEnvelope)
            return when (envelope.status) {
                CollaborationMutationStatus.APPLIED -> Applied
                CollaborationMutationStatus.QUEUED -> Queued
                CollaborationMutationStatus.NETWORK_REQUIRED -> NetworkRequired
                CollaborationMutationStatus.CONFLICT ->
                    Conflict(error ?: CollaborationError.Conflict())
                CollaborationMutationStatus.NOT_AUTHORIZED ->
                    NotAuthorized(error ?: CollaborationError.NotAuthorized)
                CollaborationMutationStatus.MEMBER_NOT_FOUND ->
                    Failure(error ?: CollaborationError.MemberNotFound)
                CollaborationMutationStatus.ALREADY_MEMBER ->
                    Failure(error ?: CollaborationError.AlreadyMember)
                CollaborationMutationStatus.INVITE_ALREADY_PENDING ->
                    Failure(error ?: CollaborationError.InviteAlreadyPending)
                CollaborationMutationStatus.INVALID_STATE ->
                    InvalidState(error ?: CollaborationError.InvalidState())
                CollaborationMutationStatus.FAILURE ->
                    Failure(error ?: CollaborationError.Unknown())
            }
        }
    }

    data object Applied : CollaborationMutationResult
    data object Queued : CollaborationMutationResult
    data class Conflict(val error: CollaborationError) : CollaborationMutationResult
    data class NotAuthorized(val error: CollaborationError) : CollaborationMutationResult
    data class InvalidState(val error: CollaborationError) : CollaborationMutationResult
    data object NetworkRequired : CollaborationMutationResult
    data class Failure(val error: CollaborationError) : CollaborationMutationResult
}
