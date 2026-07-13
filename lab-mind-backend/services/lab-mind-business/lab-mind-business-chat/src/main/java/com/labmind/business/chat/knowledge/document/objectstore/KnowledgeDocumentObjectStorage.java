package com.labmind.business.chat.knowledge.document.objectstore;

import com.labmind.business.chat.knowledge.document.config.KnowledgeDocumentStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 知识文档对象存储适配器。
 *
 * <p>封装 MinIO 的原文上传、解析文本读取和对象删除，并在首次使用时校验存储配置完整性。</p>
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentObjectStorage {

    private final KnowledgeDocumentStorageProperties storageProperties;

    public String bucket() {
        validateConfiguration();
        return storageProperties.getBucket().strip();
    }

    public void put(String objectName, byte[] content, String contentType) {
        validateObjectName(objectName);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("object content must not be empty");
        }
        try {
            MinioClient client = buildClient();
            String bucket = bucket();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                throw new IllegalStateException("MinIO bucket was not found: " + bucket);
            }
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(normalizeContentType(contentType))
                    .build());
        } catch (Exception error) {
            throw new IllegalStateException("failed to put knowledge document object: " + objectName, error);
        }
    }

    public String getText(String bucket, String objectName) {
        return new String(getBytes(bucket, objectName), java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] getBytes(String bucket, String objectName) {
        validateBucket(bucket);
        validateObjectName(objectName);
        try {
            MinioClient client = buildClient();
            try (var inputStream = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket.strip())
                    .object(objectName)
                    .build())) {
                return inputStream.readAllBytes();
            }
        } catch (Exception error) {
            throw new IllegalStateException("failed to read knowledge document object: " + objectName, error);
        }
    }

    public void remove(String bucket, String objectName) {
        validateBucket(bucket);
        validateObjectName(objectName);
        try {
            buildClient().removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket.strip())
                    .object(objectName)
                    .build());
        } catch (Exception error) {
            throw new IllegalStateException("failed to remove knowledge document object: " + objectName, error);
        }
    }

    private MinioClient buildClient() {
        validateConfiguration();
        return MinioClient.builder()
                .endpoint(storageProperties.getEndpoint().strip())
                .credentials(storageProperties.getAccessKey().strip(), storageProperties.getSecretKey().strip())
                .build();
    }

    private void validateConfiguration() {
        validateConfiguredText(storageProperties.getEndpoint(), "LAB_MIND_MINIO_ENDPOINT");
        validateConfiguredText(storageProperties.getAccessKey(), "LAB_MIND_MINIO_ACCESS_KEY");
        validateConfiguredText(storageProperties.getSecretKey(), "LAB_MIND_MINIO_SECRET_KEY");
        validateConfiguredText(storageProperties.getBucket(), "LAB_MIND_MINIO_BUCKET");
    }

    private void validateConfiguredText(String value, String name) {
        if (!StringUtils.hasText(value) || value.contains("${")) {
            throw new IllegalStateException(name + " must be configured before using knowledge documents.");
        }
    }

    private void validateBucket(String bucket) {
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
    }

    private void validateObjectName(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            throw new IllegalArgumentException("objectName must not be blank");
        }
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.strip() : "application/octet-stream";
    }
}
