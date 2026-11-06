package co.edu.corhuila.csp.catalog.adapter.in.http;

import co.edu.corhuila.csp.catalog.application.port.in.ArchiveRoomInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateRoomInput;
import co.edu.corhuila.csp.catalog.application.port.in.RoomUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateRoomInput;
import co.edu.corhuila.csp.catalog.domain.model.Room;
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
 * The room routes of {@code catalog-service.yaml}. It translates HTTP into the input port and back and holds
 * no rule of the domain: the permission is checked by the security filter, the invariants by the domain, and
 * every error is translated by {@link ApiExceptionHandler}. It answers DTOs, never an entity.
 */
@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomUseCases rooms;

    public RoomController(RoomUseCases rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    ResponseEntity<RoomResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateRoomRequest request,
            HttpServletRequest http) {
        Room room = rooms.createRoom(
                new CreateRoomInput(IdempotencyKeys.checked(idempotencyKey), request.details(), request.capacity()));
        URI location = URI.create(http.getContextPath() + "/rooms/" + room.id());
        return ResponseEntity.created(location).body(RoomResponse.of(room));
    }

    @GetMapping
    RoomListResponse list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int limit) {
        return RoomListResponse.of(rooms.listRooms(page, limit));
    }

    @GetMapping("/{roomId}")
    RoomResponse get(@PathVariable UUID roomId) {
        return RoomResponse.of(rooms.getRoom(roomId));
    }

    @PatchMapping("/{roomId}")
    RoomResponse update(
            @PathVariable UUID roomId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody UpdateRoomRequest request) {
        return RoomResponse.of(
                rooms.updateRoom(new UpdateRoomInput(IdempotencyKeys.checked(idempotencyKey), roomId, request.changes())));
    }

    @DeleteMapping("/{roomId}")
    ResponseEntity<Void> archive(@PathVariable UUID roomId, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        rooms.archiveRoom(new ArchiveRoomInput(IdempotencyKeys.checked(idempotencyKey), roomId));
        return ResponseEntity.noContent().build();
    }
}
