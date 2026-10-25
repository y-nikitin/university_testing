package membership

import "errors"

type Request struct {
	Months, Visits            int
	OffPeak, CorporateClaimed bool
}
type Result struct {
	Status                          string
	Months, Visits, DiscountPercent int
	EntryFee, Total                 int64
}
type ClubCapacity interface{ AcceptsMembers() (bool, error) }
type CorporateRegistry interface{ IsEligible() (bool, error) }
type MembershipRepository interface{ Save(Request, Result) error }
type Service struct {
	capacity    ClubCapacity
	corporate   CorporateRegistry
	memberships MembershipRepository
}

func NewService(capacity ClubCapacity, corporate CorporateRegistry, memberships MembershipRepository) (*Service, error) {
	if capacity == nil || corporate == nil || memberships == nil {
		return nil, errors.New("Усі залежності обов’язкові")
	}
	return &Service{capacity, corporate, memberships}, nil
}
func (s *Service) Process(r *Request) (Result, error) {
	if r == nil {
		return Result{}, errors.New("Заявку не задано")
	}
	if r.Months < 1 || r.Months > 12 || r.Visits < 1 || r.Visits > 29 {
		return Result{}, errors.New("Недопустиме значення заявки")
	}
	allowed, err := s.capacity.AcceptsMembers()
	if err != nil {
		return Result{}, err
	}
	if !allowed {
		return Result{Status: "Немає доступних абонементів"}, nil
	}
	eligible := false
	if r.CorporateClaimed {
		eligible, err = s.corporate.IsEligible()
		if err != nil {
			return Result{}, err
		}
	}
	discount := 0
	if r.OffPeak && eligible {
		discount = 20
	} else if eligible {
		discount = 15
	} else if r.OffPeak {
		discount = 10
	}
	monthly := int64(200000) + int64(max(0, r.Visits-12))*5000
	base := int64(r.Months) * monthly
	fee := int64(10000)
	if r.Months >= 11 {
		fee = 0
	}
	result := Result{"Активовано", r.Months, r.Visits, discount, fee, base - base*int64(discount)/100 + fee}
	if err := s.memberships.Save(*r, result); err != nil {
		return Result{}, err
	}
	return result, nil
}
