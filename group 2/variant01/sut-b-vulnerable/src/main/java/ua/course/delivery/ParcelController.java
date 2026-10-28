package ua.course.delivery;

import java.net.URI;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/parcels")
public class ParcelController {
    private final ParcelRepository repository;
    public ParcelController(ParcelRepository repository) { this.repository = repository; }
    public record Input(String code, int weightGrams, long declaredValue, boolean fragile) {}

    @PostMapping
    public ResponseEntity<Parcel> create(@RequestBody Input input) throws SQLException {
        var saved = repository.create(new Parcel(0, input.code(), input.weightGrams(), input.declaredValue(), input.fragile()));
        return ResponseEntity.created(URI.create("/parcels/" + saved.id())).body(saved);
    }
    @GetMapping("/{id}")
    public Parcel get(@PathVariable long id) throws SQLException {
        var saved = repository.findById(id);
        if (saved == null) throw new ResourceNotFoundException("Посилку не знайдено");
        return saved;
    }
}
