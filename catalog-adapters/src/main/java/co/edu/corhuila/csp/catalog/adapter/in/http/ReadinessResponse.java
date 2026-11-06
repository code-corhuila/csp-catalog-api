package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.in.Readiness;
import java.util.Map;

/** The body of {@code GET /health/ready}: the dependencies the service verified and reached (DEC-012). */
record ReadinessResponse(String status, Map<String, String> dependencies) {

    static ReadinessResponse of(Readiness readiness) {
        return new ReadinessResponse("ready", readiness.dependencies());
    }
}
