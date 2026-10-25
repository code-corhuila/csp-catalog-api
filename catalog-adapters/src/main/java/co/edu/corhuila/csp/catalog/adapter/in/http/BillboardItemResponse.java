package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.out.BillboardEntry;

/** {@code BillboardItem} of the contract: a showtime with the movie and the room it names. */
public record BillboardItemResponse(
        BillboardMovieResponse movie, BillboardRoomResponse room, BillboardShowtimeResponse showtime) {

    static BillboardItemResponse of(BillboardEntry entry) {
        return new BillboardItemResponse(BillboardMovieResponse.of(entry.movie()),
                BillboardRoomResponse.of(entry.room()), BillboardShowtimeResponse.of(entry.showtime()));
    }
}
