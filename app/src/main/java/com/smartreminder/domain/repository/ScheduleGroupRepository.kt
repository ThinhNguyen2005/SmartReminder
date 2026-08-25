package com.smartreminder.domain.repository

import com.smartreminder.domain.model.schedule.ScheduleGroup
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import kotlinx.coroutines.flow.Flow

interface ScheduleGroupRepository {
    fun observeGroups(): Flow<List<ScheduleGroup>>
    suspend fun getGroup(id: ScheduleGroupId): ScheduleGroup?
    suspend fun upsert(group: ScheduleGroup)
    suspend fun archive(id: ScheduleGroupId)
}
