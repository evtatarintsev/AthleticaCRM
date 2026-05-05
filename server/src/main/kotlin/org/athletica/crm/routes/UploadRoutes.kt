package org.athletica.crm.routes

import io.ktor.http.CacheControl
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.cacheControl
import io.ktor.utils.io.toByteArray
import org.athletica.crm.api.schemas.upload.UploadInfoRequest
import org.athletica.crm.api.schemas.upload.UploadResponse
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService
import org.athletica.crm.usecases.upload.uploadFile
import org.athletica.crm.usecases.upload.uploadInfo
import kotlin.time.Duration.Companion.days

/**
 * Регистрирует маршрут POST /upload для загрузки файлов.
 * Принимает multipart/form-data с полем "file".
 * Возвращает UploadResponse с id загрузки и presigned URL.
 * Требует контекстных параметров [Database], [MinioService] и [AuditLog].
 */
context(db: Database, minioService: MinioService, audit: AuditLog)
fun RouteWithContext.uploadRoutes() {
    get<UploadInfoRequest, UploadResponse>("/upload/info") { request, call ->
        val cacheTTL = 7.days
        call.response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = cacheTTL.inWholeSeconds.toInt()))
        uploadInfo(request.id.value, cacheTTL).bind()
    }

    post<Unit, UploadResponse>("/upload") { _, call ->
        var fileBytes: ByteArray? = null
        var originalName = "file"
        var contentType = "application/octet-stream"

        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FileItem) {
                fileBytes = part.provider().toByteArray()
                originalName = part.originalFileName?.takeIf { it.isNotBlank() } ?: "file"
                contentType = part.contentType?.toString() ?: "application/octet-stream"
            }
            part.dispose()
        }

        val bytes = fileBytes ?: raise(CommonDomainError("NO_FILE", Messages.FileNotInRequest.localize()))
        uploadFile(bytes, originalName, contentType).bind()
    }
}
