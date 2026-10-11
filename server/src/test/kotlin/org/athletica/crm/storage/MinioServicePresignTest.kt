package org.athletica.crm.storage

import io.minio.MinioClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Проверяет, что presigned URL подписывается локально, без обращения к публичному endpoint.
 * Иначе сервер должен доверять TLS-сертификату публичного адреса (в dev — самоподписанному).
 */
class MinioServicePresignTest {
    /** Клиент к недоступному хосту: любой сетевой запрос через него упадёт. */
    private fun unreachableClient(): MinioClient =
        MinioClient
            .builder()
            .endpoint("https://minio.unreachable.invalid")
            .credentials("access", "secret")
            .region("us-east-1")
            .build()

    @Test
    fun `presigned URL с заданным регионом строится без сетевого запроса`() =
        runTest {
            val service =
                MinioService(
                    internalClient = unreachableClient(),
                    publicClient = unreachableClient(),
                    bucket = "bucket",
                )

            val url = service.presignedGetUrl("uploads/file.png")

            assertTrue(url.startsWith("https://minio.unreachable.invalid/bucket/uploads/file.png?"), url)
            assertTrue("X-Amz-Signature=" in url, url)
        }
}
