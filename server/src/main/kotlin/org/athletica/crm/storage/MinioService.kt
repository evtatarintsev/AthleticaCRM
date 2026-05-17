package org.athletica.crm.storage

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.http.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Сервис для работы с объектным хранилищем MinIO.
 *
 * Использует два клиента: [internalClient] для загрузки файлов (внутренняя сеть Docker),
 * [publicClient] для генерации presigned URL (подпись считается с публичным хостом,
 * поэтому браузер сможет скачать файл напрямую).
 */
class MinioService(
    private val internalClient: MinioClient,
    private val publicClient: MinioClient,
    private val bucket: String,
) {
    /** Создаёт бакет, если он ещё не существует. */
    fun ensureBucketExists() {
        val exists = internalClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
        if (!exists) {
            internalClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
        }
    }

    /** Загружает объект в хранилище по [key]. */
    suspend fun uploadObject(
        key: String,
        stream: InputStream,
        size: Long,
        contentType: String,
    ): Unit =
        withContext(Dispatchers.IO) {
            internalClient.putObject(
                PutObjectArgs
                    .builder()
                    .bucket(bucket)
                    .`object`(key)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build(),
            )
        }

    /** Скачивает объект из хранилища по [key] и возвращает его содержимое в виде массива байт. */
    suspend fun downloadObject(key: String): ByteArray =
        withContext(Dispatchers.IO) {
            internalClient
                .getObject(
                    GetObjectArgs
                        .builder()
                        .bucket(bucket)
                        .`object`(key)
                        .build(),
                ).use { it.readBytes() }
        }

    /** Удаляет объект из хранилища по [key]. */
    suspend fun deleteObject(key: String): Unit =
        withContext(Dispatchers.IO) {
            internalClient.removeObject(
                RemoveObjectArgs
                    .builder()
                    .bucket(bucket)
                    .`object`(key)
                    .build(),
            )
        }

    /**
     * Возвращает presigned GET-ссылку на объект [key] с TTL [ttlSeconds].
     * URL подписывается публичным клиентом, поэтому хост в ссылке — публичный адрес MinIO.
     */
    suspend fun presignedGetUrl(
        key: String,
        ttlSeconds: Int = 3600,
    ): String =
        withContext(Dispatchers.IO) {
            publicClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs
                    .builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .`object`(key)
                    .expiry(ttlSeconds, TimeUnit.SECONDS)
                    .build(),
            )
        }
}
