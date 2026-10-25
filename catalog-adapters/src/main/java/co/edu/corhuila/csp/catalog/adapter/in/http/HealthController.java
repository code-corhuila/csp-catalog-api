package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.in.ReadinessUseCases;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The liveness and readiness routes of {@code catalog-service.yaml} (DEC-012). Liveness answers without asking
 * anything, so an outage of MongoDB never restarts a healthy process; readiness hands the check to the use case and
 * {@link ApiExceptionHandler} translates a dependency that does not answer into a 503. Both routes are open in the
 * security filter.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private final ReadinessUseCases readiness;

    public HealthController(ReadinessUseCases readiness) {
        this.readiness = readiness;
    }

    @GetMapping
    HealthResponse liveness() {
        return HealthResponse.ok();
    }

    @GetMapping("/ready")
    ReadinessResponse readiness() {
        return ReadinessResponse.of(readiness.check());
    }
}
