package org.athletica.crm

import io.minio.MinioClient
import org.athletica.crm.storage.MinioService
import org.testcontainers.containers.MinIOContainer

/**
 * Singleton-контейнер MinIO, общий для всех интеграционных тестов.
 * Поднимается один раз на весь тест-ран.
 */
object TestMinio {
    private const val BUCKET = "test-bucket"

    val container: MinIOContainer =
        MinIOContainer("minio/minio:latest").also { it.start() }

    val minioService: MinioService =
        MinioService(
            client =
                MinioClient
                    .builder()
                    .endpoint(container.s3URL)
                    .credentials(container.userName, container.password)
                    .build(),
            bucket = BUCKET,
        ).also { it.ensureBucketExists() }
}
