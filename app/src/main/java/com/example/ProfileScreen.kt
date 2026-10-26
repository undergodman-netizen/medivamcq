package com.example

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: McqViewModel,
    profileViewModel: ProfileViewModel,
    appState: AppState,
    profileState: ProfileState,
    initialSheet: String? = null,
    onBack: () -> Unit,
    onNavigateToBookmarks: () -> Unit = {},
    onLogout: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNotificationAction: (AppNotification) -> Unit = {},
    modifier: Modifier = Modifier,
    statsViewModel: StatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val ttsManager = LocalTtsManager.current
    var isEditingProfile by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(profileState.displayName) }
    var tempEmail by remember { mutableStateOf(profileState.email) }
    var showStatisticsSheet by remember { mutableStateOf(false) }
    var showMedivaSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showGeminiSheet by remember { mutableStateOf(false) }
    var showTtsSheet by remember { mutableStateOf(false) }
    var showVoicePickerSheet by remember { mutableStateOf(false) }

    LaunchedEffect(showTtsSheet) {
        if (showTtsSheet && ttsManager != null) {
            profileViewModel.refreshAvailableVoices(ttsManager)
        }
    }

    LaunchedEffect(initialSheet) {
        when (initialSheet) {
            "statistics" -> showStatisticsSheet = true
            "mediva" -> showMedivaSheet = true
            "settings" -> showSettingsSheet = true
            "gemini" -> showGeminiSheet = true
            "tts" -> showTtsSheet = true
            "voice" -> showTtsSheet = true
        }
    }

    var apiKeyInput by remember { mutableStateOf(profileState.geminiApiKey) }
    var geminiCoreInput by remember { 
        mutableStateOf(
            if (profileState.geminiCorePrompt.isNotBlank()) profileState.geminiCorePrompt else AiExplanationService.DEFAULT_CORE
        ) 
    }
    var geminiImageInput by remember {
        mutableStateOf(profileState.geminiImagePrompt)
    }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(profileState.geminiModel) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var ttsPitchValue by remember { mutableStateOf(profileState.ttsPitch) }
    var ttsSpeedValue by remember { mutableStateOf(profileState.ttsSpeed) }

    var isGeminiConfigExpanded by remember { mutableStateOf(false) }
    var isAudioSettingsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(profileState.displayName, profileState.email) {
        tempName = profileState.displayName
        tempEmail = profileState.email
    }

    LaunchedEffect(
        profileState.geminiApiKey,  
        profileState.geminiCorePrompt, 
        profileState.geminiImagePrompt,
        profileState.geminiModel, 
        profileState.ttsPitch, 
        profileState.ttsSpeed
    ) {
        apiKeyInput = profileState.geminiApiKey
        geminiCoreInput = if (profileState.geminiCorePrompt.isNotBlank()) profileState.geminiCorePrompt else AiExplanationService.DEFAULT_CORE
        geminiImageInput = profileState.geminiImagePrompt
        selectedModel = profileState.geminiModel
        ttsPitchValue = profileState.ttsPitch
        ttsSpeedValue = profileState.ttsSpeed
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 2. Hero Avatar: Centered, floating profile picture with soft drop-shadow
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(
                            elevation = 16.dp, 
                            shape = CircleShape,
                            spotColor = MaterialTheme.colorScheme.primary,
                            ambientColor = MaterialTheme.colorScheme.primary
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            if (!appState.isLoggedIn) onNavigateToLogin() else isEditingProfile = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val initials = if (appState.isLoggedIn && tempName.isNotBlank()) {
                        tempName.split(" ").filter { it.isNotEmpty() }.joinToString("") { it.take(1) }.uppercase().take(2)
                    } else "G"
                    
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (isEditingProfile) {
                    Column(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = tempEmail,
                            onValueChange = { tempEmail = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TextButton(onClick = { isEditingProfile = false }) { Text("Cancel") }
                            Button(onClick = {
                                if (tempName.isNotBlank() && tempEmail.isNotBlank()) {
                                    profileViewModel.saveProfile(tempName, tempEmail)
                                    isEditingProfile = false
                                }
                            }) { Text("Save") }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (appState.isLoggedIn && profileState.displayName.isNotBlank()) profileState.displayName else "Guest User",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        if (appState.isLoggedIn) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (profileState.isSubscribed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (profileState.isSubscribed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    text = if (profileState.isSubscribed) "Subscribed" else "Not Subscribed",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (appState.isLoggedIn && profileState.email.isNotBlank()) profileState.email else "Tap avatar to sign in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3 & 4. Statistics and Bookmarks in one row (adaptive buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Statistics Button
                WedikaCard(
                    modifier = Modifier.weight(1f).heightIn(min = 80.dp),
                    onClick = { showStatisticsSheet = true }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "Statistics", tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Statistics", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                // Bookmark Button
                WedikaCard(
                    modifier = Modifier.weight(1f).heightIn(min = 80.dp),
                    onClick = onNavigateToBookmarks
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Bookmarks", tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Bookmarks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            // 5. MEDIVA AI & Milestones Button
            WedikaCard(
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                onClick = { showMedivaSheet = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Psychology, contentDescription = "AI Features", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("MEDIVA AI & Milestones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("View achievements & diagnostics", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Gemini API Key & Voice Configuration Card
            WedikaCard(
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                onClick = { showGeminiSheet = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = "API Keys", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemini API Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("AI Key & clinical explanations settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Audio & Reading Settings Card
            WedikaCard(
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                onClick = { showTtsSheet = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Audio and Reading Settings", tint = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Audio & Reading Comfort", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Playback speed, font styles, and study comfort modes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. JSON Upload Button (Settings Content)
            WedikaCard(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                onClick = { showSettingsSheet = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = "App Settings", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("App Settings & Content", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 7. Sign Out Button
            if (appState.isLoggedIn) {
                TextButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }


    if (showStatisticsSheet) {
        AppBottomSheet(
            onDismissRequest = { showStatisticsSheet = false },
            title = "Study Analytics"
        ) {
            UserStatisticsScreen(
                statsViewModel = statsViewModel,
                modifier = Modifier.fillMaxSize(),
                onTopicSelected = { subject, topic ->
                    showStatisticsSheet = false
                    viewModel.startSession(
                        config = SessionConfig(subject = subject, topic = topic),
                        settings = appState.examSettings
                    )
                    viewModel.setViewMode("quiz")
                    onBack()
                }
            )
        }
    }

    if (showMedivaSheet) {
        AppBottomSheet(
            onDismissRequest = { showMedivaSheet = false },
            title = "MEDIVA Diagnostics & Milestones"
        ) {
            AiMilestonesScreen(
                appState = appState,
                profileState = profileState,
                onBack = { showMedivaSheet = false },
                onStartCustomQuiz = { subject, keyword, isBookmarked, status ->
                    viewModel.startCustomQuiz(subject, keyword, isBookmarked, status) {
                        showMedivaSheet = false
                        onBack()
                    }
                },
                onGetFilteredCount = { subject, keyword, isBookmarked, status ->
                    viewModel.getFilteredQuestionsCount(subject, keyword, isBookmarked, status)
                },
                onNotificationAction = onNotificationAction,
                onNotificationDismiss = profileViewModel::dismissNotification,
                onRestoreDismissedAlerts = profileViewModel::clearDismissedNotifications,
                isBottomSheet = true
            )
        }
    }

    if (showGeminiSheet) {
        AppBottomSheet(
            onDismissRequest = { showGeminiSheet = false },
            title = "Gemini API Configuration"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Enter your Google Gemini API Key to enable advanced custom explanations. Tap ⓘ, and create a APIKEY from Google (it's completely free).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            try {
                                uriHandler.openUri("https://www.google.com/search?q=create+Gemini+API+key+Google+AI+Studio")
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Get Gemini API Key",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { 
                        apiKeyInput = it
                        profileViewModel.saveGeminiSettings(it, selectedModel, "", geminiCoreInput, geminiImageInput)
                    },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("Enter API Key") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showApiKey) "Hide API Key" else "Show API Key"
                            )
                        }
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input")
                )

                val isUsingBuildConfigKey = apiKeyInput.isBlank() && 
                        com.example.BuildConfig.GEMINI_API_KEY.isNotBlank() && 
                        com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

                if (isUsingBuildConfigKey) {
                    Text(
                        text = "✓ Using pre-configured app key",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Text(
                    text = "Select Gemini Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )

                val modelsList = listOf(
                    "gemini-3.5-flash-lite" to "3.5 Flash Lite (Recommended) - Super Fast",
                    "gemini-3.6-flash" to "3.6 Flash - Smart Balanced"
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = modelsList.find { it.first == selectedModel }?.second ?: selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Gemini Model") },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModelDropdown = true },
                        enabled = false, // disabled to make the clickable work over it
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    
                    // Invisible box to capture click over disabled text field
                    Box(modifier = Modifier.matchParentSize().clickable { showModelDropdown = true })

                    DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        modelsList.forEach { (modelId, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedModel = modelId
                                    profileViewModel.saveGeminiSettings(apiKeyInput, modelId, "", geminiCoreInput, geminiImageInput)
                                    showModelDropdown = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Custom Behavior & Formatting Section
                Text(
                    text = "AI Core Instructions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "The elite medical PG professor AI Persona is locked internally to maintain clinical rigor. Customize only the core reasoning pathways, HTML rendering rules, or active-recall strategies below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 2. Core Behavior & Formatting Rules
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Core Instructions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customize the step-by-step reasoning logic, distractor analysis layouts, and HTML-compat rendering criteria.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = geminiCoreInput,
                        onValueChange = { 
                            geminiCoreInput = it
                            profileViewModel.saveGeminiSettings(apiKeyInput, selectedModel, "", it, geminiImageInput)
                        },
                        placeholder = { Text("e.g. Output HTML guidelines and structured flow...") },
                        minLines = 4,
                        maxLines = 10,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("gemini_core_prompt_input")
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Image Generation Prompt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = geminiImageInput,
                        onValueChange = { 
                            geminiImageInput = it
                            profileViewModel.saveGeminiSettings(apiKeyInput, selectedModel, "", geminiCoreInput, it)
                        },
                        placeholder = { Text("e.g. A clear, highly detailed 3D anatomical concept photo...") },
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("gemini_image_prompt_input")
                    )
                }
                
                Button(
                    onClick = { 
                        profileViewModel.saveGeminiSettings(apiKeyInput, selectedModel, "", geminiCoreInput, geminiImageInput) 
                        showGeminiSheet = false
                        android.widget.Toast.makeText(context, "Gemini API configurations saved!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Configuration")
                }
            }
        }
    }

    if (showTtsSheet) {
        val scrollState = rememberScrollState()
        AppBottomSheet(
            onDismissRequest = { 
                showTtsSheet = false 
                ttsManager?.stopPreview()
            },
            title = "Audio & Reading Comfort"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // section 1: TTS
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Text-to-Speech Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Adjust playback speed and choose a high-fidelity native voice for questions and explanations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Playback Speed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.75f to "Slow", 1.0f to "Normal", 1.3f to "Fast").forEach { (speedVal, label) ->
                            FilterChip(
                                selected = ttsSpeedValue == speedVal,
                                onClick = { 
                                    ttsSpeedValue = speedVal
                                    profileViewModel.saveTtsSettings(ttsPitchValue, speedVal)
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Voice selection row
                    val currentVoiceDisplayName = profileState.availableTtsVoices
                        .firstOrNull { it.name == profileState.selectedTtsVoiceName }?.displayName
                        ?: if (profileState.selectedTtsVoiceName == null) "Automatic (Best Available)" else (profileState.selectedTtsVoiceName ?: "Default")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (ttsManager != null) {
                                    profileViewModel.refreshAvailableVoices(ttsManager)
                                }
                                showVoicePickerSheet = true
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voice",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currentVoiceDisplayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Select voice",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Preview row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Preview",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Listen to the selected voice reciting clinical content",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                ttsManager?.previewVoice(profileState.selectedTtsVoiceName)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Preview Voice",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Reset to automatic voice button
                    if (profileState.selectedTtsVoiceName != null) {
                        TextButton(
                            onClick = {
                                profileViewModel.setSelectedTtsVoice(null, ttsManager)
                            },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Text(
                                "Reset to automatic (best voice)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // section 2: Clinical Reading Mode
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Clinical Reading Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Anti-eye strain and high-comfort reading layouts designed for long study sessions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Clinical Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Optimizes line-spacing and fonts for study comfort", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = profileState.isClinicalMode,
                            onCheckedChange = { profileViewModel.setClinicalMode(it) }
                        )
                    }

                    if (profileState.isClinicalMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Font Style", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { profileViewModel.setClinicalFontFamily("Sans") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    width = if (profileState.clinicalFontFamily == "Sans") 1.8.dp else 1.dp,
                                    color = if (profileState.clinicalFontFamily == "Sans") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (profileState.clinicalFontFamily == "Sans") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("Sans-Serif", style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, fontWeight = FontWeight.Bold))
                            }

                            OutlinedButton(
                                onClick = { profileViewModel.setClinicalFontFamily("Serif") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    width = if (profileState.clinicalFontFamily == "Serif") 1.8.dp else 1.dp,
                                    color = if (profileState.clinicalFontFamily == "Serif") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (profileState.clinicalFontFamily == "Serif") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("Academic Serif", style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        AppBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            title = "MCQ Source Manager"
        ) {
            JsonUploadScreen(
                viewModel = viewModel,
                appState = appState,
                profileState = profileState,
                onBack = { showSettingsSheet = false },
                isBottomSheet = true
            )
        }
    }

    if (showVoicePickerSheet) {
        AppBottomSheet(
            onDismissRequest = { 
                showVoicePickerSheet = false
                ttsManager?.stopPreview()
            },
            title = "Select English Voice"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                if (profileState.availableTtsVoices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No installed English voices found. Please check system TTS settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(profileState.availableTtsVoices) { voiceOption ->
                            val isSelected = voiceOption.name == profileState.selectedTtsVoiceName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        profileViewModel.setSelectedTtsVoice(voiceOption.name, ttsManager)
                                        showVoicePickerSheet = false
                                        ttsManager?.stopPreview()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = voiceOption.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Network / Offline badge
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (voiceOption.isNetwork) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = if (voiceOption.isNetwork) "Network" else "Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (voiceOption.isNetwork) {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Quality pill
                                        val qualityLabel = when {
                                            voiceOption.quality >= 400 -> "Very High"
                                            voiceOption.quality >= 300 -> "High"
                                            else -> "Normal"
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = qualityLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
