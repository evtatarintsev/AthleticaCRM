package org.athletica.crm.routes

import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.utils.io.toByteArray
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.storage.MinioService
import org.athletica.crm.usecases.upload.getUploadInfo
import org.athletica.crm.usecases.upload.uploadFile
import kotlin.uuid.Uuid

/**
 * Регистрирует маршрут POST /upload для загрузки файлов.
 * Принимает multipart/form-data с полем "file".
 * Возвращает UploadResponse с id загрузки и presigned URL.
 * Требует контекстных параметров [Database] и [MinioService].
 */
context(db: Database, minioService: MinioService)
fun Route.uploadRoutes() {
    getWithContext("/upload/info") {
        call.eitherToResponse {
            val idParam = call.request.queryParameters["id"]
                ?: raise(CommonDomainError("MISSING_PARAMETER", "Параметр id обязателен"))
            val id = runCatching { Uuid.parse(idParam) }.getOrElse {
                raise(CommonDomainError("INVALID_PARAMETER", "Параметр id должен быть корректным UUID"))
            }
            getUploadInfo(id).bind()
        }
    }

    postWithContext("/upload") {
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

            val bytes = fileBytes ?: raise(CommonDomainError("NO_FILE", "Файл не найден в запросе"))
            uploadFile(bytes, originalName, contentType).bind()
        }
    }
}
