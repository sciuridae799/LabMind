package com.labmind.integration.external;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinioIntegrationIT extends AbstractExternalIntegrationIT {

    @Test
    void shouldPutGetAndRemoveObjectFromMinio() throws Exception {
        ExternalServiceIntegrationProperties.MinioProperties minio = properties.getMinio();
        String objectName = "integration/" + runId("minio") + ".txt";
        byte[] content = runId("minio-payload").getBytes(StandardCharsets.UTF_8);

        MinioClient client = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();

        if (!client.bucketExists(BucketExistsArgs.builder().bucket(minio.getBucket()).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(minio.getBucket()).build());
        }

        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(minio.getBucket())
                    .object(objectName)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType("text/plain")
                    .build());

            byte[] actualContent;
            try (var inputStream = client.getObject(GetObjectArgs.builder()
                    .bucket(minio.getBucket())
                    .object(objectName)
                    .build())) {
                actualContent = inputStream.readAllBytes();
            }

            assertThat(actualContent).isEqualTo(content);
        } finally {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(minio.getBucket())
                    .object(objectName)
                    .build());
        }
    }
}
