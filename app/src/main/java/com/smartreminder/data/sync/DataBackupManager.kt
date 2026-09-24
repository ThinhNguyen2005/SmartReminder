package com.smartreminder.data.sync

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.smartreminder.domain.model.preferences.UserPreferences
import com.smartreminder.domain.repository.RoutineRepository
import com.smartreminder.domain.repository.ScheduleGroupRepository
import com.smartreminder.domain.repository.TaskRepository
import com.smartreminder.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: String,
    val wakeUpMinute: Int,
    val sleepMinute: Int,
    val routineRemindersEnabled: Boolean,
    val taskRemindersEnabled: Boolean,
    val morningBriefingEnabled: Boolean,
    val quietHoursEnabled: Boolean,
    val routineCount: Int,
    val taskCount: Int
)

/**
 * Utility responsible for generating and sharing backup data in JSON format.
 */
object DataBackupManager {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun createBackupPayload(
        userPreferencesRepository: UserPreferencesRepository,
        routineRepository: RoutineRepository,
        taskRepository: TaskRepository
    ): BackupPayload = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.preferences.first()
        val routines = routineRepository.observeRoutines().first()
        val tasks = taskRepository.observeAllTasks().first()

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        BackupPayload(
            version = 1,
            exportedAt = sdf.format(Date()),
            wakeUpMinute = prefs.wakeUpTime.hour * 60 + prefs.wakeUpTime.minute,
            sleepMinute = prefs.sleepTime.hour * 60 + prefs.sleepTime.minute,
            routineRemindersEnabled = prefs.routineRemindersEnabled,
            taskRemindersEnabled = prefs.taskRemindersEnabled,
            morningBriefingEnabled = prefs.morningBriefingEnabled,
            quietHoursEnabled = prefs.quietHoursEnabled,
            routineCount = routines.size,
            taskCount = tasks.size
        )
    }

    suspend fun exportAndShareBackup(
        context: Context,
        payload: BackupPayload
    ): File = withContext(Dispatchers.IO) {
        val jsonString = json.encodeToString(payload)
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val file = File(backupDir, "cue_backup_${System.currentTimeMillis()}.json")
        file.writeText(jsonString)
        file
    }
}
