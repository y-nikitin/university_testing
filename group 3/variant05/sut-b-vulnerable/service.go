package rental

import (
	"context"
	"fmt"
	"strings"
	"unicode/utf8"
)

type RentalService struct {
	equipment EquipmentRepository
	rentals   RentalRepository
	policy    RentalPolicy
}

func NewRentalService(e EquipmentRepository, r RentalRepository, p RentalPolicy) (*RentalService, error) {
	if e == nil || r == nil || p == nil {
		return nil, fmt.Errorf("%w: усі залежності обов’язкові", ErrInvalid)
	}
	return &RentalService{e, r, p}, nil
}
func CalculateTotal(rate int64, quantity, days int, premium bool) (int64, error) {
	if rate < 100 || rate > 1000000 || quantity < 1 || quantity > 20 || days < 1 || days > 30 {
		return 0, ErrInvalid
	}
	subtotal := rate * int64(quantity) * int64(days)
	discount := 0
	if premium {
		discount += 5
	}
	if subtotal >= 300000 {
		discount += 10
	}
	return subtotal - subtotal*int64(discount)/100, nil
}
func (s *RentalService) Rent(ctx context.Context, r RentalRequest) (RentalOrder, error) {
	if strings.TrimSpace(r.Reference) == "" || utf8.RuneCountInString(r.Reference) > 40 || r.EquipmentID <= 0 || r.Quantity < 1 || r.Quantity > 20 || r.Days < 1 || r.Days > 30 {
		return RentalOrder{}, ErrInvalid
	}
	item, err := s.equipment.FindByID(ctx, r.EquipmentID)
	if err != nil {
		return RentalOrder{}, err
	}
	if r.Quantity > item.Stock {
		return RentalOrder{}, fmt.Errorf("%w: недостатньо обладнання", ErrConflict)
	}
	allowed, err := s.policy.IsAllowed(ctx, item, r.Quantity, r.Days)
	if err != nil {
		return RentalOrder{}, err
	}
	if !allowed {
		return RentalOrder{}, fmt.Errorf("%w: оренду не дозволено", ErrConflict)
	}
	total, err := CalculateTotal(item.DailyRate, r.Quantity, r.Days, r.Premium)
	if err != nil {
		return RentalOrder{}, err
	}
	return s.rentals.Create(ctx, RentalOrder{Reference: r.Reference, Days: r.Days, Premium: r.Premium, Total: total, Status: Active, Item: RentalItem{EquipmentID: item.ID, Quantity: r.Quantity, DailyRate: item.DailyRate}})
}
func (s *RentalService) ReturnRental(ctx context.Context, id int64) (RentalOrder, error) {
	if id <= 0 {
		return RentalOrder{}, ErrInvalid
	}
	r, err := s.rentals.FindByID(ctx, id)
	if err != nil {
		return RentalOrder{}, err
	}
	if r.Status == Returned {
		return RentalOrder{}, fmt.Errorf("%w: оренду вже повернено", ErrConflict)
	}
	if err = s.rentals.MarkReturned(ctx, id, 0); err != nil {
		return RentalOrder{}, err
	}
	r.Status = Returned
	r.LateFee = 0
	return r, nil
}
