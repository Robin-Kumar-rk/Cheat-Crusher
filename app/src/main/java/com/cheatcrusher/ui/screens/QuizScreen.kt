package com.cheatcrusher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    quizId: String,
    rollNumber: String,
    studentInfoJson: String = "",
    onQuizCompleted: (String) -> Unit,
    onBack: () -> Unit,
    onExitToHome: () -> Unit,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showBackConfirmDialog by remember { mutableStateOf(false) }
    
    // Handle system back button
    BackHandler {
        if (!uiState.isDisqualified) {
            showBackConfirmDialog = true
        }
        // If disqualified, prevent back navigation - user must use Exit Quiz button
    }
    
    // App switch detection using lifecycle events
    DisposableEffect(lifecycleOwner, uiState.isDisqualified) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Only detect app switch if not already disqualified
                    if (!uiState.isDisqualified) {
                        viewModel.onAppSwitchDetected()
                    }
                }
                else -> { /* Handle other events if needed */ }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    LaunchedEffect(quizId, rollNumber, studentInfoJson) {
        val infoMap = try {
            if (studentInfoJson.isNotBlank()) {
                val decodedParam = android.net.Uri.decode(studentInfoJson)
                val decoded = android.util.Base64.decode(decodedParam, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                val json = String(decoded)
                // Very simple JSON parsing into map: expects {"key":"value",...}
                json.trim()
                    .removePrefix("{")
                    .removeSuffix("}")
                    .split(',')
                    .mapNotNull {
                        val parts = it.split(':', limit = 2)
                        if (parts.size == 2) {
                            val k = parts[0].trim().trim('"')
                            val v = parts[1].trim().trim('"')
                            k to v
                        } else null
                    }.toMap()
            } else emptyMap()
        } catch (e: Exception) { emptyMap() }
        viewModel.loadQuiz(quizId, rollNumber, infoMap)
    }
    
    // Removed early navigation tied only to submittedResponseId to avoid conflict
    // Consolidated navigation logic is below, handling both online and offline cases
    
    // Show loading state
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz") },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            if (!uiState.isDisqualified) {
                                showBackConfirmDialog = true
                            }
                        },
                        enabled = !uiState.isDisqualified
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            uiState.quiz?.let { quiz ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Timer and Quiz Info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = quiz.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Question ${uiState.currentQuestionIndex + 1} of ${quiz.questions.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatTime(uiState.timeRemainingSeconds),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.timeRemainingSeconds < 300) { // Less than 5 minutes
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Warning message for flagged users
                    if (uiState.isFlagged) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Warning: App switching detected. This has been logged.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // Current Question
                    val currentQuestion = uiState.shuffledQuestions.getOrNull(uiState.currentQuestionIndex)
                    currentQuestion?.let { question ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = question.text,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                when (question.type) {
                                    com.cheatcrusher.domain.QuestionType.MCQ -> {
                                        SingleChoiceQuestion(
                                            question = question,
                                            selectedOptionId = uiState.answers[question.id]?.optionIds?.firstOrNull(),
                                            onOptionSelected = { optionId ->
                                                if (!uiState.isDisqualified) {
                                                    viewModel.updateAnswer(question.id, listOf(optionId))
                                                }
                                            }
                                        )
                                    }
                                    com.cheatcrusher.domain.QuestionType.MSQ -> {
                                        MultipleChoiceQuestion(
                                            question = question,
                                            selectedOptionIds = uiState.answers[question.id]?.optionIds ?: emptyList(),
                                            onOptionsSelected = { optionIds ->
                                                if (!uiState.isDisqualified) {
                                                    viewModel.updateAnswer(question.id, optionIds)
                                                }
                                            }
                                        )
                                    }
                                    com.cheatcrusher.domain.QuestionType.TEXT -> {
                                        TextQuestion(
                                            question = question,
                                            answer = uiState.answers[question.id]?.answerText ?: "",
                                            onAnswerChanged = { text ->
                                                if (!uiState.isDisqualified) {
                                                    viewModel.updateTextAnswer(question.id, text)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Navigation Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { viewModel.previousQuestion() },
                            enabled = uiState.currentQuestionIndex > 0 && !uiState.isDisqualified
                        ) {
                            Text("Previous")
                        }
                        
                        if (uiState.currentQuestionIndex < quiz.questions.size - 1) {
                            Button(
                                onClick = { viewModel.nextQuestion() },
                                enabled = !uiState.isDisqualified
                            ) {
                                Text("Next")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.submitQuiz() },
                                enabled = !uiState.isDisqualified,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Submit Quiz")
                            }
                        }
                    }
                }
                }
        }
    }

    // Navigate based on submission outcome
    LaunchedEffect(uiState.isSubmitted, uiState.isPendingUpload, uiState.submittedResponseId) {
        if (uiState.isSubmitted) {
            if (uiState.isPendingUpload) {
                // Offline queued: return to home so user sees pending entry
                onExitToHome()
            } else {
                // Submitted online: go to result
                uiState.submittedResponseId?.let { onQuizCompleted(it) }
            }
        }
    }
    
    // Auto-exit on disqualification per policy
    LaunchedEffect(uiState.isDisqualified) {
        if (uiState.isDisqualified) {
            onExitToHome()
        }
    }

    // Error Message (non-disqualification)
    uiState.error?.let { error ->
        if (!uiState.isDisqualified) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("Error") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                }
            )
        }
    }

    // Back/Exit confirmation dialog when user attempts to leave quiz
    if (showBackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackConfirmDialog = false },
            title = { Text("Leave quiz?") },
            text = { Text("Would you like to submit your answers or exit the quiz?") },
            confirmButton = {
                TextButton(onClick = {
                    showBackConfirmDialog = false
                    viewModel.submitQuiz()
                }) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackConfirmDialog = false
                    onExitToHome()
                }) {
                    Text("Exit Quiz")
                }
            }
        )
    }
}

@Composable
private fun SingleChoiceQuestion(
    question: com.cheatcrusher.domain.Question,
    selectedOptionId: String?,
    onOptionSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.selectableGroup()
    ) {
        question.options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option.id == selectedOptionId,
                        onClick = { onOptionSelected(option.id) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = option.id == selectedOptionId,
                    onClick = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = option.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MultipleChoiceQuestion(
    question: com.cheatcrusher.domain.Question,
    selectedOptionIds: List<String>,
    onOptionsSelected: (List<String>) -> Unit
) {
    Column {
        question.options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option.id in selectedOptionIds,
                        onClick = {
                            val newSelection = if (option.id in selectedOptionIds) {
                                selectedOptionIds - option.id
                            } else {
                                selectedOptionIds + option.id
                            }
                            onOptionsSelected(newSelection)
                        },
                        role = Role.Checkbox
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = option.id in selectedOptionIds,
                    onCheckedChange = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = option.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun TextQuestion(
    question: com.cheatcrusher.domain.Question,
    answer: String,
    onAnswerChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = answer,
        onValueChange = onAnswerChanged,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Enter your answer...") }
    )
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}
