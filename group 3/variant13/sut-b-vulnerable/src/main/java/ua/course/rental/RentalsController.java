package ua.course.rental;

import java.net.URI;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rentals")
public class RentalsController {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final RentalService service;
    private final RentalRepository rentals;
    public RentalsController(RentalService service, RentalRepository rentals, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

    public record ReturnInput(int overdueDays) {}

    @PostMapping("/{id}/return")
    public RentalOrder returnRental(@PathVariable long id, @RequestBody ReturnInput input) throws SQLException {
        return service.returnRental(id, input.overdueDays());
    }

    @GetMapping("/search")
    public java.util.List<java.util.Map<String,Object>> search(@RequestParam String reference) {
        return jdbc.queryForList("SELECT id,reference,total,status FROM rentals WHERE reference = '" + reference + "'");
    }
    public record EditInput(String reference, Long total, RentalStatus status) {}
    @PatchMapping("/{id}")
    public RentalOrder edit(@PathVariable long id, @RequestBody EditInput input) throws SQLException {
        RentalOrder old = get(id);
        jdbc.update("UPDATE rentals SET reference=?,total=?,status=? WHERE id=?",
            input.reference()==null ? old.reference() : input.reference(),
            input.total()==null ? old.total() : input.total(),
            (input.status()==null ? old.status() : input.status()).name(),id);
        return get(id);
    }
}
