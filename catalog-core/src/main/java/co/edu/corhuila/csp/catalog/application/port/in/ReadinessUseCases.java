package co.edu.corhuila.csp.catalog.application.port.in;

/** What the catalog offers about its own readiness. The HTTP adapter calls this and nothing else. */
public interface ReadinessUseCases {

    /**
     * Verifies the dependencies the service cannot work without (MongoDB only, DEC-012).
     *
     * @throws co.edu.corhuila.csp.catalog.application.port.out.DependencyUnavailableException when one does not answer
     */
    Readiness check();
}
