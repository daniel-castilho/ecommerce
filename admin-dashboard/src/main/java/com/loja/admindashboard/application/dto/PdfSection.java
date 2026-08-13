package com.loja.admindashboard.application.dto;

import java.util.List;

/**
 * A named table inside a {@link PdfDocument} (backlog S23): an optional heading
 * plus a tabular body, e.g. the revenue-by-payment-method breakdown or the top
 * sellers list. Charts live in {@link PdfChart} elements while tabular details
 * stay right-sized in sections.
 *
 * @param heading       optional section title.
 * @param columnHeaders column names of the table header row.
 * @param rows          data rows, each with one value per column.
 */
public record PdfSection(String heading, List<String> columnHeaders, List<List<String>> rows) {

    public PdfSection {
        columnHeaders = columnHeaders == null ? List.of() : List.copyOf(columnHeaders);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
