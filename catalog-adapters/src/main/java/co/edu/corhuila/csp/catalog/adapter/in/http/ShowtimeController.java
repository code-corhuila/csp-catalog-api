package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.in.CancelShowtimeInput;
import co.edu.corhuila.csp.catalog.application.port.in.ShowtimeUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateShowtimeInput;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The showtime routes of {@code catalog-service.yaml}. It translates HTTP into the input port and back and
 * holds no rule of the domain: the permission is checked by the security filter, the invariants and the room
 * schedule by the domain and the use cases, and every error is translated by {@link ApiExceptionHandler}. It
 * answers DTOs, never an entity.
 */
@RestController
@RequestMapping("/showtimes")
public class ShowtimeController {

    private final ShowtimeUseCases showtimes;

    public ShowtimeController(ShowtimeUseCases showtimes) {
        this.showtimes = showtimes;
    }

    @PostMapping
    ResponseEntity<ShowtimeResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateShowtimeRequest request,
            HttpServletRequest http) {
        Showtime showtime = showtimes.createShowtime(request.input(IdempotencyKeys.checked(idempotencyKey)));
        URI location = URI.create(http.getContextPath() + "/showtimes/" + showtime.id());
        return ResponseEntity.created(location).body(ShowtimeResponse.of(showtime));
    }

    @GetMapping
    ShowtimeListResponse list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int limit) {
        return ShowtimeListResponse.of(showtimes.listShowtimes(page, limit));
    }

    @GetMapping("/{showtimeId}")
    ShowtimeResponse get(@PathVariable UUID showtimeId) {
        return ShowtimeResponse.of(showtimes.getShowtime(showtimeId));
    }

    @PatchMapping("/{showtimeId}")
    ShowtimeResponse update(
            @PathVariable UUID showtimeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody UpdateShowtimeRequest request) {
        return ShowtimeResponse.of(showtimes.updateShowtime(
                new UpdateShowtimeInput(IdempotencyKeys.checked(idempotencyKey), showtimeId, request.changes())));
    }

    @DeleteMapping("/{showtimeId}")
    ResponseEntity<Void> cancel(@PathVariable UUID showtimeId, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        showtimes.cancelShowtime(new CancelShowtimeInput(IdempotencyKeys.checked(idempotencyKey), showtimeId));
        return ResponseEntity.noContent().build();
    }
}
