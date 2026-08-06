package com.loja.admindashboard.application.dto;

/**
 * One point of a report line chart (admin reporting, S22). The x/y coordinates
 * are server-computed percentages (0-100) of the chart area so the SVG can be
 * rendered with existing tokens only, mirroring the server-computed heights of
 * {@link ChartBar}.
 *
 * @param label x-axis label (a date).
 * @param title tooltip with the human-readable value.
 * @param x     x position as a percentage of the chart width (0 = left, 100 = right).
 * @param y     y position as a percentage of the chart height (0 = top, 100 = bottom).
 */
public record ChartLine(String label, String title, int x, int y) { }
