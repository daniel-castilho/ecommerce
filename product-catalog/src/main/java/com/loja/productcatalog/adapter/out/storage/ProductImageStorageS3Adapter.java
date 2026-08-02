package com.loja.productcatalog.adapter.out.storage;

import com.loja.productcatalog.domain.port.out.ProductImageStoragePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.net.URI;
import java.util.UUID;

/**
 * Output adapter: implements ProductImageStoragePort against S3-compatible storage
 * (LocalStack locally, real S3 in prod). Dumb I/O — validation of content type and
 * size lives in the application service, not here.
 */
@ApplicationScoped
public class ProductImageStorageS3Adapter implements ProductImageStoragePort {

    @Inject
    S3Config config;

    private volatile S3Client client;

    @Override
    public String upload(byte[] content, String contentType, String suggestedKeyPrefix) {
        String objectKey = suggestedKeyPrefix + "/" + UUID.randomUUID() + "." + extensionFor(contentType);
        client().putObject(PutObjectRequest.builder()
                        .bucket(config.bucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
        return objectKey;
    }

    @Override
    public void delete(String objectKey) {
        client().deleteObject(DeleteObjectRequest.builder()
                .bucket(config.bucket())
                .key(objectKey)
                .build());
    }

    @Override
    public String publicUrlFor(String objectKey) {
        return config.publicBaseUrl() + "/" + objectKey;
    }

    S3Client client() {
        S3Client current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    client = buildClient();
                }
                return client;
            }
        }
        return current;
    }

    private S3Client buildClient() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKey(), config.secretKey())));
        if (config.endpointOverride() != null && !config.endpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(config.endpointOverride()));
            builder.forcePathStyle(true);
        }
        return builder.build();
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported image content type: " + contentType);
        };
    }
}
