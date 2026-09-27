using System;
namespace Delivery;
public class ShipmentRejectedException : Exception {
    public ShipmentRejectedException(string message) : base(message) { }
}
public class ShippingService {
    private readonly ICustomerRepository customers;
    private readonly IParcelRepository parcels;
    private readonly IShipmentRepository shipments;
    private readonly IShippingPolicy policy;
    public ShippingService(ICustomerRepository customers, IParcelRepository parcels, IShipmentRepository shipments, IShippingPolicy policy) {
        this.customers = customers ?? throw new ArgumentNullException(nameof(customers));
        this.parcels = parcels ?? throw new ArgumentNullException(nameof(parcels));
        this.shipments = shipments ?? throw new ArgumentNullException(nameof(shipments));
        this.policy = policy ?? throw new ArgumentNullException(nameof(policy));
    }
    public long CalculateBaseFee(int weightGrams, int distanceKm) {
        if (weightGrams < 1 || weightGrams > 30000 || distanceKm < 1 || distanceKm > 5000)
            throw new ArgumentException("Вага або відстань поза допустимими межами");
        return 5000L + ((weightGrams + 999) / 1000) * 1000L + distanceKm * 10L;
    }
    public long CalculateDiscount(long baseFee, bool pickup, bool regular) {
        if (baseFee < 6010 || baseFee > 85000) throw new ArgumentOutOfRangeException(nameof(baseFee));
        int percent = (pickup ? 10 : 0) + (regular ? 5 : 0);
        return Math.Min(10000, baseFee * percent / 100);
    }
    public Shipment CreateShipment(ShipmentRequest request) {
        if (request == null || string.IsNullOrWhiteSpace(request.Reference) || request.Reference.Length > 40 || request.CustomerId <= 0 || request.ParcelId <= 0)
            throw new ArgumentException("Некоректна заявка");
        if (request.DistanceKm < 1 || request.DistanceKm > 5000) throw new ArgumentOutOfRangeException(nameof(request.DistanceKm));
        var customer = customers.FindById(request.CustomerId) ?? throw new ArgumentException("Клієнта не знайдено");
        var parcel = parcels.FindById(request.ParcelId) ?? throw new ArgumentException("Посилку не знайдено");
        if (parcel.DeclaredValue < 0 || parcel.DeclaredValue > 100000000) throw new ArgumentException("Некоректна оголошена вартість");
        long baseFee = CalculateBaseFee(parcel.WeightGrams, request.DistanceKm);
        if (!policy.IsAllowed(customer, parcel, request.DistanceKm)) throw new ShipmentRejectedException("Доставку не дозволено");
        long adjustment = -CalculateDiscount(baseFee, request.Pickup, customer.Regular);
        return shipments.Create(new Shipment(0, request.Reference, customer.Id, parcel.Id, request.DistanceKm,
            request.Urgent, request.Pickup, customer.Regular, parcel.WeightGrams, parcel.DeclaredValue,
            baseFee, adjustment, baseFee + adjustment, ShipmentStatus.Registered));
    }
    public Shipment Dispatch(long id) => ChangeStatus(id, ShipmentStatus.Registered, ShipmentStatus.Dispatched);
    public Shipment Deliver(long id) => ChangeStatus(id, ShipmentStatus.Dispatched, ShipmentStatus.Delivered);
    public Shipment Cancel(long id) => ChangeStatus(id, ShipmentStatus.Registered, ShipmentStatus.Cancelled);
    private Shipment ChangeStatus(long id, ShipmentStatus expected, ShipmentStatus next) {
        if (id <= 0) throw new ArgumentOutOfRangeException(nameof(id));
        var shipment = shipments.FindById(id) ?? throw new ArgumentException("Відправлення не знайдено");
        if (shipment.Status != expected) throw new ShipmentRejectedException("Перехід статусу заборонений");
        shipments.UpdateStatus(id, expected, next);
        return shipment with { Status = next };
    }
}
