package ua.course.rental;

import java.net.URI;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rentals")
public class RentalsController {
    private final RentalService service;
    private final RentalRepository rentals;
    public RentalsController(RentalService service, RentalRepository rentals) {
        this.service = service;
        this.rentals = rentals;
    }

    @PostMapping
    public ResponseEntity<RentalOrder> create(@RequestBody RentalRequest input) throws SQLException {
        var saved = service.rent(input);
        return ResponseEntity.created(URI.create("/rentals/" + saved.id())).body(saved);
    }

    @GetMapping("/{id}")
    public RentalOrder get(@PathVariable long id) throws SQLException {
        var found = rentals.findById(id);
        if (found == null) throw new ResourceNotFoundException("Оренду не знайдено");
        return found;
    }

    @PostMapping("/{id}/return")
    public RentalOrder returnRental(@PathVariable long id) throws SQLException {
        return service.returnRental(id);
    }
}
