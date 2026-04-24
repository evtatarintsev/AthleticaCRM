package org.athletica.crm.routes

import arrow.core.raise.Raise
import io.ktor.http.CacheControl
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.cacheControl
import io.ktor.utils.io.toByteArray
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService
import org.athletica.crm.usecases.upload.uploadFile
import org.athletica.crm.usecases.upload.uploadInfo
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/**
 * Регистрирует маршрут POST /upload для загрузки файлов.
 * Принимает multipart/form-data с полем "file".
 * Возвращает UploadResponse с id загрузки и presigned URL.
 * Требует контекстных параметров [Database], [MinioService] и [AuditLog].
 */
context(db: Database, minioService: MinioService, audit: AuditLog)
fun RouteWithContext.uploadRoutes() {
    get("/upload/info") {
        val cacheTTL = 7.days
        call.response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = cacheTTL.inWholeSeconds.toInt()))
        call.eitherToResponse {
            val idParam =
                call.request.queryParameters["id"]
                    ?: raise(CommonDomainError("MISSING_PARAMETER", Messages.MissingParameterId.localize()))
            val id =
                runCatching { Uuid.parse(idParam) }.getOrElse {
                    raise(CommonDomainError("INVALID_PARAMETER", Messages.InvalidParameterId.localize()))
                }
            uploadInfo(id, cacheTTL).bind()
        }
    }

    post("/upload") {
        call.eitherToResponse {
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
}
