package ua.course.delivery;
public record ShipmentRequest(String reference,long customerId,long parcelId,int distanceKm,boolean urgent,boolean pickup,boolean couponValid) {}
