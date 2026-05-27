@file:SuppressLint("RestrictedApiAndroidX")

package com.example.remotecompose

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.remotecompose.data.assistant.GenDocumentClient
import com.example.remotecompose.data.remote.RemoteConfigFetcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val documentBytes: ByteArray? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val lastUpdated: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MainUiState) return false
        return documentBytes.contentEquals(other.documentBytes)
            && isLoading == other.isLoading
            && errorMessage == other.errorMessage
            && lastUpdated == other.lastUpdated
    }

    override fun hashCode(): Int {
        var result = documentBytes?.contentHashCode() ?: 0
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + lastUpdated.hashCode()
        return result
    }

    companion object {
        private const val BASE = "https://api.github.com/repos/junjun-liang/remotecompose/contents"

        fun configUrlForScreen(screenId: String): String {
            return if (screenId == "home") "$BASE/config.rc"
            else "$BASE/config_$screenId.rc"
        }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val genDocumentClient = GenDocumentClient(application)

    private var configUrl: String? = null
    private var loadJob: Job? = null

    fun setConfigUrl(url: String) {
        if (url == configUrl && _uiState.value.documentBytes != null) return
        configUrl = url
        loadDocument()
    }

    fun refresh() {
        loadDocument()
    }

    private fun loadDocument() {
        val url = configUrl ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val result = RemoteConfigFetcher.fetchDocument(url)
                result.fold(
                    onSuccess = { bytes ->
                        val changed = !bytes.contentEquals(_uiState.value.documentBytes)
                        val updatedTime = if (changed) System.currentTimeMillis() else _uiState.value.lastUpdated
                        _uiState.update { it.copy(isLoading = false, documentBytes = bytes, lastUpdated = updatedTime) }
                        // 将最新的 .rc 字节流推送给个人助理 App
                        genDocumentClient.sendBytes(bytes)
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = e.message ?: "Unknown error",
                                documentBytes = null,
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load layout",
                        documentBytes = null,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        genDocumentClient.release()
    }
}
