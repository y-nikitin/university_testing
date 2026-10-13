package hotel;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.sql.SQLException;

@RestController
@RequestMapping("/bookings")
public class BookingController {
    private final BookingService service;
    private final BookingRepository bookings;

    public BookingController(BookingService service, BookingRepository bookings) {
        this.service = service;
        this.bookings = bookings;
    }

    @PostMapping
    public ResponseEntity<Booking> create(@RequestBody BookingRequest request) throws SQLException {
        Booking booking = service.createBooking(request);
        return ResponseEntity.created(URI.create("/bookings/" + booking.id())).body(booking);
    }

    @GetMapping("/{id}")
    public Booking get(@PathVariable("id") long id) throws SQLException {
        Booking booking = bookings.findById(id);
        if (booking == null) throw new ResourceNotFoundException("Бронювання не знайдено");
        return booking;
    }

    @PostMapping("/{id}/cancel")
    public Booking cancel(@PathVariable("id") long id) throws SQLException {
        return service.cancelBooking(id);
    }
}
