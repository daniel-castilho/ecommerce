package com.loja.productcatalog.adapter.out.storage;

/**
 * Storage configuration for S3-compatible object storage. Values come from
 * system properties ({@code s3.endpoint-override}, {@code s3.bucket},
 * {@code s3.public-base-url}, {@code s3.region}, {@code s3.access-key},
 * {@code s3.secret-key}) or their environment-variable equivalents
 * ({@code S3_ENDPOINT_OVERRIDE}, {@code S3_BUCKET}, {@code S3_PUBLIC_BASE_URL},
 * {@code S3_REGION}, {@code S3_ACCESS_KEY}, {@code S3_SECRET_KEY}), falling back
 * to local-dev defaults. To force the "no endpoint override" prod path, set
 * {@code S3_ENDPOINT_OVERRIDE} to an empty value (blank means "unset").
 */
public class S3Config {

    private final String endpointOverride;
    private final String bucket;
    private final String publicBaseUrl;
    private final String region;
    private final String accessKey;
    private final String secretKey;

    public S3Config(String endpointOverride, String bucket, String publicBaseUrl,
                    String region, String accessKey, String secretKey) {
        this.endpointOverride = endpointOverride;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public static S3Config fromEnvironment() {
        return new S3Config(
                property("s3.endpoint-override", "S3_ENDPOINT_OVERRIDE", "http://localhost:4566"),
                property("s3.bucket", "S3_BUCKET", "product-images"),
                property("s3.public-base-url", "S3_PUBLIC_BASE_URL", "http://localhost:4566/product-images"),
                property("s3.region", "S3_REGION", "us-east-1"),
                property("s3.access-key", "S3_ACCESS_KEY", "test"),
                property("s3.secret-key", "S3_SECRET_KEY", "test"));
    }

    private static String property(String name, String envName, String defaultValue) {
        String value = System.getProperty(name);
        if (value != null) {
            return value;
        }
        value = System.getenv(envName);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    public String endpointOverride() { return endpointOverride; }
    public String bucket() { return bucket; }
    public String publicBaseUrl() { return publicBaseUrl; }
    public String region() { return region; }
    public String accessKey() { return accessKey; }
    public String secretKey() { return secretKey; }
}
