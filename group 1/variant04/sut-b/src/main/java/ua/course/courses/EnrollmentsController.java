package ua.course.courses;

import java.net.URI;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/enrollments")
public class EnrollmentsController {
    private final EnrollmentService service;
    private final EnrollmentRepository repository;
    public EnrollmentsController(EnrollmentService service, EnrollmentRepository repository) {
        this.service = service;
        this.repository = repository;
    }
    public record CancelInput(int daysBeforeStart) {}
    @PostMapping
    public ResponseEntity<Enrollment> create(@RequestBody EnrollmentRequest input) throws SQLException {
        var saved = service.enroll(input);
        return ResponseEntity.created(URI.create("/enrollments/" + saved.id())).body(saved);
    }
    @GetMapping("/{id}")
    public Enrollment get(@PathVariable long id) throws SQLException {
        var saved = repository.findById(id);
        if (saved == null) throw new ResourceNotFoundException("Заявку не знайдено");
        return saved;
    }
    @PostMapping("/{id}/complete")
    public Enrollment complete(@PathVariable long id) throws SQLException { return service.complete(id); }
    @PostMapping("/{id}/cancel")
    public Enrollment cancel(@PathVariable long id, @RequestBody CancelInput input) throws SQLException {
        return service.cancel(id, input.daysBeforeStart());
    }
}
