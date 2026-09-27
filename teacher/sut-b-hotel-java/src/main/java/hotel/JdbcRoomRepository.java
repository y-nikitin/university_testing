package hotel;

import java.sql.*;
import java.util.Optional;

public final class JdbcRoomRepository implements RoomRepository {
    private final Connection connection;

    public JdbcRoomRepository(Connection connection) {
        this.connection = java.util.Objects.requireNonNull(connection);
    }

    public Room create(String number, int capacity, long nightlyPrice) throws SQLException {
        try (var query = connection.prepareStatement(
                "INSERT INTO rooms(number, capacity, nightly_price) VALUES (?, ?, ?) RETURNING *")) {
            query.setString(1, number);
            query.setInt(2, capacity);
            query.setLong(3, nightlyPrice);
            try (var rows = query.executeQuery()) { rows.next(); return map(rows); }
        }
    }

    public Optional<Room> findById(long id) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM rooms WHERE id = ?")) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public void update(Room room) throws SQLException {
        try (var query = connection.prepareStatement(
                "UPDATE rooms SET number = ?, capacity = ?, nightly_price = ? WHERE id = ?")) {
            query.setString(1, room.number());
            query.setInt(2, room.capacity());
            query.setLong(3, room.nightlyPrice());
            query.setLong(4, room.id());
            if (query.executeUpdate() == 0) throw new IllegalArgumentException("Номер не знайдено");
        }
    }

    public void delete(long id) throws SQLException {
        try (var query = connection.prepareStatement("DELETE FROM rooms WHERE id = ?")) {
            query.setLong(1, id);
            query.executeUpdate();
        }
    }

    private static Room map(ResultSet rows) throws SQLException {
        return new Room(rows.getLong("id"), rows.getString("number"),
                rows.getInt("capacity"), rows.getLong("nightly_price"));
    }
}
