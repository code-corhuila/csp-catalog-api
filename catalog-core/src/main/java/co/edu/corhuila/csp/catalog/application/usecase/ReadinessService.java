package co.edu.corhuila.csp.catalog.application.usecase;

import co.edu.corhuila.csp.catalog.application.port.in.Readiness;
import co.edu.corhuila.csp.catalog.application.port.in.ReadinessUseCases;
import co.edu.corhuila.csp.catalog.application.port.out.DatabaseProbe;
import co.edu.corhuila.csp.catalog.application.port.out.DependencyUnavailableException;
import java.util.Map;

/**
 * The readiness use case (DEC-012). The service is ready when MongoDB answers; RabbitMQ is not checked because the
 * API does not connect to it. It keeps no verdict: every check asks the probe again.
 */
public class ReadinessService implements ReadinessUseCases {

    private static final String MONGODB = "mongodb";

    private final DatabaseProbe database;

    public ReadinessService(DatabaseProbe database) {
        this.database = database;
    }

    @Override
    public Readiness check() {
        if (!database.answers()) {
            throw new DependencyUnavailableException(MONGODB);
        }
        return new Readiness(Map.of(MONGODB, "connected"));
    }
}
