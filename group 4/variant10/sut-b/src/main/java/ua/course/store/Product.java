package ua.course.store;
public record Product(long id, String sku, long unitPrice, int stock, boolean onSale) {}
