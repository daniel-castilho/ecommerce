package com.loja.admindashboard.adapter.out.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;

import com.loja.admindashboard.application.dto.ChartBar;
import com.loja.admindashboard.application.dto.ChartLine;

class PdfChartDrawerTest {

    @Test
    void drawBarChart_withSeries_producesTemplateImageAtChartSize() throws Exception {
        Image image = render(writer -> PdfChartDrawer.drawBarChart(writer, "Units sold by category",
                List.of(new ChartBar("Electronics", "8", 100), new ChartBar("Home", "4", 50))));

        assertImageCompatible(image);
    }

    @Test
    void drawBarChart_withEmptySeries_rendersNoDataFrameWithoutThrowing() throws Exception {
        Image image = render(writer -> PdfChartDrawer.drawBarChart(writer, "Units sold by category", List.of()));

        assertImageCompatible(image);
    }

    @Test
    void drawBarChart_withSinglePoint_producesChart() throws Exception {
        Image image = render(writer -> PdfChartDrawer.drawBarChart(writer, "Revenue over time",
                List.of(new ChartBar("01/07/2026", "R$ 10,00", 100))));

        assertImageCompatible(image);
    }

    @Test
    void drawBarChart_withLongLabel_truncatesWithoutThrowing() throws Exception {
        String longLabel = "Very long product category name that never fits in a chart slot";
        Image image = render(writer -> PdfChartDrawer.drawBarChart(writer, "Units sold by category",
                List.of(new ChartBar(longLabel, "8", 100), new ChartBar("Home", "4", 50))));

        assertImageCompatible(image);
    }

    @Test
    void drawLineChart_withSeries_producesTemplateImageAtChartSize() throws Exception {
        Image image = render(writer -> PdfChartDrawer.drawLineChart(writer, "New Customers by Date",
                List.of(new ChartLine("01/06/2026", "1", 0, 75),
                        new ChartLine("02/06/2026", "2", 50, 50),
                        new ChartLine("03/06/2026", "4", 100, 0))));

        assertImageCompatible(image);
    }

    @Test
    void drawLineChart_withEmptySeries_rendersNoDataFrameWithoutThrowing() throws Exception {
        Image image = render(writer -> PdfChartDrawer.drawLineChart(writer, "New Customers by Date", List.of()));

        assertImageCompatible(image);
    }

    @Test
    void drawLineChart_withSinglePoint_producesChart() throws Exception {
        Image image = render(writer -> PdfChartDrawer.drawLineChart(writer, "New Customers by Date",
                List.of(new ChartLine("01/06/2026", "1", 50, 50))));

        assertImageCompatible(image);
    }

    private static Image render(ChartFn drawer) throws Exception {
        byte[] bytes;
        Image image;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document pdf = new Document(PageSize.A4, 36, 36, 48, 48);
            PdfWriter writer = PdfWriter.getInstance(pdf, out);
            pdf.open();
            image = drawer.draw(writer);
            assertThat(image.getPlainWidth()).isEqualTo(PdfChartDrawer.CHART_WIDTH);
            assertThat(image.getPlainHeight()).isEqualTo(PdfChartDrawer.CHART_HEIGHT);
            image.scaleToFit(PdfChartDrawer.CHART_WIDTH, PdfChartDrawer.CHART_HEIGHT);
            pdf.add(image);
            pdf.close();
            bytes = out.toByteArray();
        }
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        return image;
    }

    private static void assertImageCompatible(Image image) {
        assertThat(image.getPlainWidth()).isEqualTo(PdfChartDrawer.CHART_WIDTH);
        assertThat(image.getPlainHeight()).isEqualTo(PdfChartDrawer.CHART_HEIGHT);
    }

    @FunctionalInterface
    private interface ChartFn {
        Image draw(PdfWriter writer) throws Exception;
    }
}