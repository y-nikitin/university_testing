package hotel;

public final class BookingRejectedException extends IllegalStateException {
    public BookingRejectedException(String message) { super(message); }
}
