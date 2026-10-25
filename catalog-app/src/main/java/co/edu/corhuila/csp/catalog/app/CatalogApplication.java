package co.edu.corhuila.csp.catalog.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root of the catalog service. This module is the only one that knows every concrete type
 * and every limit; the adapters and the core never read configuration by themselves.
 * <p>The scan starts one package above this class so that the adapters, which live in
 * {@code ...catalog.adapter.*}, are part of the same context as this composition root.
 */
@SpringBootApplication(scanBasePackages = "co.edu.corhuila.csp.catalog")
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
