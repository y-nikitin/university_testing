package hotel;

public record Booking(long id, String reference, long roomId, int nights,
                      int guests, long total, BookingStatus status) {}
