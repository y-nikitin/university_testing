package ua.course.delivery;
@FunctionalInterface
public interface ShippingPolicy { boolean isAllowed(Customer customer,Parcel parcel,int distanceKm); }
