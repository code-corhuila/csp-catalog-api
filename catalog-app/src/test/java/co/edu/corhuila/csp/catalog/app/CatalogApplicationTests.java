package co.edu.corhuila.csp.catalog.app;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void theCompositionRootStarts() {
        assertNotNull(context.getBean(CatalogApplication.class));
    }
}
