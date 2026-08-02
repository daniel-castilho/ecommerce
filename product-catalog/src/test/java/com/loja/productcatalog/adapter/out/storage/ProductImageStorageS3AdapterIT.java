package com.loja.productcatalog.adapter.out.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImageStorageS3AdapterIT {

    private static final String BUCKET = "product-images";

    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8.1"))
            .withServices(LocalStackContainer.Service.S3)
            .withStartupTimeout(Duration.ofMinutes(2));

    static S3Client adminS3;

    ProductImageStorageS3Adapter adapter;

    @BeforeAll
    static void startContainer() throws IOException {
        localstack.start();
        adminS3 = S3Client.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build();
        adminS3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        adminS3.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(BUCKET)
                .policy("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\"," +
                        "\"Action\":[\"s3:GetObject\"],\"Resource\":\"arn:aws:s3:::" + BUCKET + "/*\"}]}")
                .build());
    }

    @AfterAll
    static void stopContainer() {
        if (adminS3 != null) adminS3.close();
        if (localstack != null) localstack.stop();
    }

    @BeforeEach
    void setUp() {
        adapter = new ProductImageStorageS3Adapter();
        adapter.config = config(localstack.getEndpoint().toString(), localstack.getRegion(),
                localstack.getAccessKey(), localstack.getSecretKey());
    }

    @Test
    void uploadsToExpectedKeyAndServesPublicly() throws Exception {
        byte[] content = "fake-jpeg-bytes".getBytes();

        String key = adapter.upload(content, "image/jpeg", "products/SKU-1");

        assertThat(key).matches("products/SKU-1/[0-9a-f-]{36}\\.jpg");
        assertThat(readObject(key)).isEqualTo(content);

        HttpRequest request = HttpRequest.newBuilder(URI.create(adapter.publicUrlFor(key))).GET().build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
    }

    @Test
    void deletesObject() {
        String key = adapter.upload("data".getBytes(), "image/png", "products/SKU-2");

        adapter.delete(key);

        assertThatThrownBy(() -> readObject(key)).isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void mapsEachAllowedContentTypeToItsExtension() {
        assertThat(adapter.upload(new byte[0], "image/jpeg", "products/SKU-3")).endsWith(".jpg");
        assertThat(adapter.upload(new byte[0], "image/png", "products/SKU-3")).endsWith(".png");
        assertThat(adapter.upload(new byte[0], "image/webp", "products/SKU-3")).endsWith(".webp");
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> adapter.upload(new byte[0], "image/gif", "products/SKU-3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported image content type");
    }

    @Test
    void appliesEndpointOverrideOnlyWhenConfigured() {
        ProductImageStorageS3Adapter withOverride = newAdapter(
                new S3Config(localstack.getEndpoint().toString(), BUCKET,
                        localstack.getEndpoint() + "/" + BUCKET, localstack.getRegion(),
                        localstack.getAccessKey(), localstack.getSecretKey()));
        assertThat(withOverride.client().serviceClientConfiguration().endpointOverride())
                .isEqualTo(Optional.of(localstack.getEndpoint()));

        ProductImageStorageS3Adapter withoutOverride = newAdapter(
                new S3Config("", BUCKET, localstack.getEndpoint() + "/" + BUCKET,
                        localstack.getRegion(), localstack.getAccessKey(), localstack.getSecretKey()));
        assertThat(withoutOverride.client().serviceClientConfiguration().endpointOverride()).isEmpty();
    }

    private S3Config config(String endpoint, String region, String accessKey, String secretKey) {
        return new S3Config(endpoint, BUCKET, endpoint + "/" + BUCKET, region, accessKey, secretKey);
    }

    private ProductImageStorageS3Adapter newAdapter(S3Config config) {
        ProductImageStorageS3Adapter a = new ProductImageStorageS3Adapter();
        a.config = config;
        return a;
    }

    private byte[] readObject(String key) {
        ResponseInputStream<GetObjectResponse> object = adminS3.getObject(
                GetObjectRequest.builder().bucket(BUCKET).key(key).build(),
                ResponseTransformer.toInputStream());
        try (object) {
            return object.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
