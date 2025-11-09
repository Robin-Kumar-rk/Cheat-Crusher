package com.cheatcrusher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswersScreen(
    quizId: String,
    pendingId: Long?,
    onBack: () -> Unit,
    viewModel: AnswersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var answerPassword by remember { mutableStateOf("") }
    var canViewAnswers by remember { mutableStateOf(false) }

    LaunchedEffect(quizId, pendingId) {
        viewModel.load(quizId, pendingId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Answers") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            uiState.quiz?.let { quiz ->
                val raw = com.cheatcrusher.util.JoinCodeVerifier.parse(quiz.rawJson)
                if (raw?.answerViewPassword != null) {
                    LaunchedEffect(uiState.savedAnsCode) {
                        val saved = uiState.savedAnsCode
                        if (!saved.isNullOrBlank() && saved == raw.answerViewPassword) {
                            canViewAnswers = true
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Answer View", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = answerPassword,
                                onValueChange = { answerPassword = it },
                                label = { Text("Enter code") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Teachers share this code after the quiz for self-evaluation.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                val ok = answerPassword == raw.answerViewPassword
                                canViewAnswers = ok
                                if (ok) {
                                    viewModel.saveAnsCode(quiz.id, answerPassword)
                                }
                            }) { Text("Unlock Answers") }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    // No gate required
                    canViewAnswers = true
                }

                if (canViewAnswers) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Questions & Answers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.breakdown.forEach { item ->
                                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                    Text(item.questionText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    if (!item.isTextQuestion) {
                                        Text("Correct: ${item.correctOptionTexts.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                                        if (uiState.hasStudentAnswers) {
                                            Text("Your answer: ${item.selectedOptionTexts.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                                            Text("Marks: ${"%.1f".format(item.earnedMarks)} / ${"%.1f".format(item.maxMarks)}", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    } else {
                                        Text("Text question — check manually", style = MaterialTheme.typography.bodyMedium)
                                        if (uiState.hasStudentAnswers) {
                                            val textAns = item.selectedOptionTexts.firstOrNull().orEmpty()
                                            if (textAns.isNotBlank()) {
                                                Text("Your answer: $textAns", style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            uiState.error?.let { err ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Error", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(err, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
