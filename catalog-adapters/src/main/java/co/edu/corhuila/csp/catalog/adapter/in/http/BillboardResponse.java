package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.BillboardPage;
import java.util.List;

/** The 200 answer of {@code getBillboard}: the items of the page and the {@code PaginatedMeta}. */
public record BillboardResponse(List<BillboardItemResponse> data, PageMeta meta) {

    static BillboardResponse of(BillboardPage page) {
        return new BillboardResponse(page.items().stream().map(BillboardItemResponse::of).toList(),
                PageMeta.of(page.page(), page.limit(), page.total()));
    }
}
