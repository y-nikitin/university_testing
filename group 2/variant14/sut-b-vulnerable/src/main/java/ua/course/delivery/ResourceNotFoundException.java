package ua.course.delivery;

public class ResourceNotFoundException extends IllegalArgumentException {
    public ResourceNotFoundException(String message) { super(message); }
}
