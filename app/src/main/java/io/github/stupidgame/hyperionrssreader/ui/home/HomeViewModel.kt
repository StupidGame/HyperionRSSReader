package io.github.stupidgame.hyperionrssreader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stupidgame.hyperionrssreader.data.local.FeedEntity
import io.github.stupidgame.hyperionrssreader.data.local.FolderEntity
import io.github.stupidgame.hyperionrssreader.data.remote.RssCandidate
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeed
import io.github.stupidgame.hyperionrssreader.data.repository.AppTheme
import io.github.stupidgame.hyperionrssreader.data.repository.NotificationHelper
import io.github.stupidgame.hyperionrssreader.data.repository.RssRepository
import io.github.stupidgame.hyperionrssreader.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.Date

sealed class FeedFilter {
    object All : FeedFilter()
    object Uncategorized : FeedFilter()
    data class Folder(val id: Int) : FeedFilter()
}

class HomeViewModel(
    private val repository: RssRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _currentFilter = MutableStateFlow<FeedFilter>(FeedFilter.All)
    val currentFilter: StateFlow<FeedFilter> = _currentFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val savedFeeds: StateFlow<List<FeedEntity>> = _currentFilter
        .flatMapLatest { filter ->
            when (filter) {
                FeedFilter.All -> repository.getAllFeeds()
                FeedFilter.Uncategorized -> repository.getUncategorizedFeeds()
                is FeedFilter.Folder -> repository.getFeedsInFolder(filter.id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val currentTheme: StateFlow<AppTheme> = settingsRepository.currentTheme
    val currentTimeZoneId: StateFlow<String> = settingsRepository.currentTimeZoneId

    private val _currentFeedContent = MutableStateFlow<RssFeed?>(null)
    val currentFeedContent: StateFlow<RssFeed?> = _currentFeedContent
    
    private var _currentSelectedFeed: FeedEntity? = null
    private var _currentSelectedFolderId: Int? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _rssCandidates = MutableStateFlow<List<RssCandidate>>(emptyList())
    val rssCandidates: StateFlow<List<RssCandidate>> = _rssCandidates.asStateFlow()

    private val _foundFeed = MutableStateFlow<RssFeed?>(null)
    val foundFeed: StateFlow<RssFeed?> = _foundFeed
    
    private val _targetUrl = MutableStateFlow<String>("")
    val targetUrl: StateFlow<String> = _targetUrl.asStateFlow()

    fun setFilter(filter: FeedFilter) {
        _currentFilter.value = filter
        if (filter is FeedFilter.Folder) {
            loadFolderContent(filter.id)
        }
    }
    
    private fun loadFolderContent(folderId: Int) {
        _currentSelectedFolderId = folderId
        _currentSelectedFeed = null
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val content = repository.fetchMergedFeedContent(folderId)
                _currentFeedContent.value = content
            } catch (e: Exception) {
                _error.value = "フォルダの読み込みに失敗しました: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFeed(feed: FeedEntity) {
        viewModelScope.launch {
            try {
                repository.deleteFeed(feed)
            } catch (e: Exception) {
                _error.value = "削除に失敗しました: ${e.message}"
            }
        }
    }

    fun verifyUrl(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _targetUrl.value = url
            _rssCandidates.value = emptyList()
            try {
                val candidates = repository.getRssCandidates(url)
                
                if (candidates.isEmpty()) {
                    _error.value = "URLからフィードが見つからなかった..."
                } else if (candidates.size == 1) {
                    val feed = repository.fetchFeedContent(candidates.first().url)
                    _foundFeed.value = feed
                } else {
                    _rssCandidates.value = candidates
                }
            } catch (e: Exception) {
                _error.value = "エラーが発生しました: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun selectCandidate(candidate: RssCandidate) {
        viewModelScope.launch {
            _isLoading.value = true
            _rssCandidates.value = emptyList()
            try {
                val feed = repository.fetchFeedContent(candidate.url)
                _foundFeed.value = feed
            } catch (e: Exception) {
                _error.value = "選択されたフィードの読み込みに失敗しました: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun cancelSelectCandidate() {
        _rssCandidates.value = emptyList()
        _targetUrl.value = ""
    }

    fun confirmAddFeed(folderId: Int? = null) {
        val feed = _foundFeed.value ?: return
        val urlToSave = _targetUrl.value.ifEmpty { feed.url }
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveFeed(feed, urlToSave, folderId)
                _foundFeed.value = null 
                _targetUrl.value = ""
            } catch (e: Exception) {
                _error.value = "保存に失敗した...: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelAddFeed() {
        _foundFeed.value = null
        _targetUrl.value = ""
    }

    fun selectFeed(feed: FeedEntity) {
        _currentSelectedFeed = feed
        _currentSelectedFolderId = null
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val content = repository.fetchFeedContent(feed)
                _currentFeedContent.value = content
            } catch (e: Exception) {
                _error.value = "読み込みに失敗した...: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refreshCurrentFeed() {
        val feed = _currentSelectedFeed
        val folderId = _currentSelectedFolderId
        
        if (feed != null) {
            selectFeed(feed)
        } else if (folderId != null) {
            loadFolderContent(folderId)
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
    
    fun setTimeZone(timeZoneId: String) {
        settingsRepository.setTimeZone(timeZoneId)
    }
    
    fun openNotificationSettings(feed: FeedEntity) {
        val channelId = if (feed.folderId != null) {
            notificationHelper.getFolderChannelId(feed.folderId)
        } else {
            notificationHelper.getFeedChannelId(feed.id)
        }
        notificationHelper.openChannelSettings(channelId)
    }
    
    fun openFolderNotificationSettings(folder: FolderEntity) {
        notificationHelper.openChannelSettings(notificationHelper.getFolderChannelId(folder.id))
    }
    
    fun openAppNotificationSettings() {
        notificationHelper.openAppSettings()
    }

    fun clearError() {
        _error.value = null
    }
    
    fun formatDate(dateString: String, timeZoneId: String): String {
        // Try common formats
        val parseFormats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ"
        )
        
        var date: Date? = null
        for (format in parseFormats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                date = sdf.parse(dateString)
                if (date != null) break
            } catch (e: Exception) { }
        }
        
        if (date == null) return dateString // パース失敗時はそのまま
        
        val outputFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getTimeZone(timeZoneId)
        return outputFormat.format(date)
    }
}