package com.smartreminder

import com.smartreminder.di.AppContainer
import org.junit.Assert.assertFalse
import org.junit.Test

class MainActivityBoundaryTest {

    @Test
    fun smartReminderAppDoesNotAcceptAppContainer() {
        val smartReminderApp = Class.forName("com.smartreminder.MainActivityKt")
            .declaredMethods
            .first { it.name == "SmartReminderApp" }

        assertFalse(
            "AppContainer must stay at the composition root",
            smartReminderApp.parameterTypes.any { it == AppContainer::class.java }
        )
    }
}
