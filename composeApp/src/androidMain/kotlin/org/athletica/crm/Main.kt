package org.athletica.crm

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

/**
 * Application-класс приложения.
 * Инициализирует [AndroidContextHolder] при старте процесса,
 * чтобы platform-функции (openUrl, downloadFile и т.д.) могли использовать контекст.
 */
class AthleticaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContextHolder.applicationContext = this
    }
}

/**
 * Единственная Activity приложения.
 * Регистрирует [ActivityResultLauncher]-ы для выбора файлов и передаёт их
 * в [AndroidFilePicker] для использования из suspend-функций [pickImageFile]/[pickAnyFile].
 */
class MainActivity : ComponentActivity() {
    /** Deferred для текущего запроса выбора изображения. */
    private var pendingImage: CompletableDeferred<Uri?>? = null

    /** Deferred для текущего запроса выбора произвольного файла. */
    private var pendingAnyFile: CompletableDeferred<Uri?>? = null

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            pendingImage?.complete(uri)
            pendingImage = null
        }

    private val anyFilePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            pendingAnyFile?.complete(uri)
            pendingAnyFile = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidFilePicker.imageLauncher = { deferred ->
            pendingImage = deferred
            imagePickerLauncher.launch("image/*")
        }
        AndroidFilePicker.anyFileLauncher = { deferred ->
            pendingAnyFile = deferred
            anyFilePickerLauncher.launch("*/*")
        }

        val tokenStorage = AndroidAccessTokenStorage(applicationContext)

        setContent {
            App(tokenStorage, apiClient(tokenStorage))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingImage?.cancel()
        pendingAnyFile?.cancel()
        AndroidFilePicker.imageLauncher = null
        AndroidFilePicker.anyFileLauncher = null
    }
}
