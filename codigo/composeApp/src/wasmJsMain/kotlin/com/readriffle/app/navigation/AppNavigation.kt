package com.readriffle.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.savedstate.read
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.readriffle.app.AppContainer
import com.readriffle.app.ui.library.BookDetailScreen
import com.readriffle.app.ui.library.LibraryScreen
import com.readriffle.app.ui.notes.NotesScreen
import com.readriffle.app.ui.reading.ReadingScreen
import com.readriffle.app.ui.settings.SettingsScreen
import com.readriffle.app.ui.splash.SplashScreen
import com.readriffle.app.ui.store.WebStoreScreen
import com.readriffle.app.ui.common.DockDestination
import com.readriffle.app.ui.common.MainDock
import com.readriffle.app.viewmodel.BookDetailViewModel
import com.readriffle.app.viewmodel.LibraryViewModel
import com.readriffle.app.viewmodel.NotesViewModel
import com.readriffle.app.viewmodel.ReadingViewModel
import com.readriffle.app.viewmodel.SettingsViewModel
import com.readriffle.app.viewmodel.SyncViewModel

/**
 * Biblioteca, Notas, Tienda y Ajustes son las cuatro secciones "hermanas" que se navegan desde el
 * dock flotante inferior (ver [com.readriffle.app.ui.common.MainDock]): siempre se vuelve a
 * apilar sobre Biblioteca para que ir y venir entre ellas no acumule pantallas en el historial.
 */
private fun navigateTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(Screen.Library.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// Orden fijo de las tres secciones "hermanas" en el dock -- se usa solo para decidir la
// DIRECCIÓN del deslizamiento al cambiar de pestaña (ver más abajo), no la navegación en sí.
private val topLevelTabOrder = listOf(Screen.Library.route, Screen.Notes.route, Screen.Store.route, Screen.Settings.route)

private fun NavBackStackEntry.tabIndex(): Int = topLevelTabOrder.indexOf(destination.route)

/**
 * Transición para cambiar entre pestañas "hermanas" (Biblioteca / Notas / Tienda / Ajustes):
 * desliza + funde en la dirección del cambio (izquierda↔derecha según el orden fijo del dock),
 * el mismo lenguaje visual que ya usa el paso de página en el modo de lectura normal -- antes no
 * había ninguna transición (un corte seco entre pantallas, "queda un poco cutre"), y esto le da
 * continuidad y sensación más cuidada al cambiar de sección.
 */
private val topLevelEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    val fromIdx = initialState.tabIndex()
    val toIdx = targetState.tabIndex()
    if (fromIdx == -1 || toIdx == -1 || toIdx == fromIdx) {
        fadeIn(tween(220))
    } else if (toIdx > fromIdx) {
        slideInHorizontally(tween(220)) { width -> width } + fadeIn(tween(220))
    } else {
        slideInHorizontally(tween(220)) { width -> -width } + fadeIn(tween(220))
    }
}

private val topLevelExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    val fromIdx = initialState.tabIndex()
    val toIdx = targetState.tabIndex()
    if (fromIdx == -1 || toIdx == -1 || toIdx == fromIdx) {
        fadeOut(tween(220))
    } else if (toIdx > fromIdx) {
        slideOutHorizontally(tween(220)) { width -> -width } + fadeOut(tween(220))
    } else {
        slideOutHorizontally(tween(220)) { width -> width } + fadeOut(tween(220))
    }
}

