package hotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class BookingControllerTest {
    @LocalServerPort
    int port;
    @MockitoBean
    RoomRepository rooms;
    @MockitoBean
    BookingRepository bookings;

    @Test
    void httpWorkflow() throws Exception {
        when(rooms.findById(1)).thenReturn(Optional.of(new Room(1, "101", 2, 120000)));
        when(bookings.create(any())).thenReturn(7L);
        var confirmed = new Booking(7, "B1", 1, 7, 2, 798000, BookingStatus.CONFIRMED);
        var cancelled = new Booking(7, "B1", 1, 7, 2, 798000, BookingStatus.CANCELLED);
        when(bookings.findById(7)).thenReturn(confirmed);
        doAnswer(call -> {
            when(bookings.findById(7)).thenReturn(cancelled);
            return null;
        })
                .when(bookings).updateStatus(7, BookingStatus.CANCELLED);
        try (var client = HttpClient.newHttpClient()) {
            String base = "http://127.0.0.1:" + port;
            var created = client.send(HttpRequest.newBuilder(URI.create(base + "/bookings"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"reference\":\"B1\",\"roomId\":1,\"nights\":7,\"guests\":2}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, created.statusCode());
            var json = new ObjectMapper();
            long id = json.readTree(created.body()).get("id").asLong();
            assertEquals(798000, json.readTree(created.body()).get("total").asLong());
            String[] methods = {"GET", "POST", "POST", "GET"};
            int[] statuses = {200, 200, 409, 200};
            for (int step = 0; step < methods.length; step++) {
                String method = methods[step];
                String path = "/bookings/" + id + (method.equals("POST") ? "/cancel" : "");
                var response = client.send(HttpRequest.newBuilder(URI.create(base + path))
                        .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(statuses[step], response.statusCode());
                if (step == 3) {
                    assertEquals("CANCELLED", json.readTree(response.body()).get("status").asText());
                    assertEquals(798000, json.readTree(response.body()).get("total").asLong());
                }
                if (response.statusCode() == 409)
                    assertEquals("BOOKING_CONFLICT", json.readTree(response.body()).get("code").asText());
                else assertEquals(200, response.statusCode());
            }
            var missing = client.send(HttpRequest.newBuilder(URI.create(base + "/bookings/999")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, missing.statusCode());
            verify(bookings, times(1)).updateStatus(7, BookingStatus.CANCELLED);
        }
    }
}
