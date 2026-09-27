package hotel;

import java.sql.SQLException;
import java.util.List;

public interface BookingRepository {
    long create(Booking booking) throws SQLException;
    Booking findById(long id) throws SQLException;
    List<Booking> findBookings(long roomId, BookingStatus status) throws SQLException;
    List<Booking> findAll() throws SQLException;
    void updateStatus(long id, BookingStatus status) throws SQLException;
    void delete(long id) throws SQLException;
}
