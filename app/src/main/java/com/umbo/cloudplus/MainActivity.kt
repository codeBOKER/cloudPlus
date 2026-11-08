package com.umbo.cloudplus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
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
import androidx.core.content.FileProvider
import com.umbo.cloudplus.data.DownloadedFile
import com.umbo.cloudplus.data.DownloadedFileRepository
import com.umbo.cloudplus.data.FileRepository
import com.umbo.cloudplus.data.OnboardingRepository
import com.umbo.cloudplus.data.SettingsRepository
import com.umbo.cloudplus.data.TelegramUploader
import com.umbo.cloudplus.data.UploadedFile
import com.umbo.cloudplus.ui.components.BottomNavigationBar
import com.umbo.cloudplus.ui.components.TopSearchBar
import com.umbo.cloudplus.ui.components.UploadFloatingActionButton
import com.umbo.cloudplus.ui.components.UploadProgressBar
import com.umbo.cloudplus.ui.screens.DownloadsTabContent
import com.umbo.cloudplus.ui.screens.FilesTabContent
import com.umbo.cloudplus.ui.screens.GalleryTabContent
import com.umbo.cloudplus.ui.screens.MenuTabContent
import com.umbo.cloudplus.ui.screens.OnboardingScreen
import com.umbo.cloudplus.ui.screens.SplashScreen
import com.umbo.cloudplus.ui.theme.cloudPlusTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            cloudPlusTheme {
                AppRootScreenHost()
            }
        }
    }
}

enum class AppRootState { SPLASH, ONBOARDING, MAIN }

enum class NavigationTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    MENU("Menu", Icons.Filled.Menu, Icons.Outlined.Menu),
    FILES("Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    DOWNLOADS("Downloads", Icons.Filled.Download, Icons.Outlined.Download),
    GALLERY("Gallery", Icons.Filled.Collections, Icons.Outlined.Collections),
}

