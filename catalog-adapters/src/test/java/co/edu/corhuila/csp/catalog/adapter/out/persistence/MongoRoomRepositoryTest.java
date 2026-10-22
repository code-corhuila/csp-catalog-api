package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.RoomNameAlreadyInUseException;
import co.edu.corhuila.csp.catalog.application.port.out.RoomPage;
import co.edu.corhuila.csp.catalog.application.port.out.RoomWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomChanges;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * The adapter against a real MongoDB, as a single-node replica set with the schema of csp-catalog-db
 * (Annex C): the validator and the unique index of the room name judge every document the adapter
 * writes. The tests run when TEST_MONGODB_URI points to a disposable database, and they empty its rooms
 * and idempotency_key. Without it they are skipped on a developer machine and fail when CI is set, so a
 * green CI ran them.
 */
class MongoRoomRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);

    private static MongoTemplate template;
    private final MongoRoomRepository repository = new MongoRoomRepository(template);

    @BeforeAll
    static void connect() {
        String uri = System.getenv("TEST_MONGODB_URI");
        boolean configured = uri != null && !uri.isBlank();
        if (!configured && "true".equals(System.getenv("CI"))) {
            fail("TEST_MONGODB_URI is required in CI: a green build must mean the MongoDB tests ran");
        }
        assumeTrue(configured, "TEST_MONGODB_URI is not set: the MongoDB integration tests were skipped");
        template = new MongoTemplate(new SimpleMongoClientDatabaseFactory(uri));
    }

    @BeforeEach
    void emptyTheCollections() {
        template.getCollection("rooms").deleteMany(new org.bson.Document());
        template.getCollection("idempotency_key").deleteMany(new org.bson.Document());
    }

    private static Room room(String name, Instant at) {
        return Room.create(UUID.randomUUID(), new RoomDetails(name, List.of(
                new Seat("A1", "A", 1, SeatType.STANDARD),
                new Seat("A2", "A", 2, SeatType.VIP),
                new Seat("W1", null, null, SeatType.ACCESSIBLE))), at);
    }

    private static RoomWrite create(Room room, String key, String hash) {
        return new RoomWrite(room, WriteOperation.CREATE_ROOM, key, hash);
    }

    @Test
    void aSavedRoomIsReadBackWithEveryField() {
        Room room = room("Synthetic Room", NOW);

        Room saved = repository.save(create(room, "synthetic-key-0001", HASH));

        assertEquals(room, saved);
        assertEquals(room, repository.findById(room.id()).orElseThrow());
    }

    @Test
    void anUnknownRoomIsNotFound() {
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void theKeyOfAWriteFindsTheRoomItProducedForTheSameRequest() {
        Room room = room("Synthetic Room", NOW);
        repository.save(create(room, "synthetic-key-0001", HASH));

        assertEquals(room.id(), repository.findIdempotentResource("synthetic-key-0001", HASH).orElseThrow());
        assertTrue(repository.findIdempotentResource("synthetic-key-9999", HASH).isEmpty());
    }

    @Test
    void theSameKeyWithAnotherRequestIsAConflict() {
        repository.save(create(room("Synthetic Room", NOW), "synthetic-key-0001", HASH));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> repository.findIdempotentResource("synthetic-key-0001", OTHER_HASH));
    }

    @Test
    void theRoomIsNotWrittenWhenItsKeyCannotBe() {
        repository.save(create(room("First Room", NOW), "synthetic-key-0001", HASH));
        Room second = room("Second Room", NOW);

        assertThrows(IdempotencyKeyConflictException.class,
                () -> repository.save(create(second, "synthetic-key-0001", OTHER_HASH)));

        assertTrue(repository.findById(second.id()).isEmpty());
    }

    @Test
    void aConcurrentReplayReturnsTheRoomTheOtherCallStored() throws Exception {
        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Room>> results = IntStream.range(0, callers)
                .mapToObj(i -> pool.submit(() -> {
                    start.await();
                    return repository.save(create(room("Synthetic Room", NOW), "synthetic-key-race", HASH));
                }))
                .toList();
        start.countDown();

        UUID winner = results.get(0).get().id();
        for (Future<Room> result : results) {
            assertEquals(winner, result.get().id());
        }
        pool.shutdown();
        assertEquals(1, template.getCollection("rooms").countDocuments());
        assertEquals(1, template.getCollection("idempotency_key").countDocuments());
    }

    @Test
    void aCreationWithANameAlreadyInUseWritesNeitherTheRoomNorTheKey() {
        repository.save(create(room("Synthetic Room", NOW), "synthetic-key-0001", HASH));
        Room twin = room("Synthetic Room", NOW.plusSeconds(60));

        assertThrows(RoomNameAlreadyInUseException.class,
                () -> repository.save(create(twin, "synthetic-key-0002", OTHER_HASH)));

        assertTrue(repository.findById(twin.id()).isEmpty());
        assertEquals(1, template.getCollection("rooms").countDocuments());
        assertEquals(1, template.getCollection("idempotency_key").countDocuments());
        assertTrue(repository.findIdempotentResource("synthetic-key-0002", OTHER_HASH).isEmpty());
    }

    @Test
    void anUpdateToANameAlreadyInUseIsRefusedAndLeavesTheStoredRoomUnchanged() {
        Room first = room("First Room", NOW);
        Room second = room("Second Room", NOW.plusSeconds(10));
        repository.save(create(first, "synthetic-key-0001", HASH));
        repository.save(create(second, "synthetic-key-0002", "1".repeat(64)));
        Room renamed = second.change(new RoomChanges("First Room", null), NOW.plusSeconds(60));

        assertThrows(RoomNameAlreadyInUseException.class, () -> repository.save(
                new RoomWrite(renamed, WriteOperation.UPDATE_ROOM, "synthetic-key-0003", OTHER_HASH)));

        assertEquals(second, repository.findById(second.id()).orElseThrow());
        assertTrue(repository.findIdempotentResource("synthetic-key-0003", OTHER_HASH).isEmpty());
    }

    @Test
    void anUpdateReplacesTheRoomAndKeepsOneRecordOfIt() {
        Room room = room("Synthetic Room", NOW);
        repository.save(create(room, "synthetic-key-0001", HASH));
        Room changed = room.change(new RoomChanges("New name", null), NOW.plusSeconds(60));

        repository.save(new RoomWrite(changed, WriteOperation.UPDATE_ROOM, "synthetic-key-0002", OTHER_HASH));

        assertEquals(changed, repository.findById(room.id()).orElseThrow());
        assertEquals(1, template.getCollection("rooms").countDocuments());
    }

    @Test
    void anArchivedRoomIsStoredWithItsDeletionDateAndIsStillReadById() {
        Room room = room("Synthetic Room", NOW);
        repository.save(create(room, "synthetic-key-0001", HASH));
        Room archived = room.archive(NOW.plusSeconds(60));

        repository.save(new RoomWrite(archived, WriteOperation.ARCHIVE_ROOM, "synthetic-key-0002", OTHER_HASH));

        assertEquals(archived, repository.findById(room.id()).orElseThrow());
        assertEquals(NOW.plusSeconds(60), repository.findById(room.id()).orElseThrow().deletedAt());
    }

    @Test
    void theListHoldsTheRoomsThatAreNotArchivedNewestFirst() {
        Room oldest = room("Oldest", NOW);
        Room newest = room("Newest", NOW.plusSeconds(120));
        Room archived = room("Archived", NOW.plusSeconds(60)).archive(NOW.plusSeconds(180));
        repository.save(create(oldest, "synthetic-key-0001", HASH));
        repository.save(create(newest, "synthetic-key-0002", "1".repeat(64)));
        repository.save(create(archived, "synthetic-key-0003", "2".repeat(64)));

        RoomPage page = repository.findActive(1, 20);

        assertEquals(List.of(newest, oldest), page.items());
        assertEquals(2, page.total());
        assertEquals(1, page.page());
        assertEquals(20, page.limit());
    }

    @Test
    void theListIsCutInTheWindowOfThePageAndReportsTheTotal() {
        for (int i = 0; i < 5; i++) {
            repository.save(create(room("Room " + i, NOW.plusSeconds(i)),
                    "synthetic-key-000" + i, String.valueOf(i).repeat(64)));
        }

        RoomPage second = repository.findActive(2, 2);

        assertEquals(2, second.items().size());
        assertEquals("Room 2", second.items().get(0).details().name());
        assertEquals("Room 1", second.items().get(1).details().name());
        assertEquals(5, second.total());
        assertTrue(repository.findActive(4, 2).items().isEmpty());
    }

    @Test
    void aWindowOutsideTheBoundsOfTheContractIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> repository.findActive(0, 20));
        assertThrows(IllegalArgumentException.class, () -> repository.findActive(1, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.findActive(1, 101));
        assertFalse(repository.findActive(1, 100).items().iterator().hasNext());
    }
}
