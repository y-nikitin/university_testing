package ua.course.rental;
public record RentalRequest(String reference, long equipmentId, int quantity, int days, boolean premium) {}
