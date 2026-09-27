package hotel;

import java.sql.SQLException;
import java.util.Optional;

public interface RoomRepository {
    Room create(String number, int capacity, long nightlyPrice) throws SQLException;
    Optional<Room> findById(long id) throws SQLException;
    void update(Room room) throws SQLException;
    void delete(long id) throws SQLException;
}
