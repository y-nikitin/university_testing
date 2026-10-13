package hotel;

import java.sql.*;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Repository
public class JdbcBookingRepository implements BookingRepository {
    private final JdbcTemplate jdbc;

    @Autowired
    public JdbcBookingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Для попередніх тестів: з’єднання закриває той, хто його відкрив.
    public JdbcBookingRepository(Connection connection) {
        this(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
    }

    public long create(Booking booking) {
        return jdbc.queryForObject("""
                INSERT INTO bookings(reference, room_id, nights, guests, total, status)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, booking.reference(), booking.roomId(), booking.nights(),
                booking.guests(), booking.total(), booking.status().name());
    }

    public Booking findById(long id) {
        return jdbc.query("SELECT * FROM bookings WHERE id = ?", (rows, index) -> map(rows), id)
                .stream().findFirst().orElse(null);
    }

    public List<Booking> findBookings(long roomId, BookingStatus status) {
        return List.copyOf(jdbc.query("SELECT * FROM bookings WHERE room_id = ? AND status = ? ORDER BY id",
                (rows, index) -> map(rows), roomId, status.name()));
    }

    public List<Booking> findAll() {
        return List.copyOf(jdbc.query("SELECT * FROM bookings ORDER BY id", (rows, index) -> map(rows)));
    }

    public void updateStatus(long id, BookingStatus status) {
        if (jdbc.update("UPDATE bookings SET status = ? WHERE id = ?", status.name(), id) == 0)
            throw new ResourceNotFoundException("Бронювання не знайдено");
    }

    public void delete(long id) { jdbc.update("DELETE FROM bookings WHERE id = ?", id); }

    private static Booking map(ResultSet rows) throws SQLException {
        return new Booking(rows.getLong("id"), rows.getString("reference"), rows.getLong("room_id"),
                rows.getInt("nights"), rows.getInt("guests"), rows.getLong("total"),
                BookingStatus.valueOf(rows.getString("status")));
    }
}
