package co.edu.corhuila.csp.catalog.application.port.out;

import java.util.List;

/** One window of the billboard and the total the contract reports in its metadata. */
public record BillboardPage(List<BillboardEntry> items, int page, int limit, long total) {

    public BillboardPage {
        items = List.copyOf(items);
    }
}
