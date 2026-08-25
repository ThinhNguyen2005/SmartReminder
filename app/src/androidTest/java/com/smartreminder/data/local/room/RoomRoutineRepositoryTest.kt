package com.smartreminder.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartreminder.data.local.room.repository.RoomRoutineRepository
import com.smartreminder.data.local.room.repository.RoomScheduleGroupRepository
import com.smartreminder.domain.model.schedule.OverrideType
import com.smartreminder.domain.model.schedule.RecurrenceRule
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineItem
import com.smartreminder.domain.model.schedule.RoutineOverride
import com.smartreminder.domain.model.schedule.ScheduleGroup
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RoomRoutineRepositoryTest {

    private lateinit var database: CueDatabase
    private lateinit var routineRepository: RoomRoutineRepository
    private lateinit var groupRepository: RoomScheduleGroupRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routineRepository = RoomRoutineRepository(database.routineDao())
        groupRepository = RoomScheduleGroupRepository(database.scheduleGroupDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun givenRoutineWithItems_whenUpserted_thenDetailsMatchSnapshotExactly() = runTest {
        val routine = Routine(
            id = RoutineId("univ_day"),
            name = "University Day",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        )
        val items = listOf(
            RoutineItem(
                id = RoutineItemId("item_1"),
                routineId = routine.id,
                title = "Prepare laptop",
                scheduledTime = LocalTime.of(7, 20),
                sortOrder = 0
            ),
            RoutineItem(
                id = RoutineItemId("item_2"),
                routineId = routine.id,
                title = "Class",
                scheduledTime = LocalTime.of(8, 0),
                durationMinutes = 90,
                sortOrder = 1
            )
        )

        routineRepository.upsertRoutine(routine, items)

        val details = routineRepository.getRoutineDetails(routine.id)
        assertNotNull(details)
        assertEquals("University Day", details?.routine?.name)
        assertEquals(2, details?.items?.size)
        assertEquals("Prepare laptop", details?.items?.get(0)?.title)
        assertEquals("Class", details?.items?.get(1)?.title)
    }

    @Test
    fun givenRoutineWithItems_whenUpdatedWithRemovedItem_thenTransactionReplacesChildrenWithoutOrphans() = runTest {
        val routine = Routine(
            id = RoutineId("univ_day"),
            name = "University Day",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        )
        val initialItems = listOf(
            RoutineItem(RoutineItemId("item_1"), routine.id, "First", LocalTime.of(7, 0), sortOrder = 0),
            RoutineItem(RoutineItemId("item_2"), routine.id, "Second (To be removed)", LocalTime.of(7, 30), sortOrder = 1),
            RoutineItem(RoutineItemId("item_3"), routine.id, "Third", LocalTime.of(8, 0), sortOrder = 2)
        )
        routineRepository.upsertRoutine(routine, initialItems)

        // When: Update routine with item_2 removed
        val updatedItems = listOf(
            RoutineItem(RoutineItemId("item_1"), routine.id, "First", LocalTime.of(7, 0), sortOrder = 0),
            RoutineItem(RoutineItemId("item_3"), routine.id, "Third", LocalTime.of(8, 0), sortOrder = 1)
        )
        routineRepository.upsertRoutine(routine, updatedItems)

        // Then: Persisted details strictly match updated snapshot (no orphaned item_2)
        val details = routineRepository.getRoutineDetails(routine.id)
        assertEquals(2, details?.items?.size)
        assertEquals("First", details?.items?.get(0)?.title)
        assertEquals("Third", details?.items?.get(1)?.title)
    }

    @Test
    fun givenRoutineBelongingToGroup_whenGroupDeleted_thenRoutineGroupIdIsSetToNull() = runTest {
        val group = ScheduleGroup(
            id = ScheduleGroupId("study_group"),
            name = "Study"
        )
        groupRepository.upsert(group)

        val routine = Routine(
            id = RoutineId("univ_day"),
            groupId = group.id,
            name = "University Day",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY))
        )
        routineRepository.upsertRoutine(routine)

        // When: Group is deleted
        database.scheduleGroupDao().deleteGroup(group.id.value)

        // Then: Routine still exists, but groupId is null (ON DELETE SET NULL)
        val retrievedRoutine = routineRepository.getRoutine(routine.id)
        assertNotNull(retrievedRoutine)
        assertNull(retrievedRoutine?.groupId)
    }

    @Test
    fun givenRoutine_whenDeleted_thenCascadeDeletesItemsAndWeeklyDays() = runTest {
        val routine = Routine(
            id = RoutineId("univ_day"),
            name = "University Day",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY))
        )
        val items = listOf(
            RoutineItem(RoutineItemId("item_1"), routine.id, "Class", LocalTime.of(8, 0), sortOrder = 0)
        )
        routineRepository.upsertRoutine(routine, items)

        // When: Delete routine
        database.routineDao().deleteRoutine(routine.id.value)

        // Then: Routine and all items are gone
        assertNull(routineRepository.getRoutine(routine.id))
        assertNull(routineRepository.getRoutineDetails(routine.id))
    }

    @Test
    fun givenOverride_whenUpsertedAndQueried_thenReturnsAccurateOverride() = runTest {
        val routine = Routine(
            id = RoutineId("univ_day"),
            name = "University Day",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY))
        )
        routineRepository.upsertRoutine(routine)

        val date = LocalDate.of(2026, 8, 24)
        val override = RoutineOverride(
            routineId = routine.id,
            date = date,
            type = OverrideType.SKIP
        )

        routineRepository.upsertOverride(override)

        val retrieved = routineRepository.getOverride(routine.id, date)
        assertNotNull(retrieved)
        assertEquals(OverrideType.SKIP, retrieved?.type)
        assertEquals(date, retrieved?.date)

        // Delete override
        routineRepository.deleteOverride(routine.id, date)
        assertNull(routineRepository.getOverride(routine.id, date))
    }
}
