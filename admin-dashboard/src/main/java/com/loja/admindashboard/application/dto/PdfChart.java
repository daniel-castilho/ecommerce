package com.loja.admindashboard.application.dto;

import java.util.List;

/**
 * One chart to embed in a {@link PdfDocument} export (backlog S23). A chart is
 * always a bar series ({@link #bars()}) or a line series ({@link #lines()}),
 * never both; the series reuse the on-screen DTOs ({@link ChartBar} /
 * {@link ChartLine}) so the PDF mirrors the UI chart with the same labels and
 * values, without re-querying with different aggregation logic.
 *
 * @param heading optional caption drawn above the chart.
 * @param bars    bar series ({@code label}/{@code title}/{@code height}); present
 *                for bar charts, empty otherwise.
 * @param lines   line series ({@code label}/{@code title}/{@code x}/{@code y} in a
 *                0-100 top-origin space); present for line charts, empty otherwise.
 */
public record PdfChart(String heading, List<ChartBar> bars, List<ChartLine> lines) {

    public PdfChart {
        heading = heading == null ? "" : heading;
        bars = bars == null ? List.of() : List.copyOf(bars);
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public boolean isLineChart() {
        return bars.isEmpty();
    }
}