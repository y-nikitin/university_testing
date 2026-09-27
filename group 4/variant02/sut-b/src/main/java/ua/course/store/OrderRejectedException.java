package ua.course.store;
public class OrderRejectedException extends RuntimeException {
    public OrderRejectedException(String message) { super(message); }
}
