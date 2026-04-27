package com.superagent.business.chat.knowledge.objectstore;

import com.superagent.business.chat.knowledge.config.KnowledgeDocumentStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        validateBucket(bucket);
        validateObjectName(objectName);
        try {
            MinioClient client = buildClient();
            try (var inputStream = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket.strip())
                    .object(objectName)
                    .build())) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
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
        validateConfiguredText(storageProperties.getEndpoint(), "SUPER_AGENT_MINIO_ENDPOINT");
        validateConfiguredText(storageProperties.getAccessKey(), "SUPER_AGENT_MINIO_ACCESS_KEY");
        validateConfiguredText(storageProperties.getSecretKey(), "SUPER_AGENT_MINIO_SECRET_KEY");
        validateConfiguredText(storageProperties.getBucket(), "SUPER_AGENT_MINIO_BUCKET");
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
