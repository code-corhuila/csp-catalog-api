package co.edu.corhuila.csp.catalog.application.port.out;

/**
 * Another active room already has that name. The database decides it with its unique index, so two
 * concurrent creations cannot both succeed; the use cases let it through for the HTTP adapter to answer.
 */
public class RoomNameAlreadyInUseException extends RuntimeException {

    public RoomNameAlreadyInUseException() {
        super("A room with that name already exists");
    }
}
