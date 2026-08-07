package com.loja.admindashboard.domain.port.out;

import com.loja.admindashboard.domain.exception.ReportGenerationException;
import com.loja.admindashboard.domain.model.CsvTable;
import com.loja.admindashboard.domain.model.PdfDocument;

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
