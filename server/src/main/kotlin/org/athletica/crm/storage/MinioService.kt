package org.athletica.crm.storage

import io.minio.BucketExistsArgs
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

class MinioService(
    private val client: MinioClient,
    private val bucket: String,
) {
    fun ensureBucketExists() {
        val exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
        }
    }

    suspend fun uploadObject(
        key: String,
        stream: InputStream,
        size: Long,
        contentType: String,
    ): Unit =
        withContext(Dispatchers.IO) {
            client.putObject(
                PutObjectArgs
                    .builder()
                    .bucket(bucket)
                    .`object`(key)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build(),
            )
        }

    suspend fun deleteObject(key: String): Unit =
        withContext(Dispatchers.IO) {
            client.removeObject(
                RemoveObjectArgs
                    .builder()
                    .bucket(bucket)
                    .`object`(key)
                    .build(),
            )
        }

    suspend fun presignedGetUrl(
        key: String,
        ttlSeconds: Int = 3600,
    ): String =
        withContext(Dispatchers.IO) {
            client.getPresignedObjectUrl(
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
