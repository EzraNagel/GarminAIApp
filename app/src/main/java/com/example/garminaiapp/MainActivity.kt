package com.example.garminaiapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var responseText by remember { mutableStateOf("Response will appear here...") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Garmin AI Assistant",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = { Text("Enter prompt") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (promptText.isNotBlank()) {
                    isLoading = true
                    responseText = "" // Clear previous response for streaming
                    sendPromptToOllama(
                        prompt = promptText,
                        onToken = { token ->
                            // Append token to text output as it arrives
                            responseText += token
                        },
                        onComplete = {
                            isLoading = false
                        },
                        onError = { error ->
                            responseText = error
                            isLoading = false
                        }
                    )
                }
            },
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(if (isLoading) "Streaming..." else "Send Prompt")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (responseText.isEmpty()) "Thinking..." else responseText,
                    style = MaterialTheme.typography.bodyLarge
                )
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
                            // Post token to Main Thread to safely update Compose state
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