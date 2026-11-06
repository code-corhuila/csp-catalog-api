package co.edu.corhuila.csp.catalog.application.port.in;

import java.util.Map;

/** The dependencies the service verified and their state, as the readiness route reports them. */
public record Readiness(Map<String, String> dependencies) {
}
