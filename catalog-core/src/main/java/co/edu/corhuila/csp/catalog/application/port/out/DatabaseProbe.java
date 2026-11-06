package co.edu.corhuila.csp.catalog.application.port.out;

/** Asks the database whether it answers. The readiness use case calls this and knows nothing of the engine. */
public interface DatabaseProbe {

    /** True when the database answers within the limits of its client; false when it cannot be reached. */
    boolean answers();
}
