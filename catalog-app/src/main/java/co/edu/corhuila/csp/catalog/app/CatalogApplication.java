package co.edu.corhuila.csp.catalog.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root of the catalog service. This module is the only one that knows every concrete type
 * and every limit; the adapters and the core never read configuration by themselves.
 */
@SpringBootApplication
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
