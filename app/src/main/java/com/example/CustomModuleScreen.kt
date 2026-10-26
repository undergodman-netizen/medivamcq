package com.example

import android.content.Context
import androidx.compose.animation.*
import com.example.utils.fuzzyMatch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.HighlightedText
import com.example.ui.designsystem.WedikaCard
import com.example.utils.formatTopicDisplayName

enum class CustomModuleStep { RAPID, SELECT, CONFIGURE }

data class SavedCustomModuleProfile(val id: Int, val name: String, val modules: Map<String, Set<String>>)

fun serializeProfile(profile: SavedCustomModuleProfile): String {
    val root = org.json.JSONObject()
    root.put("id", profile.id)
    root.put("name", profile.name)
    val map = org.json.JSONObject()
    for ((k, v) in profile.modules) {
        val array = org.json.JSONArray()
        v.forEach { array.put(it) }
        map.put(k, array)
    }
    root.put("modules", map)
    return root.toString()
}

fun parseProfile(json: String): SavedCustomModuleProfile? {
    return try {
        val root = org.json.JSONObject(json)
        val id = root.getInt("id")
        val name = root.getString("name")
        val mapObj = root.getJSONObject("modules")
        val map = mutableMapOf<String, Set<String>>()
        val keys = mapObj.keys()
        while(keys.hasNext()) {
            val key = keys.next()
            val arr = mapObj.getJSONArray(key)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            map[key] = set
        }
        SavedCustomModuleProfile(id, name, map)
    } catch(e: Exception) { null }
}

fun Map<String, Set<String>>.toJsonString(): String {
    val obj = org.json.JSONObject()
    this.forEach { (sub, topics) ->
        val arr = org.json.JSONArray()
        topics.forEach { arr.put(it) }
        obj.put(sub, arr)
    }
    return obj.toString()
}

