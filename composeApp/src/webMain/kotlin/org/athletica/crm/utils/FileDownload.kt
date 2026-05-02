package org.athletica.crm.utils

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Скачивает файл в браузере, используя data URI и создание временного скачиваемого линка.
 */
@OptIn(ExperimentalEncodingApi::class)
actual fun downloadFile(filename: String, data: ByteArray) {
    val base64 = Base64.encode(data)
    val dataUri = "data:text/csv;charset=UTF-8;base64,$base64"

    val link = document.createElement("a") as HTMLAnchorElement
    link.href = dataUri
    link.download = filename
    link.style.display = "none"

    document.body?.appendChild(link)
    link.click()
    document.body?.removeChild(link)
}
