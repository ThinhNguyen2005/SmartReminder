package com.smartreminder.data.local.room.repository

import com.smartreminder.data.local.room.dao.ScheduleGroupDao
import com.smartreminder.data.local.room.mapper.ScheduleGroupMapper
import com.smartreminder.domain.model.schedule.ScheduleGroup
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import com.smartreminder.domain.repository.ScheduleGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomScheduleGroupRepository(
    private val dao: ScheduleGroupDao
) : ScheduleGroupRepository {

    override fun observeGroups(): Flow<List<ScheduleGroup>> {
        return dao.observeActiveGroups().map { list ->
            list.map { ScheduleGroupMapper.toDomain(it) }
        }
    }

    override suspend fun getGroup(id: ScheduleGroupId): ScheduleGroup? {
        return dao.getGroupById(id.value)?.let { ScheduleGroupMapper.toDomain(it) }
    }

    override suspend fun upsert(group: ScheduleGroup) {
        dao.upsertGroup(ScheduleGroupMapper.toEntity(group))
    }

    override suspend fun archive(id: ScheduleGroupId) {
        dao.archiveGroup(id.value)
    }
}
