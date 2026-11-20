package com.readriffle.app

import com.readriffle.app.data.ai.GeminiImageClient
import com.readriffle.app.data.lock.CoverLockController
import com.readriffle.app.data.preferences.UserPreferencesRepository
import com.readriffle.app.data.repository.LibraryRepository
import com.readriffle.app.data.stats.StatsRepository
import com.readriffle.app.data.store.StoreLinksRepository
import com.readriffle.app.data.sync.DriveAuthManager
import com.readriffle.app.data.sync.DriveSyncManager

/**
 * Contenedor manual de dependencias (sin framework de DI): una única instancia de cada
 * repositorio para toda la app, creada al arrancar (ver Main.kt).
 */
class AppContainer {
    val libraryRepository: LibraryRepository = LibraryRepository()
    val userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepository()
    val geminiImageClient: GeminiImageClient = GeminiImageClient()
    val driveAuthManager: DriveAuthManager = DriveAuthManager()
    val driveSyncManager: DriveSyncManager = DriveSyncManager(libraryRepository) { driveAuthManager.getAccessToken() }
    val storeLinksRepository: StoreLinksRepository = StoreLinksRepository()
    val coverLock: CoverLockController = CoverLockController()
    val statsRepository: StatsRepository = StatsRepository()
}
