package co.edu.corhuila.csp.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.csp.catalog.application.port.out.DependencyUnavailableException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The readiness use case (DEC-012), with a fake of the output port (Annex C). */
class ReadinessServiceTest {

    @Test
    void reportsMongoDbAsConnectedWhenTheProbeAnswers() {
        ReadinessService service = new ReadinessService(() -> true);

        assertEquals(Map.of("mongodb", "connected"), service.check().dependencies());
    }

    @Test
    void failsNamingMongoDbWhenTheProbeDoesNotAnswer() {
        ReadinessService service = new ReadinessService(() -> false);

        DependencyUnavailableException failure = assertThrows(DependencyUnavailableException.class, service::check);

        assertEquals("mongodb", failure.dependency());
    }

    @Test
    void asksTheProbeOnEveryCheckAndKeepsNoVerdict() {
        boolean[] answers = {false, true};
        int[] asked = {0};
        ReadinessService service = new ReadinessService(() -> answers[asked[0]++]);

        assertThrows(DependencyUnavailableException.class, service::check);

        assertEquals(Map.of("mongodb", "connected"), service.check().dependencies());
        assertEquals(2, asked[0]);
    }
}
