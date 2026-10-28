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
	u := url.URL{Scheme: "postgres", Host: net.JoinHostPort(Env("SECURITY_DB_HOST", "localhost"), Env("SECURITY_DB_PORT", "55433")), Path: "/" + Env("SECURITY_DB_NAME", "rental_variant02_security"), User: url.UserPassword(Env("SECURITY_DB_USER", "rental_student"), Env("SECURITY_DB_PASSWORD", ""))}
	query := u.Query()
	query.Set("sslmode", Env("SECURITY_DB_SSLMODE", "disable"))
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
