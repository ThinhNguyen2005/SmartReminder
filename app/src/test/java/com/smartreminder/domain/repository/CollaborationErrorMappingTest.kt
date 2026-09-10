package com.smartreminder.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollaborationErrorMappingTest {

    @Test
    fun `typed error envelopes map to domain errors without parsing database text`() {
        val cases = listOf(
            CollaborationErrorCode.NOT_AUTHORIZED to CollaborationError.NotAuthorized,
            CollaborationErrorCode.MEMBER_NOT_FOUND to CollaborationError.MemberNotFound,
            CollaborationErrorCode.ALREADY_MEMBER to CollaborationError.AlreadyMember,
            CollaborationErrorCode.INVITE_ALREADY_PENDING to CollaborationError.InviteAlreadyPending,
            CollaborationErrorCode.INVALID_STATE to CollaborationError.InvalidState()
        )

        cases.forEach { (code, expected) ->
            assertEquals(expected, CollaborationError.fromEnvelope(CollaborationErrorEnvelope(code)))
        }
    }

    @Test
    fun `typed mutation envelopes map to typed result variants`() {
        val cases = listOf(
            CollaborationMutationEnvelope(CollaborationMutationStatus.APPLIED) to CollaborationMutationResult.Applied,
            CollaborationMutationEnvelope(CollaborationMutationStatus.QUEUED) to CollaborationMutationResult.Queued,
            CollaborationMutationEnvelope(CollaborationMutationStatus.NETWORK_REQUIRED) to CollaborationMutationResult.NetworkRequired,
            CollaborationMutationEnvelope(
                status = CollaborationMutationStatus.NOT_AUTHORIZED,
                error = CollaborationErrorEnvelope(CollaborationErrorCode.NOT_AUTHORIZED)
            ) to CollaborationMutationResult.NotAuthorized(CollaborationError.NotAuthorized),
            CollaborationMutationEnvelope(
                status = CollaborationMutationStatus.FAILURE,
                error = CollaborationErrorEnvelope(CollaborationErrorCode.MEMBER_NOT_FOUND)
            ) to CollaborationMutationResult.Failure(CollaborationError.MemberNotFound),
            CollaborationMutationEnvelope(CollaborationMutationStatus.MEMBER_NOT_FOUND) to
                CollaborationMutationResult.Failure(CollaborationError.MemberNotFound),
            CollaborationMutationEnvelope(CollaborationMutationStatus.ALREADY_MEMBER) to
                CollaborationMutationResult.Failure(CollaborationError.AlreadyMember),
            CollaborationMutationEnvelope(CollaborationMutationStatus.INVITE_ALREADY_PENDING) to
                CollaborationMutationResult.Failure(CollaborationError.InviteAlreadyPending)
        )

        cases.forEach { (envelope, expected) ->
            assertEquals(expected, CollaborationMutationResult.fromEnvelope(envelope))
        }
    }

    @Test
    fun `unknown typed error remains a domain error`() {
        val result = CollaborationMutationResult.fromEnvelope(
            CollaborationMutationEnvelope(
                status = CollaborationMutationStatus.FAILURE,
                error = CollaborationErrorEnvelope(CollaborationErrorCode.UNKNOWN)
            )
        )

        assertTrue(result is CollaborationMutationResult.Failure)
        assertTrue((result as CollaborationMutationResult.Failure).error is CollaborationError.Unknown)
    }
}
