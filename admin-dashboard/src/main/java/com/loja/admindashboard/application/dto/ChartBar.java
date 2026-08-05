package com.loja.admindashboard.application.dto;

/**
 * One bar of a report bar chart (admin reporting, S20/S21). The height is a
 * server-computed percentage (0-100) so the chart can be shared by any report
 * series through the {@code barChart} composite component.
 *
 * @param label  x-axis label (date, category name, ...).
 * @param title  tooltip with the human-readable value.
 * @param height bar height as a percentage of the tallest bar.
 */
public record ChartBar(String label, String title, int height) { }
