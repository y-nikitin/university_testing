package rental

import (
	"context"
	"errors"
)

var ErrInvalid = errors.New("Некоректні дані")
var ErrNotFound = errors.New("Ресурс не знайдено")
var ErrConflict = errors.New("Операцію заборонено")
var ErrPolicy = errors.New("Зовнішня перевірка недоступна")

type Status string

const (
	Active   Status = "Active"
	Returned Status = "Returned"
)

type Equipment struct {
	ID        int64  `json:"id"`
	Code      string `json:"code"`
	DailyRate int64  `json:"dailyRate"`
	Stock     int    `json:"stock"`
}
type RentalItem struct {
	ID          int64 `json:"id"`
	EquipmentID int64 `json:"equipmentId"`
	Quantity    int   `json:"quantity"`
	DailyRate   int64 `json:"dailyRate"`
}
type RentalOrder struct {
	ID        int64      `json:"id"`
	Reference string     `json:"reference"`
	Days      int        `json:"days"`
	Premium   bool       `json:"premium"`
	Total     int64      `json:"total"`
	LateFee   int64      `json:"lateFee"`
	Status    Status     `json:"status"`
	Item      RentalItem `json:"item"`
}
type RentalRequest struct {
	Reference   string `json:"reference"`
	EquipmentID int64  `json:"equipmentId"`
	Quantity    int    `json:"quantity"`
	Days        int    `json:"days"`
	Premium     bool   `json:"premium"`
}
type EquipmentRepository interface {
	Create(context.Context, Equipment) (Equipment, error)
	FindByID(context.Context, int64) (Equipment, error)
	Update(context.Context, Equipment) error
	Delete(context.Context, int64) error
}
type RentalRepository interface {
	Create(context.Context, RentalOrder) (RentalOrder, error)
	FindByID(context.Context, int64) (RentalOrder, error)
	FindRentals(context.Context, int64, Status) ([]RentalOrder, error)
	MarkReturned(context.Context, int64, int64) error
	Delete(context.Context, int64) error
}
type RentalPolicy interface {
	IsAllowed(context.Context, Equipment, int, int) (bool, error)
}
type PolicyFunc func(context.Context, Equipment, int, int) (bool, error)

func (f PolicyFunc) IsAllowed(ctx context.Context, e Equipment, q, d int) (bool, error) {
	return f(ctx, e, q, d)
}
