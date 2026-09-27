using System;
namespace Rental;
public class RentalRejectedException : Exception {
    public RentalRejectedException(string message) : base(message) { }
}
public class RentalService {
    private readonly IEquipmentRepository equipment;
    private readonly IRentalRepository rentals;
    private readonly IRentalPolicy policy;
    public RentalService(IEquipmentRepository equipment, IRentalRepository rentals, IRentalPolicy policy) {
        this.equipment = equipment ?? throw new ArgumentNullException(nameof(equipment));
        this.rentals = rentals ?? throw new ArgumentNullException(nameof(rentals));
        this.policy = policy ?? throw new ArgumentNullException(nameof(policy));
    }
    public long CalculateTotal(long dailyRate, int quantity, int days) {
        Validate(dailyRate, quantity, days);
        return dailyRate * quantity * days;
    }
    public long CalculateLateFee(long dailyRate, int quantity, int rentalDays, int overdueDays) {
        Validate(dailyRate, quantity, rentalDays);
        if (overdueDays < 0 || overdueDays > 36500) throw new ArgumentOutOfRangeException(nameof(overdueDays));
        if (overdueDays <= 1) return 0;
        long fee = dailyRate * quantity * (overdueDays - 1) / 10;
        return Math.Min(fee, CalculateTotal(dailyRate, quantity, rentalDays));
    }
    public RentalOrder Rent(RentalRequest request) {
        if (request == null || string.IsNullOrWhiteSpace(request.Reference) || request.Reference.Length > 40 || request.EquipmentId <= 0)
            throw new ArgumentException("Некоректна заявка");
        Validate(100, request.Quantity, request.Days);
        var item = equipment.FindById(request.EquipmentId) ?? throw new ArgumentException("Обладнання не знайдено");
        if (request.Quantity > item.Stock) throw new RentalRejectedException("Недостатньо обладнання");
        if (!policy.IsAllowed(item, request.Quantity, request.Days)) throw new RentalRejectedException("Оренду не дозволено");
        long total = CalculateTotal(item.DailyRate, request.Quantity, request.Days);
        return rentals.Create(new RentalOrder(0, request.Reference, request.Days, total, 0, RentalStatus.Active,
            new RentalItem(0, item.Id, request.Quantity, item.DailyRate)));
    }
    public RentalOrder ReturnRental(long id, int overdueDays) {
        if (id <= 0 || overdueDays < 0 || overdueDays > 36500) throw new ArgumentException("Некоректні параметри повернення");
        var rental = rentals.FindById(id) ?? throw new ArgumentException("Оренду не знайдено");
        if (rental.Status == RentalStatus.Returned) throw new RentalRejectedException("Оренду вже повернено");
        long fee = CalculateLateFee(rental.Item.DailyRate, rental.Item.Quantity, rental.Days, overdueDays);
        rentals.MarkReturned(id, fee);
        return rental with { Status = RentalStatus.Returned, LateFee = fee };
    }
    private static void Validate(long dailyRate, int quantity, int days) {
        if (dailyRate < 100 || dailyRate > 1000000 || quantity < 1 || quantity > 20 || days < 1 || days > 30)
            throw new ArgumentException("Ціна, кількість або тривалість поза допустимими межами");
    }
}
