package com.cheatcrusher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedScreen(
    onEnterOffline: (String) -> Unit,
    onSeeAnswers: (String) -> Unit,
    viewModel: DownloadedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Downloaded Quizzes") })
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No downloaded quizzes yet.")
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        items(uiState.items, key = { it.cached.quizId }) { item ->
                            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(item.cached.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { onSeeAnswers(item.cached.quizId) }) {
                                            Icon(Icons.Filled.Visibility, contentDescription = "See Answers")
                                        }
                                        IconButton(onClick = { viewModel.deleteCached(item.cached.quizId) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Code: ${item.cached.quizCode}", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (item.isAttempted) {
                                        Text("Attempted", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Row {
                                            IconButton(onClick = { onEnterOffline(item.cached.quizId) }) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = "Enter")
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
