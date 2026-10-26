package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McqTopAppBar(
    appState: AppState,
    profileState: ProfileState,
    scrollBehavior: TopAppBarScrollBehavior,
    onSearchQueryChanged: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    onViewModeToggle: () -> Unit,
    onProfileClick: () -> Unit,
    onThemeToggle: () -> Unit = {},
    titleOverride: String? = null,
    currentRoute: String? = null,
    customModuleStepTitle: String? = null
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            scrollBehavior = scrollBehavior,
            title = {
                TitleContent(
                    appState = appState,
                    titleOverride = titleOverride,
                    searchQuery = appState.searchQuery,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onSearchClose = { onSearchToggle(false) },
                    currentRoute = currentRoute,
                    customModuleStepTitle = customModuleStepTitle
                )
            },
            navigationIcon = {
                NavigationIcon(
                    appState = appState, 
                    onBack = {
                        if (appState.isSearchVisible) onSearchToggle(false)
                        else onBack()
                    }, 
                    hasOverride = titleOverride != null,
                    currentRoute = currentRoute
                )
            },
            actions = {
                ActionsContent(
                    appState = appState,
                    profileState = profileState,
                    viewMode = appState.viewMode,
                    uiState = McqUiState.Success(emptyList()),
                    onViewModeToggle = onViewModeToggle,
                    onProfileClick = onProfileClick,
                    onSearchToggle = { onSearchToggle(true) },
                    onThemeToggle = onThemeToggle,
                    currentRoute = currentRoute
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Embedded, collapsing linear progress bar for the MCQ test session
        if (currentRoute == "Session" && titleOverride == null && appState.viewMode != "list") {
            val questions = (appState.uiState as? McqUiState.Success)?.questions ?: emptyList()
            if (questions.isNotEmpty()) {
                val index = appState.quizIndex.coerceIn(0, questions.size)
                val total = questions.size
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (total > 0) ((index + 1).coerceAtMost(total).toFloat() / total) else 0f,
                    label = "TopAppBarProgressAnimation"
                )

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .graphicsLayer {
                            // Translate in perfect lockstep with the app bar's vertical offset collapse
                            translationY = scrollBehavior.state.heightOffset
                        },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun TitleContent(
    appState: AppState,
    titleOverride: String?,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearchClose: () -> Unit,
    currentRoute: String?,
    customModuleStepTitle: String?
) {
    if (appState.isSearchVisible && titleOverride == null && currentRoute?.startsWith("Home") == true) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            placeholder = { Text("Search everything...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                } else {
                    IconButton(onClick = onSearchClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close Search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )
        return
    }

    if (titleOverride != null) {
        Text(titleOverride, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        return
    }

    when {
        currentRoute?.contains("CustomList") == true -> Text("Custom Sets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        currentRoute?.contains("CustomModule") == true -> Text(customModuleStepTitle ?: "Custom Module", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        currentRoute?.contains("Bookmarks") == true -> Text("Bookmarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        currentRoute?.contains("Profile") == true -> Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        currentRoute?.contains("AiMilestones") == true -> Text("Milestones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        currentRoute?.contains("Topic") == true -> Text("Topics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        currentRoute?.contains("Subject") == true -> Text("Subjects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        currentRoute?.contains("Source") == true -> Text("Source Documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        currentRoute?.contains("Session") == true -> {
            val config = appState.activeSessionConfig
            if (config != null) {
                if (config.topic != null) {
                    Column {
                        Text(config.topic, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(config.subject ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (config.customModules.isNotEmpty()) {
                    Column {
                        Text("Custom Session", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${config.customModules.values.sumOf { it.size }} Topics Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else if (config.source != null) {
                    Text(config.source, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (appState.filterMode == "review") {
                    Column {
                        Text("Review Mistakes", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Reattempt Session", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text("Active Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("Active Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        else -> {
            Text(
                text = "MEDIVA",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NavigationIcon(appState: AppState, onBack: () -> Unit, hasOverride: Boolean, currentRoute: String?) {
    val isHome = currentRoute?.startsWith("Home") == true || currentRoute == null
    val shouldShowBack = !isHome || hasOverride || appState.searchQuery.isNotBlank()

    if (shouldShowBack) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
    }
}

@Composable
private fun ActionsContent(
    appState: AppState,
    profileState: ProfileState,
    viewMode: String,
    uiState: McqUiState,
    onViewModeToggle: () -> Unit,
    onProfileClick: () -> Unit,
    onSearchToggle: () -> Unit,
    onThemeToggle: () -> Unit = {},
    currentRoute: String?
) {
    val isHome = currentRoute?.startsWith("Home") == true || currentRoute == null
    
    if (currentRoute == "Bookmarks") {
        IconButton(onClick = {
            // viewMode toggle handled via onViewModeToggle callback
            // We can reuse onViewModeToggle here, which just toggles global view mode, 
            // but Bookmarks toggle is more complex. We'll add a simple mode switch here.
            onViewModeToggle()
        }) {
            if (viewMode == "list") {
                Icon(Icons.Default.ViewCarousel, contentDescription = "Switch to Card Mode")
            } else {
                Icon(Icons.Default.List, contentDescription = "Switch to List Mode")
            }
        }
        return
    }
    
    if (currentRoute == "Profile") {
        IconButton(onClick = onThemeToggle) {
            Icon(
                imageVector = when(profileState.customTheme) {
                    AppThemeMode.LIGHT -> Icons.Default.LightMode
                    AppThemeMode.DARK -> Icons.Default.DarkMode
                    AppThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                    AppThemeMode.NORD -> Icons.Default.AcUnit
                    AppThemeMode.OCEAN -> Icons.Default.Waves
                    AppThemeMode.SEPIA -> Icons.Default.MenuBook
                    AppThemeMode.AMOLED -> Icons.Default.Nightlight
                    AppThemeMode.CLINICAL -> Icons.Default.WbSunny
                    AppThemeMode.SLATE -> Icons.Default.NightsStay
                },
                contentDescription = "Toggle Theme"
            )
        }
        return 
    }
    
    if (currentRoute == "CustomModule" || currentRoute == "AiMilestones") {
        return
    }

    val isMainDashboard = !appState.isSearchVisible && currentRoute == "Home"
    val isDeepNavigation = currentRoute == "Session"
    
    val streak = profileState.streakCount
    if (streak > 0 && isMainDashboard) {
        var isSpaceAllocated by remember { mutableStateOf(true) }
        val scale = remember { androidx.compose.animation.core.Animatable(0f) }
        val offsetX = remember { androidx.compose.animation.core.Animatable(50f) }
        
        LaunchedEffect(Unit) {
            // 1. Enter: Simple tween
            launch {
                scale.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(150))
            }
            offsetX.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(150))
            
            // 2. Wait ~1.8s
            delay(1800)
            
            // 3. Bulge to get attention (Duolingo style)
            scale.animateTo(1.4f, animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            
            // 4. Fly towards profile and shrink down to 0
            launch {
                scale.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutLinearInEasing))
            }
            // Move 150 pixels right (into the profile icon area)
            offsetX.animateTo(150f, animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutLinearInEasing))
            
            // 5. Shrink layout space gracefully after visual disappear
            isSpaceAllocated = false
        }
        
        AnimatedVisibility(
            visible = isSpaceAllocated,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = shrinkHorizontally(animationSpec = androidx.compose.animation.core.tween(300), shrinkTowards = Alignment.End)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationX = offsetX.value
                    }
            ) {
                Icon(
                    Icons.Default.LocalFireDepartment, 
                    contentDescription = "Streak",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "$streak",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFF9800)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }

    if (isMainDashboard) {
        IconButton(onClick = onSearchToggle) {
            Icon(
                Icons.Default.Search, 
                contentDescription = "Search",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    if (uiState is McqUiState.Success && isDeepNavigation) {
        IconButton(onClick = onViewModeToggle) {
            Icon(
                if (viewMode == "quiz") Icons.Default.Style else Icons.AutoMirrored.Filled.Article,
                contentDescription = if (viewMode == "quiz") "Switch to Card Mode" else "Switch to Classic Mode"
            )
        }
    }
    IconButton(onClick = onProfileClick) {
        Icon(
            Icons.Default.AccountCircle, 
            contentDescription = "Profile",
            modifier = Modifier.size(28.dp)
        )
    }
}
