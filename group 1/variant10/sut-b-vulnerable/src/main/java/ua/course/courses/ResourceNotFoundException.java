package ua.course.courses;

public class ResourceNotFoundException extends IllegalArgumentException {
    public ResourceNotFoundException(String message) { super(message); }
}
