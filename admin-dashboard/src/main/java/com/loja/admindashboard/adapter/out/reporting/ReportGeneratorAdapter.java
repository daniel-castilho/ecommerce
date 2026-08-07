package com.loja.admindashboard.adapter.out.reporting;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.loja.admindashboard.domain.exception.ReportGenerationException;
import com.loja.admindashboard.domain.model.CsvTable;
import com.loja.admindashboard.domain.model.PdfDocument;
import com.loja.admindashboard.domain.model.PdfKeyValue;
import com.loja.admindashboard.domain.model.PdfSection;
import com.loja.admindashboard.domain.port.out.ReportExportPort;

/**
 * Default implementation of {@link ReportExportPort} (backlog S23). Renders the
 * generic CSV/PDF payloads with Apache Commons CSV and OpenPDF. PDF colors mirror
 * the semantic tokens in {@code design-tokens.css} (e.g. {@code --color-bg-inverse},
 * {@code --color-action-primary-hover}, {@code --color-text-secondary}) — see the
 * color constants below.
 *
 * <p>Right-sized: charts are not embedded as images; the underlying series is
 * exported as a data table instead (mirrors the S20/S21 right-sizing notes).
 */
@ApplicationScoped
public class ReportGeneratorAdapter implements ReportExportPort {

    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final int HEADER_BACKGROUND = 0x2C3E50;   // --color-bg-inverse
    private static final int HEADER_TEXT = 0xFFFFFF;          // --color-text-inverse
    private static final int TABLE_HEADER_BACKGROUND = 0x2980B9; // --color-action-primary-hover
    private static final int TABLE_HEADER_TEXT = 0xFFFFFF;
    private static final int BODY_TEXT = 0x2C3E50;            // --color-text-primary
    private static final int SUBTITLE_TEXT = 0x7F8C8D;        // --color-text-secondary
    private static final int STRIPE_BACKGROUND = 0xF9FAFB;    // --color-bg-subtle
    private static final int BORDER = 0xBDC3C7;               // --color-border-default

    @Override
    public byte[] generateCsv(CsvTable table) {
        if (table == null) {
            throw new ReportGenerationException("CSV table must not be null");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(UTF_8_BOM, 0, UTF_8_BOM.length);
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader(table.headers().toArray(String[]::new))
                    .build();
            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (List<String> row : table.rows()) {
                    printer.printRecord(row);
                }
            }
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to generate CSV", e);
        }
        return out.toByteArray();
    }

    @Override
    public byte[] generatePdf(PdfDocument document) {
        if (document == null) {
            throw new ReportGenerationException("PDF document must not be null");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4, 36, 36, 48, 48);
        try {
            PdfWriter.getInstance(pdf, out);
            pdf.open();
            addTitleBand(pdf, document);
            addKpiRow(pdf, document.kpis());
            for (PdfSection section : document.sections()) {
                addSection(pdf, section);
            }
            pdf.close();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate PDF", e);
        }
        return out.toByteArray();
    }

    private static void addTitleBand(Document pdf, PdfDocument document) throws Exception {
        PdfPTable band = new PdfPTable(1);
        band.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setBackgroundColor(new Color(HEADER_BACKGROUND));
        cell.setPadding(14);
        cell.addElement(new Paragraph(document.title(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Font.BOLD, new Color(HEADER_TEXT))));
        if (document.subtitle() != null && !document.subtitle().isBlank()) {
            cell.addElement(new Paragraph(document.subtitle(),
                    FontFactory.getFont(FontFactory.HELVETICA, 11, Font.NORMAL, new Color(SUBTITLE_TEXT))));
        }
        band.addCell(cell);
        pdf.add(band);
        pdf.add(spacer());
    }

    private static void addKpiRow(Document pdf, List<PdfKeyValue> kpis) throws Exception {
        if (kpis.isEmpty()) {
            return;
        }
        PdfPTable kpiTable = new PdfPTable(kpis.size());
        kpiTable.setWidthPercentage(100);
        for (PdfKeyValue kpi : kpis) {
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(new Color(BORDER));
            cell.setPadding(8);
            cell.addElement(new Paragraph(kpi.value(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD, new Color(BODY_TEXT))));
            cell.addElement(new Paragraph(kpi.label(),
                    FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, new Color(SUBTITLE_TEXT))));
            kpiTable.addCell(cell);
        }
        pdf.add(kpiTable);
        pdf.add(spacer());
    }

    private static void addSection(Document pdf, PdfSection section) throws Exception {
        if (section.heading() != null && !section.heading().isBlank()) {
            pdf.add(new Paragraph(section.heading(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.BOLD, new Color(BODY_TEXT))));
        }
        if (section.columnHeaders().isEmpty()) {
            pdf.add(new Paragraph("No data.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Font.ITALIC, new Color(SUBTITLE_TEXT))));
            pdf.add(spacer());
            return;
        }
        int columns = section.columnHeaders().size();
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : section.columnHeaders()) {
            addTableHeaderCell(table, header);
        }
        for (int i = 0; i < section.rows().size(); i++) {
            List<String> row = section.rows().get(i);
            boolean stripe = i % 2 == 1;
            for (int c = 0; c < columns; c++) {
                addTableBodyCell(table, cellValue(row, c), stripe);
            }
        }
        pdf.add(table);
        pdf.add(spacer());
    }

    private static void addTableHeaderCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, FontFactory.getFont(
                FontFactory.HELVETICA_BOLD, 10, Font.BOLD, new Color(TABLE_HEADER_TEXT))));
        cell.setBackgroundColor(new Color(TABLE_HEADER_BACKGROUND));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private static void addTableBodyCell(PdfPTable table, String value, boolean stripe) {
        PdfPCell cell = new PdfPCell(new Phrase(value, FontFactory.getFont(
                FontFactory.HELVETICA, 10, Font.NORMAL, new Color(BODY_TEXT))));
        cell.setBorderColor(new Color(BORDER));
        if (stripe) {
            cell.setBackgroundColor(new Color(STRIPE_BACKGROUND));
        }
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private static String cellValue(List<String> row, int index) {
        if (row == null || index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index);
    }

    private static Paragraph spacer() {
        return new Paragraph("\n", FontFactory.getFont(FontFactory.HELVETICA, 8));
    }
}
