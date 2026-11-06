package co.edu.corhuila.csp.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimePage;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeWrite;
import co.edu.corhuila.csp.catalog.application.port.out.WriteOperation;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import co.edu.corhuila.csp.catalog.domain.model.Showtime;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeChanges;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeOverlapException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeReferenceException;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeStatus;
import co.edu.corhuila.csp.catalog.domain.model.TimeInterval;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * The adapter against a real MongoDB, as a single-node replica set with the schema of csp-catalog-db
 * (Annex C): the validators of showtimes and outbox_event judge every document the adapter writes. The
 * tests run when TEST_MONGODB_URI points to a disposable database, and they empty its rooms, showtimes,
 * outbox_event and idempotency_key. Without it they are skipped on a developer machine and fail when CI is
 * set, so a green CI ran them.
 */
class MongoShowtimeRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final Instant NIGHT = Instant.parse("2026-10-23T20:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);
    private static final UUID MOVIE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final int CALLERS = 8;

    private static MongoTemplate template;
    private final MongoShowtimeRepository repository = new MongoShowtimeRepository(template);
    private Room room;

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
    void emptyTheCollectionsAndStoreARoom() {
        for (String collection : List.of("rooms", "showtimes", "outbox_event", "idempotency_key")) {
            template.getCollection(collection).deleteMany(new Document());
        }
        room = storedRoom("Synthetic Room");
    }

    private static Room storedRoom(String name) {
        Room created = Room.create(UUID.randomUUID(),
                new RoomDetails(name, List.of(new Seat("A1", "A", 1, SeatType.STANDARD))), NOW);
        template.insert(RoomDocumentMapper.toDocument(created), "rooms");
        return created;
    }

    private static Showtime showtime(Room inRoom, int startMinutes, int lengthMinutes) {
        Instant start = NIGHT.plusSeconds(startMinutes * 60L);
        return new Showtime(UUID.randomUUID(), MOVIE_ID, inRoom.id(), "Synthetic Movie", inRoom.details().name(),
                new TimeInterval(start, start.plusSeconds(lengthMinutes * 60L)), 15000, "EN", null,
                ShowtimeStatus.SCHEDULED, NOW, NOW);
    }

    private static ShowtimeWrite creation(Showtime showtime, String key, String hash) {
        return new ShowtimeWrite(showtime, WriteOperation.CREATE_SHOWTIME, key, hash, true, showtime.publishedEvent());
    }

    private static ShowtimeWrite update(Showtime showtime, String key, boolean checksOverlap) {
        return new ShowtimeWrite(showtime, WriteOperation.UPDATE_SHOWTIME, key, HASH, checksOverlap, null);
    }

    private static long count(String collection) {
        return template.getCollection(collection).countDocuments();
    }

    private static int scheduleVersion(Room of) {
        Integer version = template.findById(of.id().toString(), Document.class, "rooms").getInteger("schedule_version");
        return version == null ? 0 : version;
    }

    private void assertNothingWasStoredBeyond(long showtimes, long keys, long events) {
        assertEquals(showtimes, count("showtimes"));
        assertEquals(keys, count("idempotency_key"));
        assertEquals(events, count("outbox_event"));
    }

    @Test
    void aSavedShowtimeIsReadBackWithEveryFieldAndPassesTheValidator() {
        Showtime showtime = showtime(room, 0, 120);

        Showtime saved = repository.save(creation(showtime, "synthetic-key-0001", HASH));

        assertEquals(showtime, saved);
        assertEquals(showtime, repository.findById(showtime.id()).orElseThrow());
        Document stored = template.findById(showtime.id().toString(), Document.class, "showtimes");
        assertInstanceOf(Long.class, stored.get("price_cents"));
    }

    @Test
    void anUnknownShowtimeIsNotFound() {
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void theKeyOfAWriteFindsTheShowtimeItProducedAndAnotherRequestIsAConflict() {
        Showtime showtime = showtime(room, 0, 120);
        repository.save(creation(showtime, "synthetic-key-0001", HASH));

        assertEquals(showtime.id(), repository.findIdempotentResource("synthetic-key-0001", HASH).orElseThrow());
        assertTrue(repository.findIdempotentResource("synthetic-key-9999", HASH).isEmpty());
        assertThrows(IdempotencyKeyConflictException.class,
                () -> repository.findIdempotentResource("synthetic-key-0001", OTHER_HASH));
    }

    @Test
    void theScheduleVersionOfTheRoomGrowsByOnePerWrite() {
        Showtime first = showtime(room, 0, 60);
        Showtime second = showtime(room, 120, 60);

        repository.save(creation(first, "synthetic-key-0001", HASH));
        assertEquals(1, scheduleVersion(room));
        repository.save(creation(second, "synthetic-key-0002", OTHER_HASH));
        assertEquals(2, scheduleVersion(room));
        repository.save(new ShowtimeWrite(first.cancel(NOW.plusSeconds(60)), WriteOperation.CANCEL_SHOWTIME,
                "synthetic-key-0003", HASH, false, null));
        assertEquals(3, scheduleVersion(room));
    }

    @Test
    void aCreationWritesExactlyOneEventWithThePayloadOfTheContract() {
        Showtime showtime = showtime(room, 0, 120);

        repository.save(creation(showtime, "synthetic-key-0001", HASH));

        Document event = template.getCollection("outbox_event").find().first();
        assertEquals(1, count("outbox_event"));
        assertEquals("Showtime", event.getString("aggregate_type"));
        assertEquals(showtime.id().toString(), event.getString("aggregate_id"));
        assertEquals("ShowtimePublished", event.getString("event_type"));
        Document payload = event.get("payload", Document.class);
        assertEquals(showtime.id().toString(), payload.getString("showtimeId"));
        assertEquals(room.id().toString(), payload.getString("roomId"));
        assertEquals("2026-10-23T20:00:00Z", payload.getString("startsAt"));
        assertEquals(15000L, payload.get("priceCents"));
    }

    @Test
    void anUpdateAndACancellationWriteNoEvent() {
        Showtime showtime = showtime(room, 0, 120);
        repository.save(creation(showtime, "synthetic-key-0001", HASH));
        Showtime moved = showtime.change(new ShowtimeChanges(null, null, 20000L, null, null, null), NOW.plusSeconds(60));
        repository.save(update(moved, "synthetic-key-0002", true));
        Showtime cancelled = moved.cancel(NOW.plusSeconds(120));

        repository.save(new ShowtimeWrite(cancelled, WriteOperation.CANCEL_SHOWTIME, "synthetic-key-0003", HASH,
                false, null));

        assertEquals(1, count("outbox_event"));
        assertEquals(1, count("showtimes"));
        assertEquals(ShowtimeStatus.CANCELLED, repository.findById(showtime.id()).orElseThrow().status());
        assertEquals(20000L, repository.findById(showtime.id()).orElseThrow().priceCents());
    }

    @Test
    void aReplayReturnsTheFirstShowtimeAndStoresNothingMore() {
        Showtime showtime = showtime(room, 0, 120);
        repository.save(creation(showtime, "synthetic-key-0001", HASH));

        Showtime replay = repository.save(creation(showtime(room, 0, 120), "synthetic-key-0001", HASH));

        assertEquals(showtime, replay);
        assertNothingWasStoredBeyond(1, 1, 1);
        assertEquals(1, scheduleVersion(room));
    }

    @Test
    void theSameKeyWithAnotherRequestIsAConflictAndStoresNothing() {
        repository.save(creation(showtime(room, 0, 60), "synthetic-key-0001", HASH));
        Showtime other = showtime(room, 300, 60);

        assertThrows(IdempotencyKeyConflictException.class,
                () -> repository.save(creation(other, "synthetic-key-0001", OTHER_HASH)));

        assertTrue(repository.findById(other.id()).isEmpty());
        assertNothingWasStoredBeyond(1, 1, 1);
        assertEquals(1, scheduleVersion(room));
    }

    @Test
    void anOverlappingCreationIsRefusedAndLeavesNothingNotEvenTheCounter() {
        repository.save(creation(showtime(room, 0, 120), "synthetic-key-0001", HASH));
        Showtime overlapping = showtime(room, 60, 120);

        assertThrows(ShowtimeOverlapException.class,
                () -> repository.save(creation(overlapping, "synthetic-key-0002", OTHER_HASH)));

        assertTrue(repository.findById(overlapping.id()).isEmpty());
        assertTrue(repository.findIdempotentResource("synthetic-key-0002", OTHER_HASH).isEmpty());
        assertNothingWasStoredBeyond(1, 1, 1);
        assertEquals(1, scheduleVersion(room));
    }

    @Test
    void aShowtimeThatStartsWhenAnotherEndsIsAccepted() {
        repository.save(creation(showtime(room, 0, 120), "synthetic-key-0001", HASH));

        repository.save(creation(showtime(room, 120, 60), "synthetic-key-0002", OTHER_HASH));

        assertEquals(2, count("showtimes"));
    }

    @Test
    void aCancelledShowtimeDoesNotBlockTheRoom() {
        Showtime first = showtime(room, 0, 120);
        repository.save(creation(first, "synthetic-key-0001", HASH));
        repository.save(new ShowtimeWrite(first.cancel(NOW.plusSeconds(60)), WriteOperation.CANCEL_SHOWTIME,
                "synthetic-key-0002", HASH, false, null));

        repository.save(creation(showtime(room, 30, 60), "synthetic-key-0003", OTHER_HASH));

        assertEquals(2, count("showtimes"));
    }

    @Test
    void aShowtimeOfAnotherRoomDoesNotBlock() {
        Room other = storedRoom("Other Room");
        repository.save(creation(showtime(other, 0, 120), "synthetic-key-0001", HASH));

        repository.save(creation(showtime(room, 0, 120), "synthetic-key-0002", OTHER_HASH));

        assertEquals(2, count("showtimes"));
        assertEquals(1, scheduleVersion(other));
        assertEquals(1, scheduleVersion(room));
    }

    @Test
    void aShowtimeBeingRescheduledDoesNotConflictWithItself() {
        Showtime showtime = showtime(room, 0, 120);
        repository.save(creation(showtime, "synthetic-key-0001", HASH));
        Showtime moved = showtime.change(new ShowtimeChanges(NIGHT.plusSeconds(1800), NIGHT.plusSeconds(9000), null,
                null, null, null), NOW.plusSeconds(60));

        repository.save(update(moved, "synthetic-key-0002", true));

        assertEquals(moved, repository.findById(showtime.id()).orElseThrow());
    }

    @Test
    void aRescheduleOntoAnotherScheduledShowtimeIsRefused() {
        Showtime first = showtime(room, 0, 60);
        Showtime second = showtime(room, 120, 60);
        repository.save(creation(first, "synthetic-key-0001", HASH));
        repository.save(creation(second, "synthetic-key-0002", OTHER_HASH));
        Showtime moved = second.change(new ShowtimeChanges(NIGHT.plusSeconds(1800), NIGHT.plusSeconds(5400), null,
                null, null, null), NOW.plusSeconds(60));

        assertThrows(ShowtimeOverlapException.class, () -> repository.save(update(moved, "synthetic-key-0003", true)));

        assertEquals(second, repository.findById(second.id()).orElseThrow());
        assertEquals(2, scheduleVersion(room));
    }

    @Test
    void whenTheOverlapIsNotCheckedTheShowtimeIsStoredAnyway() {
        Showtime first = showtime(room, 0, 120);
        Showtime second = showtime(room, 60, 120);
        repository.save(creation(first, "synthetic-key-0001", HASH));

        repository.save(update(second, "synthetic-key-0002", false));

        assertEquals(2, count("showtimes"));
    }

    @Test
    void aMissingRoomIsAReferenceErrorAndNothingIsStored() {
        Room missing = Room.create(UUID.randomUUID(),
                new RoomDetails("Ghost", List.of(new Seat("A1", "A", 1, SeatType.STANDARD))), NOW);
        Showtime orphan = showtime(missing, 0, 120);

        assertThrows(ShowtimeReferenceException.class,
                () -> repository.save(creation(orphan, "synthetic-key-0001", HASH)));

        assertNothingWasStoredBeyond(0, 0, 0);
    }

    @Test
    void theListIsOrderedByStartIncludesCancelledOnesAndReportsTheTotal() {
        Showtime late = showtime(room, 600, 60);
        Showtime early = showtime(room, 0, 60);
        Showtime cancelled = showtime(room, 300, 60);
        repository.save(creation(late, "synthetic-key-0001", "1".repeat(64)));
        repository.save(creation(early, "synthetic-key-0002", "2".repeat(64)));
        repository.save(creation(cancelled, "synthetic-key-0003", "3".repeat(64)));
        repository.save(new ShowtimeWrite(cancelled.cancel(NOW.plusSeconds(60)), WriteOperation.CANCEL_SHOWTIME,
                "synthetic-key-0004", HASH, false, null));

        ShowtimePage all = repository.findAll(1, 20);
        ShowtimePage second = repository.findAll(2, 2);

        assertEquals(List.of(early.id(), cancelled.id(), late.id()), all.items().stream().map(Showtime::id).toList());
        assertEquals(3, all.total());
        assertEquals(1, all.page());
        assertEquals(20, all.limit());
        assertEquals(List.of(late.id()), second.items().stream().map(Showtime::id).toList());
        assertEquals(3, second.total());
        assertTrue(repository.findAll(5, 2).items().isEmpty());
    }

    @Test
    void aWindowOutsideTheBoundsOfTheContractIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> repository.findAll(0, 20));
        assertThrows(IllegalArgumentException.class, () -> repository.findAll(1, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.findAll(1, 101));
        assertTrue(repository.findAll(1, 100).items().isEmpty());
    }

    @Test
    void ofSeveralSchedulersOfOverlappingShowtimesInOneRoomExactlyOneWins() throws Exception {
        for (int round = 0; round < 3; round++) {
            emptyTheCollectionsAndStoreARoom();
            final int current = round;
            List<Callable<Showtime>> calls = IntStream.range(0, CALLERS)
                    .<Callable<Showtime>>mapToObj(i -> () -> repository.save(creation(showtime(room, i * 10, 120),
                            "synthetic-key-" + current + "-" + i, String.valueOf(i).repeat(64))))
                    .toList();

            Outcomes outcomes = runTogether(calls);

            assertEquals(1, outcomes.stored.size(), "round " + round);
            assertEquals(CALLERS - 1, outcomes.failures.size(), "round " + round);
            outcomes.failures.forEach(failure -> assertInstanceOf(ShowtimeOverlapException.class, failure));
            assertNothingWasStoredBeyond(1, 1, 1);
            assertEquals(1, scheduleVersion(room));
        }
    }

    @Test
    void ofSeveralSchedulersOfSeparateShowtimesInOneRoomEveryOneIsStored() throws Exception {
        List<Callable<Showtime>> calls = IntStream.range(0, CALLERS)
                .<Callable<Showtime>>mapToObj(i -> () -> repository.save(creation(showtime(room, i * 120, 60),
                        "synthetic-key-" + i, String.valueOf(i).repeat(64))))
                .toList();

        Outcomes outcomes = runTogether(calls);

        assertEquals(CALLERS, outcomes.stored.size(), () -> "failures: " + outcomes.failures);
        assertNothingWasStoredBeyond(CALLERS, CALLERS, CALLERS);
        assertEquals(CALLERS, scheduleVersion(room));
    }

    @Test
    void ofSeveralCallsWithTheSameKeyAndRequestOneShowtimeAndOneEventAreStored() throws Exception {
        List<Callable<Showtime>> calls = IntStream.range(0, CALLERS)
                .<Callable<Showtime>>mapToObj(i -> () -> repository.save(
                        creation(showtime(room, 0, 120), "synthetic-key-race", HASH)))
                .toList();

        Outcomes outcomes = runTogether(calls);

        assertEquals(CALLERS, outcomes.stored.size(), () -> "failures: " + outcomes.failures);
        UUID winner = outcomes.stored.get(0).id();
        outcomes.stored.forEach(stored -> assertEquals(winner, stored.id()));
        assertTrue(repository.findById(winner).isPresent());
        assertNothingWasStoredBeyond(1, 1, 1);
        assertEquals(1, scheduleVersion(room));
    }

    private record Outcomes(List<Showtime> stored, List<Throwable> failures) {
    }

    private static Outcomes runTogether(List<Callable<Showtime>> calls) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Showtime>> futures = calls.stream()
                .map(call -> pool.submit(() -> {
                    start.await();
                    return call.call();
                }))
                .toList();
        start.countDown();
        List<Showtime> stored = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Showtime> future : futures) {
            try {
                stored.add(future.get());
            } catch (ExecutionException failure) {
                failures.add(failure.getCause());
            }
        }
        pool.shutdown();
        return new Outcomes(stored, failures);
    }
}
