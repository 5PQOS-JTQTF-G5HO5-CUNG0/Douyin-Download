package com.douyin.downloader.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.downloader.data.model.ResolveResponse
import com.douyin.downloader.data.model.ResolveStage
import com.douyin.downloader.domain.resolver.MockResolver
import com.douyin.downloader.domain.resolver.RemoteApiResolver
import com.douyin.downloader.domain.resolver.VideoResolver
import com.douyin.downloader.download.AndroidMediaDownloader
import com.douyin.downloader.download.AppDownloadManager
import com.douyin.downloader.download.DownloadState
import com.douyin.downloader.download.MediaDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val remoteResolver: RemoteApiResolver = RemoteApiResolver(),
    private val mockResolver: MockResolver = MockResolver(),
    private val downloader: MediaDownloader = AndroidMediaDownloader()
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _resolveStage = MutableStateFlow(ResolveStage.IDLE)
    val resolveStage: StateFlow<ResolveStage> = _resolveStage.asStateFlow()

    private val _resolveResult = MutableStateFlow<ResolveResponse?>(null)
    val resolveResult: StateFlow<ResolveResponse?> = _resolveResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isMockMode = MutableStateFlow(com.douyin.downloader.data.local.AppPreferences.isMockMode())
    val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private val _serverBaseUrl = MutableStateFlow(remoteResolver.getBaseUrl())
    val serverBaseUrl: StateFlow<String> = _serverBaseUrl.asStateFlow()

    val downloadState: StateFlow<DownloadState> = AppDownloadManager.downloadState

    fun onInputChanged(newText: String) {
        _inputText.value = newText
        if (_errorMessage.value != null) {
            _errorMessage.value = null
        }
    }

    fun clearInput() {
        _inputText.value = ""
        _errorMessage.value = null
    }

    fun clearResult() {
        _resolveResult.value = null
        _errorMessage.value = null
        _resolveStage.value = ResolveStage.IDLE
        AppDownloadManager.resetState()
    }

    fun toggleMockMode(enabled: Boolean) {
        _isMockMode.value = enabled
        com.douyin.downloader.data.local.AppPreferences.setMockMode(enabled)
    }

    fun updateServerUrl(url: String) {
        val clean = url.trim()
        if (clean.isNotEmpty()) {
            remoteResolver.updateBaseUrl(clean)
            _serverBaseUrl.value = clean
        }
    }

    fun resolveUrl(urlToResolve: String? = null) {
        val target = urlToResolve ?: _inputText.value
        if (target.isBlank()) {
            _errorMessage.value = "请输入或粘贴抖音分享链接"
            return
        }

        viewModelScope.launch {
            _isResolving.value = true
            _errorMessage.value = null
            _resolveResult.value = null
            _resolveStage.value = ResolveStage.PARSING_URL
            AppDownloadManager.resetState()

            val activeResolver: VideoResolver = if (_isMockMode.value) mockResolver else remoteResolver

            val response = activeResolver.resolve(target) { stage ->
                _resolveStage.value = stage
            }

            if (response.ok) {
                _resolveResult.value = response
            } else {
                _errorMessage.value = response.getUserFriendlyMessage()
            }

            _isResolving.value = false
            _resolveStage.value = ResolveStage.IDLE
        }
    }

    fun startDownload(context: Context) {
        val result = _resolveResult.value ?: return
        val media = result.media.firstOrNull() ?: return
        downloader.download(
            context = context,
            media = media,
            title = result.title ?: "抖音视频",
            id = result.id ?: "douyin_${System.currentTimeMillis()}"
        )
    }

    fun cancelDownload() {
        downloader.cancel()
    }
}
