package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.in.BillboardQuery;
import co.edu.corhuila.csp.catalog.application.port.in.BillboardUseCases;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public billboard route of {@code catalog-service.yaml} (ADR-041). It translates the query parameters
 * into the input port and back and holds no rule of the domain: the route is open in the security filter, the
 * selection of the showtimes belongs to the use case and every error is translated by
 * {@link ApiExceptionHandler}. It answers DTOs, never an entity.
 */
@RestController
@RequestMapping("/billboard")
public class BillboardController {

    private final BillboardUseCases billboard;

    public BillboardController(BillboardUseCases billboard) {
        this.billboard = billboard;
    }

    @GetMapping
    BillboardResponse get(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID movieId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return BillboardResponse.of(billboard.getBillboard(new BillboardQuery(date, movieId, page, limit)));
    }
}
