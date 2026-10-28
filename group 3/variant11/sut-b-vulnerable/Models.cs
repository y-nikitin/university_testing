namespace Rental;
public enum RentalStatus { Active, Returned }
public record Equipment(long Id, string Code, long DailyRate, int Stock);
public record RentalItem(long Id, long EquipmentId, int Quantity, long DailyRate);
public record RentalOrder(long Id, string Reference, int Days, long Total, long LateFee, RentalStatus Status, RentalItem Item);
public record RentalRequest(string Reference, long EquipmentId, int Quantity, int Days);
public interface IRentalPolicy { bool IsAllowed(Equipment equipment, int quantity, int days); }
public interface IEquipmentRepository {
    Equipment Create(Equipment equipment);
    Equipment? FindById(long id);
    void Update(Equipment equipment);
    void Delete(long id);
}
public interface IRentalRepository {
    RentalOrder Create(RentalOrder rental);
    RentalOrder? FindById(long id);
    System.Collections.Generic.List<RentalOrder> FindRentals(long equipmentId, RentalStatus status);
    void MarkReturned(long id, long lateFee);
    void Delete(long id);
}
