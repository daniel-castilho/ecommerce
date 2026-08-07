package com.loja.admindashboard.domain.exception;

/**
 * Thrown when a report cannot be serialized to CSV or PDF (backlog S23).
 * Wraps any underlying I/O or rendering failure so the web layer never sees
 * library-specific exceptions.
 */
public class ReportGenerationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReportGenerationException(String message) {
        super(message);
    }
}