@Composable
fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()

    // Instancia única para toda la vida de la Activity (igual que `container`), compartida entre
    // Biblioteca y Ajustes -- una sincronización disparada desde cualquiera de las dos pantallas
    // deja su resultado visible en la otra sin tener que volver a leerlo de disco.
    val syncViewModel: SyncViewModel = viewModel(factory = SyncViewModel.factory(container))

    // Atrás del navegador / gesto de volver del iPhone: vuelve a la pantalla anterior de la app.
    val currentEntry by navController.currentBackStackEntryAsState()
    BackHandler(enabled = currentEntry != null && navController.previousBackStackEntry != null) {
        navController.popBackStack()
    }

    // Ordenador: barra lateral fija con las secciones (en vez del dock del móvil). Se esconde al leer.
    val wide = com.readriffle.app.ui.common.LocalWide.current
    val route = currentEntry?.destination?.route
    val showSidebar = wide && route != null && route != Screen.Splash.route && !route.startsWith("reading")
    val syncState by syncViewModel.uiState.collectAsState()
    androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showSidebar,
            enter = androidx.compose.animation.slideInHorizontally { -it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { -it } + androidx.compose.animation.fadeOut()
        ) {
            com.readriffle.app.ui.common.DesktopSidebar(
                current = when (route) {
                    Screen.Library.route -> DockDestination.LIBRARY
                    Screen.Notes.route -> DockDestination.NOTES
                    Screen.Store.route -> DockDestination.STORE
                    Screen.Settings.route -> DockDestination.SETTINGS
                    else -> null
                },
                onSelect = { dest ->
                    navigateTopLevel(
                        navController, when (dest) {
                            DockDestination.LIBRARY -> Screen.Library.route
                            DockDestination.NOTES -> Screen.Notes.route
                            DockDestination.STORE -> Screen.Store.route
                            DockDestination.SETTINGS -> Screen.Settings.route
                        }
                    )
                },
                syncEmail = syncState.accountEmail,
                isSyncing = syncState.isSyncing,
                onSync = {
                    if (syncState.accountEmail != null) syncViewModel.syncNow()
                    else navigateTopLevel(navController, Screen.Settings.route)
                }
            )
        }
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxHeight()) {
    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        composable(Screen.Splash.route) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Library.route,
            enterTransition = topLevelEnterTransition,
            exitTransition = topLevelExitTransition
        ) {
            val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
            LibraryScreen(
                viewModel = viewModel,
                syncViewModel = syncViewModel,
                onOpenBook = { bookId -> navController.navigate(Screen.BookDetail.createRoute(bookId)) },
                onContinue = { bookId -> navController.navigate(Screen.Reading.createRoute(bookId)) },
                onOpenStats = { navController.navigate(Screen.Stats.route) },
                onLockCover = { book -> container.coverLock.lock(book) },
                onOpenRead = { key -> navController.navigate(Screen.ReadBook.createRoute(key)) },
                onOpenNotes = { navigateTopLevel(navController, Screen.Notes.route) },
                onOpenStore = { navigateTopLevel(navController, Screen.Store.route) },
                onOpenSettings = { navigateTopLevel(navController, Screen.Settings.route) }
            )
        }

        composable(route = Screen.Stats.route) {
            com.readriffle.app.ui.stats.StatsScreen(
                repository = container.statsRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Notes.route,
            enterTransition = topLevelEnterTransition,
            exitTransition = topLevelExitTransition
        ) {
            val viewModel: NotesViewModel = viewModel(factory = NotesViewModel.factory(container))
            NotesScreen(
                viewModel = viewModel,
                onOpenLibrary = { navigateTopLevel(navController, Screen.Library.route) },
                onOpenStore = { navigateTopLevel(navController, Screen.Store.route) },
                onOpenSettings = { navigateTopLevel(navController, Screen.Settings.route) },
                onOpenAt = { bookId, chapterIndex, wordIndex ->
                    navController.navigate(Screen.Reading.createRouteAt(bookId, chapterIndex, wordIndex))
                }
            )
        }

        composable(
            route = Screen.BookDetail.route,
            arguments = listOf(navArgument(Screen.BookDetail.ARG_BOOK_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val bookId = backStackEntry.argString(Screen.BookDetail.ARG_BOOK_ID)
            if (bookId != null) {
                val viewModel: BookDetailViewModel = viewModel(factory = BookDetailViewModel.factory(bookId, container))
                BookDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onRead = { id -> navController.navigate(Screen.Reading.createRoute(id)) },
                    onOpenRead = { key -> navController.navigate(Screen.ReadBook.createRoute(key)) }
                )
            }
        }

        composable(
            route = Screen.Reading.route,
            arguments = listOf(
                navArgument(Screen.Reading.ARG_BOOK_ID) { type = NavType.StringType },
                navArgument(Screen.Reading.ARG_CHAPTER_INDEX) { type = NavType.IntType; defaultValue = -1 },
                navArgument(Screen.Reading.ARG_WORD_INDEX) { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.argString(Screen.Reading.ARG_BOOK_ID)
            val initialChapterIndex = backStackEntry.argInt(Screen.Reading.ARG_CHAPTER_INDEX) ?: -1
            val initialWordIndex = backStackEntry.argInt(Screen.Reading.ARG_WORD_INDEX) ?: -1
            if (bookId != null) {
                val viewModel: ReadingViewModel = viewModel(
                    factory = ReadingViewModel.factory(bookId, container, initialChapterIndex, initialWordIndex)
                )
                // Ordenador: el texto va en una columna centrada de ancho cómodo de leer.
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.TopCenter
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxHeight().then(
                            if (com.readriffle.app.ui.common.LocalWide.current) Modifier.widthIn(max = 820.dp) else Modifier.fillMaxSize()
                        )
                    ) {
                        ReadingScreen(
                            viewModel = viewModel,
                            onExit = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        composable(
            route = Screen.ReadBook.route,
            arguments = listOf(navArgument(Screen.ReadBook.ARG_KEY) { type = NavType.StringType })
        ) { backStackEntry ->
            val key = backStackEntry.argString(Screen.ReadBook.ARG_KEY)
            if (key != null) {
                val viewModel: com.readriffle.app.viewmodel.ReadBookViewModel =
                    viewModel(factory = com.readriffle.app.viewmodel.ReadBookViewModel.factory(key, container))
                com.readriffle.app.ui.read.ReadBookScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onReread = { id -> navController.navigate(Screen.Reading.createRoute(id)) }
                )
            }
        }

        composable(
            route = Screen.Settings.route,
            enterTransition = topLevelEnterTransition,
            exitTransition = topLevelExitTransition
        ) {
            val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
            SettingsScreen(
                viewModel = viewModel,
                syncViewModel = syncViewModel,
                onOpenLibrary = { navigateTopLevel(navController, Screen.Library.route) },
                onOpenNotes = { navigateTopLevel(navController, Screen.Notes.route) },
                onOpenStore = { navigateTopLevel(navController, Screen.Store.route) }
            )
        }

        composable(
            route = Screen.Store.route,
            enterTransition = topLevelEnterTransition,
            exitTransition = topLevelExitTransition
        ) {
            WebStoreScreen(
                links = container.storeLinksRepository,
                onImportEpub = { bytes -> container.libraryRepository.importBookFromBytes(bytes) },
                dock = {
                    MainDock(
                        current = DockDestination.STORE,
                        onLibrary = { navigateTopLevel(navController, Screen.Library.route) },
                        onNotes = { navigateTopLevel(navController, Screen.Notes.route) },
                        onStore = {},
                        onSettings = { navigateTopLevel(navController, Screen.Settings.route) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            )
        }
    }
        }
    }
}

private fun NavBackStackEntry.argString(key: String): String? =
    arguments?.let { a -> runCatching { a.read { if (contains(key)) getString(key) else null } }.getOrNull() }

private fun NavBackStackEntry.argInt(key: String): Int? =
    arguments?.let { a -> runCatching { a.read { if (contains(key)) getInt(key) else null } }.getOrNull() }
