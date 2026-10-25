package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import co.edu.corhuila.csp.catalog.application.port.out.DatabaseProbe;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * The readiness probe of DEC-012: a {@code ping} on the database of the service. The command needs no privilege, so
 * {@code catalog_app} (DEC-011) can run it, and it is bounded by the explicit limits of the client (server selection
 * and read, Norma 5.3.10). Any failure of the driver means the database does not answer.
 */
public class MongoDatabaseProbe implements DatabaseProbe {

    private static final Document PING = new Document("ping", 1);

    private final MongoTemplate template;

    public MongoDatabaseProbe(MongoTemplate template) {
        this.template = template;
    }

    @Override
    public boolean answers() {
        try {
            template.executeCommand(PING);
            return true;
        } catch (RuntimeException unreachable) {
            return false;
        }
    }
}
