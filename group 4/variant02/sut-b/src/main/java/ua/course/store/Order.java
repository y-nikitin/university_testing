package ua.course.store;
public record Order(long id, String reference, boolean premium, boolean couponValid, long total, OrderStatus status, OrderItem item) {}
