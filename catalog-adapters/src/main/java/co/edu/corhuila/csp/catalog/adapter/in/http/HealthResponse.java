package co.edu.corhuila.csp.catalog.adapter.in.http;

/** The body of {@code GET /health}: the process answers (liveness, DEC-012). */
record HealthResponse(String status) {

    static HealthResponse ok() {
        return new HealthResponse("ok");
    }
}
