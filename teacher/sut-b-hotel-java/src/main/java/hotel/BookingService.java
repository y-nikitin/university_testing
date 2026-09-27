package hotel;

import java.sql.SQLException;
import java.util.Objects;

public final class BookingService {
    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final BookingPolicy policy;

    public BookingService(RoomRepository rooms, BookingRepository bookings, BookingPolicy policy) {
        this.rooms = Objects.requireNonNull(rooms);
        this.bookings = Objects.requireNonNull(bookings);
        this.policy = Objects.requireNonNull(policy);
    }

    public Booking createBooking(BookingRequest request) throws SQLException {
        if (request == null) throw new IllegalArgumentException("Заявку не задано");
        String reference = request.reference();
        long roomId = request.roomId();
        int nights = request.nights();
        int guests = request.guests();
        if (reference == null || reference.isBlank() || reference.length() > 40
                || roomId <= 0 || nights < 1 || nights > 30 || guests < 1 || guests > 4)
            throw new IllegalArgumentException("Некоректні дані бронювання");
        Room room = rooms.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Номер не знайдено"));
        if (guests > room.capacity()) throw new BookingRejectedException("Перевищено місткість номера");
        if (!policy.isAllowed(room, nights, guests)) throw new BookingRejectedException("Бронювання не дозволено");
        long base = room.nightlyPrice() * nights;
        long total = nights >= 7 ? base - base * 5 / 100 : base;
        long id = bookings.create(new Booking(0, reference, roomId, nights, guests, total, BookingStatus.CONFIRMED));
        return new Booking(id, reference, roomId, nights, guests, total, BookingStatus.CONFIRMED);
    }

    public Booking cancelBooking(long id) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("Некоректний ідентифікатор");
        Booking booking = bookings.findById(id);
        if (booking == null) throw new IllegalArgumentException("Бронювання не знайдено");
        if (booking.status() == BookingStatus.CANCELLED)
            throw new IllegalStateException("Бронювання вже скасовано");
        bookings.updateStatus(id, BookingStatus.CANCELLED);
        return new Booking(booking.id(), booking.reference(), booking.roomId(), booking.nights(),
                booking.guests(), booking.total(), BookingStatus.CANCELLED);
    }
}
