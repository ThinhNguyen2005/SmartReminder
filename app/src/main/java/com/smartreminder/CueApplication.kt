package com.smartreminder

import android.app.Application
import com.smartreminder.di.AppContainer

/**
 * Application entry point providing [AppContainer] for dependency resolution.
 */
class CueApplication : Application() {

    val appContainer: AppContainer by lazy { AppContainer(this) }
}
