package rental

import (
	"context"
	"github.com/jackc/pgx/v5/pgxpool"
	"net"
	"net/url"
	"os"
)

func Env(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}
func OpenDatabase(ctx context.Context) (*pgxpool.Pool, error) {
	u := url.URL{Scheme: "postgres", Host: net.JoinHostPort(Env("DB_HOST", "localhost"), Env("DB_PORT", "5432")), Path: "/" + Env("DB_NAME", "rental_variant02_test"), User: url.UserPassword(Env("DB_USER", "rental_student"), Env("DB_PASSWORD", ""))}
	query := u.Query()
	query.Set("sslmode", Env("DB_SSLMODE", "disable"))
	u.RawQuery = query.Encode()
	db, err := pgxpool.New(ctx, u.String())
	if err != nil {
		return nil, err
	}
	if err = db.Ping(ctx); err != nil {
		db.Close()
		return nil, err
	}
	return db, nil
}
