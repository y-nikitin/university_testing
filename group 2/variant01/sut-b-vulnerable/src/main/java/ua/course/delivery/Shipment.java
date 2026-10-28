package ua.course.delivery;
public record Shipment(long id,String reference,long customerId,long parcelId,int distanceKm,boolean urgent,boolean pickup,boolean regular,boolean couponValid,boolean fragile,int weightGrams,long declaredValue,long baseFee,long adjustment,long total,long refund,ShipmentStatus status) {}
