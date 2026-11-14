package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.in.AvailabilityUseCases;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The availability route of {@code catalog-service.yaml} (HU-CATALOG-005, ADR-042), read by Booking before it
 * holds a seat. It translates the path into the input port and back and holds no rule of the domain: who may
 * call it is decided by the security filter with the service subjects, which showtime is available by the use
 * case, and every error by {@link ApiExceptionHandler}. It answers a DTO, never an entity, and writes nothing.
 */
@RestController
public class AvailabilityController {

    private final AvailabilityUseCases availability;

    public AvailabilityController(AvailabilityUseCases availability) {
        this.availability = availability;
    }

    @GetMapping("/showtimes/{showtimeId}/availability")
    AvailabilityResponse get(@PathVariable UUID showtimeId) {
        return AvailabilityResponse.of(availability.getAvailability(showtimeId));
    }
}
