package com.douyin.downloader.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.downloader.data.model.ResolveResult
import com.douyin.downloader.domain.resolver.DouyinResolver
import com.douyin.downloader.domain.resolver.MockDouyinResolver
import com.douyin.downloader.download.AppDownloadManager
import com.douyin.downloader.download.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val resolver: DouyinResolver = MockDouyinResolver()
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _resolveResult = MutableStateFlow<ResolveResult?>(null)
    val resolveResult: StateFlow<ResolveResult?> = _resolveResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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
        AppDownloadManager.resetState()
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
            AppDownloadManager.resetState()

            val result = resolver.resolve(target)
            result.onSuccess { data ->
                _resolveResult.value = data
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "解析失败，请稍后重试"
            }

            _isResolving.value = false
        }
    }

    fun startDownload(context: Context) {
        val result = _resolveResult.value ?: return
        val media = result.media.firstOrNull() ?: return
        AppDownloadManager.startDownload(
            context = context,
            workId = result.id,
            title = result.title,
            videoUrl = media.url
        )
    }

    fun cancelDownload() {
        AppDownloadManager.cancelDownload()
    }
}
