package com.loja.admindashboard.application.dto;

import java.util.List;

/**
 * Structured payload for a PDF report export (backlog S23): a title, an optional
 * subtitle (usually the date range), a row of key performance indicators, chart
 * graphics and any number of {@link PdfSection} tables. The outbound
 * {@code ReportExportPort} adapter renders this into a styled PDF.
 *
 * @param title    report title, e.g. "Revenue Report".
 * @param subtitle optional context line, e.g. "01/07/2026 - 31/07/2026 (Daily)".
 * @param kpis     key performance indicator label/value pairs.
 * @param charts   chart graphics embedded between the KPIs and the tables.
 * @param sections data tables of the report body.
 */
public record PdfDocument(String title, String subtitle, List<PdfKeyValue> kpis,
                          List<PdfChart> charts, List<PdfSection> sections) {

    public PdfDocument {
        kpis = List.copyOf(kpis);
        charts = List.copyOf(charts);
        sections = List.copyOf(sections);
    }
}
