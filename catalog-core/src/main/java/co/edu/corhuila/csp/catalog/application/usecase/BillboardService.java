package co.edu.corhuila.csp.catalog.application.usecase;

import co.edu.corhuila.csp.catalog.application.port.in.BillboardQuery;
import co.edu.corhuila.csp.catalog.application.port.in.BillboardUseCases;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardPage;
import co.edu.corhuila.csp.catalog.application.port.out.BillboardReader;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * The billboard use case (ADR-041). It asks the clock for the instant from which a showtime is still
 * upcoming and leaves the selection to the reader.
 */
public class BillboardService implements BillboardUseCases {

    private final BillboardReader reader;
    private final Clock clock;

    public BillboardService(BillboardReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    @Override
    public BillboardPage getBillboard(BillboardQuery query) {
        return reader.find(query, clock.instant().truncatedTo(ChronoUnit.MILLIS));
    }
}
