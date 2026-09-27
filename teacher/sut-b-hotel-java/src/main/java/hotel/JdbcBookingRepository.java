package hotel;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class JdbcBookingRepository implements BookingRepository {
    private final Connection connection;

    public JdbcBookingRepository(Connection connection) {
        this.connection = java.util.Objects.requireNonNull(connection);
    }

    public long create(Booking booking) throws SQLException {
        try (var query = connection.prepareStatement("""
                INSERT INTO bookings(reference, room_id, nights, guests, total, status)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """)) {
            query.setString(1, booking.reference());
            query.setLong(2, booking.roomId());
            query.setInt(3, booking.nights());
            query.setInt(4, booking.guests());
            query.setLong(5, booking.total());
            query.setString(6, booking.status().name());
            try (var rows = query.executeQuery()) { rows.next(); return rows.getLong("id"); }
        }
    }

    public Booking findById(long id) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM bookings WHERE id = ?")) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
                return rows.next() ? map(rows) : null;
            }
        }
    }

    public List<Booking> findBookings(long roomId, BookingStatus status) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT * FROM bookings WHERE room_id = ? AND status = ? ORDER BY id")) {
            query.setLong(1, roomId);
            query.setString(2, status.name());
            try (var rows = query.executeQuery()) {
                var bookings = new ArrayList<Booking>();
                while (rows.next()) bookings.add(map(rows));
                return List.copyOf(bookings);
            }
        }
    }

    public List<Booking> findAll() throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM bookings ORDER BY id");
             var rows = query.executeQuery()) {
            var bookings = new ArrayList<Booking>();
            while (rows.next()) bookings.add(map(rows));
            return List.copyOf(bookings);
        }
    }

    public void updateStatus(long id, BookingStatus status) throws SQLException {
        try (var query = connection.prepareStatement("UPDATE bookings SET status = ? WHERE id = ?")) {
            query.setString(1, status.name());
            query.setLong(2, id);
            if (query.executeUpdate() == 0) throw new IllegalArgumentException("Бронювання не знайдено");
        }
    }

    public void delete(long id) throws SQLException {
        try (var query = connection.prepareStatement("DELETE FROM bookings WHERE id = ?")) {
            query.setLong(1, id);
            query.executeUpdate();
        }
    }

    private static Booking map(ResultSet rows) throws SQLException {
        return new Booking(rows.getLong("id"), rows.getString("reference"), rows.getLong("room_id"),
                rows.getInt("nights"), rows.getInt("guests"), rows.getLong("total"),
                BookingStatus.valueOf(rows.getString("status")));
    }
}
