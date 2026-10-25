package co.edu.corhuila.csp.catalog.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.csp.catalog.application.port.in.ArchiveRoomInput;
import co.edu.corhuila.csp.catalog.application.port.in.CreateRoomInput;
import co.edu.corhuila.csp.catalog.application.port.in.RoomUseCases;
import co.edu.corhuila.csp.catalog.application.port.in.UpdateRoomInput;
import co.edu.corhuila.csp.catalog.application.port.out.IdempotencyKeyConflictException;
import co.edu.corhuila.csp.catalog.application.port.out.RoomNameAlreadyInUseException;
import co.edu.corhuila.csp.catalog.application.port.out.RoomPage;
import co.edu.corhuila.csp.catalog.domain.model.Room;
import co.edu.corhuila.csp.catalog.domain.model.RoomDetails;
import co.edu.corhuila.csp.catalog.domain.model.RoomNotFoundException;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The routes of the rooms: statuses, headers, DTOs and the data handed to the use cases (catalog-service.yaml). */
class RoomControllerTest {

    private static final UUID ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00.123Z");
    private static final String KEY = "synthetic-key-0001";
    private static final String CREATE = """
            {"name":"Synthetic Room","capacity":2,
             "seats":[{"label":"A1","row":"A","number":1,"type":"STANDARD"},{"label":"A2","type":"VIP"}]}""";

    private final RoomUseCases useCases = mock(RoomUseCases.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RoomController(useCases))
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                    .findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
            .addFilters(new CorrelationIdFilter())
            .build();

    private static Room room() {
        return Room.create(ID, new RoomDetails("Synthetic Room", List.of(
                new Seat("A1", "A", 1, SeatType.STANDARD), new Seat("A2", null, null, SeatType.VIP))), NOW);
    }