@Composable
fun AppRootScreenHost() {
    val context = LocalContext.current
    val onboardingRepository = remember { OnboardingRepository(context) }
    var rootState by remember { mutableStateOf(AppRootState.SPLASH) }

    AnimatedContent(
        targetState = rootState,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "AppRootTransition"
    ) { state ->
        when (state) {
            AppRootState.SPLASH -> {
                SplashScreen(
                    onSplashFinished = {
                        if (onboardingRepository.isFirstLaunch()) {
                            rootState = AppRootState.ONBOARDING
                        } else {
                            rootState = AppRootState.MAIN
                        }
                    }
                )
            }
            AppRootState.ONBOARDING -> {
                OnboardingScreen(
                    onFinishOnboarding = {
                        onboardingRepository.setFirstLaunchCompleted()
                        rootState = AppRootState.MAIN
                    }
                )
            }
            AppRootState.MAIN -> {
                MainScreen(
                    onReopenOnboarding = {
                        rootState = AppRootState.ONBOARDING
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    onReopenOnboarding: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settingsRepository = remember { SettingsRepository(context) }
    val fileRepository = remember { FileRepository(context) }
    val downloadedFileRepository = remember { DownloadedFileRepository(context) }
    val telegramUploader = remember { TelegramUploader(context) }

    var currentTab by remember { mutableStateOf(NavigationTab.FILES) }
    var searchQuery by remember { mutableStateOf("") }

    var settings by remember { mutableStateOf(settingsRepository.getSettings()) }
    var uploadedFiles by remember { mutableStateOf(fileRepository.getUploadedFiles()) }
    var downloadedFiles by remember { mutableStateOf(downloadedFileRepository.getDownloadedFiles()) }

    var isUploading by remember { mutableStateOf(false) }
    var currentUploadingFileName by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableFloatStateOf(0f) }

    fun refreshFiles() {
        uploadedFiles = fileRepository.getUploadedFiles()
    }

    fun refreshDownloadedFiles() {
        downloadedFiles = downloadedFileRepository.getDownloadedFiles()
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
                    Toast.makeText(context, "خطأ في الرفع والتشفير: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
            isUploading = false
            if (successCount > 0) {
                snackbarHostState.showSnackbar("تم تشفير ورفع $successCount ملف بنجاح إلى التلجرام!")
            }
        }
    }

    fun handleDownloadFile(file: UploadedFile) {
        if (!settings.isConfigured) {
            scope.launch {
                snackbarHostState.showSnackbar("الرجاء إعداد بيانات التلجرام (Bot Token) أولاً من قائمة Menu.")
            }
            currentTab = NavigationTab.MENU
            return
        }

        scope.launch {
            isUploading = true
            currentUploadingFileName = "تنزيل وفك تشفير: ${file.name}"
            uploadProgress = 0f

            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads").apply { mkdirs() }
            var destFile = File(downloadsDir, file.name)
            if (destFile.exists()) {
                destFile = File(downloadsDir, "${System.currentTimeMillis()}_${file.name}")
            }

            val result = telegramUploader.downloadAndDecryptFile(
                uploadedFile = file,
                settings = settings,
                destinationFile = destFile,
                onProgress = { progress ->
                    uploadProgress = progress
                }
            )

            isUploading = false

            result.onSuccess { downloadedFile ->
                val savedRecord = DownloadedFile(
                    name = file.name,
                    mimeType = file.mimeType,
                    size = downloadedFile.length(),
                    localPath = downloadedFile.absolutePath,
                    telegramFileId = file.telegramFileId
                )
                downloadedFileRepository.saveDownloadedFile(savedRecord)
                refreshDownloadedFiles()

                scope.launch {
                    snackbarHostState.showSnackbar("تم تنزيل وفك تشفير الملف وحفظه بنجاح!")
                }

                try {
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        downloadedFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, file.mimeType.ifBlank { "*/*" })
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "تم حفظ الملف في: ${downloadedFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }.onFailure { error ->
                Toast.makeText(context, "خطأ في تنزيل وفك تشفير الملف: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun handleOpenDownloadedFile(file: DownloadedFile) {
        try {
            val physicalFile = File(file.localPath)
            if (!physicalFile.exists()) {
                Toast.makeText(context, "الملف غير موجود على الجهاز", Toast.LENGTH_SHORT).show()
                return
            }
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                physicalFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, file.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "لم يتم العثور على تطبيق لفتح هذا الملف", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleShareDownloadedFile(file: DownloadedFile) {
        try {
            val physicalFile = File(file.localPath)
            if (!physicalFile.exists()) {
                Toast.makeText(context, "الملف غير موجود على الجهاز", Toast.LENGTH_SHORT).show()
                return
            }
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                physicalFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = file.mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الملف"))
        } catch (e: Exception) {
            Toast.makeText(context, "فشل مشاركة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
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
                                onReopenOnboarding = onReopenOnboarding,
                                totalFilesCount = uploadedFiles.size
                            )
                        }
                        NavigationTab.FILES -> {
                            FilesTabContent(
                                searchQuery = searchQuery,
                                files = uploadedFiles,
                                onDownloadFile = { file ->
                                    handleDownloadFile(file)
                                },
                                onDeleteFile = { id ->
                                    fileRepository.deleteFile(id)
                                    refreshFiles()
                                },
                                onUploadClick = {
                                    filePickerLauncher.launch("*/*")
                                }
                            )
                        }
                        NavigationTab.DOWNLOADS -> {
                            DownloadsTabContent(
                                searchQuery = searchQuery,
                                downloadedFiles = downloadedFiles,
                                onOpenFile = { file ->
                                    handleOpenDownloadedFile(file)
                                },
                                onShareFile = { file ->
                                    handleShareDownloadedFile(file)
                                },
                                onDeleteFile = { id ->
                                    downloadedFileRepository.deleteDownloadedFile(id)
                                    refreshDownloadedFiles()
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
