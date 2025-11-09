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
import com.cheatcrusher.data.firebase.FirestoreRepository
import com.cheatcrusher.data.local.OfflineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadedQuizId: String? = null
)

@HiltViewModel
class DownloadQuizViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val offlineRepository: OfflineRepository
) : androidx.lifecycle.ViewModel() {
    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    fun download(code: String) {
        val normalized = code.trim().uppercase()
        if (normalized.length != 6) {
            _uiState.value = _uiState.value.copy(error = "Enter a valid 6-character code")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            firestoreRepository.getQuizRawByDownloadCode(normalized).fold(
                onSuccess = { (docId, rawJson) ->
                    val ok = offlineRepository.cacheRawQuizFromDownload(docId, normalized, rawJson)
                    if (ok) {
                        _uiState.value = _uiState.value.copy(isLoading = false, downloadedQuizId = docId)
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to cache quiz")
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Download failed")
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQuizScreen(
    onDownloaded: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DownloadQuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Download Quiz") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it.uppercase() },
                label = { Text("Enter Download Code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.download(code) }, enabled = !uiState.isLoading && code.length == 6) {
                Text("Download")
            }
            Spacer(modifier = Modifier.height(16.dp))

            uiState.error?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(err, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            uiState.downloadedQuizId?.let { id ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Downloaded successfully", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { onDownloaded(id) }) { Text("Continue") }
                    }
                }
            }
        }
    }
}

