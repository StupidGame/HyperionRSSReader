package io.github.stupidgame.hyperionrssreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.stupidgame.hyperionrssreader.data.local.RssDatabase
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeed
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeedParser
import io.github.stupidgame.hyperionrssreader.data.remote.RssFeedService
import io.github.stupidgame.hyperionrssreader.data.repository.AppTheme
import io.github.stupidgame.hyperionrssreader.data.repository.RssRepository
import io.github.stupidgame.hyperionrssreader.data.repository.SettingsRepository
import io.github.stupidgame.hyperionrssreader.ui.home.HomeViewModel
import io.github.stupidgame.hyperionrssreader.ui.theme.HyperionRSSReaderTheme
import okhttp3.OkHttpClient
import kotlinx.coroutines.launch

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
            val repository = RssRepository(database.rssDao(), rssFeedService)
            val settingsRepository = SettingsRepository(context)
            
            val homeViewModel: HomeViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                            @Suppress("UNCHECKED_CAST")
                            return HomeViewModel(repository, settingsRepository) as T
                        }
                        throw IllegalArgumentException("Unknown ViewModel class")
                    }
                }
            )

            val currentTheme by homeViewModel.currentTheme.collectAsState()
            val darkTheme = when (currentTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            HyperionRSSReaderTheme(darkTheme = darkTheme) {
                HomeScreen(homeViewModel = homeViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(homeViewModel: HomeViewModel) {
    val savedFeeds by homeViewModel.savedFeeds.collectAsState()
    val folders by homeViewModel.folders.collectAsState()
    val currentFeedContent by homeViewModel.currentFeedContent.collectAsState()
    val foundFeed by homeViewModel.foundFeed.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val error by homeViewModel.error.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("Folders", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                
                // 全てのフィード（未分類）
                NavigationDrawerItem(
                    label = { Text("Uncategorized") },
                    selected = false,
                    onClick = { /* TODO: Filter by uncategorized */ },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // フォルダ一覧
                folders.forEach { folder ->
                    NavigationDrawerItem(
                        label = { Text(folder.name) },
                        selected = false,
                        onClick = { /* TODO: Filter by folder */ },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Settings (Theme)") },
                    selected = false,
                    onClick = { showThemeDialog = true },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                TextButton(
                    onClick = { showAddFolderDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Create New Folder")
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Hyperion RSS") },
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
                // Horizontal list of feeds (Quick Access)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    items(savedFeeds) { feed ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable { homeViewModel.selectFeed(feed.url) },
                        ) {
                            Text(
                                text = feed.title,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 2,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                HorizontalDivider()

                // Feed Content Area
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (currentFeedContent != null) {
                        FeedContent(
                            rssFeed = currentFeedContent!!,
                            onItemClick = { url ->
                                try {
                                    uriHandler.openUri(url)
                                } catch (e: Exception) {
                                    // Ignore or show error
                                }
                            }
                        )
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

    // エラーダイアログ
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

    // URL入力ダイアログ
    if (showAddDialog) {
        AddFeedDialog(
            onDismiss = { showAddDialog = false },
            onVerify = { url ->
                homeViewModel.verifyUrl(url)
                showAddDialog = false
            }
        )
    }
    
    // フィード確認ダイアログ
    if (foundFeed != null) {
        ConfirmFeedDialog(
            feed = foundFeed!!,
            folders = folders,
            onConfirm = { folderId ->
                homeViewModel.confirmAddFeed(folderId)
            },
            onCancel = {
                homeViewModel.cancelAddFeed()
            }
        )
    }
    
    // フォルダ追加ダイアログ
    if (showAddFolderDialog) {
        AddFolderDialog(
            onDismiss = { showAddFolderDialog = false },
            onAdd = { name ->
                homeViewModel.addFolder(name)
                showAddFolderDialog = false
            }
        )
    }
    
    // テーマ選択ダイアログ
    if (showThemeDialog) {
        ThemeSelectionDialog(
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { theme ->
                homeViewModel.setTheme(theme)
                showThemeDialog = false
            }
        )
    }
}

@Composable
fun ThemeSelectionDialog(onDismiss: () -> Unit, onThemeSelected: (AppTheme) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column {
                TextButton(
                    onClick = { onThemeSelected(AppTheme.LIGHT) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Light")
                }
                TextButton(
                    onClick = { onThemeSelected(AppTheme.DARK) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dark")
                }
                TextButton(
                    onClick = { onThemeSelected(AppTheme.SYSTEM) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("System Default")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
    folders: List<io.github.stupidgame.hyperionrssreader.data.local.FolderEntity>,
    onConfirm: (Int?) -> Unit,
    onCancel: () -> Unit
) {
    var selectedFolderId by remember { mutableStateOf<Int?>(null) }
    
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Subscribe to Feed?") },
        text = {
            Column {
                Text("Title: ${feed.channel.title}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text("URL: ${feed.url}", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(feed.channel.description, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                
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
fun FeedContent(rssFeed: RssFeed, onItemClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = rssFeed.channel.title, style = MaterialTheme.typography.headlineMedium)
        
        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(rssFeed.channel.items) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(item.pubDate) },
                    modifier = Modifier
                        .clickable { onItemClick(item.link) }
                        .padding(vertical = 4.dp)
                )
                HorizontalDivider()
            }
        }
    }
}