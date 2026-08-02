package com.loja.productcatalog.application.dto;

/** Inbound DTO for {@code UploadProductImageUseCase}: raw bytes plus display metadata. */
public record UploadProductImageCommand(
        byte[] content,
        String contentType,
        String altText,
        int position,
        boolean primary) {
}
