package com.loja.admindashboard.domain.port.out;

import com.loja.admindashboard.application.dto.CsvTable;
import com.loja.admindashboard.application.dto.PdfDocument;
import com.loja.admindashboard.domain.exception.ReportGenerationException;

/**
 * Outbound port for report export (backlog S23). The admin-dashboard module owns
 * the export contract (it is the reporting consumer) and provides an adapter that
 * renders the generic {@link CsvTable} / {@link PdfDocument} payloads with
 * Apache Commons CSV / OpenPDF.
 */
public interface ReportExportPort {

    byte[] generateCsv(CsvTable table) throws ReportGenerationException;

    byte[] generatePdf(PdfDocument document) throws ReportGenerationException;
}
