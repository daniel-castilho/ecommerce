package com.loja.admindashboard.adapter.out.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.loja.admindashboard.domain.exception.ReportGenerationException;
import com.loja.admindashboard.domain.model.CsvTable;
import com.loja.admindashboard.domain.model.PdfDocument;
import com.loja.admindashboard.domain.model.PdfKeyValue;
import com.loja.admindashboard.domain.model.PdfSection;

class ReportGeneratorAdapterTest {

    private final ReportGeneratorAdapter adapter = new ReportGeneratorAdapter();

    @Test
    void generateCsv_withHeadersAndRows_producesUtf8CsvWithBomAndHeader() {
        CsvTable table = CsvTable.of(
                List.of("Date", "Revenue"),
                List.of(List.of("01/07/2026", "R$ 1.234,50"), List.of("02/07/2026", "R$ 567,00")));

        byte[] bytes = adapter.generateCsv(table);

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        String csv = withoutBom(bytes);
        assertThat(csv).startsWith("Date,Revenue");
        assertThat(csv).contains("01/07/2026,\"R$ 1.234,50\"");
        assertThat(csv).contains("02/07/2026,\"R$ 567,00\"");
    }

    @Test
    void generateCsv_withCommaInValue_quotesTheField() {
        CsvTable table = CsvTable.of(
                List.of("Name", "Revenue"),
                List.of(List.of("Card, credit", "R$ 10,00")));

        String csv = withoutBom(adapter.generateCsv(table));

        assertThat(csv).contains("\"Card, credit\",\"R$ 10,00\"");
    }

    @Test
    void generateCsv_withEmptyRows_stillEmitsHeaderRow() {
        CsvTable table = CsvTable.of(List.of("A", "B"), List.of());

        String csv = withoutBom(adapter.generateCsv(table));

        assertThat(csv).startsWith("A,B");
    }

    @Test
    void generateCsv_withNullTable_throwsReportGenerationException() {
        assertThatThrownBy(() -> adapter.generateCsv(null))
                .isInstanceOf(ReportGenerationException.class);
    }

    @Test
    void generatePdf_withDocument_producesPdfWithPdfHeader() {
        PdfDocument document = new PdfDocument(
                "Revenue Report",
                "01/07/2026 - 31/07/2026 (Daily)",
                List.of(new PdfKeyValue("Total Revenue", "R$ 1.234,50")),
                List.of(new PdfSection("Revenue by payment method",
                        List.of("Payment Method", "Revenue"),
                        List.of(List.of("card", "R$ 800,00"), List.of("pix", "R$ 434,50")))));

        byte[] bytes = adapter.generatePdf(document);

        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(bytes.length).isGreaterThan(500);
    }

    @Test
    void generatePdf_withSectionWithoutColumns_doesNotThrow() {
        PdfDocument document = new PdfDocument("Empty Report", null, List.of(),
                List.of(new PdfSection("No data", List.of(), List.of())));

        byte[] bytes = adapter.generatePdf(document);

        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void generatePdf_withNullDocument_throwsReportGenerationException() {
        assertThatThrownBy(() -> adapter.generatePdf(null))
                .isInstanceOf(ReportGenerationException.class);
    }

    private static String withoutBom(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
    }
}
