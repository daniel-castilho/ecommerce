package com.loja.admindashboard.adapter.out.reporting;

import java.awt.Color;
import java.util.List;

import com.lowagie.text.BadElementException;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import com.loja.admindashboard.application.dto.ChartBar;
import com.loja.admindashboard.application.dto.ChartLine;
import com.loja.admindashboard.domain.exception.ReportGenerationException;

/**
 * Shared vector chart renderer for PDF report exports (backlog S23). Draws bar
 * and line charts with pure OpenPDF primitives on a {@link PdfTemplate} and
 * returns it as an embeddable {@link Image}, so the same report that shows a
 * chart on screen mirrors that chart in its PDF export with the same series,
 * labels and order.
 *
 * <p>Colors mirror the semantic tokens consumed by the admin chart CSS
 * (design-tokens.css): fill/track/label/axis map to {@code --color-action-primary},
 * {@code --color-bg-subtle}, {@code --color-text-muted} and
 * {@code --color-border-default}. An empty or all-zero series renders an axis
 * frame with a "No data" caption instead of throwing.
 */
public final class PdfChartDrawer {

    public static final float CHART_WIDTH = 480f;
    public static final float CHART_HEIGHT = 150f;

    private static final Color FILL = new Color(0x3498DB);      // --color-action-primary
    private static final Color TRACK = new Color(0xF9FAFB);      // --color-bg-subtle
    private static final Color LABEL = new Color(0x95A5A6);      // --color-text-muted
    private static final Color AXIS = new Color(0xBDC3C7);       // --color-border-default
    private static final Color TITLE = new Color(0x2C3E50);      // --color-text-primary
    private static final Color VALUE = new Color(0x7F8C8D);      // --color-text-secondary

    private static final float TITLE_SPACE = 16f;
    private static final float LABEL_SPACE = 14f;
    private static final float LEFT = 26f;
    private static final float RIGHT = 8f;

    private static final BaseFont FONT;

    static {
        try {
            FONT = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to load chart font", e);
        }
    }

    private PdfChartDrawer() {
    }

    public static Image drawBarChart(PdfWriter writer, String heading, List<ChartBar> bars)
            throws BadElementException {
        PdfTemplate canvas = writer.getDirectContent().createTemplate(CHART_WIDTH, CHART_HEIGHT);
        drawTitle(canvas, heading);
        if (bars == null || bars.isEmpty()) {
            drawNoData(canvas);
            return Image.getInstance(canvas);
        }

        float plotX = LEFT;
        float plotW = CHART_WIDTH - LEFT - RIGHT;
        float plotY = LABEL_SPACE;
        float plotH = CHART_HEIGHT - TITLE_SPACE - LABEL_SPACE;

        drawFrame(canvas, plotX, plotY, plotW, plotH);

        int n = bars.size();
        float slot = plotW / n;
        float barW = Math.min(slot * 0.6f, 36f);
        int labelStep = Math.max(1, (int) Math.ceil(n / 12.0));
        boolean renderValues = n <= 10;

        for (int i = 0; i < n; i++) {
            ChartBar bar = bars.get(i);
            int height = Math.max(0, Math.min(100, bar.height()));
            float barLeft = plotX + i * slot + (slot - barW) / 2f;
            float barH = plotH * height / 100f;

            canvas.setColorFill(TRACK);
            canvas.rectangle(barLeft, plotY, barW, plotH);
            canvas.fill();
            if (barH > 0f) {
                canvas.setColorFill(FILL);
                canvas.rectangle(barLeft, plotY, barW, barH);
                canvas.fill();
            }

            if (i % labelStep == 0 || i == n - 1) {
                String label = fit(bar.label(), slot - 2f, 6.5f);
                text(canvas, FONT, 6.5f, LABEL, label, plotX + i * slot + slot / 2f, plotY - 7f);
            }
            if (renderValues && !bar.title().isBlank()) {
                String value = fit(bar.title(), barW + 30f, 6f);
                text(canvas, FONT, 6f, VALUE, value, barLeft + barW / 2f, plotY + barH + 5f);
            }
        }
        return Image.getInstance(canvas);
    }

