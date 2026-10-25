package main

import (
	"context"
	"errors"
	rental "example.edu/rentalvariant05"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	db, err := rental.OpenDatabase(ctx)
	cancel()
	if err != nil {
		log.Fatal("Не вдалося підключитися до БД")
	}
	defer db.Close()
	var policy rental.RentalPolicy = rental.PolicyFunc(func(context.Context, rental.Equipment, int, int) (bool, error) { return true, nil })
	if endpoint := os.Getenv("RENTAL_POLICY_URL"); endpoint != "" {
		policy, err = rental.NewHTTPRentalPolicy(endpoint, 2*time.Second)
		if err != nil {
			log.Fatal("Некоректна адреса HTTP-політики")
		}
	}
	equipment := &rental.PostgresEquipmentRepository{DB: db}
	rentals := &rental.PostgresRentalRepository{DB: db}
	service, err := rental.NewRentalService(equipment, rentals, policy)
	if err != nil {
		log.Fatal(err)
	}
	server := &http.Server{Addr: net.JoinHostPort(rental.Env("API_HOST", "127.0.0.1"), rental.Env("API_PORT", "8080")), Handler: rental.NewHandler(equipment, rentals, service), ReadHeaderTimeout: 5 * time.Second}
	stopped := make(chan os.Signal, 1)
	signal.Notify(stopped, os.Interrupt, syscall.SIGTERM)
	defer signal.Stop(stopped)
	go func() {
		<-stopped
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := server.Shutdown(ctx); err != nil {
			_ = server.Close()
		}
	}()
	log.Printf("API запущено: http://%s", server.Addr)
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Print("Помилка HTTP-сервера")
	}
}
