package org.athletica.crm

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.FileReader
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop

actual suspend fun pickImageFile(): Triple<ByteArray, String, String>? =
    suspendCancellableCoroutine { continuation ->
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "image/jpeg,image/png,image/webp"
        input.style.display = "none"
        document.body?.appendChild(input)

        fun cleanup() = runCatching { document.body?.removeChild(input) }

        input.onchange = { _: Event ->
            val file = input.files?.item(0)
            if (file == null) {
                cleanup()
                continuation.resume(null)
            } else {
                val reader = FileReader()
                reader.onload = { _: Event ->
                    @OptIn(ExperimentalWasmJsInterop::class)
                    val dataUrl = reader.result?.toString()
                    if (dataUrl == null) {
                        cleanup()
                        continuation.resume(null)
                    } else {
                        @OptIn(ExperimentalEncodingApi::class)
                        val bytes = Base64.decode(dataUrl.substringAfter(","))
                        val contentType =
                            when {
                                file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                                file.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                                else -> "image/jpeg"
                            }
                        cleanup()
                        continuation.resume(Triple(bytes, file.name, contentType))
                    }
                }
                reader.readAsDataURL(file)
            }
        }

        continuation.invokeOnCancellation { cleanup() }

        input.click()
    }

actual suspend fun pickAnyFile(): Triple<ByteArray, String, String>? =
    suspendCancellableCoroutine { continuation ->
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "*/*"
        input.style.display = "none"
        document.body?.appendChild(input)

        fun cleanup() = runCatching { document.body?.removeChild(input) }

        input.onchange = { _: Event ->
            val file = input.files?.item(0)
            if (file == null) {
                cleanup()
                continuation.resume(null)
            } else {
                val reader = FileReader()
                reader.onload = { _: Event ->
                    @OptIn(ExperimentalWasmJsInterop::class)
                    val dataUrl = reader.result?.toString()
                    if (dataUrl == null) {
                        cleanup()
                        continuation.resume(null)
                    } else {
                        @OptIn(ExperimentalEncodingApi::class)
                        val bytes = Base64.decode(dataUrl.substringAfter(","))
                        val contentType =
                            when {
                                file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                                file.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                                file.name.endsWith(".jpg", ignoreCase = true) ||
                                    file.name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                                file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                                file.name.endsWith(".docx", ignoreCase = true) ->
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                file.name.endsWith(".doc", ignoreCase = true) -> "application/msword"
                                else -> "application/octet-stream"
                            }
                        cleanup()
                        continuation.resume(Triple(bytes, file.name, contentType))
                    }
                }
                reader.readAsDataURL(file)
            }
        }

        continuation.invokeOnCancellation { cleanup() }

        input.click()
    }
