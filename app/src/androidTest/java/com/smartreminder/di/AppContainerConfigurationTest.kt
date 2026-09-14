package com.smartreminder.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppContainerConfigurationTest {

    @Test
    fun containerConstructionDoesNotResolveMissingCollaborationSupabaseConfig() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)

        assertNotNull(container.userPreferencesCloudRepository)
        assertNotNull(container.userPreferencesSyncCoordinator)
        assertNotNull(container.groupTasksViewModelFactory)
    }
}
