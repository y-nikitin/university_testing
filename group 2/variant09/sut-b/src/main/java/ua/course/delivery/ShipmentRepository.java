package ua.course.delivery;
import java.sql.*;
import java.util.*;
public class ShipmentRepository {
    private final Connection connection;
    public ShipmentRepository(Connection connection) { this.connection=Objects.requireNonNull(connection); }
    public Shipment create(Shipment s) throws SQLException {
        try(var q=Sql.command(connection,"INSERT INTO shipments(reference,customer_id,parcel_id,distance_km,urgent,pickup,regular,coupon_valid,fragile,weight_grams,declared_value,base_fee,adjustment,total,refund,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING *",
            s.reference(),s.customerId(),s.parcelId(),s.distanceKm(),s.urgent(),s.pickup(),s.regular(),s.couponValid(),s.fragile(),s.weightGrams(),s.declaredValue(),s.baseFee(),s.adjustment(),s.total(),s.refund(),s.status().name());var r=q.executeQuery()) {
            r.next();return map(r);
        }
    }
    public Shipment findById(long id) throws SQLException {
        try(var q=Sql.command(connection,"SELECT * FROM shipments WHERE id=?",id);var r=q.executeQuery()) { return r.next()?map(r):null; }
    }
    public List<Shipment> findShipments(long customerId,ShipmentStatus status) throws SQLException {
        if(customerId<=0 || status==null) throw new IllegalArgumentException("Некоректний фільтр");
        try(var q=Sql.command(connection,"SELECT * FROM shipments WHERE customer_id=? AND status=? ORDER BY id",customerId,status.name());var r=q.executeQuery()) {
            var result=new ArrayList<Shipment>();while(r.next()) result.add(map(r));return result;
        }
    }
    public void updateStatus(long id,ShipmentStatus expected,ShipmentStatus next,long refund) throws SQLException {
        boolean allowed=(expected==ShipmentStatus.Registered && (next==ShipmentStatus.Dispatched || next==ShipmentStatus.Cancelled))
            || (expected==ShipmentStatus.Dispatched && next==ShipmentStatus.Delivered) || (expected==ShipmentStatus.Dispatched && next==ShipmentStatus.Cancelled);
        if(!allowed || refund<0 || refund>3085000) throw new IllegalArgumentException("Некоректний перехід або сума повернення");
        try(var q=Sql.command(connection,"UPDATE shipments SET status=?,refund=? WHERE id=? AND status=?",next.name(),refund,id,expected.name())) {
            if(q.executeUpdate()==0) throw new ShipmentRejectedException("Відправлення відсутнє або його статус уже змінено");
        }
    }
    public void delete(long id) throws SQLException {
        try(var q=Sql.command(connection,"DELETE FROM shipments WHERE id=?",id)) { q.executeUpdate(); }
    }
    private Shipment map(ResultSet r) throws SQLException {
        return new Shipment(r.getLong("id"),r.getString("reference"),r.getLong("customer_id"),r.getLong("parcel_id"),r.getInt("distance_km"),r.getBoolean("urgent"),r.getBoolean("pickup"),r.getBoolean("regular"),r.getBoolean("coupon_valid"),r.getBoolean("fragile"),r.getInt("weight_grams"),r.getLong("declared_value"),r.getLong("base_fee"),r.getLong("adjustment"),r.getLong("total"),r.getLong("refund"),ShipmentStatus.valueOf(r.getString("status")));
    }
}
