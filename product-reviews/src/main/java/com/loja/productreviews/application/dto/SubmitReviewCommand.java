package com.loja.productreviews.application.dto;

/**
 * Input for {@link com.loja.productreviews.domain.port.in.SubmitReviewUseCase}.
 *
 * <p>Lives in {@code application.dto} rather than nested in the port (lesson #7:
 * ArchUnit treats nested port records as a violation).
 *
 * @param productId target product
 * @param authorId  authenticated user id
 * @param rating    star value 1..5
 * @param title     optional headline
 * @param body      optional body, already HTML-sanitized by the caller
 */
public record SubmitReviewCommand(String productId, String authorId, int rating,
                                  String title, String body) { }
