package ua.course.rental;

import java.sql.*;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class RentalRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Connection suppliedConnection;
    private static final String SELECT = "SELECT r.*,i.id AS item_id,i.equipment_id,i.quantity,i.daily_rate FROM rentals r JOIN rental_items i ON i.rental_id=r.id ";

    @Autowired
    public RentalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.suppliedConnection = null;
    }

    // Сумісність із попередніми тестами: з’єднання закриває його власник.
    public RentalRepository(Connection connection) {
        var source = new SingleConnectionDataSource(connection, true);
        this.jdbc = new JdbcTemplate(source);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        this.suppliedConnection = connection;
    }

    public RentalOrder create(RentalOrder rental) throws SQLException {
        if (suppliedConnection != null && !suppliedConnection.getAutoCommit())
            throw new IllegalStateException("Потрібне з'єднання з autoCommit=true");
        return transaction.execute(status -> {
            long id = jdbc.queryForObject("INSERT INTO rentals(reference,days,premium,total,late_fee,deposit,status) VALUES (?,?,?,?,?,?,?) RETURNING id",
                    Long.class, rental.reference(), rental.days(), rental.premium(), rental.total(), rental.lateFee(), rental.deposit(), rental.status().name());
            var item = rental.item();
            long itemId = jdbc.queryForObject("INSERT INTO rental_items(rental_id,equipment_id,quantity,daily_rate) VALUES (?,?,?,?) RETURNING id",
                    Long.class, id, item.equipmentId(), item.quantity(), item.dailyRate());
            return new RentalOrder(id, rental.reference(), rental.days(), rental.premium(), rental.total(), rental.lateFee(), rental.deposit(), rental.status(),
                    new RentalItem(itemId, item.equipmentId(), item.quantity(), item.dailyRate()));
        });
    }

    public RentalOrder findById(long id) throws SQLException {
        return jdbc.query(SELECT + "WHERE r.id=?", (r, index) -> map(r), id).stream().findFirst().orElse(null);
    }

    public List<RentalOrder> findRentals(long equipmentId, RentalStatus status) throws SQLException {
        if (equipmentId <= 0 || status == null) throw new IllegalArgumentException("Некоректний фільтр");
        return jdbc.query(SELECT + "WHERE i.equipment_id=? AND r.status=? ORDER BY r.id",
                (r, index) -> map(r), equipmentId, status.name());
    }

    public void markReturned(long id, long lateFee) throws SQLException {
        if (lateFee < 0) throw new IllegalArgumentException("Доплата не може бути від'ємною");
        if (jdbc.update("UPDATE rentals SET status='Returned',late_fee=? WHERE id=? AND status='Active'", lateFee, id) == 0)
            throw new RentalRejectedException("Активну оренду не знайдено");
    }

    public void delete(long id) throws SQLException { jdbc.update("DELETE FROM rentals WHERE id=?", id); }

    private RentalOrder map(ResultSet r) throws SQLException {
        return new RentalOrder(r.getLong("id"),r.getString("reference"),r.getInt("days"),r.getBoolean("premium"),r.getLong("total"),r.getLong("late_fee"),r.getLong("deposit"),RentalStatus.valueOf(r.getString("status")),new RentalItem(r.getLong("item_id"),r.getLong("equipment_id"),r.getInt("quantity"),r.getLong("daily_rate")));
    }
}
