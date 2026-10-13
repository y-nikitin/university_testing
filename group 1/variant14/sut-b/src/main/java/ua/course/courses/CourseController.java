package ua.course.courses;

import java.net.URI;
import java.sql.SQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/courses")
public class CourseController {
    private final CourseRepository repository;
    public CourseController(CourseRepository repository) { this.repository = repository; }
    public record Input(String code, long price, int capacity, int level) {}
    @PostMapping
    public ResponseEntity<Course> create(@RequestBody Input input) throws SQLException {
        var saved = repository.create(new Course(0, input.code(), input.price(), input.capacity(), input.level()));
        return ResponseEntity.created(URI.create("/courses/" + saved.id())).body(saved);
    }
    @GetMapping("/{id}")
    public Course get(@PathVariable long id) throws SQLException {
        var saved = repository.findById(id);
        if (saved == null) throw new ResourceNotFoundException("Курс не знайдено");
        return saved;
    }
}
