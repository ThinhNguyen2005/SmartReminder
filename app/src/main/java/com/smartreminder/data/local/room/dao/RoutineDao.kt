package com.smartreminder.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.smartreminder.data.local.room.entity.RoutineEntity
import com.smartreminder.data.local.room.entity.RoutineItemEntity
import com.smartreminder.data.local.room.entity.RoutineOverrideEntity
import com.smartreminder.data.local.room.entity.RoutineWeeklyDayEntity
import com.smartreminder.data.local.room.relation.RoutineWithDetailsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Transaction
    @Query("SELECT * FROM routines ORDER BY sort_order ASC, created_at ASC, id ASC")
    fun observeRoutines(): Flow<List<RoutineWithDetailsEntity>>

    @Transaction
    @Query("SELECT * FROM routines WHERE group_id = :groupId ORDER BY sort_order ASC, created_at ASC, id ASC")
    fun observeRoutinesByGroup(groupId: String): Flow<List<RoutineWithDetailsEntity>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutineWithDetails(id: String): RoutineWithDetailsEntity?

    @Upsert
    suspend fun upsertRoutineEntity(routine: RoutineEntity)

    @Query("DELETE FROM routine_weekly_days WHERE routine_id = :routineId")
    suspend fun deleteWeeklyDaysForRoutine(routineId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyDays(days: List<RoutineWeeklyDayEntity>)

    @Query("DELETE FROM routine_items WHERE routine_id = :routineId")
    suspend fun deleteItemsForRoutine(routineId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RoutineItemEntity>)

    /**
     * Atomic snapshot transaction:
     * Upserts routine, replaces all weekly days, and replaces all items.
     * Guarantees persisted children strictly match the domain snapshot without orphaned rows.
     */
    @Transaction
    suspend fun upsertRoutineWithDetails(
        routine: RoutineEntity,
        weeklyDays: List<RoutineWeeklyDayEntity>,
        items: List<RoutineItemEntity>
    ) {
        upsertRoutineEntity(routine)
        deleteWeeklyDaysForRoutine(routine.id)
        if (weeklyDays.isNotEmpty()) {
            insertWeeklyDays(weeklyDays)
        }
        deleteItemsForRoutine(routine.id)
        if (items.isNotEmpty()) {
            insertItems(items)
        }
    }

    @Query("SELECT * FROM routine_overrides WHERE routine_id = :routineId AND override_date_epoch_day = :epochDay")
    suspend fun getOverride(routineId: String, epochDay: Long): RoutineOverrideEntity?

    @Upsert
    suspend fun upsertOverride(override: RoutineOverrideEntity)

    @Query("DELETE FROM routine_overrides WHERE routine_id = :routineId AND override_date_epoch_day = :epochDay")
    suspend fun deleteOverride(routineId: String, epochDay: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)
}
