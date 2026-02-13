package io.github.stupidgame.hyperionrssreader

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

import io.github.stupidgame.hyperionrssreader.data.local.FeedEntity
import io.github.stupidgame.hyperionrssreader.data.local.FolderEntity
import io.github.stupidgame.hyperionrssreader.data.local.RssDatabase
import io.github.stupidgame.hyperionrssreader.data.remote.RssCandidate
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeed
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeedParser
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeedService
import io.github.stupidgame.hyperionrssreader.data.repository.AppTheme
import io.github.stupidgame.hyperionrssreader.data.repository.NotificationHelper
import io.github.stupidgame.hyperionrssreader.data.repository.RssRepository
import io.github.stupidgame.hyperionrssreader.data.repository.SettingsRepository
import io.github.stupidgame.hyperionrssreader.ui.home.FeedFilter
import io.github.stupidgame.hyperionrssreader.ui.home.HomeViewModel
import io.github.stupidgame.hyperionrssreader.ui.theme.HyperionRSSReaderTheme
import io.github.stupidgame.hyperionrssreader.worker.RssUpdateWorker
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val database = RssDatabase.getDatabase(context)
            val okHttpClient = OkHttpClient.Builder().build()
            val rssFeedParser = RssFeedParser()
            val rssFeedService = RssFeedService(okHttpClient, rssFeedParser)
            val notificationHelper = remember { NotificationHelper(context) }
            val repository = RssRepository(database.rssDao(), rssFeedService, notificationHelper)
            val settingsRepository = remember { SettingsRepository(context) }

            val homeViewModel: HomeViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                            @Suppress("UNCHECKED_CAST")
                            return HomeViewModel(repository, settingsRepository, notificationHelper) as T
                        }
                        throw IllegalArgumentException("Unknown ViewModel class")
                    }
                }
            )

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            val updateInterval by homeViewModel.updateInterval.collectAsState()

            LaunchedEffect(updateInterval) {
                val workRequest =
                    PeriodicWorkRequestBuilder<RssUpdateWorker>(
                        updateInterval.toLong(),
                        TimeUnit.MINUTES
                    ).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "rss_update_work",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    workRequest
                )
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val currentTheme by homeViewModel.currentTheme.collectAsState()
            val darkTheme = when (currentTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(Unit) {
                val feeds = repository.getAllFeedsSync()
                val folders = repository.getAllFoldersSync()
                repository.createNotificationChannelsForExistingData(feeds, folders)
            }

            HyperionRSSReaderTheme(darkTheme = darkTheme) {
                HomeScreen(homeViewModel = homeViewModel, updateInterval = updateInterval)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(homeViewModel: HomeViewModel, updateInterval: Int) {
    val savedFeeds by homeViewModel.savedFeeds.collectAsState()
    val folders by homeViewModel.folders.collectAsState()
    val currentFeedContent by homeViewModel.currentFeedContent.collectAsState()
    val foundFeed by homeViewModel.foundFeed.collectAsState()
    val targetUrl by homeViewModel.targetUrl.collectAsState()
    val rssCandidates by homeViewModel.rssCandidates.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val isRefreshing by homeViewModel.isRefreshing.collectAsState()
    val error by homeViewModel.error.collectAsState()
    val currentFilter by homeViewModel.currentFilter.collectAsState()
    val currentTimeZoneId by homeViewModel.currentTimeZoneId.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var feedToDelete by remember { mutableStateOf<FeedEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var feedToEdit by remember { mutableStateOf<FeedEntity?>(null) }
    var folderToEdit by remember { mutableStateOf<FolderEntity?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val pullRefreshState = rememberPullToRefreshState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        Text("Folders", style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    item {
                        NavigationDrawerItem(
                            label = { Text("All Feeds") },
                            selected = currentFilter is FeedFilter.All,
                            onClick = {
                                homeViewModel.setFilter(FeedFilter.All)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            label = { Text("Uncategorized") },
                            selected = currentFilter is FeedFilter.Uncategorized,
                            onClick = {
                                homeViewModel.setFilter(FeedFilter.Uncategorized)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    items(folders, key = { "folder_${it.id}" }) { folder ->
                        NavigationDrawerItem(
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(folder.name, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { folderToEdit = folder }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { folderToDelete = folder }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            selected = currentFilter is FeedFilter.Folder && (currentFilter as FeedFilter.Folder).id == folder.id,
                            onClick = {
                                homeViewModel.setFilter(FeedFilter.Folder(folder.id))
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Feeds", style = MaterialTheme.typography.titleMedium)
                    }

                    items(savedFeeds, key = { "feed_${it.id}" }) { feed ->
                        NavigationDrawerItem(
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(decodeHtml(feed.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(feed.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { feedToEdit = feed }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { feedToDelete = feed }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            selected = false,
                            onClick = {
                                homeViewModel.selectFeed(feed)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        NavigationDrawerItem(
                            label = { Text("Settings") },
                            selected = false,
                            onClick = { showSettingsDialog = true },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { showAddFolderDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Create New Folder")
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(when(currentFilter) {
                            FeedFilter.All -> "Hyperion RSS (All)"
                            FeedFilter.Uncategorized -> "Hyperion RSS (Uncategorized)"
                            is FeedFilter.Folder -> {
                                val folderId = (currentFilter as FeedFilter.Folder).id
                                val folderName = folders.find { it.id == folderId }?.name ?: "Folder"
                                "Hyperion RSS ($folderName)"
                            }
                        })
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Feed")
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    items(savedFeeds, key = { it.id }) { feed ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .pointerInput(feed.id) {
                                    detectTapGestures(
                                        onLongPress = { feedToDelete = feed },
                                        onTap = { homeViewModel.selectFeed(feed) }
                                    )
                                },
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = decodeHtml(feed.title),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = feed.url,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    if (currentFeedContent != null) {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { homeViewModel.refreshCurrentFeed() },
                            state = pullRefreshState,
                            modifier = Modifier.fillMaxSize(),
                            indicator = {
                                PullToRefreshDefaults.Indicator(
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    state = pullRefreshState,
                                    isRefreshing = isRefreshing
                                )
                            }
                        ) {
                            FeedContent(
                                rssFeed = currentFeedContent!!,
                                timeZoneId = currentTimeZoneId,
                                formatDate = { date, tz -> homeViewModel.formatDate(date, tz) },
                                onItemClick = { url ->
                                    try {
                                        uriHandler.openUri(url)
                                    } catch (_: Exception) {
                                    }
                                }
                            )
                        }
                    } else if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        Text(
                            text = "Select a feed to view",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = { homeViewModel.clearError() },
            title = { Text("Error") },
            text = { Text(error ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = { homeViewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }

    if (feedToEdit != null) {
        EditTitleDialog(
            title = "Edit Feed Title",
            initialValue = decodeHtml(feedToEdit!!.title),
            onDismiss = { feedToEdit = null },
            onConfirm = { newTitle ->
                homeViewModel.updateFeedTitle(feedToEdit!!, newTitle)
                feedToEdit = null
            }
        )
    }

    if (folderToEdit != null) {
        EditTitleDialog(
            title = "Edit Folder Name",
            initialValue = folderToEdit!!.name,
            onDismiss = { folderToEdit = null },
            onConfirm = { newName ->
                homeViewModel.updateFolderName(folderToEdit!!, newName)
                folderToEdit = null
            }
        )
    }

    if (feedToDelete != null) {
        AlertDialog(
            onDismissRequest = { feedToDelete = null },
            title = { Text("Delete Feed?") },
            text = { Text("Are you sure you want to delete '${decodeHtml(feedToDelete?.title ?: "")}'?") },
            confirmButton = {
                TextButton(onClick = {
                    feedToDelete?.let { homeViewModel.deleteFeed(it) }
                    feedToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { feedToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete Folder?") },
            text = { Text("Are you sure you want to delete '${folderToDelete?.name ?: ""}'? Feeds inside will become uncategorized.") },
            confirmButton = {
                TextButton(onClick = {
                    folderToDelete?.let { homeViewModel.deleteFolder(it) }
                    folderToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AddFeedDialog(
            onDismiss = { showAddDialog = false },
            onVerify = { url ->
                homeViewModel.verifyUrl(url)
                showAddDialog = false
            }
        )
    }

    if (rssCandidates.isNotEmpty()) {
        SelectCandidateDialog(
            candidates = rssCandidates,
            onSelect = { candidate ->
                homeViewModel.selectCandidate(candidate)
            },
            onCancel = {
                homeViewModel.cancelSelectCandidate()
            }
        )
    }

    if (foundFeed != null) {
        ConfirmFeedDialog(
            feed = foundFeed!!,
            targetUrl = targetUrl,
            folders = folders,
            onConfirm = { folderId ->
                homeViewModel.confirmAddFeed(folderId)
            },
            onCancel = {
                homeViewModel.cancelAddFeed()
            }
        )
    }

    if (showAddFolderDialog) {
        AddFolderDialog(
            onDismiss = { showAddFolderDialog = false },
            onAdd = { name ->
                homeViewModel.addFolder(name)
                showAddFolderDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            currentTimeZoneId = currentTimeZoneId,
            updateInterval = updateInterval,
            onThemeSelected = {
                homeViewModel.setTheme(it)
            },
            onTimeZoneSelected = {
                homeViewModel.setTimeZone(it)
            },
            onUpdateIntervalChanged = {
                homeViewModel.setUpdateInterval(it)
            },
            onOpenNotificationSettings = {
                homeViewModel.openAppNotificationSettings()
            }
        )
    }
}


@Composable
fun EditTitleDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SelectCandidateDialog(
    candidates: List<RssCandidate>,
    onSelect: (RssCandidate) -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Select RSS Feed") },
        text = {
            Column {
                Text("Multiple feeds found. Please select one:")
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(candidates, key = { it.url }) { candidate ->
                        ListItem(
                            headlineContent = { Text(decodeHtml(candidate.title)) },
                            supportingContent = { Text(candidate.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier
                                .clickable { onSelect(candidate) }
                                .padding(vertical = 4.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    currentTimeZoneId: String,
    updateInterval: Int,
    onThemeSelected: (AppTheme) -> Unit,
    onTimeZoneSelected: (String) -> Unit,
    onUpdateIntervalChanged: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Remove unused variable 'availableTimeZones' if it's not being used yet
    // But since it's just 'val', it doesn't hurt much, but let's clean it if requested.
    // However, I'll keep commonTimeZones.
    val commonTimeZones = listOf("UTC", "Asia/Tokyo", "America/New_York", "Europe/London", "Asia/Shanghai")
    val displayTimeZones = (commonTimeZones + currentTimeZoneId).distinct().sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { onThemeSelected(AppTheme.LIGHT) }) { Text("Light") }
                    TextButton(onClick = { onThemeSelected(AppTheme.DARK) }) { Text("Dark") }
                    TextButton(onClick = { onThemeSelected(AppTheme.SYSTEM) }) { Text("System") }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Time Zone", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(currentTimeZoneId)
                        Icon(Icons.Filled.ArrowDropDown, "Select Time Zone")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        displayTimeZones.forEach { id ->
                            DropdownMenuItem(
                                text = { Text(id) },
                                onClick = {
                                    onTimeZoneSelected(id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Notifications", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Update Interval: $updateInterval minutes")
                Slider(
                    value = updateInterval.toFloat(),
                    onValueChange = { onUpdateIntervalChanged(it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 10
                )

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { onOpenNotificationSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Notifications")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AddFeedDialog(onDismiss: () -> Unit, onVerify: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add RSS Feed") },
        text = {
            Column {
                Text("Enter the URL of the website or RSS feed.")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onVerify(url) }) {
                Text("Search")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConfirmFeedDialog(
    feed: RssFeed,
    targetUrl: String,
    folders: List<FolderEntity>,
    onConfirm: (Int?) -> Unit,
    onCancel: () -> Unit
) {
    var selectedFolderId by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Subscribe to Feed?") },
        text = {
            Column {
                Text("Title: ${decodeHtml(feed.channel.title)}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Source URL: ${feed.url}", style = MaterialTheme.typography.bodySmall)
                Text("Register as: $targetUrl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(decodeHtml(feed.channel.description), maxLines = 3, style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Folder (Optional):")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedFolderId == null,
                        onClick = { selectedFolderId = null }
                    )
                    Text("None")
                }
                folders.forEach { folder ->
                     Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedFolderId == folder.id,
                            onClick = { selectedFolderId = folder.id }
                        )
                        Text(folder.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedFolderId) }) {
                Text("Subscribe")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddFolderDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Folder") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FeedContent(
    rssFeed: RssFeed,
    timeZoneId: String,
    formatDate: (String, String) -> String,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = decodeHtml(rssFeed.channel.title), style = MaterialTheme.typography.headlineMedium)

        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(rssFeed.channel.items, key = { it.link }) { item ->
                val formattedDate = formatDate(item.pubDate, timeZoneId)
                ListItem(
                    headlineContent = { Text(decodeHtml(item.title)) },
                    supportingContent = { Text(formattedDate) }, // Display formatted date
                    modifier = Modifier
                        .clickable { onItemClick(item.link) }
                        .padding(vertical = 4.dp)
                )
                HorizontalDivider()
            }
        }
    }
}

fun decodeHtml(text: String): String {
    return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
}
