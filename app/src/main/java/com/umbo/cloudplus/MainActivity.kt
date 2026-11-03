package com.umbo.cloudplus

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.umbo.cloudplus.data.FileRepository
import com.umbo.cloudplus.data.SettingsRepository
import com.umbo.cloudplus.data.TelegramUploader
import com.umbo.cloudplus.ui.components.BottomNavigationBar
import com.umbo.cloudplus.ui.components.TopSearchBar
import com.umbo.cloudplus.ui.components.UploadFloatingActionButton
import com.umbo.cloudplus.ui.components.UploadProgressBar
import com.umbo.cloudplus.ui.screens.AppLockScreen
import com.umbo.cloudplus.ui.screens.FilesTabContent
import com.umbo.cloudplus.ui.screens.GalleryTabContent
import com.umbo.cloudplus.ui.screens.MenuTabContent
import com.umbo.cloudplus.ui.theme.cloudPlusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            cloudPlusTheme {
                MainScreen()
            }
        }
    }
}

enum class NavigationTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    MENU("Menu", Icons.Filled.Menu, Icons.Outlined.Menu),
    FILES("Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    GALLERY("Gallery", Icons.Filled.Collections, Icons.Outlined.Collections),
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settingsRepository = remember { SettingsRepository(context) }
    val fileRepository = remember { FileRepository(context) }
    val telegramUploader = remember { TelegramUploader(context) }

    var currentTab by remember { mutableStateOf(NavigationTab.FILES) }
    var searchQuery by remember { mutableStateOf("") }

    var settings by remember { mutableStateOf(settingsRepository.getSettings()) }
    var uploadedFiles by remember { mutableStateOf(fileRepository.getUploadedFiles()) }

    var isAppLocked by remember { mutableStateOf(settings.isAppLockEnabled) }

    var isUploading by remember { mutableStateOf(false) }
    var currentUploadingFileName by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableFloatStateOf(0f) }

    // Dynamic FLAG_SECURE window flag management
    LaunchedEffect(settings.isScreenSecurityEnabled) {
        activity?.let { act ->
            if (settings.isScreenSecurityEnabled) {
                act.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    // Auto-lock when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (settings.isAppLockEnabled) {
                    isAppLocked = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun refreshFiles() {
        uploadedFiles = fileRepository.getUploadedFiles()
    }

    fun handleUploadUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!settings.isConfigured) {
            scope.launch {
                snackbarHostState.showSnackbar("الرجاء إعداد بيانات التلجرام (Bot Token و Chat ID) أولاً من قائمة Menu.")
            }
            currentTab = NavigationTab.MENU
            return
        }

        scope.launch {
            isUploading = true
            var successCount = 0
            for (uri in uris) {
                val metadata = getFileMetadata(context, uri)
                currentUploadingFileName = metadata.name
                uploadProgress = 0f

                val result = telegramUploader.uploadFile(
                    uri = uri,
                    fileName = metadata.name,
                    mimeType = metadata.mimeType,
                    fileSize = metadata.size,
                    settings = settings,
                    onProgress = { progress ->
                        uploadProgress = progress
                    }
                )

                result.onSuccess { file ->
                    fileRepository.saveFile(file)
                    refreshFiles()
                    successCount++
                }.onFailure { error ->
                    Toast.makeText(context, "خطأ في الرفع: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
            isUploading = false
            if (successCount > 0) {
                snackbarHostState.showSnackbar("تم رفع $successCount ملف بنجاح إلى التلجرام!")
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        handleUploadUris(uris)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        handleUploadUris(uris)
    }

    if (isAppLocked && settings.isAppLockEnabled) {
        AppLockScreen(
            onVerifyPasscode = { pin ->
                settingsRepository.verifyPasscode(pin)
            },
            onSuccess = {
                isAppLocked = false
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    TopSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "\"Search \"Mountains"
                    )
                }
            },
            bottomBar = {
                BottomNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            },
            floatingActionButton = {
                UploadFloatingActionButton(
                    onClick = {
                        filePickerLauncher.launch("*/*")
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.Start
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isUploading) {
                        UploadProgressBar(
                            fileName = currentUploadingFileName,
                            progress = uploadProgress
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                            NavigationTab.MENU -> {
                                MenuTabContent(
                                    settings = settings,
                                    onSaveSettings = { newSettings ->
                                        settingsRepository.saveSettings(newSettings)
                                        settings = newSettings
                                        scope.launch {
                                            snackbarHostState.showSnackbar("تم حفظ الإعدادات بنجاح!")
                                        }
                                    },
                                    onTestConnection = { settingsToTest, onResult ->
                                        scope.launch {
                                            val result = telegramUploader.testConnection(settingsToTest)
                                            onResult(result)
                                        }
                                    },
                                    onSetPasscode = { pin ->
                                        val newSettings = settingsRepository.setPasscode(pin)
                                        settings = newSettings
                                        scope.launch {
                                            snackbarHostState.showSnackbar("تم تعيين رمز PIN وقفل التطبيق بنجاح!")
                                        }
                                    },
                                    onRemovePasscode = {
                                        val newSettings = settingsRepository.removePasscode()
                                        settings = newSettings
                                        isAppLocked = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar("تم إلغاء قفل التطبيق.")
                                        }
                                    },
                                    totalFilesCount = uploadedFiles.size
                                )
                            }
                            NavigationTab.FILES -> {
                                FilesTabContent(
                                    searchQuery = searchQuery,
                                    files = uploadedFiles,
                                    onDeleteFile = { id ->
                                        fileRepository.deleteFile(id)
                                        refreshFiles()
                                    },
                                    onUploadClick = {
                                        filePickerLauncher.launch("*/*")
                                    }
                                )
                            }
                            NavigationTab.GALLERY -> {
                                GalleryTabContent(
                                    onPickPhotos = {
                                        photoPickerLauncher.launch("image/*")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class FileMetadata(val name: String, val mimeType: String, val size: Long)

private fun getFileMetadata(context: Context, uri: Uri): FileMetadata {
    var name = "file_${System.currentTimeMillis()}"
    var size = 0L
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
        }
    }
    return FileMetadata(name, mimeType, size)
}

@Preview(showBackground = true, backgroundColor = 0xFF16191B)
@Composable
fun MainScreenPreview() {
    cloudPlusTheme {
        MainScreen()
    }
}
