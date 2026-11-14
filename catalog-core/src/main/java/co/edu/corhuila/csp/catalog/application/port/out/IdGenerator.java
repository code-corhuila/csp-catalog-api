package co.edu.corhuila.csp.catalog.application.port.out;

import java.util.UUID;

/** Source of identifiers, so a test can know the id a movie will get. */
@FunctionalInterface
public interface IdGenerator {

    UUID next();
}
