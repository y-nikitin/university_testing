package ua.course.store;
public record OrderItem(long id, long productId, int quantity, long unitPrice, boolean onSale) {}
