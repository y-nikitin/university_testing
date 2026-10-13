package ua.course.courses;

import java.net.URI;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/students")
public class StudentController {
    private final StudentRepository repository;
    public StudentController(StudentRepository repository) { this.repository = repository; }
    public record Input(String code, int age, int entryScore, boolean regular) {}
    @PostMapping
    public ResponseEntity<Student> create(@RequestBody Input input) throws SQLException {
        var saved = repository.create(new Student(0, input.code(), input.age(), input.entryScore(), input.regular()));
        return ResponseEntity.created(URI.create("/students/" + saved.id())).body(saved);
    }
    @GetMapping("/{id}")
    public Student get(@PathVariable long id) throws SQLException {
        var saved = repository.findById(id);
        if (saved == null) throw new ResourceNotFoundException("Слухача не знайдено");
        return saved;
    }
}
