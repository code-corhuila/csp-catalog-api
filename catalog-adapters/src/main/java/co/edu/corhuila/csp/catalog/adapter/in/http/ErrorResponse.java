package co.edu.corhuila.csp.catalog.adapter.in.http;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * The error envelope of the contract ({@code _shared.yaml}): {@code error}, {@code message} and
 * {@code traceId}. The messages never carry tokens, passwords, driver text or internal exception detail.
 * The optional {@code details} of the contract, which name the fields of a failed validation, arrive
 * with the validation of the request bodies.
 */
public record ErrorResponse(String error, String message, String traceId) {

    /**
     * The envelope with the traceId of the correlation filter, so a log line and the answer of its
     * request carry the same id; a new one when that filter has not run for this thread.
     */
    public static ErrorResponse of(String error, String message) {
        String traceId = MDC.get(CorrelationIdFilter.TRACE_ID_MDC_KEY);
        return new ErrorResponse(error, message, traceId == null ? UUID.randomUUID().toString() : traceId);
    }
}
