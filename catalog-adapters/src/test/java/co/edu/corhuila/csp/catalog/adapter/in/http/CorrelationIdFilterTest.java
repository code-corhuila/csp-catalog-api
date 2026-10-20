package co.edu.corhuila.csp.catalog.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Every request carries a correlation id that the log and the answer share (Annex C, correlation). */
class CorrelationIdFilterTest {

    private static final String RECEIVED = "550e8400-e29b-41d4-a716-446655440000";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String run(String header, AtomicReference<String> traceIdInTheChain, MockHttpServletResponse response)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(CorrelationIdFilter.CORRELATION_HEADER, header);
        }
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                traceIdInTheChain.set(MDC.get(CorrelationIdFilter.TRACE_ID_MDC_KEY));
            }
        });
        return response.getHeader(CorrelationIdFilter.CORRELATION_HEADER);
    }

    @Test
    void aCorrelationIdThatIsAUuidIsKeptAndAnsweredAndPutInTheLog() throws Exception {
        AtomicReference<String> inTheChain = new AtomicReference<>();

        String answered = run(RECEIVED, inTheChain, new MockHttpServletResponse());

        assertEquals(RECEIVED, answered);
        assertEquals(RECEIVED, inTheChain.get());
    }

    @Test
    void aRequestWithoutCorrelationIdGetsANewUuid() throws Exception {
        AtomicReference<String> inTheChain = new AtomicReference<>();

        String answered = run(null, inTheChain, new MockHttpServletResponse());

        assertEquals(answered, UUID.fromString(answered).toString());
        assertEquals(answered, inTheChain.get());
    }

    @Test
    void aValueThatIsNotAUuidIsNeverCopiedIntoTheLogNorTheAnswer() throws Exception {
        AtomicReference<String> inTheChain = new AtomicReference<>();

        String answered = run("injected\nline", inTheChain, new MockHttpServletResponse());

        assertNotEquals("injected\nline", answered);
        assertTrue(answered.matches("[0-9a-f-]{36}"));
        assertEquals(answered, inTheChain.get());
    }

    @Test
    void theLogContextIsClearedWhenTheRequestEnds() throws Exception {
        run(RECEIVED, new AtomicReference<>(), new MockHttpServletResponse());

        assertNull(MDC.get(CorrelationIdFilter.TRACE_ID_MDC_KEY));
    }
}
