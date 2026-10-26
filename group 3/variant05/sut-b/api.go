package rental

import (
	"encoding/json"
	"errors"
	"github.com/jackc/pgx/v5/pgconn"
	"io"
	"net/http"
 "path/filepath"
	"strconv"
)

type APIError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
func writeError(w http.ResponseWriter, err error) {
	status, code, message := 500, "INTERNAL_ERROR", "Внутрішня помилка сервера"
	var sql *pgconn.PgError
	switch {
	case errors.Is(err, ErrNotFound):
		status, code, message = 404, "NOT_FOUND", err.Error()
	case errors.Is(err, ErrConflict):
		status, code, message = 409, "RENTAL_CONFLICT", err.Error()
	case errors.Is(err, ErrInvalid):
		status, code, message = 400, "INVALID_REQUEST", "Некоректний запит"
	case errors.Is(err, ErrPolicy):
		status, code, message = 502, "POLICY_UNAVAILABLE", "Зовнішня перевірка недоступна"
	case errors.As(err, &sql):
		switch sql.Code {
		case "23505":
			status, code, message = 409, "DUPLICATE_RESOURCE", "Ресурс із таким кодом уже існує"
		case "23503":
			status, code, message = 409, "RESOURCE_CONFLICT", "Конфлікт пов’язаних даних"
		case "23514", "23502", "22003":
			status, code, message = 400, "INVALID_REQUEST", "Некоректні дані ресурсу"
		default:
			code, message = "DATABASE_ERROR", "Помилка роботи з БД"
		}
	}
	writeJSON(w, status, APIError{code, message})
}
func readJSON(w http.ResponseWriter, r *http.Request, value any) error {
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1048576))
	if err := decoder.Decode(value); err != nil {
		return ErrInvalid
	}
	var extra any
	if decoder.Decode(&extra) != io.EOF {
		return ErrInvalid
	}
	return nil
}
func resourceID(r *http.Request) (int64, error) {
	id, err := strconv.ParseInt(r.PathValue("id"), 10, 64)
	if err != nil || id <= 0 {
		return 0, ErrInvalid
	}
	return id, nil
}
func method(w http.ResponseWriter, r *http.Request, allowed string) bool {
	if r.Method == allowed {
		return true
	}
	w.Header().Set("Allow", allowed)
	writeJSON(w, 405, APIError{"METHOD_NOT_ALLOWED", "Метод не підтримується"})
	return false
}
func NewHandler(e EquipmentRepository, rentals RentalRepository, s *RentalService) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/equipment", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, "POST") {
			return
		}
		var input *Equipment
		if err := readJSON(w, r, &input); err != nil || input == nil {
			writeError(w, ErrInvalid)
			return
		}
		saved, err := e.Create(r.Context(), *input)
		if err != nil {
			writeError(w, err)
			return
		}
		w.Header().Set("Location", "/equipment/"+strconv.FormatInt(saved.ID, 10))
		writeJSON(w, 201, saved)
	})
	mux.HandleFunc("/equipment/{id}", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, "GET") {
			return
		}
		id, err := resourceID(r)
		if err != nil {
			writeError(w, err)
			return
		}
		saved, err := e.FindByID(r.Context(), id)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, 200, saved)
	})
	mux.HandleFunc("/rentals", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, "POST") {
			return
		}
		var input *RentalRequest
		if err := readJSON(w, r, &input); err != nil || input == nil {
			writeError(w, ErrInvalid)
			return
		}
		saved, err := s.Rent(r.Context(), *input)
		if err != nil {
			writeError(w, err)
			return
		}
		w.Header().Set("Location", "/rentals/"+strconv.FormatInt(saved.ID, 10))
		writeJSON(w, 201, saved)
	})
	mux.HandleFunc("/rentals/{id}", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, "GET") {
			return
		}
		id, err := resourceID(r)
		if err != nil {
			writeError(w, err)
			return
		}
		saved, err := rentals.FindByID(r.Context(), id)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, 200, saved)
	})
	mux.HandleFunc("/rentals/{id}/return", func(w http.ResponseWriter, r *http.Request) {
		if !method(w, r, "POST") {
			return
		}
		id, err := resourceID(r)
		if err != nil {
			writeError(w, err)
			return
		}
		saved, err := s.ReturnRental(r.Context(), id)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, 200, saved)
	})
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
        switch r.URL.Path {
        case "/", "/index.html", "/styles.css", "/app.js":
            name := r.URL.Path[1:]
            if name == "" { name = "index.html" }
            http.ServeFile(w, r, filepath.Join("..", "sut-c-ui", name))
            return
        }

		writeJSON(w, 404, APIError{"NOT_FOUND", "Маршрут не знайдено"})
	})
	return mux
}
