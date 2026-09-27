package hotel;

@FunctionalInterface
public interface BookingPolicy {
    boolean isAllowed(Room room, int nights, int guests);
}
