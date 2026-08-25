package com.smartreminder.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartreminder.data.local.room.repository.RoomScheduleGroupRepository
import com.smartreminder.domain.model.schedule.ScheduleGroup
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomScheduleGroupRepositoryTest {

    private lateinit var database: CueDatabase
    private lateinit var repository: RoomScheduleGroupRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomScheduleGroupRepository(database.scheduleGroupDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun givenNewGroup_whenUpserted_thenCanBeRetrievedAndObserved() = runTest {
        val group = ScheduleGroup(
            id = ScheduleGroupId("group_study"),
            name = "Study",
            iconKey = "school",
            colorKey = "indigo",
            sortOrder = 1
        )

        repository.upsert(group)

        val retrieved = repository.getGroup(group.id)
        assertEquals("Study", retrieved?.name)
        assertEquals("school", retrieved?.iconKey)

        val activeList = repository.observeGroups().first()
        assertEquals(1, activeList.size)
        assertEquals(group.id, activeList[0].id)
    }

    @Test
    fun givenMultipleGroups_whenObserved_thenOrdersBySortOrderThenCreatedAt() = runTest {
        val groupB = ScheduleGroup(
            id = ScheduleGroupId("group_b"),
            name = "Personal",
            sortOrder = 2
        )
        val groupA = ScheduleGroup(
            id = ScheduleGroupId("group_a"),
            name = "Study",
            sortOrder = 1
        )

        repository.upsert(groupB)
        repository.upsert(groupA)

        val list = repository.observeGroups().first()
        assertEquals(2, list.size)
        assertEquals("group_a", list[0].id.value)
        assertEquals("group_b", list[1].id.value)
    }

    @Test
    fun givenActiveGroup_whenArchived_thenNoLongerAppearsInActiveObservation() = runTest {
        val group = ScheduleGroup(
            id = ScheduleGroupId("group_study"),
            name = "Study",
            sortOrder = 0
        )
        repository.upsert(group)

        repository.archive(group.id)

        val activeList = repository.observeGroups().first()
        assertTrue(activeList.isEmpty())

        val retrieved = repository.getGroup(group.id)
        assertTrue(retrieved?.isArchived == true)
    }
}
