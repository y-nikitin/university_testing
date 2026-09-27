package ua.course.rental;
import java.sql.*;
import java.util.*;
public class RentalRepository {
    private final Connection connection;
    private static final String SELECT="SELECT r.id,r.reference,r.days,r.premium,r.total,r.late_fee,r.deposit,r.status,i.id AS item_id,i.equipment_id,i.quantity,i.daily_rate FROM rentals r JOIN rental_items i ON i.rental_id=r.id ";
    public RentalRepository(Connection connection) { this.connection=Objects.requireNonNull(connection); }
    public RentalOrder create(RentalOrder rental) throws SQLException {
        if (!connection.getAutoCommit()) throw new IllegalStateException("Потрібне з'єднання з autoCommit=true");
        connection.setAutoCommit(false);
        Throwable failure=null;
        boolean rollbackFailed=false;
        try {
            long id;
            try (var q=connection.prepareStatement("INSERT INTO rentals(reference,days,premium,total,late_fee,deposit,status) VALUES (?,?,?,?,?,?,?) RETURNING id")) {
                q.setString(1,rental.reference());q.setInt(2,rental.days());q.setBoolean(3,rental.premium());q.setLong(4,rental.total());q.setLong(5,rental.lateFee());q.setLong(6,rental.deposit());q.setString(7,rental.status().name());
                try (var r=q.executeQuery()) { r.next();id=r.getLong(1); }
            }
            var item=rental.item();long itemId;
            try (var q=connection.prepareStatement("INSERT INTO rental_items(rental_id,equipment_id,quantity,daily_rate) VALUES (?,?,?,?) RETURNING id")) {
                q.setLong(1,id);q.setLong(2,item.equipmentId());q.setInt(3,item.quantity());q.setLong(4,item.dailyRate());
                try (var r=q.executeQuery()) { r.next();itemId=r.getLong(1); }
            }
            var saved=new RentalOrder(id,rental.reference(),rental.days(),rental.premium(),rental.total(),rental.lateFee(),rental.deposit(),rental.status(),new RentalItem(itemId,item.equipmentId(),item.quantity(),item.dailyRate()));
            connection.commit();return saved;
        } catch (SQLException | RuntimeException | Error error) {
            failure=error;
            try { connection.rollback(); } catch (SQLException rollbackError) {
                rollbackFailed=true;error.addSuppressed(rollbackError);
                try { connection.close(); } catch (SQLException closeError) { error.addSuppressed(closeError); }
            }
            throw error;
        } finally {
            if (!rollbackFailed) {
                try { connection.setAutoCommit(true); } catch (SQLException restoreError) {
                    if (failure!=null) failure.addSuppressed(restoreError);else throw restoreError;
                }
            }
        }
    }
    public RentalOrder findById(long id) throws SQLException {
        try (var q=connection.prepareStatement(SELECT+"WHERE r.id=?")) {
            q.setLong(1,id);
            try (var r=q.executeQuery()) { return r.next()?map(r):null; }
        }
    }
    public List<RentalOrder> findRentals(long equipmentId,RentalStatus status) throws SQLException {
        if (equipmentId<=0 || status==null) throw new IllegalArgumentException("Некоректний фільтр");
        try (var q=connection.prepareStatement(SELECT+"WHERE i.equipment_id=? AND r.status=? ORDER BY r.id")) {
            q.setLong(1,equipmentId);q.setString(2,status.name());
            var result=new ArrayList<RentalOrder>();
            try (var r=q.executeQuery()) { while(r.next()) result.add(map(r)); }
            return result;
        }
    }
    public void markReturned(long id,long lateFee) throws SQLException {
        if (lateFee<0) throw new IllegalArgumentException("Доплата не може бути від'ємною");
        try (var q=connection.prepareStatement("UPDATE rentals SET status='Returned',late_fee=? WHERE id=? AND status='Active'")) {
            q.setLong(1,lateFee);q.setLong(2,id);
            if (q.executeUpdate()==0) throw new RentalRejectedException("Активну оренду не знайдено");
        }
    }
    public void delete(long id) throws SQLException {
        try (var q=connection.prepareStatement("DELETE FROM rentals WHERE id=?")) { q.setLong(1,id);q.executeUpdate(); }
    }
    private RentalOrder map(ResultSet r) throws SQLException {
        return new RentalOrder(r.getLong("id"),r.getString("reference"),r.getInt("days"),r.getBoolean("premium"),r.getLong("total"),r.getLong("late_fee"),r.getLong("deposit"),RentalStatus.valueOf(r.getString("status")),new RentalItem(r.getLong("item_id"),r.getLong("equipment_id"),r.getInt("quantity"),r.getLong("daily_rate")));
    }
}