    private static MockHttpServletRequestBuilder creation(String body) {
        return post("/rooms").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void creatingARoomAnswers201WithItsLocationAndEveryFieldOfTheContract() throws Exception {
        when(useCases.createRoom(any())).thenReturn(room());

        mvc.perform(creation(CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/rooms/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.name").value("Synthetic Room"))
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.seats[0].label").value("A1"))
                .andExpect(jsonPath("$.seats[0].row").value("A"))
                .andExpect(jsonPath("$.seats[0].number").value(1))
                .andExpect(jsonPath("$.seats[0].type").value("STANDARD"))
                .andExpect(jsonPath("$.seats[1].label").value("A2"))
                .andExpect(jsonPath("$.seats[1].type").value("VIP"));
    }

    @Test
    void theCreationHandsTheKeyTheDetailsAndTheDeclaredCapacityToTheUseCase() throws Exception {
        when(useCases.createRoom(any())).thenReturn(room());

        mvc.perform(creation(CREATE));

        ArgumentCaptor<CreateRoomInput> input = ArgumentCaptor.forClass(CreateRoomInput.class);
        verify(useCases).createRoom(input.capture());
        assertEquals(KEY, input.getValue().idempotencyKey());
        assertEquals(2, input.getValue().declaredCapacity());
        assertEquals("Synthetic Room", input.getValue().details().name());
        assertEquals(new Seat("A1", "A", 1, SeatType.STANDARD), input.getValue().details().seats().get(0));
        assertEquals(SeatType.VIP, input.getValue().details().seats().get(1).type());
    }

    @Test
    void theCapacityIsOptionalInTheRequest() throws Exception {
        when(useCases.createRoom(any())).thenReturn(room());

        mvc.perform(creation("{\"name\":\"Synthetic Room\",\"seats\":[{\"label\":\"A1\"}]}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateRoomInput> input = ArgumentCaptor.forClass(CreateRoomInput.class);
        verify(useCases).createRoom(input.capture());
        assertNull(input.getValue().declaredCapacity());
    }

    @Test
    void aSeatWithoutRowAndNumberIsAnsweredWithoutThemAndWithTheStandardType() throws Exception {
        Room plain = Room.create(ID, new RoomDetails("Plain Room", List.of(new Seat("S1", null, null, null))), NOW);
        when(useCases.getRoom(ID)).thenReturn(plain);

        mvc.perform(get("/rooms/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[0].label").value("S1"))
                .andExpect(jsonPath("$.seats[0].row").doesNotExist())
                .andExpect(jsonPath("$.seats[0].number").doesNotExist())
                .andExpect(jsonPath("$.seats[0].type").value("STANDARD"));
    }

    @Test
    void aRepeatedSeatLabelIsA400WithTheEnvelopeAndTheUseCaseIsNeverCalled() throws Exception {
        mvc.perform(creation("{\"name\":\"Synthetic Room\",\"seats\":[{\"label\":\"A1\"},{\"label\":\"A1\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("the seat label A1 is repeated"));

        verifyNoInteractions(useCases);
    }

    @Test
    void aMissingNameOrMissingSeatsOrABlankLabelIsA400AndTheUseCaseIsNeverCalled() throws Exception {
        mvc.perform(creation("{\"seats\":[{\"label\":\"A1\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mvc.perform(creation("{\"name\":\"Synthetic Room\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mvc.perform(creation("{\"name\":\"Synthetic Room\",\"seats\":[{\"row\":\"A\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(useCases);
    }

    @Test
    void aSeatTypeOutsideTheEnumOrABrokenBodyIsA400() throws Exception {
        mvc.perform(creation("{\"name\":\"Synthetic Room\",\"seats\":[{\"label\":\"A1\",\"type\":\"BALCONY\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mvc.perform(creation("{")).andExpect(status().isBadRequest());

        verifyNoInteractions(useCases);
    }

    @Test
    void theIdempotencyKeyIsRequiredAndBetween8And128CharactersOnTheThreeWrites() throws Exception {
        mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/rooms").header("Idempotency-Key", "short").contentType(MediaType.APPLICATION_JSON)
                .content(CREATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("the Idempotency-Key must have between 8 and 128 characters"));
        mvc.perform(post("/rooms").header("Idempotency-Key", "k".repeat(129)).contentType(MediaType.APPLICATION_JSON)
                .content(CREATE))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/rooms/" + ID).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/rooms/" + ID).header("Idempotency-Key", "short").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/rooms/" + ID)).andExpect(status().isBadRequest());
        mvc.perform(delete("/rooms/" + ID).header("Idempotency-Key", "k".repeat(129)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCases);
    }

    @Test
    void aNameInUseAndAKeyUsedWithAnotherRequestAreA409() throws Exception {
        when(useCases.createRoom(any())).thenThrow(new RoomNameAlreadyInUseException());

        mvc.perform(creation(CREATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("A room with that name already exists"));

        doThrow(new IdempotencyKeyConflictException()).when(useCases).createRoom(any());

        mvc.perform(creation(CREATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void aRoomIsReadById() throws Exception {
        when(useCases.getRoom(ID)).thenReturn(room());

        mvc.perform(get("/rooms/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.capacity").value(2));
    }

    @Test
    void anUnknownRoomIsA404AndAPathThatIsNotAUuidIsA400() throws Exception {
        when(useCases.getRoom(ID)).thenThrow(new RoomNotFoundException(ID));

        mvc.perform(get("/rooms/" + ID)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mvc.perform(get("/rooms/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void theListUsesPage1AndLimit20ByDefaultAndReportsThePaginationMetadata() throws Exception {
        when(useCases.listRooms(1, 20)).thenReturn(new RoomPage(List.of(room()), 1, 20, 41));

        mvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.data[0].seats[1].label").value("A2"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.total").value(41))
                .andExpect(jsonPath("$.meta.totalPages").value(3));
    }

    @Test
    void theListTakesThePageAndTheLimitOfTheRequestAndAnEmptyListHasNoPages() throws Exception {
        when(useCases.listRooms(2, 10)).thenReturn(new RoomPage(List.of(), 2, 10, 0));

        mvc.perform(get("/rooms?page=2&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    void aWindowOutOfBoundsOrNotANumberIsA400() throws Exception {
        when(useCases.listRooms(1, 101))
                .thenThrow(new IllegalArgumentException("page starts at 1 and limit is between 1 and 100"));

        mvc.perform(get("/rooms?limit=101")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page starts at 1 and limit is between 1 and 100"));
        mvc.perform(get("/rooms?page=one")).andExpect(status().isBadRequest());
    }

    @Test
    void aPartialUpdateHandsOnlyTheFieldsThatWereSentAndAnswers200() throws Exception {
        when(useCases.updateRoom(any())).thenReturn(room());

        mvc.perform(patch("/rooms/" + ID).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));

        ArgumentCaptor<UpdateRoomInput> input = ArgumentCaptor.forClass(UpdateRoomInput.class);
        verify(useCases).updateRoom(input.capture());
        assertEquals(KEY, input.getValue().idempotencyKey());
        assertEquals(ID, input.getValue().roomId());
        assertEquals("New name", input.getValue().changes().name());
        assertNull(input.getValue().changes().seats());
    }

    @Test
    void anUpdateOfTheSeatsHandsTheNewLayout() throws Exception {
        when(useCases.updateRoom(any())).thenReturn(room());

        mvc.perform(patch("/rooms/" + ID).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"seats\":[{\"label\":\"B1\",\"type\":\"ACCESSIBLE\"}]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateRoomInput> input = ArgumentCaptor.forClass(UpdateRoomInput.class);
        verify(useCases).updateRoom(input.capture());
        assertNull(input.getValue().changes().name());
        assertEquals(List.of(new Seat("B1", null, null, SeatType.ACCESSIBLE)), input.getValue().changes().seats());
    }

    @Test
    void archivingAnswers204WithoutBodyAndHandsTheKeyAndTheIdToTheUseCase() throws Exception {
        mvc.perform(delete("/rooms/" + ID).header("Idempotency-Key", KEY))
                .andExpect(status().isNoContent());

        ArgumentCaptor<ArchiveRoomInput> input = ArgumentCaptor.forClass(ArchiveRoomInput.class);
        verify(useCases).archiveRoom(input.capture());
        assertEquals(KEY, input.getValue().idempotencyKey());
        assertEquals(ID, input.getValue().roomId());
    }

    @Test
    void anUnknownRoomCannotBeArchived() throws Exception {
        doThrow(new RoomNotFoundException(ID)).when(useCases).archiveRoom(any());

        mvc.perform(delete("/rooms/" + ID).header("Idempotency-Key", KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
