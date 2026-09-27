package ua.course.rental;
import java.sql.SQLException;
import java.util.Objects;
public class RentalService {
    private final EquipmentRepository equipment;
    private final RentalRepository rentals;
    private final RentalPolicy policy;
    public RentalService(EquipmentRepository equipment, RentalRepository rentals, RentalPolicy policy) {
        this.equipment=Objects.requireNonNull(equipment); this.rentals=Objects.requireNonNull(rentals); this.policy=Objects.requireNonNull(policy);
    }
    public long calculateTotal(long dailyRate, int quantity, int days) {
        if (dailyRate<100 || dailyRate>1000000) throw new IllegalArgumentException("Ціна поза допустимими межами");
        validate(quantity,days);
        return dailyRate*quantity*days;
    }
    public long calculateLateFee(long dailyRate, int quantity, int days, int overdueDays, boolean premium) {
        calculateTotal(dailyRate,quantity,days);
        if (overdueDays<0 || overdueDays>36500) throw new IllegalArgumentException("Некоректне прострочення");
        return Math.min(calculateTotal(dailyRate, quantity, days), dailyRate * quantity * Math.max(0, overdueDays - (premium ? 3 : 1)) / 4);
    }
    public RentalOrder rent(RentalRequest request) throws SQLException {
        if (request==null || request.reference()==null || request.reference().isBlank() || request.reference().length()>40 || request.equipmentId()<=0)
            throw new IllegalArgumentException("Некоректна заявка");
        validate(request.quantity(),request.days());
        var item=equipment.findById(request.equipmentId());
        if (item==null) throw new IllegalArgumentException("Обладнання не знайдено");
        if (request.quantity()>item.stock()) throw new RentalRejectedException("Недостатньо обладнання");
        if (!policy.isAllowed(item,request.quantity(),request.days())) throw new RentalRejectedException("Оренду не дозволено");
        long total=calculateTotal(item.dailyRate(),request.quantity(),request.days());
        return rentals.create(new RentalOrder(0,request.reference(),request.days(),request.premium(),total,0,RentalStatus.Active,
            new RentalItem(0,item.id(),request.quantity(),item.dailyRate())));
    }
    public RentalOrder returnRental(long id) throws SQLException {
        return returnRental(id,0);
    }
    public RentalOrder returnRental(long id,int overdueDays) throws SQLException {
        if (overdueDays<0 || overdueDays>36500) throw new IllegalArgumentException("Некоректне прострочення");
        if (id<=0) throw new IllegalArgumentException("Некоректний ідентифікатор");
        var rental=rentals.findById(id);
        if (rental==null) throw new IllegalArgumentException("Оренду не знайдено");
        if (rental.status()==RentalStatus.Returned) throw new RentalRejectedException("Оренду вже повернено");
        long fee=calculateLateFee(rental.item().dailyRate(),rental.item().quantity(),rental.days(),overdueDays,rental.premium());
        rentals.markReturned(id,fee);
        return new RentalOrder(rental.id(),rental.reference(),rental.days(),rental.premium(),rental.total(),fee,RentalStatus.Returned,rental.item());
    }
    private static void validate(int quantity,int days) {
        if (quantity<1 || quantity>20 || days<1 || days>30) throw new IllegalArgumentException("Кількість або тривалість поза допустимими межами");
    }
}
