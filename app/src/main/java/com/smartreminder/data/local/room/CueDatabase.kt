package com.smartreminder.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartreminder.data.local.room.dao.RoutineDao
import com.smartreminder.data.local.room.dao.ScheduleGroupDao
import com.smartreminder.data.local.room.entity.RoutineEntity
import com.smartreminder.data.local.room.entity.RoutineItemEntity
import com.smartreminder.data.local.room.entity.RoutineOverrideEntity
import com.smartreminder.data.local.room.entity.RoutineWeeklyDayEntity
import com.smartreminder.data.local.room.entity.ScheduleGroupEntity

@Database(
    entities = [
        ScheduleGroupEntity::class,
        RoutineEntity::class,
        RoutineWeeklyDayEntity::class,
        RoutineItemEntity::class,
        RoutineOverrideEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class CueDatabase : RoomDatabase() {

    abstract fun scheduleGroupDao(): ScheduleGroupDao
    abstract fun routineDao(): RoutineDao

    companion object {
        private const val DATABASE_NAME = "cue_database.db"

        fun buildDatabase(context: Context): CueDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CueDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}
