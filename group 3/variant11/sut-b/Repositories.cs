using System;
using System.Collections.Generic;
using System.Data.Common;
namespace Rental;
internal static class Sql {
    internal static DbCommand Command(DbConnection connection, string sql, params object[] values) {
        var command = connection.CreateCommand(); command.CommandText = sql;
        for (int i = 0; i < values.Length; i++) {
            var parameter = command.CreateParameter(); parameter.ParameterName = "p" + i; parameter.Value = values[i]; command.Parameters.Add(parameter);
        }
        return command;
    }
}
public class EquipmentRepository : IEquipmentRepository {
    private readonly DbConnection connection;
    public EquipmentRepository(DbConnection connection) { this.connection = connection ?? throw new ArgumentNullException(nameof(connection)); }
    public Equipment Create(Equipment equipment) {
        using var command = Sql.Command(connection, "INSERT INTO equipment(code,daily_rate,stock) VALUES (@p0,@p1,@p2) RETURNING id", equipment.Code, equipment.DailyRate, equipment.Stock);
        return equipment with { Id = Convert.ToInt64(command.ExecuteScalar()) };
    }
    public Equipment? FindById(long id) {
        using var command = Sql.Command(connection, "SELECT id,code,daily_rate,stock FROM equipment WHERE id=@p0", id);
        using var reader = command.ExecuteReader();
        return reader.Read() ? new Equipment(reader.GetInt64(0),reader.GetString(1),reader.GetInt64(2),reader.GetInt32(3)) : null;
    }
    public void Update(Equipment equipment) {
        using var command = Sql.Command(connection, "UPDATE equipment SET code=@p0,daily_rate=@p1,stock=@p2 WHERE id=@p3", equipment.Code,equipment.DailyRate,equipment.Stock,equipment.Id);
        if (command.ExecuteNonQuery() == 0) throw new ArgumentException("Обладнання не знайдено");
    }
    public void Delete(long id) {
        using var command = Sql.Command(connection, "DELETE FROM equipment WHERE id=@p0",id); command.ExecuteNonQuery();
    }
}
public class RentalRepository : IRentalRepository {
    private readonly DbConnection connection;
    private const string Select = "SELECT r.id,r.reference,r.days,r.total,r.late_fee,r.status,i.id,i.equipment_id,i.quantity,i.daily_rate FROM rentals r JOIN rental_items i ON i.rental_id=r.id ";
    public RentalRepository(DbConnection connection) { this.connection = connection ?? throw new ArgumentNullException(nameof(connection)); }
    public RentalOrder Create(RentalOrder rental) {
        using var transaction = connection.BeginTransaction();
        using var header = Sql.Command(connection, "INSERT INTO rentals(reference,days,total,late_fee,status) VALUES (@p0,@p1,@p2,@p3,@p4) RETURNING id",rental.Reference,rental.Days,rental.Total,rental.LateFee,rental.Status.ToString());
        header.Transaction = transaction;
        long id = Convert.ToInt64(header.ExecuteScalar());
        using var item = Sql.Command(connection, "INSERT INTO rental_items(rental_id,equipment_id,quantity,daily_rate) VALUES (@p0,@p1,@p2,@p3) RETURNING id",id,rental.Item.EquipmentId,rental.Item.Quantity,rental.Item.DailyRate);
        item.Transaction = transaction;
        long itemId = Convert.ToInt64(item.ExecuteScalar());
        var saved = rental with { Id = id, Item = rental.Item with { Id = itemId } };
        transaction.Commit(); return saved;
    }
    public RentalOrder? FindById(long id) {
        using var command = Sql.Command(connection, Select + "WHERE r.id=@p0",id);
        using var reader = command.ExecuteReader(); return reader.Read() ? Map(reader) : null;
    }
    public List<RentalOrder> FindRentals(long equipmentId, RentalStatus status) {
        if (equipmentId <= 0 || !Enum.IsDefined(typeof(RentalStatus),status)) throw new ArgumentException("Некоректний фільтр");
        using var command = Sql.Command(connection, Select + "WHERE i.equipment_id=@p0 AND r.status=@p1 ORDER BY r.id",equipmentId,status.ToString());
        using var reader = command.ExecuteReader(); var result = new List<RentalOrder>();
        while (reader.Read()) result.Add(Map(reader)); return result;
    }
    public void MarkReturned(long id, long lateFee) {
        if (lateFee < 0) throw new ArgumentOutOfRangeException(nameof(lateFee));
        using var command = Sql.Command(connection, "UPDATE rentals SET status='Returned',late_fee=@p0 WHERE id=@p1 AND status='Active'",lateFee,id);
        if (command.ExecuteNonQuery() == 0) throw new RentalRejectedException("Активну оренду не знайдено");
    }
    public void Delete(long id) {
        using var command = Sql.Command(connection, "DELETE FROM rentals WHERE id=@p0",id); command.ExecuteNonQuery();
    }
    private static RentalOrder Map(DbDataReader r) => new RentalOrder(r.GetInt64(0),r.GetString(1),r.GetInt32(2),r.GetInt64(3),r.GetInt64(4),Enum.Parse<RentalStatus>(r.GetString(5)),new RentalItem(r.GetInt64(6),r.GetInt64(7),r.GetInt32(8),r.GetInt64(9)));
}
