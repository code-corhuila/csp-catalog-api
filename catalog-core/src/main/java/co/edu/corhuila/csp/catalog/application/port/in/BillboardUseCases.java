package co.edu.corhuila.csp.catalog.application.port.in;

import co.edu.corhuila.csp.catalog.application.port.out.BillboardPage;

/** What the catalog offers about the public billboard. The HTTP adapter calls this and nothing else. */
public interface BillboardUseCases {

    /** The showtimes that can still be attended, as of the current instant, in the window of the query. */
    BillboardPage getBillboard(BillboardQuery query);
}
