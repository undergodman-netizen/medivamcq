package com.example

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import com.example.ProfileState
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

data class Flashcard(
    val title: String,
    val points: List<String>,
    val imageUrl: String? = null
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    profileState: ProfileState,
    onBack: () -> Unit
) {
    var flashcards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var newTopic by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pagerState = rememberPagerState(
        pageCount = { flashcards.size.coerceAtLeast(1) }
    )

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                text = { Text("New Flashcard") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (flashcards.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "No flashcards yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap the button below to generate a new medical flashcard with AI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    if (page < flashcards.size) {
                        FlashcardItem(flashcard = flashcards[page])
                    }
                }
            }
        }

        if (showDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { if (!isGenerating) showDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Generate Flashcard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        TextField(
                            value = newTopic,
                            onValueChange = { newTopic = it },
                            placeholder = { Text("e.g. Tuberculosis, CPR...") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isGenerating,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        if (isGenerating) {
                            Spacer(modifier = Modifier.height(24.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                            )
                            Text(
                                text = "AI is thinking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showDialog = false },
                                enabled = !isGenerating
                            ) {
                                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (profileState.geminiApiKey.isBlank()) {
                                        Toast.makeText(context, "Please set Gemini API Key in Profile Settings", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    
                                    scope.launch {
                                        isGenerating = true
                                        try {
                                            val newCard = generateFlashcard(
                                                topic = newTopic,
                                                apiKey = profileState.geminiApiKey,
                                                imagePrompt = profileState.geminiImagePrompt.replace("{topic}", newTopic)
                                            )
                                            if (newCard != null) {
                                                flashcards = flashcards + newCard
                                                showDialog = false
                                                newTopic = ""
                                                pagerState.animateScrollToPage(flashcards.size - 1)
                                            } else {
                                                Toast.makeText(context, "Failed to generate. Try again.", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isGenerating = false
                                        }
                                    }
                                },
                                enabled = !isGenerating && newTopic.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Generate")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlashcardItem(flashcard: Flashcard) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .padding(vertical = 24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            flashcard.imageUrl?.let { url ->
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Flashcard Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = flashcard.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                flashcard.points.forEach { point ->
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = point,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

suspend fun generateFlashcard(topic: String, apiKey: String, imagePrompt: String): Flashcard? {
    return withContext(Dispatchers.IO) {
        try {
            // 1. Generate Image (using gemini-3.1-flash-image / interactions api)
            var imageUrl: String? = null
            try {
                val imgUrlString = "https://generativelanguage.googleapis.com/v1beta/interactions?key=$apiKey"
                val imgUrl = URL(imgUrlString)
                val imgConn = imgUrl.openConnection() as HttpURLConnection
                imgConn.requestMethod = "POST"
                imgConn.setRequestProperty("Content-Type", "application/json")
                imgConn.doOutput = true

                val imgBody = JSONObject().apply {
                    put("model", "gemini-3.1-flash-image")
                    put("input", JSONArray().put(JSONObject().put("type", "text").put("text", imagePrompt)))
                }

                OutputStreamWriter(imgConn.outputStream).use { it.write(imgBody.toString()) }

                if (imgConn.responseCode == 200) {
                    val responseStr = imgConn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)
                    imageUrl = json.optJSONArray("output")
                        ?.optJSONObject(0)
                        ?.optString("image_url")
                }
            } catch (e: Exception) {
                // Ignore image error, fallback to no image
                e.printStackTrace()
            }

            // 2. Generate Text Content (using gemini-3.1-flash-lite)
            val txtUrlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"
            val txtUrl = URL(txtUrlString)
            val txtConn = txtUrl.openConnection() as HttpURLConnection
            txtConn.requestMethod = "POST"
            txtConn.setRequestProperty("Content-Type", "application/json")
            txtConn.doOutput = true

            val txtBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", "Create a structured, concise medical flashcard for the topic: '$topic'. Return ONLY a JSON object with 'title' (String) and 'points' (Array of Strings). Keep the points strictly factual and concise.")
                    ))
                ))
            }

            OutputStreamWriter(txtConn.outputStream).use { it.write(txtBody.toString()) }

            if (txtConn.responseCode == 200) {
                val responseStr = txtConn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                
                var textResponse = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    
                // Clean markdown code blocks if any
                textResponse = textResponse.replace("```json", "").replace("```", "").trim()
                
                val resultJson = JSONObject(textResponse)
                val title = resultJson.getString("title")
                val pointsArray = resultJson.getJSONArray("points")
                val pointsList = mutableListOf<String>()
                for (i in 0 until pointsArray.length()) {
                    pointsList.add(pointsArray.getString(i))
                }
                
                return@withContext Flashcard(title = title, points = pointsList, imageUrl = imageUrl)
            } else {
                return@withContext null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
