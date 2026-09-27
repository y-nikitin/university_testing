package ua.course.rental;
@FunctionalInterface
public interface RentalPolicy { boolean isAllowed(Equipment equipment, int quantity, int days); }
