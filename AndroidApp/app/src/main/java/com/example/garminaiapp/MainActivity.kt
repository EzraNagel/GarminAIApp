package com.example.garminaiapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.garminaiapp.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

data class ChatMessage(
    val sender: String, // "You" or "AI"
    var text: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enables modern edge-to-edge layout
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OllamaChatScreen()
                }
            }
        }
    }
}

@Composable
fun OllamaChatScreen() {
    var promptText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new text arrives
    LaunchedEffect(messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding() // Protects top status bar
            .navigationBarsPadding() // Pushes the prompt box UP above phone's bottom menu bar
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Garmin AI Assistant",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Chat History List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                val isUser = message.sender == "You"

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        ),
                        // Distinct color schemes: Deep Teal/Blue for User, Light Gray/Surface for AI
                        color = if (isUser) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isUser) "You" else "AI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Input Field + Send Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp), // Extra spacing at bottom
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                label = { Text("Enter prompt") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    if (promptText.isNotBlank()) {
                        val userPrompt = promptText
                        promptText = "" // Clear input box

                        messages.add(ChatMessage("You", userPrompt))
                        val aiMessageIndex = messages.size
                        messages.add(ChatMessage("AI", "Thinking..."))

                        isLoading = true

                        sendPromptToOllama(
                            prompt = userPrompt,
                            onToken = { token ->
                                if (messages[aiMessageIndex].text == "Thinking...") {
                                    messages[aiMessageIndex] = messages[aiMessageIndex].copy(text = "")
                                }
                                val currentText = messages[aiMessageIndex].text
                                messages[aiMessageIndex] = messages[aiMessageIndex].copy(text = currentText + token)
                            },
                            onComplete = {
                                isLoading = false
                            },
                            onError = { error ->
                                messages[aiMessageIndex] = messages[aiMessageIndex].copy(text = error)
                                isLoading = false
                            }
                        )
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.height(56.dp)
            ) {
                Text(if (isLoading) "..." else "Send")
            }
        }
    }
}

private fun sendPromptToOllama(
    prompt: String,
    onToken: (String) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val client = OkHttpClient()
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val mainHandler = Handler(Looper.getMainLooper())

    val jsonPayload = JSONObject().apply {
        put("model", "llama3")
        put("prompt", prompt)
        put("stream", true)
    }.toString()

    val request = Request.Builder()
        .url(BuildConfig.OLLAMA_URL)
        .post(jsonPayload.toRequestBody(mediaType))
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            mainHandler.post { onError("Error: ${e.message}") }
        }

        override fun onResponse(call: Call, response: Response) {
            if (!response.isSuccessful) {
                mainHandler.post { onError("Server error: ${response.code}") }
                return
            }

            try {
                response.body?.source()?.let { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        if (line.isNotBlank()) {
                            val token = JSONObject(line).optString("response", "")
                            mainHandler.post { onToken(token) }
                        }
                    }
                }
                mainHandler.post { onComplete() }
            } catch (e: Exception) {
                mainHandler.post { onError("Streaming error: ${e.message}") }
            }
        }
    })
}