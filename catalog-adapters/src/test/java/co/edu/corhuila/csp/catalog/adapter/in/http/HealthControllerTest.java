package co.edu.corhuila.csp.catalog.adapter.in.http;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.csp.catalog.application.port.in.Readiness;
import co.edu.corhuila.csp.catalog.application.port.in.ReadinessUseCases;
import co.edu.corhuila.csp.catalog.application.port.out.DependencyUnavailableException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The liveness and readiness routes: status, bodies and the 503 envelope (catalog-service.yaml, DEC-012). */
class HealthControllerTest {

    private final ReadinessUseCases useCases = mock(ReadinessUseCases.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(useCases))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new CorrelationIdFilter())
            .build();

    @Test
    void livenessAnswersOkWithoutAskingTheUseCase() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database").doesNotExist());

        verifyNoInteractions(useCases);
    }

    @Test
    void readinessAnswersReadyWithTheConnectedDependencies() throws Exception {
        when(useCases.check()).thenReturn(new Readiness(Map.of("mongodb", "connected")));

        mvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.dependencies.mongodb").value("connected"));
    }

    @Test
    void readinessAnswers503WithTheErrorEnvelopeWhenADependencyDoesNotAnswer() throws Exception {
        when(useCases.check()).thenThrow(new DependencyUnavailableException("mongodb"));

        mvc.perform(get("/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("A required service or dependency is unavailable"))
                .andExpect(jsonPath("$.traceId").exists());
    }
}
