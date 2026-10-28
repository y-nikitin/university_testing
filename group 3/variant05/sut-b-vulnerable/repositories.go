package rental

import (
	"context"
	"errors"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type PostgresEquipmentRepository struct{ DB *pgxpool.Pool }
type PostgresRentalRepository struct{ DB *pgxpool.Pool }

func noRows(err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	return err
}
func (r *PostgresEquipmentRepository) Create(ctx context.Context, e Equipment) (Equipment, error) {
	err := r.DB.QueryRow(ctx, "INSERT INTO equipment(code,daily_rate,stock) VALUES($1,$2,$3) RETURNING id", e.Code, e.DailyRate, e.Stock).Scan(&e.ID)
	return e, err
}
func (r *PostgresEquipmentRepository) FindByID(ctx context.Context, id int64) (Equipment, error) {
	var e Equipment
	err := r.DB.QueryRow(ctx, "SELECT id,code,daily_rate,stock FROM equipment WHERE id=$1", id).Scan(&e.ID, &e.Code, &e.DailyRate, &e.Stock)
	return e, noRows(err)
}
func (r *PostgresEquipmentRepository) Update(ctx context.Context, e Equipment) error {
	result, err := r.DB.Exec(ctx, "UPDATE equipment SET code=$1,daily_rate=$2,stock=$3 WHERE id=$4", e.Code, e.DailyRate, e.Stock, e.ID)
	if err == nil && result.RowsAffected() == 0 {
		return ErrNotFound
	}
	return err
}
func (r *PostgresEquipmentRepository) Delete(ctx context.Context, id int64) error {
	_, err := r.DB.Exec(ctx, "DELETE FROM equipment WHERE id=$1", id)
	return err
}
func (r *PostgresRentalRepository) Create(ctx context.Context, o RentalOrder) (RentalOrder, error) {
	tx, err := r.DB.Begin(ctx)
	if err != nil {
		return RentalOrder{}, err
	}
	defer tx.Rollback(context.Background())
	err = tx.QueryRow(ctx, "INSERT INTO rentals(reference,days,premium,total,late_fee,status) VALUES($1,$2,$3,$4,$5,$6) RETURNING id", o.Reference, o.Days, o.Premium, o.Total, o.LateFee, o.Status).Scan(&o.ID)
	if err != nil {
		return RentalOrder{}, err
	}
	err = tx.QueryRow(ctx, "INSERT INTO rental_items(rental_id,equipment_id,quantity,daily_rate) VALUES($1,$2,$3,$4) RETURNING id", o.ID, o.Item.EquipmentID, o.Item.Quantity, o.Item.DailyRate).Scan(&o.Item.ID)
	if err != nil {
		return RentalOrder{}, err
	}
	if err = tx.Commit(ctx); err != nil {
		return RentalOrder{}, err
	}
	return o, nil
}

const rentalSelect = "SELECT r.id,r.reference,r.days,r.premium,r.total,r.late_fee,r.status,i.id,i.equipment_id,i.quantity,i.daily_rate FROM rentals r JOIN rental_items i ON i.rental_id=r.id "

func scanRental(row pgx.Row) (RentalOrder, error) {
	var o RentalOrder
	err := row.Scan(&o.ID, &o.Reference, &o.Days, &o.Premium, &o.Total, &o.LateFee, &o.Status, &o.Item.ID, &o.Item.EquipmentID, &o.Item.Quantity, &o.Item.DailyRate)
	return o, noRows(err)
}
func (r *PostgresRentalRepository) FindByID(ctx context.Context, id int64) (RentalOrder, error) {
	return scanRental(r.DB.QueryRow(ctx, rentalSelect+"WHERE r.id=$1", id))
}
func (r *PostgresRentalRepository) FindRentals(ctx context.Context, id int64, status Status) ([]RentalOrder, error) {
	if id <= 0 || (status != Active && status != Returned) {
		return nil, ErrInvalid
	}
	rows, err := r.DB.Query(ctx, rentalSelect+"WHERE i.equipment_id=$1 AND r.status=$2 ORDER BY r.id", id, status)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []RentalOrder{}
	for rows.Next() {
		o, err := scanRental(rows)
		if err != nil {
			return nil, err
		}
		result = append(result, o)
	}
	return result, rows.Err()
}
func (r *PostgresRentalRepository) MarkReturned(ctx context.Context, id, fee int64) error {
	if fee < 0 {
		return ErrInvalid
	}
	result, err := r.DB.Exec(ctx, "UPDATE rentals SET status='Returned',late_fee=$1 WHERE id=$2 AND status='Active'", fee, id)
	if err == nil && result.RowsAffected() == 0 {
		return ErrConflict
	}
	return err
}
func (r *PostgresRentalRepository) Delete(ctx context.Context, id int64) error {
	_, err := r.DB.Exec(ctx, "DELETE FROM rentals WHERE id=$1", id)
	return err
}
