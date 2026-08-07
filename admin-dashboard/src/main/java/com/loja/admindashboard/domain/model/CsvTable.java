package com.loja.admindashboard.domain.model;

import java.util.List;

/**
 * Flat tabular payload for a CSV export (backlog S23): a header row plus any
 * number of data rows. This is the outbound-port contract shared by every
 * report; each report bean flattens its domain model into one of these.
 *
 * @param headers column names for the first row.
 * @param rows    data rows, each with one value per header.
 */
public record CsvTable(List<String> headers, List<List<String>> rows) {

    public CsvTable {
        headers = List.copyOf(headers);
        rows = List.copyOf(rows);
    }

    public static CsvTable of(List<String> headers, List<List<String>> rows) {
        return new CsvTable(headers, rows);
    }
}
