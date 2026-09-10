package com.smartreminder.data.remote.collaboration

/** Indicates that a remote payload violates the collaboration data contract. */
class CollaborationMappingException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)
