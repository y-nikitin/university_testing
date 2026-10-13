package ua.course.delivery;
import java.sql.SQLException;
import java.util.Objects;
@org.springframework.stereotype.Service
public class ShippingService {
    private final CustomerRepository customers;
    private final ParcelRepository parcels;
    private final ShipmentRepository shipments;
    private final ShippingPolicy policy;
    public ShippingService(CustomerRepository customers, ParcelRepository parcels, ShipmentRepository shipments, ShippingPolicy policy) {
        this.customers=Objects.requireNonNull(customers);this.parcels=Objects.requireNonNull(parcels);
        this.shipments=Objects.requireNonNull(shipments);this.policy=Objects.requireNonNull(policy);
    }
    public long calculateBaseFee(int weightGrams,int distanceKm) {
        validate(weightGrams,distanceKm);
        long weightFee=weightGrams<=1000?1000:weightGrams<=5000?3000:6000;
        return 5000+weightFee+distanceKm*10L;
    }

    public long calculateRefund(long total,ShipmentStatus status) {
        if(total<0 || total>3085000 || status==null) throw new IllegalArgumentException("Некоректна сума або статус");
        if(status==ShipmentStatus.Registered) return total;

        throw new ShipmentRejectedException("Скасування у цьому статусі заборонене");
    }
    public Shipment createShipment(ShipmentRequest request) throws SQLException {
        if(request==null || request.reference()==null || request.reference().isBlank() || request.reference().length()>40 || request.customerId()<=0 || request.parcelId()<=0)
            throw new IllegalArgumentException("Некоректна заявка");
        if(request.distanceKm()<1 || request.distanceKm()>5000) throw new IllegalArgumentException("Некоректна відстань");
        var customer=customers.findById(request.customerId());
        if(customer==null) throw new ResourceNotFoundException("Клієнта не знайдено");
        var parcel=parcels.findById(request.parcelId());
        if(parcel==null) throw new ResourceNotFoundException("Посилку не знайдено");
        if(parcel.declaredValue()<0 || parcel.declaredValue()>100000000) throw new IllegalArgumentException("Некоректна оголошена вартість");
        long baseFee=calculateBaseFee(parcel.weightGrams(),request.distanceKm());

        if(!policy.isAllowed(customer,parcel,request.distanceKm())) throw new ShipmentRejectedException("Доставку не дозволено");
        long adjustment=0;
        return shipments.create(new Shipment(0,request.reference(),customer.id(),parcel.id(),request.distanceKm(),request.urgent(),request.pickup(),
            customer.regular(),request.couponValid(),parcel.fragile(),parcel.weightGrams(),parcel.declaredValue(),baseFee,adjustment,baseFee+adjustment,0,ShipmentStatus.Registered));
    }
    public Shipment dispatch(long id) throws SQLException { return changeStatus(id,ShipmentStatus.Registered,ShipmentStatus.Dispatched); }
    public Shipment deliver(long id) throws SQLException { return changeStatus(id,ShipmentStatus.Dispatched,ShipmentStatus.Delivered); }
    public Shipment cancel(long id) throws SQLException {
        var shipment=find(id);
        long refund=calculateRefund(shipment.total(),shipment.status());
        shipments.updateStatus(id,shipment.status(),ShipmentStatus.Cancelled,refund);
        return withStatus(shipment,ShipmentStatus.Cancelled,refund);
    }
    private Shipment changeStatus(long id,ShipmentStatus expected,ShipmentStatus next) throws SQLException {
        var shipment=find(id);
        if(shipment.status()!=expected) throw new ShipmentRejectedException("Перехід статусу заборонений");
        shipments.updateStatus(id,expected,next,shipment.refund());
        return withStatus(shipment,next,shipment.refund());
    }
    private Shipment find(long id) throws SQLException {
        if(id<=0) throw new IllegalArgumentException("Некоректний ідентифікатор");
        var shipment=shipments.findById(id);
        if(shipment==null) throw new ResourceNotFoundException("Відправлення не знайдено");
        return shipment;
    }
    private Shipment withStatus(Shipment s,ShipmentStatus status,long refund) {
        return new Shipment(s.id(),s.reference(),s.customerId(),s.parcelId(),s.distanceKm(),s.urgent(),s.pickup(),s.regular(),s.couponValid(),s.fragile(),s.weightGrams(),s.declaredValue(),s.baseFee(),s.adjustment(),s.total(),refund,status);
    }
    private static void validate(int weightGrams,int distanceKm) {
        if(weightGrams<1 || weightGrams>30000 || distanceKm<1 || distanceKm>5000) throw new IllegalArgumentException("Вага або відстань поза допустимими межами");
    }
}
