package org.athletica.crm.utils

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Скачивает файл на десктопе, открывая диалог Save As для выбора места сохранения.
 */
actual fun downloadFile(filename: String, data: ByteArray) {
    val fileChooser = JFileChooser()
    fileChooser.dialogTitle = "Сохранить файл"
    fileChooser.selectedFile = File(filename)

    val extension = filename.substringAfterLast(".", "")
    if (extension.isNotEmpty()) {
        fileChooser.fileFilter = FileNameExtensionFilter("${extension.uppercase()} файлы", extension)
    }

    val result = fileChooser.showSaveDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        fileChooser.selectedFile.writeBytes(data)
    }
}
