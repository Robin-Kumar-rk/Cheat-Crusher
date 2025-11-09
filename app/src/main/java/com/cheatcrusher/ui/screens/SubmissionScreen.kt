package com.cheatcrusher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionScreen(
    onSeeAnswers: (quizId: String, pendingId: Long?) -> Unit,
    viewModel: SubmissionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Submissions") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            Spacer(modifier = Modifier.height(12.dp))
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (uiState.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No submissions yet.")
                }
            } else {
                uiState.items.forEach { item ->
                    val isPending = item.status == "pending"
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .then(if (isPending) Modifier.clickable { item.pendingId?.let { viewModel.uploadPending(it) } } else Modifier)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            var showDialog by remember { mutableStateOf(false) }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.quizTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Roll: ${item.rollNumber}", style = MaterialTheme.typography.bodySmall)
                                    // Show marks (percentage) after local submission or upload
                                    item.score?.let { sc ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Marks: ${String.format("%.1f", sc)}%",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // See answers (works for submitted or pending)
                                    IconButton(onClick = { onSeeAnswers(item.quizId, item.pendingId) }) {
                                        Icon(Icons.Filled.Visibility, contentDescription = "See Answers")
                                    }
                                    if (item.status == "submitted") {
                                        // Delete local history entry
                                        IconButton(onClick = { viewModel.deleteSubmittedHistory(item.quizId, item.rollNumber) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    } else {
                                        // Pending-specific actions in the action row
                                        // Upload icon (disabled while uploading)
                                        IconButton(onClick = { item.pendingId?.let { if (item.status != "uploading") viewModel.uploadPending(it) } }, enabled = item.status != "uploading") {
                                            Icon(Icons.Filled.CloudUpload, contentDescription = "Upload Pending")
                                        }
                                        // Status indicator chips
                                        when (item.status) {
                                            "uploading" -> {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                AssistChip(onClick = {}, enabled = false, label = { Text("Uploading…") })
                                            }
                                            "failed" -> {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                AssistChip(onClick = {}, label = { Text("Failed") })
                                            }
                                        }
                                        // Delete pending record
                                        IconButton(onClick = { item.pendingId?.let { viewModel.deletePending(it) } }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }
                            }

                            // Prominent full-width Fill Details button below the row
                            if (item.status != "submitted" && item.detailsMissing) {
                                Spacer(modifier = Modifier.height(12.dp))
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text("Details missing") },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = { showDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Fill Details")
                                }
                                if (showDialog) {
                                    FillDetailsDialog(
                                        onClose = { showDialog = false },
                                        onSave = { values ->
                                            item.pendingId?.let { viewModel.updatePendingDetails(it, values) }
                                            showDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FillDetailsDialog(onClose: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var roll by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            Button(onClick = { onSave(mapOf("name" to name, "email" to email, "roll" to roll, "section" to section)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        title = { Text("Fill Required Details") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = roll, onValueChange = { roll = it }, label = { Text("Roll Number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = section, onValueChange = { section = it }, label = { Text("Section") }, modifier = Modifier.fillMaxWidth())
            }
        }
    )
}
