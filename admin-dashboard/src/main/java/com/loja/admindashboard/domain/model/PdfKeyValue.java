package com.loja.admindashboard.domain.model;

/**
 * A label/value pair of a {@link PdfDocument} key performance indicator
 * (backlog S23), e.g. ("Total Revenue", "R$ 1.234,50").
 */
public record PdfKeyValue(String label, String value) { }
