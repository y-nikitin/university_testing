package hotel;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.sql.SQLException;

@RestController
@RequestMapping("/rooms")
public class RoomController {
    private final RoomRepository rooms;
    public RoomController(RoomRepository rooms) { this.rooms = rooms; }

    public record RoomRequest(String number, int capacity, long nightlyPrice) {}

    @PostMapping
    public ResponseEntity<Room> create(@RequestBody RoomRequest request) throws SQLException {
        Room room = rooms.create(request.number(), request.capacity(), request.nightlyPrice());
        return ResponseEntity.created(URI.create("/rooms/" + room.id())).body(room);
    }

    @GetMapping("/{id}")
    public Room get(@PathVariable("id") long id) throws SQLException {
        return rooms.findById(id).orElseThrow(() -> new ResourceNotFoundException("Номер не знайдено"));
    }
}
