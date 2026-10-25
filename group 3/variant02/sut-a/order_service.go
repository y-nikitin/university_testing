package order

import "errors"

type Request struct {
	Quantity, UnitPrice    int
	Premium, CouponClaimed bool
}
type Result struct {
	Status                    string
	Quantity, DiscountPercent int
	Goods, Delivery, Total    int64
}
type StockProvider interface{ AvailableUnits() (int, error) }
type CouponValidator interface{ IsValid() (bool, error) }
type OrderRepository interface{ Save(Request, Result) error }
type Service struct {
	stock   StockProvider
	coupons CouponValidator
	orders  OrderRepository
}

func NewService(stock StockProvider, coupons CouponValidator, orders OrderRepository) (*Service, error) {
	if stock == nil || coupons == nil || orders == nil {
		return nil, errors.New("Усі залежності обов’язкові")
	}
	return &Service{stock, coupons, orders}, nil
}
func (s *Service) Process(r *Request) (Result, error) {
	if r == nil {
		return Result{}, errors.New("Заявку не задано")
	}
	if r.Quantity < 1 || r.Quantity > 19 || r.UnitPrice < 100 || r.UnitPrice > 100000 {
		return Result{}, errors.New("Недопустиме значення заявки")
	}
	available, err := s.stock.AvailableUnits()
	if err != nil {
		return Result{}, err
	}
	if available < 0 {
		return Result{}, errors.New("Від’ємний залишок товару")
	}
	if available < r.Quantity {
		return Result{Status: "Недостатньо товару"}, nil
	}
	coupon := false
	if r.CouponClaimed {
		coupon, err = s.coupons.IsValid()
		if err != nil {
			return Result{}, err
		}
	}
	discount := 0
	if r.Premium && coupon {
		discount = 15
	} else if r.Premium {
		discount = 10
	} else if coupon {
		discount = 5
	}
	subtotal := int64(r.Quantity) * int64(r.UnitPrice)
	goods := subtotal - subtotal*int64(discount)/100
	delivery := int64(5000)
	if goods > 50000 {
		delivery = 0
	}
	result := Result{"Оформлено", r.Quantity, discount, goods, delivery, goods + delivery}
	if err := s.orders.Save(*r, result); err != nil {
		return Result{}, err
	}
	return result, nil
}
