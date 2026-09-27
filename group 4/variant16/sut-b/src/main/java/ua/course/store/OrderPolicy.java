package ua.course.store;
@FunctionalInterface
public interface OrderPolicy { boolean isAllowed(Product product, int quantity); }
