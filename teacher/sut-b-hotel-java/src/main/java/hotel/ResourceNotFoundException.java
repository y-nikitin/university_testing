package hotel;

public final class ResourceNotFoundException extends IllegalArgumentException {
    public ResourceNotFoundException(String message) { super(message); }
}
