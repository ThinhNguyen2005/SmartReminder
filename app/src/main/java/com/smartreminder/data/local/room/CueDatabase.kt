package com.smartreminder.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartreminder.data.local.room.dao.CollaborationCacheDao
import com.smartreminder.data.local.room.dao.PendingGroupCommandDao
import com.smartreminder.data.local.room.dao.RoutineDao
import com.smartreminder.data.local.room.dao.ScheduleGroupDao
import com.smartreminder.data.local.room.entity.RoutineEntity
import com.smartreminder.data.local.room.entity.RoutineItemEntity
import com.smartreminder.data.local.room.entity.RoutineOverrideEntity
import com.smartreminder.data.local.room.entity.RoutineWeeklyDayEntity
import com.smartreminder.data.local.room.entity.ScheduleGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupReminderEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
import com.smartreminder.data.local.room.entity.collaboration.PendingGroupCommandEntity
import com.smartreminder.data.local.room.migration.MIGRATION_1_2

@Database(
    entities = [
        ScheduleGroupEntity::class,
        RoutineEntity::class,
        RoutineWeeklyDayEntity::class,
        RoutineItemEntity::class,
        RoutineOverrideEntity::class,
        CachedCollaborationGroupEntity::class,
        CachedGroupMemberEntity::class,
        CachedGroupInviteEntity::class,
        CachedGroupTaskEntity::class,
        CachedGroupTaskReminderEntity::class,
        CachedGroupReminderEntity::class,
        PendingGroupCommandEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class CueDatabase : RoomDatabase() {

    abstract fun scheduleGroupDao(): ScheduleGroupDao
    abstract fun routineDao(): RoutineDao
    abstract fun collaborationCacheDao(): CollaborationCacheDao
    abstract fun pendingGroupCommandDao(): PendingGroupCommandDao

    companion object {
        private const val DATABASE_NAME = "cue_database.db"

        fun buildDatabase(context: Context): CueDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CueDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
