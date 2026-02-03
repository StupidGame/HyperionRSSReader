package io.github.stupidgame.hyperionrssreader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stupidgame.hyperionrssreader.data.local.FeedEntity
import io.github.stupidgame.hyperionrssreader.data.local.FolderEntity
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeed
import io.github.stupidgame.hyperionrssreader.data.repository.AppTheme
import io.github.stupidgame.hyperionrssreader.data.repository.RssRepository
import io.github.stupidgame.hyperionrssreader.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: RssRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val savedFeeds: StateFlow<List<FeedEntity>> = repository.getAllFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val currentTheme: StateFlow<AppTheme> = settingsRepository.currentTheme

    private val _currentFeedContent = MutableStateFlow<RssFeed?>(null)
    val currentFeedContent: StateFlow<RssFeed?> = _currentFeedContent

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // URL入力後の確認用
    private val _foundFeed = MutableStateFlow<RssFeed?>(null)
    val foundFeed: StateFlow<RssFeed?> = _foundFeed

    fun verifyUrl(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // 自動検出付きでフィードを取得
                val feed = repository.fetchFeedContent(url)
                _foundFeed.value = feed
            } catch (e: Exception) {
                _error.value = "URLからフィードが見つからなかった...: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun confirmAddFeed(folderId: Int? = null) {
        val feed = _foundFeed.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveFeed(feed, folderId)
                _foundFeed.value = null // 追加完了したらクリア
            } catch (e: Exception) {
                _error.value = "保存に失敗した...: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelAddFeed() {
        _foundFeed.value = null
    }

    fun selectFeed(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val feed = repository.fetchFeedContent(url)
                _currentFeedContent.value = feed
            } catch (e: Exception) {
                _error.value = "読み込みに失敗した...: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun addFolder(name: String) {
        viewModelScope.launch {
            try {
                repository.addFolder(name)
            } catch (e: Exception) {
                _error.value = "フォルダ作成失敗: ${e.message}"
            }
        }
    }
    
    fun setTheme(theme: AppTheme) {
        settingsRepository.setTheme(theme)
    }

    fun clearError() {
        _error.value = null
    }
}