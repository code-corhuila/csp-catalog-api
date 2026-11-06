package co.edu.corhuila.csp.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The hash of a request and the replay step shared by the movie and the room use cases. */
class IdempotentWritesTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String PINNED_HASH = "4fa29c6e9053d54daa35581ba6bad4f672ae8ead9fe251c52d35def71274b0e1";

    @Test
    void theHashOfAFixedRequestNeverChangesBecauseStoredKeysAreComparedWithIt() {
        String hash = IdempotentWrites.requestHash(WriteOperation.CREATE_MOVIE, ID, "Synthetic Movie", 120, null,
                List.of("a", "b"));

        assertEquals(PINNED_HASH, hash);
    }

    @Test
    void theOperationAndTheResourceTakePartInTheHash() {
        String hash = IdempotentWrites.requestHash(WriteOperation.UPDATE_ROOM, ID, "x");

        assertNotEquals(hash, IdempotentWrites.requestHash(WriteOperation.ARCHIVE_ROOM, ID, "x"));
        assertNotEquals(hash, IdempotentWrites.requestHash(WriteOperation.UPDATE_ROOM, null, "x"));
    }

    @Test
    void aReplayLoadsTheStoredResourceAndDoesNotWrite() {
        String answer = IdempotentWrites.replayOr(Optional.of(ID), id -> "loaded " + id, () -> "written");

        assertEquals("loaded " + ID, answer);
    }

    @Test
    void withoutAReplayTheWriteRuns() {
        String answer = IdempotentWrites.replayOr(Optional.empty(), id -> "loaded", () -> "written");

        assertEquals("written", answer);
    }
}
