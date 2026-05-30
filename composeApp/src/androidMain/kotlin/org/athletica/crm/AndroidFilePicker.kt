package org.athletica.crm

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

/**
 * Синглтон-мост между платформенными suspend-функциями [pickImageFile]/[pickAnyFile]
 * и [androidx.activity.result.ActivityResultLauncher], регистрируемым в [MainActivity].
 *
 * [MainActivity] присваивает лямбды при создании и обнуляет при уничтожении.
 * Лямбда принимает [CompletableDeferred], запускает выбор файла и завершает деferred
 * в колбэке результата.
 */
object AndroidFilePicker {
    /** Лончер выбора изображения. Устанавливается из [MainActivity]. */
    var imageLauncher: ((CompletableDeferred<Uri?>) -> Unit)? = null

    /** Лончер выбора произвольного файла. Устанавливается из [MainActivity]. */
    var anyFileLauncher: ((CompletableDeferred<Uri?>) -> Unit)? = null
}
