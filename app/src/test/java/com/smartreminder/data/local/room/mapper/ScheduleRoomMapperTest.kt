package com.smartreminder.data.local.room.mapper

import com.smartreminder.data.local.room.entity.RoutineEntity
import com.smartreminder.data.local.room.entity.RoutineItemEntity
import com.smartreminder.data.local.room.entity.RoutineOverrideEntity
import com.smartreminder.data.local.room.entity.RoutineWeeklyDayEntity
import com.smartreminder.data.local.room.relation.RoutineWithDetailsEntity
import com.smartreminder.domain.model.schedule.OverrideType
import com.smartreminder.domain.model.schedule.RecurrenceRule
import com.smartreminder.domain.model.schedule.Routine
import com.smartreminder.domain.model.schedule.RoutineItem
import com.smartreminder.domain.model.schedule.RoutineOverride
import com.smartreminder.domain.model.schedule.ScheduleGroup
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ScheduleRoomMapperTest {

    @Test
    fun `given ScheduleGroup domain, when mapped to entity and back, then properties match`() {
        val domain = ScheduleGroup(
            id = ScheduleGroupId("group_1"),
            name = "Study",
            iconKey = "school",
            colorKey = "indigo",
            sortOrder = 1,
            isArchived = false,
            createdAt = Instant.ofEpochMilli(1700000000000L),
            updatedAt = Instant.ofEpochMilli(1700000001000L)
        )

        val entity = ScheduleGroupMapper.toEntity(domain)
        val roundTrip = ScheduleGroupMapper.toDomain(entity)

        assertEquals(domain.id, roundTrip.id)
        assertEquals(domain.name, roundTrip.name)
        assertEquals(domain.iconKey, roundTrip.iconKey)
        assertEquals(domain.colorKey, roundTrip.colorKey)
        assertEquals(domain.sortOrder, roundTrip.sortOrder)
        assertEquals(domain.isArchived, roundTrip.isArchived)
        assertEquals(domain.createdAt, roundTrip.createdAt)
        assertEquals(domain.updatedAt, roundTrip.updatedAt)
    }

    @Test
    fun `given Routine domain with Weekly recurrence, when mapped to entities and back, then properties match`() {
        val domain = Routine(
            id = RoutineId("routine_1"),
            groupId = ScheduleGroupId("group_1"),
            name = "University Day",
            description = "Mon, Wed, Fri classes",
            iconKey = "school",
            colorKey = "indigo",
            recurrence = RecurrenceRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            enabled = true,
            sortOrder = 0,
            createdAt = Instant.ofEpochMilli(1700000000000L),
            updatedAt = Instant.ofEpochMilli(1700000001000L)
        )

        val routineEntity = RoutineMapper.toEntity(domain)
        val weeklyDays = RoutineMapper.toWeeklyDayEntities(domain)

        assertEquals(3, weeklyDays.size)
        assertTrue(weeklyDays.any { it.dayOfWeek == 1 })
        assertTrue(weeklyDays.any { it.dayOfWeek == 3 })
        assertTrue(weeklyDays.any { it.dayOfWeek == 5 })

        val roundTrip = RoutineMapper.toDomain(routineEntity, weeklyDays)
        assertEquals(domain.id, roundTrip.id)
        assertEquals(domain.groupId, roundTrip.groupId)
        assertEquals(domain.name, roundTrip.name)
        assertEquals(domain.recurrence, roundTrip.recurrence)
        assertEquals(domain.enabled, roundTrip.enabled)
    }

    @Test
    fun `given RoutineItem domain, when mapped to entity and back, then scheduledMinute maps to LocalTime correctly`() {
        val domain = RoutineItem(
            id = RoutineItemId("item_1"),
            routineId = RoutineId("routine_1"),
            title = "Prepare laptop",
            scheduledTime = LocalTime.of(7, 20),
            durationMinutes = 15,
            sortOrder = 1,
            enabled = true
        )

        val entity = RoutineItemMapper.toEntity(domain)
        assertEquals(440, entity.scheduledMinute) // 7 * 60 + 20 = 440

        val roundTrip = RoutineItemMapper.toDomain(entity)
        assertEquals(domain.id, roundTrip.id)
        assertEquals(domain.title, roundTrip.title)
        assertEquals(LocalTime.of(7, 20), roundTrip.scheduledTime)
        assertEquals(15, roundTrip.durationMinutes)
    }

    @Test
    fun `given RoutineOverride domain, when mapped to entity and back, then date maps to epochDay and type uses stable storageKey`() {
        val date = LocalDate.of(2026, 8, 26)
        val domain = RoutineOverride(
            routineId = RoutineId("routine_1"),
            date = date,
            type = OverrideType.SKIP
        )

        val entity = RoutineOverrideMapper.toEntity(domain)
        assertEquals(date.toEpochDay(), entity.overrideDateEpochDay)
        assertEquals("skip", entity.overrideType)

        val roundTrip = RoutineOverrideMapper.toDomain(entity)
        assertEquals(domain.routineId, roundTrip.routineId)
        assertEquals(date, roundTrip.date)
        assertEquals(OverrideType.SKIP, roundTrip.type)
    }

    @Test
    fun `given RoutineWithDetailsEntity, when mapped to RoutineDetails, then items are ordered deterministically`() {
        val routineEntity = RoutineEntity(
            id = "routine_1",
            groupId = null,
            name = "Morning",
            description = null,
            iconKey = null,
            colorKey = null,
            enabled = true,
            sortOrder = 0,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )
        val weeklyDays = listOf(RoutineWeeklyDayEntity("routine_1", 1))
        val item1 = RoutineItemEntity("item_2", "routine_1", "Second", 480, 30, 2, true)
        val item2 = RoutineItemEntity("item_1", "routine_1", "First", 420, 20, 1, true)

        val composite = RoutineWithDetailsEntity(
            routine = routineEntity,
            weeklyDays = weeklyDays,
            items = listOf(item1, item2)
        )

        val details = RoutineMapper.toDetailsDomain(composite)
        assertEquals(2, details.items.size)
        assertEquals("First", details.items[0].title)
        assertEquals("Second", details.items[1].title)
    }

    @Test(expected = IllegalStateException::class)
    fun `given invalid day_of_week in database, when mapping routine, then throws IllegalStateException`() {
        val routineEntity = RoutineEntity("r1", null, "Test", null, null, null, true, 0, 0L, 0L)
        val corruptDay = listOf(RoutineWeeklyDayEntity("r1", 99))

        RoutineMapper.toDomain(routineEntity, corruptDay)
    }

    @Test(expected = IllegalStateException::class)
    fun `given empty weekly days in database, when mapping routine, then throws IllegalStateException`() {
        val routineEntity = RoutineEntity("r1", null, "Test", null, null, null, true, 0, 0L, 0L)
        RoutineMapper.toDomain(routineEntity, emptyList())
    }

    @Test(expected = IllegalStateException::class)
    fun `given out of bounds scheduled_minute in database, when mapping item, then throws IllegalStateException`() {
        val corruptItem = RoutineItemEntity("i1", "r1", "Bad Minute", 9999, null, 0, true)
        RoutineItemMapper.toDomain(corruptItem)
    }

    @Test(expected = IllegalStateException::class)
    fun `given unknown override_type in database, when mapping override, then throws IllegalStateException`() {
        val corruptOverride = RoutineOverrideEntity("r1", 20000L, "non_existent_type")
        RoutineOverrideMapper.toDomain(corruptOverride)
    }
}
