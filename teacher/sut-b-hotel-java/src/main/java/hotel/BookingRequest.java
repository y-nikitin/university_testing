package hotel;

public record BookingRequest(String reference, long roomId, int nights, int guests) {
}
