package com.smartreminder.domain.repository

sealed interface CollaborationMutationResult {
    data object Applied : CollaborationMutationResult
    data object Queued : CollaborationMutationResult
    data class Conflict(val error: CollaborationError) : CollaborationMutationResult
    data class NotAuthorized(val error: CollaborationError) : CollaborationMutationResult
    data class InvalidState(val error: CollaborationError) : CollaborationMutationResult
    data object NetworkRequired : CollaborationMutationResult
    data class Failure(val error: CollaborationError) : CollaborationMutationResult
}
