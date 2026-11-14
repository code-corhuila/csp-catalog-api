package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.csp.catalog.adapter.in.http.ServiceSubjects;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * The service subjects that may read the availability come from the environment variable SERVICE_SUBJECTS
 * (ADR-042): comma separated, {@code booking-service} when it is absent, and nobody when it is present but empty.
 */
class ServiceSubjectsConfigurationTest {

    private final ServiceSubjectsConfiguration configuration = new ServiceSubjectsConfiguration();
    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(ServiceSubjectsConfiguration.class);

    private Set<String> subjectsOf(String raw) {
        return configuration.serviceSubjects(raw).values();
    }

    @Test
    void withoutTheVariableTheOnlySubjectIsBookingService() {
        context.run(started -> assertEquals(Set.of("booking-service"), started.getBean(ServiceSubjects.class).values()));
    }

    @Test
    void theVariableOfTheEnvironmentIsTheSourceOfTheList() {
        context.withInitializer(application -> application.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("test-environment",
                                Map.of("SERVICE_SUBJECTS", "booking-service, csp-worker"))))
                .run(started -> assertEquals(Set.of("booking-service", "csp-worker"),
                        started.getBean(ServiceSubjects.class).values()));
    }

    @Test
    void aVariablePresentButEmptyAllowsNobody() {
        context.withInitializer(application -> application.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("test-environment", Map.of("SERVICE_SUBJECTS", ""))))
                .run(started -> assertTrue(started.getBean(ServiceSubjects.class).values().isEmpty()));
    }

    @Test
    void theEntriesAreSplitAtTheCommasAndTrimmed() {
        assertEquals(Set.of("booking-service", "csp-worker", "auth-service"),
                subjectsOf(" booking-service ,csp-worker,\tauth-service "));
    }

    @Test
    void blankEntriesAreIgnored() {
        assertEquals(Set.of("booking-service", "csp-worker"), subjectsOf(",booking-service,, ,csp-worker,"));
    }

    @Test
    void aListOfBlanksOrAnEmptyValueAllowsNobody() {
        assertTrue(subjectsOf("").isEmpty());
        assertTrue(subjectsOf("   ").isEmpty());
        assertTrue(subjectsOf(" , ,, ").isEmpty());
    }

    @Test
    void aSubjectIsMatchedExactlyAndTheDefaultIsNotAddedToAConfiguredList() {
        ServiceSubjects subjects = configuration.serviceSubjects("csp-worker");

        assertTrue(subjects.contains("csp-worker"));
        assertFalse(subjects.contains("booking-service"));
        assertFalse(subjects.contains("CSP-WORKER"));
        assertFalse(subjects.contains(null));
    }
}
