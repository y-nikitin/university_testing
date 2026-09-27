using System.Collections.Generic;
namespace Delivery;
public enum ShipmentStatus { Registered, Dispatched, Delivered, Cancelled }
public record Customer(long Id, string Code, bool Regular);
public record Parcel(long Id, string Code, int WeightGrams, long DeclaredValue);
public record Shipment(long Id, string Reference, long CustomerId, long ParcelId, int DistanceKm,
    bool Urgent, bool Pickup, bool Regular, int WeightGrams, long DeclaredValue,
    long BaseFee, long Adjustment, long Total, ShipmentStatus Status);
public record ShipmentRequest(string Reference, long CustomerId, long ParcelId, int DistanceKm, bool Urgent = false, bool Pickup = false);
public interface ICustomerRepository {
    Customer Create(Customer customer);
    Customer? FindById(long id);
    void Update(Customer customer);
    void Delete(long id);
}
public interface IParcelRepository {
    Parcel Create(Parcel parcel);
    Parcel? FindById(long id);
    void Update(Parcel parcel);
    void Delete(long id);
}
public interface IShipmentRepository {
    Shipment Create(Shipment shipment);
    Shipment? FindById(long id);
    List<Shipment> FindShipments(long customerId, ShipmentStatus status);
    void UpdateStatus(long id, ShipmentStatus expected, ShipmentStatus next);
    void Delete(long id);
}
public interface IShippingPolicy {
    bool IsAllowed(Customer customer, Parcel parcel, int distanceKm);
}