    public static Image drawLineChart(PdfWriter writer, String heading, List<ChartLine> points)
            throws BadElementException {
        PdfTemplate canvas = writer.getDirectContent().createTemplate(CHART_WIDTH, CHART_HEIGHT);
        drawTitle(canvas, heading);
        if (points == null || points.isEmpty()) {
            drawNoData(canvas);
            return Image.getInstance(canvas);
        }

        float plotX = LEFT;
        float plotW = CHART_WIDTH - LEFT - RIGHT;
        float plotY = LABEL_SPACE;
        float plotH = CHART_HEIGHT - TITLE_SPACE - LABEL_SPACE;

        drawFrame(canvas, plotX, plotY, plotW, plotH);

        int n = points.size();
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            ChartLine point = points.get(i);
            int x = Math.max(0, Math.min(100, point.x()));
            int y = Math.max(0, Math.min(100, point.y()));
            xs[i] = plotX + plotW * x / 100f;
            ys[i] = plotY + plotH * (100 - y) / 100f;
        }

        canvas.setColorStroke(FILL);
        canvas.setLineWidth(1.1f);
        canvas.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            canvas.lineTo(xs[i], ys[i]);
        }
        canvas.stroke();

        canvas.setColorFill(FILL);
        for (int i = 0; i < n; i++) {
            canvas.circle(xs[i], ys[i], 1.6f);
            canvas.fill();
        }

        int step = Math.max(1, (int) Math.ceil(n / 10.0));
        for (int i = 0; i < n; i++) {
            if (i % step != 0 && i != n - 1) {
                continue;
            }
            String label = fit(points.get(i).label(), slotWidth(plotW, n, step), 6.5f);
            text(canvas, FONT, 6.5f, LABEL, label, xs[i], plotY - 7f);
        }
        return Image.getInstance(canvas);
    }

    private static float slotWidth(float plotW, int n, int step) {
        return plotW / n * Math.min(n, step) * 1.2f;
    }

    private static void drawTitle(PdfContentByte canvas, String heading) {
        if (heading == null || heading.isBlank()) {
            return;
        }
        text(canvas, FONT, 8f, TITLE, fit(heading, CHART_WIDTH - LEFT - RIGHT, 8f), LEFT, CHART_HEIGHT - 9f);
    }

    private static void drawFrame(PdfContentByte canvas, float plotX, float plotY, float plotW, float plotH) {
        canvas.setColorStroke(AXIS);
        canvas.setLineWidth(0.6f);
        canvas.moveTo(plotX, plotY);
        canvas.lineTo(plotX + plotW, plotY);
        canvas.stroke();
        canvas.moveTo(plotX, plotY);
        canvas.lineTo(plotX, plotY + plotH);
        canvas.stroke();
    }

    private static void drawNoData(PdfContentByte canvas) {
        float plotX = LEFT;
        float plotW = CHART_WIDTH - LEFT - RIGHT;
        float plotY = LABEL_SPACE;
        float plotH = CHART_HEIGHT - TITLE_SPACE - LABEL_SPACE;
        drawFrame(canvas, plotX, plotY, plotW, plotH);
        text(canvas, FONT, 8f, LABEL, "No data", plotX + plotW / 2f, plotY + plotH / 2f);
    }

    private static void text(PdfContentByte canvas, BaseFont font, float size, Color color,
                             String value, float x, float y) {
        canvas.setColorFill(color);
        canvas.beginText();
        canvas.setFontAndSize(font, size);
        canvas.showTextAligned(PdfContentByte.ALIGN_CENTER, value, x, y, 0f);
        canvas.endText();
    }

    private static String fit(String value, float maxWidth, float size) {
        String text = value == null ? "" : value;
        if (FONT.getWidthPoint(text, size) <= maxWidth) {
            return text;
        }
        String ellipsis = "\u2026";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String attempt = sb + text.substring(i, i + 1) + ellipsis;
            if (FONT.getWidthPoint(attempt, size) > maxWidth) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + ellipsis;
    }
}