package rental

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"time"
)

type HTTPRentalPolicy struct {
	endpoint string
	client   *http.Client
}

func NewHTTPRentalPolicy(base string, timeout time.Duration) (*HTTPRentalPolicy, error) {
	u, err := url.Parse(base)
	if err != nil || u == nil || (u.Scheme != "http" && u.Scheme != "https") || u.Hostname() == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || (u.Path != "" && u.Path != "/") || timeout <= 0 {
		return nil, ErrInvalid
	}
	if u.Port() != "" {
		port, err := strconv.Atoi(u.Port())
		if err != nil || port < 1 || port > 65535 {
			return nil, ErrInvalid
		}
	}
	u.Path = "/rental-policy"
	return &HTTPRentalPolicy{u.String(), &http.Client{Timeout: timeout, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}}, nil
}
func (p *HTTPRentalPolicy) IsAllowed(ctx context.Context, e Equipment, q, d int) (bool, error) {
	body, _ := json.Marshal(map[string]any{"equipmentId": e.ID, "quantity": q, "days": d})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, p.endpoint, bytes.NewReader(body))
	if err != nil {
		return false, fmt.Errorf("%w: %v", ErrPolicy, err)
	}
	req.Header.Set("Content-Type", "application/json")
	response, err := p.client.Do(req)
	if err != nil {
		return false, fmt.Errorf("%w: %v", ErrPolicy, err)
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return false, ErrPolicy
	}
	var result struct {
		Allowed *bool `json:"allowed"`
	}
	decoder := json.NewDecoder(io.LimitReader(response.Body, 1048576))
	if err = decoder.Decode(&result); err != nil || result.Allowed == nil {
		return false, ErrPolicy
	}
	var extra any
	if decoder.Decode(&extra) != io.EOF {
		return false, ErrPolicy
	}
	return *result.Allowed, nil
}
