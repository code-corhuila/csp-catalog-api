package co.edu.corhuila.csp.catalog.application.port.out;

/** A dependency the service needs does not answer; the HTTP adapter translates it into a 503 (DEC-012). */
public class DependencyUnavailableException extends RuntimeException {

    private final String dependency;

    public DependencyUnavailableException(String dependency) {
        super(dependency + " does not answer");
        this.dependency = dependency;
    }

    public String dependency() {
        return dependency;
    }
}
