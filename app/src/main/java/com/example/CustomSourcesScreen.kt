package com.example

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EmptyState
import com.example.ui.designsystem.AppAlertDialog
import com.example.ui.designsystem.AppBottomSheet
import com.example.ui.designsystem.WedikaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSourcesScreen(
    viewModel: McqViewModel,
    appState: AppState,
    onOpenSet: (String) -> Unit,
    onBack: () -> Unit,
    showTopBar: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val legacySources by viewModel.customSources.collectAsStateWithLifecycle()

    var selectedCollection by remember { mutableStateOf<McqCollectionItem?>(null) }
    var selectedSection by remember { mutableStateOf<McqSectionItem?>(null) }
    var itemToDelete by remember { mutableStateOf<Pair<String, String>?>(null) } // type ("collection"/"set"/"legacy"), id
    var showImportSheet by remember { mutableStateOf(false) }

    // Handle back button press in drill-down mode
    BackHandler(enabled = selectedSection != null || selectedCollection != null) {
        when {
            selectedSection != null -> selectedSection = null
            selectedCollection != null -> selectedCollection = null
            else -> onBack()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.loadCustomJsonsFromUris(context, uris)
            showImportSheet = false
        }
    }

    val currentTitle = when {
        selectedSection != null -> selectedSection!!.name
        selectedCollection != null -> selectedCollection!!.name
        else -> "Custom Sets Library"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedCollection != null) {
                            Text(
                                text = if (selectedSection != null) "${selectedCollection!!.name} • Section" else "Collection Overview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectedSection != null -> selectedSection = null
                            selectedCollection != null -> selectedCollection = null
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(Modifier.width(8.dp))
                    Text("Import Bank", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (appState.importDiagnostics != null) {
                WedikaCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Import Diagnostics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            IconButton(onClick = { viewModel.dismissImportDiagnostics() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        appState.importDiagnostics?.forEach { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            when {
                selectedCollection == null -> {
                    // Level 0: Collections Library View
                    CollectionsLibraryView(
                        collections = collections,
                        legacySources = legacySources,
                        onSelectCollection = { selectedCollection = it },
                        onOpenLegacySource = onOpenSet,
                        onDeleteCollection = { colId -> itemToDelete = "collection" to colId },
                        onDeleteLegacySource = { src -> itemToDelete = "legacy" to src }
                    )
                }
                selectedSection == null -> {
                    // Level 1: Collection View (Sections + Unsectioned Sets)
                    CollectionView(
                        viewModel = viewModel,
                        collection = selectedCollection!!,
                        onSelectSection = { selectedSection = it },
                        onStartSet = { setItem ->
                            viewModel.startSetSession(setItem.id, setItem.title, setItem.setType, setItem.durationMinutes) {
                                onOpenSet(setItem.id)
                            }
                        },
                        onDeleteSet = { setId -> itemToDelete = "set" to setId }
                    )
                }
                else -> {
                    // Level 2: Section Sets View
                    SectionSetsView(
                        viewModel = viewModel,
                        section = selectedSection!!,
                        onStartSet = { setItem ->
                            viewModel.startSetSession(setItem.id, setItem.title, setItem.setType, setItem.durationMinutes) {
                                onOpenSet(setItem.id)
                            }
                        },
                        onDeleteSet = { setId -> itemToDelete = "set" to setId }
                    )
                }
            }
        }
    }

    if (itemToDelete != null) {
        val (type, id) = itemToDelete!!
        AppAlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = when (type) {
                "collection" -> "Delete Collection"
                "set" -> "Delete Question Set"
                else -> "Delete Legacy Set"
            },
            confirmText = "Delete",
            onConfirm = {
                when (type) {
                    "collection" -> {
                        viewModel.deleteCollection(id)
                        if (selectedCollection?.id == id) selectedCollection = null
                    }
                    "set" -> viewModel.deleteSet(id)
                    "legacy" -> viewModel.deleteQuestionsBySource(id)
                }
                itemToDelete = null
            },
            dismissText = "Cancel",
            onDismiss = { itemToDelete = null },
            icon = Icons.Default.Delete,
            iconColor = MaterialTheme.colorScheme.error,
            text = {
                Text(
                    when (type) {
                        "collection" -> "Are you sure you want to delete this complete question collection and all its sets?"
                        "set" -> "Are you sure you want to delete this question set?"
                        else -> "Are you sure you want to delete this question set?"
                    }
                )
            }
        )
    }

    if (showImportSheet) {
        ImportOptionsBottomSheet(
            context = context,
            filePickerLauncher = filePickerLauncher,
            onDismiss = { showImportSheet = false }
        )
    }
}

@Composable
private fun CollectionsLibraryView(
    collections: List<McqCollectionItem>,
    legacySources: List<com.example.local.SourceCount>,
    onSelectCollection: (McqCollectionItem) -> Unit,
    onOpenLegacySource: (String) -> Unit,
    onDeleteCollection: (String) -> Unit,
    onDeleteLegacySource: (String) -> Unit
) {
    if (collections.isEmpty() && legacySources.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                icon = Icons.Default.FolderOpen,
                title = "No Custom Sets",
                subtitle = "No question collections yet. Tap 'Import Bank' to upload JSON files or copy a schema template."
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (collections.isNotEmpty()) {
                item {
                    Text(
                        text = "Question Collections",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(collections, key = { it.id }) { col ->
                    WedikaCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelectCollection(col) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LibraryBooks,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = col.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = col.sourceProvider ?: "Imported Bank",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteCollection(col.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Collection",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (legacySources.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Legacy / Direct Sets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(legacySources, key = { it.sourceUrl ?: "" }) { row ->
                    val src = row.sourceUrl ?: ""
                    WedikaCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (src.isNotBlank()) onOpenLegacySource(src) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatSourceDisplayName(src.removePrefix("custom://")),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${row.count} questions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { if (src.isNotBlank()) onDeleteLegacySource(src) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionView(
    viewModel: McqViewModel,
    collection: McqCollectionItem,
    onSelectSection: (McqSectionItem) -> Unit,
    onStartSet: (McqSetItem) -> Unit,
    onDeleteSet: (String) -> Unit
) {
    val sections by viewModel.getSectionsForCollectionFlow(collection.id).collectAsStateWithLifecycle()
    val unsectionedSets by viewModel.getUnsectionedSetsForCollectionFlow(collection.id).collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (sections.isNotEmpty()) {
            item {
                Text(
                    text = "Subjects & Categories",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(sections, key = { it.id }) { sec ->
                WedikaCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectSection(sec) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (sec.sectionType == "test_category") Icons.Default.Quiz else Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = sec.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Section",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (unsectionedSets.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Question Sets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            items(unsectionedSets, key = { it.id }) { setItem ->
                SetCardItem(
                    setItem = setItem,
                    onStartSet = onStartSet,
                    onDeleteSet = onDeleteSet
                )
            }
        }
    }
}

@Composable
private fun SectionSetsView(
    viewModel: McqViewModel,
    section: McqSectionItem,
    onStartSet: (McqSetItem) -> Unit,
    onDeleteSet: (String) -> Unit
) {
    val sets by viewModel.getSetsForSectionFlow(section.id).collectAsStateWithLifecycle()

    if (sets.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No sets in this section", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sets, key = { it.id }) { setItem ->
                SetCardItem(
                    setItem = setItem,
                    onStartSet = onStartSet,
                    onDeleteSet = onDeleteSet
                )
            }
        }
    }
}

@Composable
private fun SetCardItem(
    setItem: McqSetItem,
    onStartSet: (McqSetItem) -> Unit,
    onDeleteSet: (String) -> Unit
) {
    val isTest = setItem.setType == "TEST" || setItem.setType == "MOCK" || setItem.setType == "EXAM"

    WedikaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onStartSet(setItem) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = setItem.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isTest) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (isTest) "TIMED TEST" else "PRACTICE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isTest) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${setItem.questionCount} questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (setItem.durationMinutes != null && setItem.durationMinutes > 0) {
                        Text(
                            text = "${setItem.durationMinutes} mins",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDeleteSet(setItem.id) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Set",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = { onStartSet(setItem) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (isTest) "Start Test" else "Practice")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportOptionsBottomSheet(

    context: Context,
    filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onDismiss: () -> Unit
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        title = "Import Question Bank"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WedikaCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                    )
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Import JSON File(s)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Supports Core BTR, nested collections, and flat arrays",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            WedikaCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val sampleJson = """{
  "collection_name": "Sample Medical Bank",
  "source_provider": "Medika Custom",
  "qbank": {
    "Anatomy": [
      {
        "set_name": "Cardiovascular Anatomy",
        "questions": [
          {
            "question": "What is the primary pumping chamber of the heart?",
            "options": ["Left Ventricle", "Right Atrium", "Aorta", "Vena Cava"],
            "correct_answer": "Left Ventricle",
            "explanation": "The left ventricle pumps oxygenated blood into systemic circulation."
          }
        ]
      }
    ]
  }
}"""
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Required App JSON Format", sampleJson))
                    android.widget.Toast.makeText(context, "Copied canonical JSON format template to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Copy Canonical JSON Template",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Copy complete collection schema template",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
