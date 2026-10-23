package co.edu.corhuila.csp.catalog.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.csp.catalog.application.port.in.AvailabilityUseCases;
import co.edu.corhuila.csp.catalog.application.port.out.ShowtimeAvailability;
import co.edu.corhuila.csp.catalog.domain.model.Seat;
import co.edu.corhuila.csp.catalog.domain.model.SeatType;
import co.edu.corhuila.csp.catalog.domain.model.ShowtimeNotFoundException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The availability route of Booking: status, DTO and the id handed to the use case (catalog-service.yaml, ADR-042). */
class AvailabilityControllerTest {

    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final Instant STARTS = Instant.parse("2026-11-01T20:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-11-01T22:00:00Z");
    private static final String TRACE = "550e8400-e29b-41d4-a716-446655440000";

    private final AvailabilityUseCases useCases = mock(AvailabilityUseCases.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AvailabilityController(useCases))
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                    .findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
            .addFilters(new CorrelationIdFilter())
            .build();

    private static ShowtimeAvailability availability() {
        return new ShowtimeAvailability(ID, "Synthetic Movie", "Synthetic Room", STARTS, ENDS, 1500,
                List.of(new Seat("B2", "B", 2, SeatType.VIP), new Seat("A1", "A", 1, SeatType.STANDARD),
                        new Seat("A2", null, null, SeatType.ACCESSIBLE)));
    }

    @Test
    void theAvailabilityAnswers200WithEveryFieldOfTheContract() throws Exception {
        when(useCases.getAvailability(ID)).thenReturn(availability());

        mvc.perform(get("/showtimes/" + ID + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showtimeId").value(ID.toString()))
                .andExpect(jsonPath("$.movieTitle").value("Synthetic Movie"))
                .andExpect(jsonPath("$.roomName").value("Synthetic Room"))
                .andExpect(jsonPath("$.startsAt").value("2026-11-01T20:00:00Z"))
                .andExpect(jsonPath("$.endsAt").value("2026-11-01T22:00:00Z"))
                .andExpect(jsonPath("$.price").value(1500))
                .andExpect(jsonPath("$.seats.length()").value(3));
        verify(useCases).getAvailability(ID);
    }

    @Test
    void theAnswerHasNoFieldBesidesThoseOfTheContract() throws Exception {
        when(useCases.getAvailability(ID)).thenReturn(availability());

        mvc.perform(get("/showtimes/" + ID + "/availability"))
                .andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void thePriceIsAnIntegerNumberOfCentsNeverATextOrADecimal() throws Exception {
        when(useCases.getAvailability(ID)).thenReturn(availability());

        String body = mvc.perform(get("/showtimes/" + ID + "/availability"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(true, body.contains("\"price\":1500,") || body.contains("\"price\":1500}"), body);
    }

    @Test
    void theSeatsAreTheLabelsInTheOrderTheUseCaseGaveThem() throws Exception {
        when(useCases.getAvailability(ID)).thenReturn(availability());

        mvc.perform(get("/showtimes/" + ID + "/availability"))
                .andExpect(jsonPath("$.seats[0]").value("B2"))
                .andExpect(jsonPath("$.seats[1]").value("A1"))
                .andExpect(jsonPath("$.seats[2]").value("A2"));
    }

    @Test
    void theSeatLayoutHasTheLabelTheRowTheNumberAndTheTypeOfEverySeatInOrder() throws Exception {
        when(useCases.getAvailability(ID)).thenReturn(availability());

        mvc.perform(get("/showtimes/" + ID + "/availability"))
                .andExpect(jsonPath("$.seatLayout.length()").value(3))
                .andExpect(jsonPath("$.seatLayout[0].label").value("B2"))
                .andExpect(jsonPath("$.seatLayout[0].row").value("B"))
                .andExpect(jsonPath("$.seatLayout[0].number").value(2))
                .andExpect(jsonPath("$.seatLayout[0].type").value("VIP"))
                .andExpect(jsonPath("$.seatLayout[1].label").value("A1"))
                .andExpect(jsonPath("$.seatLayout[1].type").value("STANDARD"))
                .andExpect(jsonPath("$.seatLayout[2].label").value("A2"))
                .andExpect(jsonPath("$.seatLayout[2].type").value("ACCESSIBLE"));
    }

    @Test
    void aSeatWithNoRowOrNumberLeavesThoseFieldsOutNeverNull() throws Exception {
        when(useCases.getAvailability(ID)).thenReturn(availability());

        mvc.perform(get("/showtimes/" + ID + "/availability"))
                .andExpect(jsonPath("$.seatLayout[2].row").doesNotExist())
                .andExpect(jsonPath("$.seatLayout[2].number").doesNotExist())
                .andExpect(jsonPath("$.seatLayout[2].length()").value(2));
    }

    @Test
    void aRoomWithoutSeatsAnswersAnEmptyList() throws Exception {
        when(useCases.getAvailability(ID)).thenReturn(
                new ShowtimeAvailability(ID, "Synthetic Movie", "Synthetic Room", STARTS, ENDS, 0, List.of()));

        mvc.perform(get("/showtimes/" + ID + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(0))
                .andExpect(jsonPath("$.seats.length()").value(0))
                .andExpect(jsonPath("$.seatLayout.length()").value(0));
    }

    @Test
    void theCorrelationIdOfTheRequestIsEchoed() throws Exception {
        when(useCases.getAvailability(ID)).thenReturn(availability());

        mvc.perform(get("/showtimes/" + ID + "/availability").header("X-Correlation-Id", TRACE))
                .andExpect(header().string("X-Correlation-Id", TRACE));
    }

    @Test
    void aShowtimeThatIsNotAvailableIsA404WithTheErrorEnvelope() throws Exception {
        when(useCases.getAvailability(ID)).thenThrow(new ShowtimeNotFoundException(ID));

        mvc.perform(get("/showtimes/" + ID + "/availability"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "123", "33333333-3333-4333-8333-33333333333g"})
    void aShowtimeIdThatIsNotAUuidIsA400BeforeTheUseCaseIsAsked(String id) throws Exception {
        mvc.perform(get("/showtimes/" + id + "/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").exists());

        verifyNoInteractions(useCases);
    }

    @Test
    void theRouteOnlyReadsSoAWriteMethodIsRefusedWithoutReachingTheUseCase() throws Exception {
        mvc.perform(post("/showtimes/" + ID + "/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verifyNoInteractions(useCases);
    }
}
