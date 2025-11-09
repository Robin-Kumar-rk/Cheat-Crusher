package com.cheatcrusher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheatcrusher.domain.GradeStatus
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    responseId: String,
    onBackToHome: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var answerPassword by remember { mutableStateOf("") }
    var canViewAnswers by remember { mutableStateOf(false) }
    
    LaunchedEffect(responseId) {
        viewModel.loadResult(responseId)
    }
    
    // Handle system back to return to Home
    BackHandler {
        onBackToHome()
    }
    
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Success Icon
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = "Quiz Submitted!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Your answers have been recorded successfully",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Result Card
        uiState.response?.let { response ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Quiz Results",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Score (if available)
                    response.score?.let { score ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Score:",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "%.1f%%".format(score),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Submission Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Submitted:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = response.clientSubmittedAt?.let { timestamp ->
                                java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                                    .format(timestamp.toDate())
                            } ?: "Unknown",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Grade Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Status:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = when (response.gradeStatus) {
                                GradeStatus.AUTO -> "Auto-graded"
                                GradeStatus.PENDING -> "Pending review"
                                GradeStatus.GRADED -> "Graded"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (response.gradeStatus) {
                                GradeStatus.AUTO -> MaterialTheme.colorScheme.primary
                                GradeStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                                GradeStatus.GRADED -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Answer view password gate
        uiState.quiz?.let { quiz ->
            val raw = com.cheatcrusher.util.JoinCodeVerifier.parse(quiz.rawJson)
            if (raw?.answerViewPassword != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Answer View", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = answerPassword,
                            onValueChange = { answerPassword = it },
                            label = { Text("Enter password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { canViewAnswers = answerPassword == raw.answerViewPassword }) {
                            Text("Unlock Answers")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Correct answers (shown only when unlocked)
        if (canViewAnswers) {
            uiState.quiz?.let { quiz ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Correct Answers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.breakdown.forEach { item ->
                            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                                Text(item.questionText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                if (!item.isTextQuestion) {
                                    Text("Correct: ${item.correctOptionTexts.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Your answer: ${item.selectedOptionTexts.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Marks: ${"%.1f".format(item.earnedMarks)} / ${"%.1f".format(item.maxMarks)}", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Text("Text question — check manually", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Warnings (if any)
        uiState.response?.let { response ->
            if (response.flagged || response.disqualified || response.appSwitchEvents.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Warnings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (response.disqualified) {
                            Text(
                                text = "• Quiz was disqualified due to policy violation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        
                        if (response.flagged) {
                            Text(
                                text = "• Response has been flagged for review",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        
                        if (response.appSwitchEvents.isNotEmpty()) {
                            Text(
                                text = "• ${response.appSwitchEvents.size} app switch event(s) detected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        
        // Answer breakdown (show answers with marks)
        if (uiState.quiz?.showAnswersWithMarks == true && uiState.breakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Answer Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            uiState.breakdown.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = item.questionText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Selected answers
                        Text(
                            text = if (item.isTextQuestion) {
                                "Your Answer: ${item.selectedOptionTexts.firstOrNull()?.ifBlank { "(no answer)" } ?: "(no answer)"}"
                            } else {
                                val chosen = if (item.selectedOptionTexts.isEmpty()) "(no selection)" else item.selectedOptionTexts.joinToString()
                                "Your Selection: $chosen"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Correct answers
                        if (!item.isTextQuestion) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Correct Answer: ${if (item.correctOptionTexts.isEmpty()) "(none)" else item.correctOptionTexts.joinToString()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Will be graded manually",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Marks: ${"%.1f".format(item.earnedMarks)} / ${"%.1f".format(item.maxMarks)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (item.isTextQuestion) "Pending" else if (item.isCorrect) "Correct" else "Incorrect",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.isTextQuestion) MaterialTheme.colorScheme.tertiary else if (item.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "What's Next?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your teacher will review the results and release them according to the quiz settings. You can check your quiz history from the home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Back to Home Button
        Button(
            onClick = onBackToHome,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Back to Home")
        }
        
        // Error Message
        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
