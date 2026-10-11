package org.athletica.crm

import io.minio.MinioClient
import org.athletica.crm.storage.MinioService
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 * Singleton-контейнер S3-совместимого хранилища (RustFS, как в docker-compose),
 * общий для всех интеграционных тестов. Поднимается один раз на весь тест-ран.
 */
object TestMinio {
    private const val BUCKET = "test-bucket"
    private const val ACCESS_KEY = "test_access_key"
    private const val SECRET_KEY = "test_secret_key"
    private const val S3_PORT = 9000

    /** Контейнер RustFS с S3 API на порту [S3_PORT]. */
    val container: GenericContainer<*> =
        GenericContainer(DockerImageName.parse("rustfs/rustfs:1.0.0"))
            .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
            .withExposedPorts(S3_PORT)
            .waitingFor(Wait.forHttp("/health").forPort(S3_PORT).forStatusCode(200))
            .also { it.start() }

    /** [MinioService] поверх контейнера; бакет [BUCKET] создаётся при первом обращении. */
    val minioService: MinioService by lazy {
        val client =
            MinioClient
                .builder()
                .endpoint("http://${container.host}:${container.getMappedPort(S3_PORT)}")
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build()
        MinioService(
            internalClient = client,
            publicClient = client,
            bucket = BUCKET,
        ).also { it.ensureBucketExists() }
    }
}
