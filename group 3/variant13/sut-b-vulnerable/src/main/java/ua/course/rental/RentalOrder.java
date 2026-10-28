package ua.course.rental;
public record RentalOrder(long id, String reference, int days, boolean premium, long total, long lateFee, RentalStatus status, RentalItem item) {}
