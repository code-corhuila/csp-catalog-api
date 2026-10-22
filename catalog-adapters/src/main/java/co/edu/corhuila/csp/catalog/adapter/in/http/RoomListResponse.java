package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.RoomPage;
import java.util.List;

/** {@code RoomListResponse} of the contract: the rooms of the page and the {@code PaginatedMeta}. */
public record RoomListResponse(List<RoomResponse> data, PageMeta meta) {

    static RoomListResponse of(RoomPage page) {
        return new RoomListResponse(page.items().stream().map(RoomResponse::of).toList(),
                PageMeta.of(page.page(), page.limit(), page.total()));
    }
}
