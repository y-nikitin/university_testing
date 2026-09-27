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
    public long calculateDeposit(long dailyRate, int quantity, int days, boolean premium) {
        long subtotal=calculateTotal(dailyRate,quantity,days);
        return subtotal * (premium ? 10 : 20) / 100;
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
        return rentals.create(new RentalOrder(0,request.reference(),request.days(),request.premium(),total,0,calculateDeposit(item.dailyRate(),request.quantity(),request.days(),request.premium()),RentalStatus.Active,
            new RentalItem(0,item.id(),request.quantity(),item.dailyRate())));
    }
    public RentalOrder returnRental(long id) throws SQLException {
        if (id<=0) throw new IllegalArgumentException("Некоректний ідентифікатор");
        var rental=rentals.findById(id);
        if (rental==null) throw new IllegalArgumentException("Оренду не знайдено");
        if (rental.status()==RentalStatus.Returned) throw new RentalRejectedException("Оренду вже повернено");
        rentals.markReturned(id,0);
        return new RentalOrder(rental.id(),rental.reference(),rental.days(),rental.premium(),rental.total(),0,rental.deposit(),RentalStatus.Returned,rental.item());
    }
    private static void validate(int quantity,int days) {
        if (quantity<1 || quantity>20 || days<1 || days>30) throw new IllegalArgumentException("Кількість або тривалість поза допустимими межами");
    }
}
