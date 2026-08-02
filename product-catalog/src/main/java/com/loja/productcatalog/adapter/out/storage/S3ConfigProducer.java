package com.loja.productcatalog.adapter.out.storage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer for {@link S3Config}: reads system properties / environment variables
 * with local defaults (no MicroProfile Config in this project). The produced bean is
 * {@code @Dependent} because {@link S3Config} is an immutable value object without a
 * no-args constructor and therefore cannot be proxied for a normal scope.
 */
@ApplicationScoped
public class S3ConfigProducer {

    @Produces
    public S3Config s3Config() {
        return S3Config.fromEnvironment();
    }
}
