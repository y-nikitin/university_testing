using System;
using System.Collections.Generic;
using System.Data.Common;
namespace Delivery;
internal static class Sql {
    internal static DbCommand Command(DbConnection connection, string sql, params object[] values) {
        var command = connection.CreateCommand(); command.CommandText = sql;
        for (int i = 0; i < values.Length; i++) {
            var parameter = command.CreateParameter(); parameter.ParameterName = "p" + i; parameter.Value = values[i]; command.Parameters.Add(parameter);
        }
        return command;
    }
}
public class CustomerRepository : ICustomerRepository {
    private readonly DbConnection connection;
    public CustomerRepository(DbConnection connection) { this.connection = connection ?? throw new ArgumentNullException(nameof(connection)); }
    public Customer Create(Customer customer) {
        using var command = Sql.Command(connection, "INSERT INTO customers(code,regular) VALUES (@p0,@p1) RETURNING id",customer.Code,customer.Regular);
        return customer with { Id = Convert.ToInt64(command.ExecuteScalar()) };
    }
    public Customer? FindById(long id) {
        using var command = Sql.Command(connection, "SELECT id,code,regular FROM customers WHERE id=@p0",id);
        using var r = command.ExecuteReader(); return r.Read() ? new Customer(r.GetInt64(0),r.GetString(1),r.GetBoolean(2)) : null;
    }
    public void Update(Customer customer) {
        using var command = Sql.Command(connection, "UPDATE customers SET code=@p0,regular=@p1 WHERE id=@p2",customer.Code,customer.Regular,customer.Id);
        if (command.ExecuteNonQuery() == 0) throw new ArgumentException("Клієнта не знайдено");
    }
    public void Delete(long id) {
        using var command = Sql.Command(connection, "DELETE FROM customers WHERE id=@p0",id); command.ExecuteNonQuery();
    }
}
public class ParcelRepository : IParcelRepository {
    private readonly DbConnection connection;
    public ParcelRepository(DbConnection connection) { this.connection = connection ?? throw new ArgumentNullException(nameof(connection)); }
    public Parcel Create(Parcel parcel) {
        using var command = Sql.Command(connection, "INSERT INTO parcels(code,weight_grams,declared_value) VALUES (@p0,@p1,@p2) RETURNING id",parcel.Code,parcel.WeightGrams,parcel.DeclaredValue);
        return parcel with { Id = Convert.ToInt64(command.ExecuteScalar()) };
    }
    public Parcel? FindById(long id) {
        using var command = Sql.Command(connection, "SELECT id,code,weight_grams,declared_value FROM parcels WHERE id=@p0",id);
        using var r = command.ExecuteReader(); return r.Read() ? new Parcel(r.GetInt64(0),r.GetString(1),r.GetInt32(2),r.GetInt64(3)) : null;
    }
    public void Update(Parcel parcel) {
        using var command = Sql.Command(connection, "UPDATE parcels SET code=@p0,weight_grams=@p1,declared_value=@p2 WHERE id=@p3",parcel.Code,parcel.WeightGrams,parcel.DeclaredValue,parcel.Id);
        if (command.ExecuteNonQuery() == 0) throw new ArgumentException("Посилку не знайдено");
    }
    public void Delete(long id) {
        using var command = Sql.Command(connection, "DELETE FROM parcels WHERE id=@p0",id); command.ExecuteNonQuery();
    }
}
public class ShipmentRepository : IShipmentRepository {
    private readonly DbConnection connection;
    private const string Select = "SELECT id,reference,customer_id,parcel_id,distance_km,urgent,pickup,regular,weight_grams,declared_value,base_fee,adjustment,total,status FROM shipments ";
    public ShipmentRepository(DbConnection connection) { this.connection = connection ?? throw new ArgumentNullException(nameof(connection)); }
    public Shipment Create(Shipment s) {
        using var command = Sql.Command(connection,
            "INSERT INTO shipments(reference,customer_id,parcel_id,distance_km,urgent,pickup,regular,weight_grams,declared_value,base_fee,adjustment,total,status) VALUES (@p0,@p1,@p2,@p3,@p4,@p5,@p6,@p7,@p8,@p9,@p10,@p11,@p12) RETURNING id",
            s.Reference,s.CustomerId,s.ParcelId,s.DistanceKm,s.Urgent,s.Pickup,s.Regular,s.WeightGrams,s.DeclaredValue,s.BaseFee,s.Adjustment,s.Total,s.Status.ToString());
        return s with { Id = Convert.ToInt64(command.ExecuteScalar()) };
    }
    public Shipment? FindById(long id) {
        using var command = Sql.Command(connection, Select + "WHERE id=@p0",id);
        using var r = command.ExecuteReader(); return r.Read() ? Map(r) : null;
    }
    public List<Shipment> FindShipments(long customerId, ShipmentStatus status) {
        if (customerId <= 0 || !Enum.IsDefined(typeof(ShipmentStatus),status)) throw new ArgumentException("Некоректний фільтр");
        using var command = Sql.Command(connection, Select + "WHERE customer_id=@p0 AND status=@p1 ORDER BY id",customerId,status.ToString());
        using var r = command.ExecuteReader(); var result = new List<Shipment>();
        while (r.Read()) result.Add(Map(r)); return result;
    }
    public void UpdateStatus(long id, ShipmentStatus expected, ShipmentStatus next) {
        bool allowed = (expected == ShipmentStatus.Registered && (next == ShipmentStatus.Dispatched || next == ShipmentStatus.Cancelled))
            || (expected == ShipmentStatus.Dispatched && next == ShipmentStatus.Delivered);
        if (!allowed) throw new ArgumentException("Перехід статусу заборонений");
        using var command = Sql.Command(connection, "UPDATE shipments SET status=@p0 WHERE id=@p1 AND status=@p2",next.ToString(),id,expected.ToString());
        if (command.ExecuteNonQuery() == 0) throw new ShipmentRejectedException("Відправлення відсутнє або його статус уже змінено");
    }
    public void Delete(long id) {
        using var command = Sql.Command(connection, "DELETE FROM shipments WHERE id=@p0",id); command.ExecuteNonQuery();
    }
    private static Shipment Map(DbDataReader r) => new Shipment(r.GetInt64(0),r.GetString(1),r.GetInt64(2),r.GetInt64(3),r.GetInt32(4),r.GetBoolean(5),r.GetBoolean(6),r.GetBoolean(7),r.GetInt32(8),r.GetInt64(9),r.GetInt64(10),r.GetInt64(11),r.GetInt64(12),Enum.Parse<ShipmentStatus>(r.GetString(13)));
}
