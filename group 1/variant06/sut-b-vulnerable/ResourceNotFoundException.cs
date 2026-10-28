namespace Courses;

public class ResourceNotFoundException : ArgumentException {
    public ResourceNotFoundException(string message) : base(message) { }
}
