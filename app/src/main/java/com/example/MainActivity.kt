package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.theme.MyApplicationTheme

import com.example.ui.designsystem.WedikaCard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            val viewModel: McqViewModel = viewModel()
            val profileViewModel: ProfileViewModel = viewModel()
            val statsViewModel: StatsViewModel = viewModel()
            val appState by viewModel.state.collectAsStateWithLifecycle()
            val profileState by profileViewModel.state.collectAsStateWithLifecycle()
            val isDark = when (profileState.customTheme) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                else -> true
            }

            MyApplicationTheme(appTheme = profileState.customTheme, darkTheme = isDark) {
                val context = LocalContext.current
                val ttsManager = remember { TtsManager(context) }
                
                LaunchedEffect(
                    profileState.ttsPitch,
                    profileState.ttsSpeed
                ) {
                    ttsManager.ttsPitch = profileState.ttsPitch
                    ttsManager.ttsSpeed = profileState.ttsSpeed
                }

                DisposableEffect(ttsManager) {
                    onDispose { 
                        ttsManager.shutdown()
                    }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.LocalTtsManager provides ttsManager
                ) {
                    McqViewerApp(viewModel, profileViewModel, statsViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McqViewerApp(
    viewModel: McqViewModel,
    profileViewModel: ProfileViewModel,
    statsViewModel: StatsViewModel
) {
    val appState by viewModel.state.collectAsStateWithLifecycle()
    val profileState by profileViewModel.state.collectAsStateWithLifecycle()
    val bookmarkState by viewModel.bookmarkUiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.let { route ->
        route.split("/").firstOrNull()?.split("?")?.firstOrNull()?.split(".")?.lastOrNull() ?: route
    }
    
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val customNestedScrollConnection = remember(scrollBehavior, currentRoute) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var accumulatedScrollUp = 0f
            val threshold = 500f // Solid 500px threshold to prevent accidental reveal on slight scrolls

            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (currentRoute != "Session") {
                    return scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
                }
                val dy = available.y
                if (dy < 0) {
                    accumulatedScrollUp = 0f
                    // Scrolling down: let it collapse freely
                    return scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
                } else if (dy > 0) {
                    accumulatedScrollUp += dy
                    if (accumulatedScrollUp >= threshold) {
                        return scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
                    } else {
                        // Suppress top bar expansion until user pulls past threshold
                        return androidx.compose.ui.geometry.Offset.Zero
                    }
                }
                return scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (currentRoute != "Session") {
                    return scrollBehavior.nestedScrollConnection.onPostScroll(consumed, available, source)
                }
                if (available.y > 0) {
                    // Reached the top of the scroll list, let it uncollapse smoothly
                    scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
                }
                return scrollBehavior.nestedScrollConnection.onPostScroll(consumed, available, source)
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (currentRoute != "Session") {
                    return scrollBehavior.nestedScrollConnection.onPreFling(available)
                }
                // Suppress fling-based expansion when completely collapsed to require deliberate scrolls first
                if (available.y > 0 && scrollBehavior.state.collapsedFraction >= 1f) {
                    return androidx.compose.ui.unit.Velocity.Zero
                }
                return scrollBehavior.nestedScrollConnection.onPreFling(available)
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                if (currentRoute != "Session") {
                    return scrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
                }
                return scrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
            }
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != "Session") {
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val ttsManager = com.example.LocalTtsManager.current
    
    val customModuleStepTitle = remember { mutableStateOf<String?>(null) }
    val customModuleBackHandler = remember { mutableStateOf<(() -> Boolean)?>(null) }
    
    // We no longer automatically pull the latest cloud snapshot on login
    // to prevent unnecessary overrides or sync screens unless manually requested.

    // Force system back and UI back to behave symmetrically
    val performBackNavigation: () -> Unit = {
        val route = currentRoute
        if (route == "CustomModule") {
            val handled = customModuleBackHandler.value?.invoke() ?: false
            if (!handled) {
                navController.popBackStack()
            }
        } else {
            navController.popBackStack()
        }
    }

    // Stop audio when internal category selection states change or navigation occurs
    LaunchedEffect(
        currentRoute,
        appState.viewMode,
        appState.quizIndex
    ) {
        ttsManager?.stop()
    }

    // Stop audio if the user goes to the phone's home screen or backgrounds the app
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, ttsManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                ttsManager?.stop()
                viewModel.commitSession()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(customNestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val hideOuterTopBar = currentRoute == "Intro" || currentRoute == "Auth" || currentRoute?.contains("Bookmarks") == true
            if (!hideOuterTopBar) {
                val questions = (appState.uiState as? McqUiState.Success)?.questions ?: emptyList()
                val isEditingReview = currentRoute == "Session" && questions.isNotEmpty() && appState.quizIndex >= questions.size
                val overrideTitle = when {
                    currentRoute?.contains("Statistics") == true -> "Performance Analytics"
                    currentRoute?.contains("Flashcards") == true -> "Flashcards"
                    isEditingReview -> "Session Summary"
                    else -> null
                }
                McqTopAppBar(
                    appState = appState,
                    profileState = profileState,
                    scrollBehavior = scrollBehavior,
                    onSearchQueryChanged = viewModel::onSearchQueryChanged,
                    onSearchToggle = viewModel::setSearchVisible,
                    onBack = {
                        if (isEditingReview) {
                            viewModel.clearActiveSessionBackup()
                            performBackNavigation()
                        } else {
                            performBackNavigation()
                        }
                    },
                    onViewModeToggle = { viewModel.setViewMode(if (appState.viewMode == "quiz") "card" else "quiz") },
                    onProfileClick = { 
                        navController.navigate(Screen.Profile()) 
                    },
                    onThemeToggle = {
                        val themes = AppThemeMode.entries.toTypedArray()
                        val currentIndex = themes.indexOf(profileState.customTheme)
                        val nextIndex = (currentIndex + 1) % themes.size
                        val nextTheme = themes[nextIndex]
                        profileViewModel.saveCustomTheme(nextTheme)
                        android.widget.Toast.makeText(context, "Theme: ${nextTheme.title}", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    titleOverride = overrideTitle,
                    currentRoute = currentRoute,
                    customModuleStepTitle = customModuleStepTitle.value
                )
            }
        },

    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Intro,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { 
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150))
            },
            exitTransition = { 
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
            },
            popEnterTransition = { 
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150))
            },
            popExitTransition = { 
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
            }
        ) {
            composable<Screen.Intro>(
                exitTransition = {
                    androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                    ) + androidx.compose.animation.scaleOut(
                        targetScale = 1.1f,
                        animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                }
            ) {
                BrainLaunchScreen(
                    onNavigateToHome = {
                        navController.navigate(Screen.Home) {
                            popUpTo(Screen.Intro) { inclusive = true }
                        }
                    }
                )
            }
            composable<Screen.Home>(
                enterTransition = {
                    androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(400, delayMillis = 100, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                    ) + androidx.compose.animation.scaleIn(
                        initialScale = 0.95f,
                        animationSpec = androidx.compose.animation.core.tween(400, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                }
            ) {
                HomeScreen(
                    viewModel = viewModel, 
                    profileViewModel = profileViewModel,
                    appState = appState, 
                    profileState = profileState,
                    bookmarkState = bookmarkState,
                    onImport = { navController.navigate(Screen.Profile(initialSheet = "settings")) },
                    onSearchToggle = { viewModel.setSearchVisible(it) },
                    onNavigateToProfile = { 
                        navController.navigate(Screen.Profile()) 
                    },
                    onNavigateToStats = { navController.navigate(Screen.Profile(initialSheet = "statistics")) },
                    onNavigateToCustomModule = { initialJson ->
                        navController.navigate(Screen.CustomModule(initialJson))
                    },
                    onNavigateToSession = {
                        navController.navigate(Screen.Session())
                    },
                    onNavigateToSubject = { groupName ->
                        navController.navigate(Screen.Subject(groupName))
                    },
                    onNavigateToSource = { source ->
                        navController.navigate(Screen.Source(source))
                    },
                    onNavigateToFlashcards = {
                        navController.navigate(Screen.Flashcards)
                    },
                    onNavigateToCustomList = {
                        navController.navigate(Screen.CustomList)
                    }
                )
            }
            composable<Screen.Subject> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.Subject>()
                SubSubjectsScreen(
                    subjectGroup = route.groupName ?: "General",
                    summary = profileState.categorySummary,
                    onSubjectSelected = { rawSubject -> 
                        navController.navigate(Screen.Topic(rawSubject))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Screen.Topic> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.Topic>()
                val pendingTopic = remember { mutableStateOf<String?>(null) }
                McqTopicList(
                    subject = route.subjectId,
                    topicCounts = profileState.categorySummary[route.subjectId] ?: emptyMap(),
                    onTopicSelected = { pendingTopic.value = it },
                    onOpenCustomModule = { modules ->
                        navController.navigate(Screen.CustomModule(modules.toJsonString()))
                    },
                    onBack = { navController.popBackStack() }
                )
                val currentPendingTopic = pendingTopic.value
                if (currentPendingTopic != null) {
                    val maxQ = profileState.categorySummary[route.subjectId]?.get(currentPendingTopic)?.total ?: 500
                    ModeSelectionDialog(
                        topic = currentPendingTopic,
                        availableQuestions = maxQ,
                        onDismiss = { pendingTopic.value = null },
                        onStart = { isExamMode, count, timeLimit, secondsPerQ, randomize ->
                            viewModel.startSession(
                                config = SessionConfig(subject = route.subjectId, topic = currentPendingTopic),
                                settings = ExamSettings(
                                    isExamMode = isExamMode,
                                    questionCount = count,
                                    timeLimitMinutes = timeLimit,
                                    secondsPerQuestion = secondsPerQ,
                                    randomizeQuestions = randomize
                                )
                            )
                            navController.navigate(Screen.Session())
                            pendingTopic.value = null
                        }
                    )
                }
            }
            composable<Screen.Source> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.Source>()
                LaunchedEffect(route.subjectId) {
                    viewModel.loadSourceTopics(route.subjectId)
                }
                val pendingTopic = remember { mutableStateOf<Pair<String, String>?>(null) }
                McqFileTopicList(
                    sourceName = route.subjectId,
                    appState = appState,
                    onTopicSelected = { subject, topic ->
                        pendingTopic.value = subject to topic
                    },
                    onBack = { navController.popBackStack() }
                )
                val currentPendingTopic = pendingTopic.value
                if (currentPendingTopic != null) {
                    val sub = currentPendingTopic.first
                    val top = currentPendingTopic.second
                    val maxQ = appState.sourceTopics[sub]?.get(top)?.total ?: 500
                    ModeSelectionDialog(
                        topic = top,
                        availableQuestions = maxQ,
                        onDismiss = { pendingTopic.value = null },
                        onStart = { isExamMode, count, timeLimit, secondsPerQ, randomize ->
                            viewModel.startSession(
                                config = SessionConfig(subject = sub, topic = top, source = route.subjectId),
                                settings = ExamSettings(
                                    isExamMode = isExamMode,
                                    questionCount = count,
                                    timeLimitMinutes = timeLimit,
                                    secondsPerQuestion = secondsPerQ,
                                    randomizeQuestions = randomize
                                )
                            )
                            navController.navigate(Screen.Session())
                            pendingTopic.value = null
                        }
                    )
                }
            }
            composable<Screen.Session> {
                StandardMcqSessionScreen(
                    viewModel = viewModel,
                    profileViewModel = profileViewModel,
                    appState = appState,
                    profileState = profileState,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Screen.CustomModule> { backStackEntry ->
                val customModuleRoute = backStackEntry.toRoute<Screen.CustomModule>()
                val initialSelection = remember(customModuleRoute.initialSelectionJson) {
                    customModuleRoute.initialSelectionJson?.toCustomModulesMap()
                }
                CustomModuleScreen(
                    summary = profileState.categorySummary,
                    initialSelection = initialSelection,
                    onTopBarTitleUpdate = { customModuleStepTitle.value = it },
                    onSetBackHandler = { customModuleBackHandler.value = it },
                    onStart = { modules, settings ->
                        navController.popBackStack()
                        viewModel.startSession(SessionConfig(customModules = modules), settings)
                        navController.navigate(Screen.Session())
                    }
                )
            }
            composable<Screen.Flashcards> {
                FlashcardScreen(
                    profileState = profileState,
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Screen.Profile> { backStackEntry ->
                val authVm: AuthViewModel = viewModel()
                val profileRoute = backStackEntry.toRoute<Screen.Profile>()
                val context = LocalContext.current
                ProfileScreen(
                    viewModel = viewModel,
                    profileViewModel = profileViewModel,
                    appState = appState,
                    profileState = profileState,
                    initialSheet = profileRoute.initialSheet,
                    onBack = { navController.popBackStack() },
                    onNavigateToBookmarks = { navController.navigate(Screen.Bookmarks) },
                    onLogout = { 
                        authVm.logout()
                        viewModel.logout()
                        profileViewModel.reloadProfile()
                        android.widget.Toast.makeText(context, "Logged out successfully", android.widget.Toast.LENGTH_SHORT).show()
                        navController.navigate(Screen.Home) { 
                            popUpTo(0) 
                        }
                    },
                    onNavigateToLogin = { navController.navigate(Screen.Auth) },
                    onNotificationAction = { notification ->
                        profileViewModel.handleNotificationAction(
                            notification = notification,
                            onNavigateToProfile = { /* Already on profile screen */ },
                            onNavigateToStats = { /* Will open stats bottom sheet on profile screen */ }
                        )
                    },
                    statsViewModel = statsViewModel
                )
            }
            composable<Screen.Bookmarks> {
                BookmarksScreen(
                    viewModel = viewModel,
                    profileViewModel = profileViewModel,
                    appState = appState,
                    profileState = profileState,
                    onStartReview = { 
                        navController.popBackStack()
                        navController.navigate(Screen.Session())
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Screen.Auth> {
                val authVm: AuthViewModel = viewModel()
                AuthScreen(
                    viewModel = authVm,
                    onLoginSuccess = {
                        viewModel.login()
                        profileViewModel.reloadProfile()
                        navController.navigate(Screen.Home) {
                            popUpTo(0)
                        }
                    }
                )
            }
            composable<Screen.CustomList> {
                val appStateNow by viewModel.state.collectAsStateWithLifecycle()
                CustomSourcesScreen(
                    viewModel           = viewModel,
                    appState            = appStateNow,
                    onBack              = { navController.popBackStack() },
                    onOpenSet           = { src -> navController.navigate(Screen.Source(src)) },
                    onNavigateToSession = { navController.navigate(Screen.Session()) }
                )
            }
        }

        // Global check: Download DB overlay
        if (appState.isDownloadingDb) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                WedikaCard(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(0.9f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "Syncing MCQ Database...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Fetching the most up-to-date questions, topics, and progress files from cloud storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (appState.importProgress != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "${(appState.importProgress!! * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { appState.importProgress ?: 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: McqViewModel, 
    profileViewModel: ProfileViewModel,
    appState: AppState, 
    profileState: ProfileState,
    bookmarkState: BookmarkUiState,
    onImport: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToCustomModule: (String?) -> Unit,
    onNavigateToSession: () -> Unit,
    onNavigateToSubject: (String) -> Unit,
    onNavigateToSource: (String) -> Unit,
    onNavigateToFlashcards: () -> Unit,
    onNavigateToCustomList: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        McqDashboard(
            summary = profileState.categorySummary,
            sourceSummary = profileState.sourceSummary,
            filterMode = appState.filterMode,
            onFilterModeChanged = viewModel::setFilterMode,
            onSourceSelected = { source -> source?.let { onNavigateToSource(it) } },
            onCategorySelected = { groupName, _ -> if (groupName != null) onNavigateToSubject(groupName) },
            onResetProgress = viewModel::clearSelections,
            onSearchClick = { onSearchToggle(true) },
            dashboardSearchQuery = appState.dashboardSearchQuery,
            isDashboardSearchVisible = appState.isDashboardSearchVisible,
            onDashboardSearchQueryChanged = viewModel::onDashboardSearchQueryChanged,
            onToggleDashboardSearch = viewModel::setDashboardSearchVisible,
            onStartCustomSession = { modules, settings ->
                viewModel.startSession(SessionConfig(customModules = modules), settings)
                onNavigateToSession()
            },
            onResumeActiveSession = {
                viewModel.resumeActiveSession()
                onNavigateToSession()
            },
            appState = appState,
            profileState = profileState,
            onNavigateToFlashcards = onNavigateToFlashcards,
            onNavigateToCustomList = onNavigateToCustomList,
            onNotificationAction = { notification ->
                profileViewModel.handleNotificationAction(
                    notification, 
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToStats = onNavigateToStats
                )
            },
            onNotificationDismiss = profileViewModel::dismissNotification,
            onNavigateToCustomModule = onNavigateToCustomModule
        )
    }
}




@Composable
fun ErrorState(message: String) {
    Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
        Text(message)
    }
}
