package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import org.athletica.crm.core.entityids.UploadId

class DocumentsApiClient(private val http: HttpClient) {
    /** Возвращает информацию о загрузке по [id], включая presigned URL аватара. */
    suspend fun info(id: UploadId): Either<ApiClientError, UploadResponse> =
        requestCatching {
            http.get("/api/upload/info") {
                url { parameters.append("id", id.toString()) }
            }
        }

    /** Загружает файл на сервер. Возвращает [UploadResponse] с id и presigned URL. */
    suspend fun upload(bytes: ByteArray, filename: String, contentType: String): Either<ApiClientError, UploadResponse> =
        requestCatching {
            http.post("/api/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "file",
                                bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, contentType)
                                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                                },
                            )
                        },
                    ),
                )
            }
        }
}

// Re-export for convenience
typealias UploadResponse = org.athletica.crm.api.schemas.upload.UploadResponse