fun String.toCustomModulesMap(): Map<String, Set<String>> {
    val map = mutableMapOf<String, Set<String>>()
    try {
        val obj = org.json.JSONObject(this)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = obj.getJSONArray(key)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            map[key] = set
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return map
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomModuleScreen(
    summary: Map<String, Map<String, TopicStats>>,
    initialSelection: Map<String, Set<String>>? = null,
    onTopBarTitleUpdate: (String?) -> Unit = {},
    onSetBackHandler: ((() -> Boolean)?) -> Unit = {},
    onStart: (modules: Map<String, Set<String>>, settings: ExamSettings) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("custom_modules", Context.MODE_PRIVATE) }
    
    var currentStep by remember(initialSelection) { mutableStateOf(if (initialSelection != null) CustomModuleStep.CONFIGURE else CustomModuleStep.RAPID) }
    
    DisposableEffect(currentStep) {
        onSetBackHandler {
            if (currentStep == CustomModuleStep.RAPID) {
                false
            } else if (currentStep == CustomModuleStep.SELECT) {
                currentStep = CustomModuleStep.RAPID
                true
            } else {
                currentStep = CustomModuleStep.SELECT
                true
            }
        }
        onDispose { onSetBackHandler(null) }
    }
    
    LaunchedEffect(currentStep) {
        val title = when (currentStep) {
            CustomModuleStep.RAPID -> "Rapid Flow"
            CustomModuleStep.SELECT -> "Select Modules"
            CustomModuleStep.CONFIGURE -> "Configure Session"
        }
        onTopBarTitleUpdate(title)
    }

    var selectedMode by remember { mutableStateOf("Practice") }
    var isAllSelected by remember { mutableStateOf(true) }
    var customCountText by remember { mutableStateOf("25") }
    var randomize by remember { mutableStateOf(true) }
    var showAdvanced by remember { mutableStateOf(false) }
    
    var activeTags by rememberSaveable { mutableStateOf(setOf<String>()) }
    var customTags by remember { 
        mutableStateOf(sharedPrefs.getStringSet("custom_tags", emptySet())?.toList() ?: emptyList()) 
    }
    
    val presetSubjects = remember { listOf(
        "Anatomy", "Physiology", "Biochemistry", "Pathology", "Pharmacology", "Microbiology", "Forensic", "PSM", 
        "ENT", "Ophthalmology", "Medicine", "Surgery", "OBG", "Pediatrics", "Orthopedics", "Psychiatry", "Dermatology", "Anesthesia", "Radiology"
    ) }
    val presetTags = remember { listOf("PYQ", "NEET", "AIIMS") }
    
    val allSelectable = remember(summary) { summary.mapValues { it.value.keys.toSet() } }
    var selectedModules by remember(initialSelection) { mutableStateOf<Map<String, Set<String>>>(initialSelection ?: emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedSubjects by remember { mutableStateOf(setOf<String>()) }
    
    var savedClubs by remember { 
        mutableStateOf(
            (1..3).mapNotNull { id ->
                val json = sharedPrefs.getString("club_$id", null)
                if (json != null) parseProfile(json) else null
            }.sortedBy { it.id }
        )
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var newClubName by remember { mutableStateOf("") }
    
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Current Club") },
            text = {
                OutlinedTextField(
                    value = newClubName,
                    onValueChange = { newClubName = it },
                    label = { Text("Club Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.navigationBarsPadding(),
                    onClick = {
                        if (newClubName.isNotBlank()) {
                            val newId = (savedClubs.maxOfOrNull { it.id } ?: 0) + 1
                            val replaceId = if (savedClubs.size >= 3) savedClubs.minByOrNull { it.id }?.id ?: 1 else newId
                            val newClub = SavedCustomModuleProfile(replaceId, newClubName, selectedModules)
                            val newList = savedClubs.filter { it.id != replaceId } + newClub
                            savedClubs = newList.sortedBy { it.id }
                            sharedPrefs.edit().putString("club_$replaceId", serializeProfile(newClub)).apply()
                            showSaveDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.navigationBarsPadding(),
                    onClick = { showSaveDialog = false }
                ) { Text("Cancel") }
            }
        )
    }

    val filteredSummary = remember(summary, searchQuery, activeTags) {
        var res = summary
        if (activeTags.isNotEmpty()) {
            res = res.mapNotNull { (sub, tops) ->
                val filteredTops = tops.filterKeys { top ->
                    activeTags.all { tag ->
                        sub.contains(tag, ignoreCase = true) || top.contains(tag, ignoreCase = true)
                    }
                }
                if (filteredTops.isNotEmpty()) sub to filteredTops else null
            }.toMap()
        }
        if (searchQuery.isNotBlank()) {
            res = res.mapNotNull { (sub, tops) ->
                if (sub.fuzzyMatch(searchQuery)) {
                    sub to tops
                } else {
                    val filteredTops = tops.filterKeys { it.fuzzyMatch(searchQuery) }
                    if (filteredTops.isNotEmpty()) sub to filteredTops else null
                }
            }.toMap()
        }
        res
    }

    val autoSelectedModules = remember(filteredSummary, activeTags) {
        if (activeTags.isNotEmpty()) filteredSummary.mapValues { it.value.keys.toSet() } else emptyMap()
    }

    val rapidAvailableQuestions = remember(autoSelectedModules, summary) {
        var total = 0
        autoSelectedModules.forEach { (sub, topics) ->
            topics.forEach { top -> total += summary[sub]?.get(top)?.total ?: 0 }
        }
        total
    }

    val RapidPane: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Select tags, subjects or exam formats below to combine them.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val allTags = presetSubjects + presetTags + customTags
                    allTags.forEach { tag ->
                        val isSelected = activeTags.contains(tag)
                        FilterChip(
                            selected = isSelected,
                            onClick = { activeTags = if (isSelected) activeTags - tag else activeTags + tag },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                    
                    var chipInput by remember { mutableStateOf("") }
                    androidx.compose.foundation.text.BasicTextField(
                        value = chipInput,
                        onValueChange = { chipInput = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            val newTag = chipInput.trim()
                            if (newTag.isNotEmpty()) {
                                if (newTag !in presetSubjects && newTag !in presetTags) {
                                    val newCustom = (customTags + newTag).distinct()
                                    customTags = newCustom
                                    sharedPrefs.edit().putStringSet("custom_tags", newCustom.toSet()).apply()
                                }
                                activeTags = activeTags + newTag
                                chipInput = ""
                            }
                        }),
                        textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.height(32.dp).widthIn(min = 60.dp, max = 150.dp),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (chipInput.isEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Add", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    innerTextField()
                                }
                            }
                        }
                    )
                }

                if (activeTags.isNotEmpty()) {
                    WedikaCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${autoSelectedModules.values.sumOf { it.size }} Topics Matched", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                                Text("$rapidAvailableQuestions Questions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { 
                                        selectedModules = autoSelectedModules
                                        currentStep = CustomModuleStep.CONFIGURE 
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Configure", style = MaterialTheme.typography.labelLarge)
                                }
                                Button(
                                    onClick = {
                                        if (autoSelectedModules.isNotEmpty()) {
                                            val count = minOf(rapidAvailableQuestions, 20)
                                            val settings = ExamSettings(isExamMode = false, questionCount = count, randomizeQuestions = true, secondsPerQuestion = 0)
                                            onStart(autoSelectedModules, settings)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    enabled = autoSelectedModules.isNotEmpty() && rapidAvailableQuestions > 0
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Fast Play", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
            
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Button(
                    onClick = { currentStep = CustomModuleStep.SELECT },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Advanced Custom Module", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    val SelectionPane: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search subjects and topics...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                singleLine = true
            )
            
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Info block
                Text("Filter tags currently applied or edit below", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp).align(Alignment.CenterVertically))
                
                val allSelected = activeTags.toList()
                allSelected.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { activeTags = activeTags - tag },
                        label = { Text(tag) },
                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedModules.values.sumOf { it.size }} selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { selectedModules = allSelectable },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Select All", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { selectedModules = emptyMap() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                filteredSummary.forEach { (subject, topics) ->
                    item {
                        val subjectSelectedTopics = selectedModules[subject] ?: emptySet()
                        val isAllSelectedSubject = subjectSelectedTopics.containsAll(topics.keys) && topics.isNotEmpty()
                        val isPartiallySelected = !isAllSelectedSubject && subjectSelectedTopics.any { it in topics.keys }
                        val isExpanded = expandedSubjects.contains(subject)
                        
                        WedikaCard(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            expandedSubjects = if (isExpanded) expandedSubjects - subject else expandedSubjects + subject
                                        }
                                        .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { expandedSubjects = if (isExpanded) expandedSubjects - subject else expandedSubjects + subject },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand/Collapse",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                                        TriStateCheckbox(
                                            state = if (isAllSelectedSubject) androidx.compose.ui.state.ToggleableState.On else if (isPartiallySelected) androidx.compose.ui.state.ToggleableState.Indeterminate else androidx.compose.ui.state.ToggleableState.Off,
                                            onClick = {
                                                val newMap = selectedModules.toMutableMap()
                                                if (isAllSelectedSubject) {
                                                    newMap[subject] = newMap[subject]?.minus(topics.keys) ?: emptySet()
                                                    if (newMap[subject]?.isEmpty() == true) newMap.remove(subject)
                                                } else {
                                                    newMap[subject] = (newMap[subject] ?: emptySet()) + topics.keys
                                                }
                                                selectedModules = newMap
                                            }
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    HighlightedText(
                                        text = subject,
                                        query = searchQuery,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                
                                if (isExpanded) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                        topics.forEach { (topic, stats) ->
                                            val isTopicSelected = subjectSelectedTopics.contains(topic)
                                            val isMatch = searchQuery.isNotBlank() && topic.fuzzyMatch(searchQuery)
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .then(if (isMatch) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else Modifier)
                                                    .clickable {
                                                        val newMap = selectedModules.toMutableMap()
                                                        val curSet = newMap[subject] ?: emptySet()
                                                        val newSet = if (isTopicSelected) curSet - topic else curSet + topic
                                                        if (newSet.isEmpty()) newMap.remove(subject) else newMap[subject] = newSet
                                                        selectedModules = newMap
                                                    }
                                                    .padding(start = 48.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isTopicSelected,
                                                    onCheckedChange = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    HighlightedText(
                                                        text = formatTopicDisplayName(topic),
                                                        query = searchQuery,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(text = "${stats.total} questions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val ConfigPane: @Composable () -> Unit = {
        val availableQuestions = remember(selectedModules, summary) {
            var total = 0
            selectedModules.forEach { (sub, topics) ->
                topics.forEach { top ->
                    total += summary[sub]?.get(top)?.total ?: 0
                }
            }
            total
        }
        val selectedCount = if (isAllSelected) availableQuestions else (customCountText.toIntOrNull() ?: 25).coerceIn(1, availableQuestions)

        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // List of Selected Modules in Small UI
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${selectedModules.values.sumOf { it.size }} Topics Selected ($availableQuestions MCQs)", 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedModules.forEach { (subject, topics) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    subject.ifEmpty { "General" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                topics.take(3).forEach { topic ->
                                    Text("• " + formatTopicDisplayName(topic), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                if (topics.size > 3) {
                                    Text("  + ${topics.size - 3} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // Mode Selection
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Practice", "Timed", "Exam").forEach { mode ->
                        val isSelected = selectedMode == mode
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { selectedMode = mode }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                mode,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Mode rules / description helper content
                val modeDesc = when (selectedMode) {
                    "Practice" -> "Practice"
                    "Timed" -> "Practice with timer (60sec)"
                    "Exam" -> "Only 60sec for MCQ"
                    else -> "Practice"
                }

                Text(
                    text = modeDesc,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
                if (selectedMode == "Exam") {
                    Text(
                        text = "Scoring: +4 for correct, -1 for incorrect",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // Advanced Settings Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced Settings", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Advanced Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (showAdvanced) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Questions Limit inside Advanced Settings
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.2f)).padding(12.dp)
                        ) {
                            Text(
                                text = "Questions Limit", 
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Custom Input button/field
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (!isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(if (!isAllSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { isAllSelected = false }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = customCountText,
                                        onValueChange = { input ->
                                            isAllSelected = false
                                            val clean = input.filter { it.isDigit() }
                                            if (clean.length <= 4) {
                                                customCountText = clean
                                            }
                                        },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (!isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        ),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                        modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 20.dp, max = 50.dp)
                                    )
                                    Text(
                                        text = "/$availableQuestions",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (!isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // All button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { isAllSelected = true }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "All ($availableQuestions)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Shuffling config option
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.2f)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Shuffle Questions", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = randomize,
                                    onCheckedChange = { randomize = it },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Saved Clubs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { newClubName = ""; showSaveDialog = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Current")
                    }
                }
                
                if (savedClubs.isEmpty()) {
                    Text(
                        "No saved clubs yet. Select modules and save them for quick access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    savedClubs.forEach { club ->
                        WedikaCard(
                            onClick = { selectedModules = club.modules },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(club.name, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = {
                                    val newList = savedClubs.filter { it.id != club.id }
                                    savedClubs = newList
                                    sharedPrefs.edit().remove("club_${club.id}").apply()
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false)) 
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val progress = when(currentStep) {
            CustomModuleStep.RAPID -> 0.33f
            CustomModuleStep.SELECT -> 0.66f
            CustomModuleStep.CONFIGURE -> 1.0f
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )

        Box(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (currentStep) {
                CustomModuleStep.RAPID -> RapidPane()
                CustomModuleStep.SELECT -> SelectionPane()
                CustomModuleStep.CONFIGURE -> ConfigPane()
            }
        }
        
        if (currentStep != CustomModuleStep.RAPID) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    val isSelectStep = currentStep == CustomModuleStep.SELECT
                    
                    if (isSelectStep) {
                        Button(
                            onClick = { currentStep = CustomModuleStep.RAPID },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.weight(1f).height(48.dp).widthIn(max = 240.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
                        }
                        
                        Button(
                            onClick = { currentStep = CustomModuleStep.CONFIGURE },
                            enabled = selectedModules.values.any { it.isNotEmpty() },
                            modifier = Modifier.weight(1f).height(48.dp).widthIn(max = 240.dp)
                        ) {
                            Text("Next Step", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        val availableQuestions = remember(selectedModules, summary) {
                            var total = 0
                            selectedModules.forEach { (sub, topics) ->
                                topics.forEach { top ->
                                    total += summary[sub]?.get(top)?.total ?: 0
                                }
                            }
                            total
                        }
                        val selectedCount = if (isAllSelected) availableQuestions else (customCountText.toIntOrNull() ?: 25).coerceIn(1, availableQuestions)
                        
                        Button(
                            onClick = { currentStep = CustomModuleStep.SELECT },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.weight(0.8f).height(48.dp)
                        ) {
                            Text("Back", fontWeight = FontWeight.SemiBold)
                        }
                        
                        Button(
                            onClick = {
                                if (selectedModules.isEmpty()) return@Button
                                val settings = ExamSettings(
                                    isExamMode = selectedMode == "Exam",
                                    questionCount = selectedCount,
                                    timeLimitMinutes = if (selectedMode == "Timed" || selectedMode == "Exam") (selectedCount * 60) / 60 else 0,
                                    secondsPerQuestion = if (selectedMode == "Timed" || selectedMode == "Exam") 60 else 0,
                                    randomizeQuestions = randomize
                                )
                                onStart(selectedModules, settings)
                             },
                             enabled = selectedModules.isNotEmpty() && (isAllSelected || customCountText.toIntOrNull() != null),
                             modifier = Modifier.weight(1.2f).height(48.dp)
                        ) {
                             Text("Start Module", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
}
