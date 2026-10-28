package ua.course.rental;

public class ResourceNotFoundException extends IllegalArgumentException {
    public ResourceNotFoundException(String message) { super(message); }
}
