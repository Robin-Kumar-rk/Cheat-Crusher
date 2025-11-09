package com.cheatcrusher

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.List
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cheatcrusher.ui.screens.AnswersScreen
// Removed unused profile setup imports
import com.cheatcrusher.ui.screens.HomeScreen
import com.cheatcrusher.ui.screens.DownloadedScreen
import com.cheatcrusher.ui.screens.DownloadQuizScreen
import com.cheatcrusher.ui.screens.SubmissionScreen
import com.cheatcrusher.ui.screens.JoinQuizScreen
import com.cheatcrusher.ui.screens.PreQuizFormScreen
import com.cheatcrusher.ui.screens.QuizScreen
import com.cheatcrusher.ui.screens.ResultScreen
import com.cheatcrusher.ui.theme.CheatCrusherTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            CheatCrusherTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController: NavHostController = rememberNavController()
                    val ctx = LocalContext.current

                    // Global back: go Home from any route; back again on Home exits app
                    BackHandler {
                        val currentDestination = navController.currentBackStackEntry?.destination?.route
                        if (currentDestination != "home") {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            (ctx as? Activity)?.finish()
                        }
                    }

                    Scaffold(
                        bottomBar = {
                            val currentDestination = navController.currentBackStackEntry?.destination?.route
                            val isInQuiz = currentDestination == "quiz/{quizId}/{roll}/{info}"
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentDestination == "home",
                                    onClick = { navController.navigate("home") },
                                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                                    label = { Text("Home") },
                                    enabled = !isInQuiz
                                )
                                NavigationBarItem(
                                    selected = currentDestination == "downloaded",
                                    onClick = { navController.navigate("downloaded") },
                                    icon = { Icon(Icons.Filled.Download, contentDescription = "Downloaded") },
                                    label = { Text("Downloaded") },
                                    enabled = !isInQuiz
                                )
                                NavigationBarItem(
                                    selected = currentDestination == "submission",
                                    onClick = { navController.navigate("submission") },
                                    icon = { Icon(Icons.Filled.List, contentDescription = "Submission") },
                                    label = { Text("Submission") },
                                    enabled = !isInQuiz
                                )
                            }
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            composable("home") {
                                HomeScreen(
                                    onJoinQuiz = { navController.navigate("join") },
                                    onDownloadQuiz = { navController.navigate("download") }
                                )
                            }
                            composable("download") {
                                DownloadQuizScreen(
                                    onDownloaded = { quizId -> navController.navigate("preform/$quizId") },
                                    onBack = {
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable("downloaded") {
                                DownloadedScreen(
                                    onEnterOffline = { quizId -> navController.navigate("preform/$quizId") },
                                    onSeeAnswers = { quizId -> navController.navigate("answers/$quizId/0") }
                                )
                            }
                            composable("submission") {
                                SubmissionScreen(
                                    onSeeAnswers = { quizId, pendingId ->
                                        val pid = pendingId ?: 0L
                                        navController.navigate("answers/$quizId/$pid")
                                    }
                                )
                            }
                            composable("join") {
                                JoinQuizScreen(
                                    onQuizFound = { quizId -> navController.navigate("preform/$quizId") },
                                    onBack = {
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable("preform/{quizId}") { backStackEntry ->
                                val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
                                PreQuizFormScreen(
                                    quizId = quizId,
                                    onStartQuiz = { roll, infoJson ->
                                        // Encode payloads for safe routing; use a sentinel for blank roll
                                        val encodedInfo = android.net.Uri.encode(infoJson)
                                        val encodedRoll = android.net.Uri.encode(roll)
                                        val routeRoll = if (roll.isBlank()) "_" else encodedRoll
                                        navController.navigate("quiz/$quizId/$routeRoll/$encodedInfo")
                                    },
                                    onBack = {
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            // Removed unused profile setup route
                            composable("quiz/{quizId}/{roll}/{info}") { backStackEntry ->
                                val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
                                val rawRoll = backStackEntry.arguments?.getString("roll") ?: ""
                                val infoJson = backStackEntry.arguments?.getString("info") ?: ""
                                // Translate sentinel back to blank and decode any encoding
                                val roll = if (rawRoll == "_") "" else android.net.Uri.decode(rawRoll)
                                QuizScreen(
                                    quizId = quizId,
                                    rollNumber = roll,
                                    studentInfoJson = infoJson,
                                    onQuizCompleted = { responseId -> navController.navigate("result/$responseId") },
                                    onBack = { navController.popBackStack() },
                                    onExitToHome = {
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable("result/{responseId}") { backStackEntry ->
                                val responseId = backStackEntry.arguments?.getString("responseId") ?: ""
                                ResultScreen(
                                    responseId = responseId,
                                    onBackToHome = {
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("answers/{quizId}/{pendingId}") { backStackEntry ->
                                val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
                                val pendingIdStr = backStackEntry.arguments?.getString("pendingId") ?: "0"
                                val pendingId = pendingIdStr.toLongOrNull()
                                AnswersScreen(
                                    quizId = quizId,
                                    pendingId = pendingId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
