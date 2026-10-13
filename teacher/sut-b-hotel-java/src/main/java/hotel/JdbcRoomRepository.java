package hotel;

import java.sql.*;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Repository
public class JdbcRoomRepository implements RoomRepository {
    private final JdbcTemplate jdbc;

    @Autowired
    public JdbcRoomRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Для попередніх тестів: з’єднання закриває той, хто його відкрив.
    public JdbcRoomRepository(Connection connection) {
        this(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
    }

    public Room create(String number, int capacity, long nightlyPrice) {
        return jdbc.queryForObject("INSERT INTO rooms(number, capacity, nightly_price) VALUES (?, ?, ?) RETURNING *",
                (rows, index) -> map(rows), number, capacity, nightlyPrice);
    }

    public Optional<Room> findById(long id) {
        return jdbc.query("SELECT * FROM rooms WHERE id = ?", (rows, index) -> map(rows), id).stream().findFirst();
    }

    public void update(Room room) {
        if (jdbc.update("UPDATE rooms SET number = ?, capacity = ?, nightly_price = ? WHERE id = ?",
                room.number(), room.capacity(), room.nightlyPrice(), room.id()) == 0)
            throw new ResourceNotFoundException("Номер не знайдено");
    }

    public void delete(long id) { jdbc.update("DELETE FROM rooms WHERE id = ?", id); }

    private static Room map(ResultSet rows) throws SQLException {
        return new Room(rows.getLong("id"), rows.getString("number"),
                rows.getInt("capacity"), rows.getLong("nightly_price"));
    }
}
