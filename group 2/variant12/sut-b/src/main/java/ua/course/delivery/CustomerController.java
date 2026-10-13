package ua.course.delivery;

import java.net.URI;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers")
public class CustomerController {
    private final CustomerRepository repository;
    public CustomerController(CustomerRepository repository) { this.repository = repository; }
    public record Input(String code, boolean regular) {}

    @PostMapping
    public ResponseEntity<Customer> create(@RequestBody Input input) throws SQLException {
        var saved = repository.create(new Customer(0, input.code(), input.regular()));
        return ResponseEntity.created(URI.create("/customers/" + saved.id())).body(saved);
    }
    @GetMapping("/{id}")
    public Customer get(@PathVariable long id) throws SQLException {
        var saved = repository.findById(id);
        if (saved == null) throw new ResourceNotFoundException("Клієнта не знайдено");
        return saved;
    }
}
