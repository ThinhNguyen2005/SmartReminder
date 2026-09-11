package com.smartreminder.data.repository.collaboration

import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import kotlinx.coroutines.flow.Flow

/** Room-facing cache contract kept separate from Android Room in repository tests. */
interface CollaborationCacheDataSource {
    fun observeGroups(): Flow<List<CachedCollaborationGroupEntity>>
    fun observeGroup(groupId: String): Flow<CachedCollaborationGroupEntity?>
    fun observeMembers(groupId: String): Flow<List<CachedGroupMemberEntity>>
    fun observeTasks(groupId: String): Flow<List<CachedGroupTaskEntity>>
    fun observeInvites(): Flow<List<CachedGroupInviteEntity>>

    suspend fun replaceGroups(groups: List<CachedCollaborationGroupEntity>)
    suspend fun replaceGroup(
        group: CachedCollaborationGroupEntity,
        members: List<CachedGroupMemberEntity>
    )
    suspend fun replaceInvites(invites: List<CachedGroupInviteEntity>)
    suspend fun removeGroup(groupId: String)

    /** Removes all collaboration rows at an authenticated-session boundary. */
    suspend fun clearAll() = Unit

    /** Removes invite rows that could otherwise outlive a revoked group membership. */
    suspend fun removeInvitesForGroup(groupId: String) = Unit

    /** Removes one invite after a response or explicit invite revocation. */
    suspend fun removeInvite(inviteId: String) = Unit
}
